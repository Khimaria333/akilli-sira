package com.akillisira.app;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uygulamanın beyni.
 *  - Servisten "şarkı değişti / şu saniyedeyiz" haberlerini alır
 *  - Sıradaki şarkıyı hesaplar (BPM, enerji, ruh hali, dil)
 *  - Zamanı gelince sesi yumuşakça kısıp YT Music'e sıradakini çaldırır, sonra sesi geri açar
 *  - Keşfet ekranı için ruh haline göre öneri listesi hazırlar
 * Tüm durum ana (UI) iş parçacığında tutulur; ağ işleri arka planda yapılır.
 */
public final class Engine {

    public interface Listener { void onEngineChanged(); }

    public static final String YTM = "com.google.android.apps.youtube.music";

    // Geçiş yöntemleri
    public static final int SILENT_UNKNOWN = -1, SILENT_NO = 0, SILENT_YES = 1;
    private static final int M_NONE = 0, M_SILENT = 1, M_INTENT = 2, M_SEARCH = 3;

    private static final long NEAR_END_MS = 6000;

    private static Engine instance;

    public static synchronized Engine get(Context c) {
        if (instance == null) instance = new Engine(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService discoverWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newCachedThreadPool();
    private final Deezer deezer = new Deezer();
    private final SharedPreferences prefs;
    private final SharedPreferences pairs;   // şarkı → daha önce önerilenler (kalıcı)
    private final java.util.Random rnd = new java.util.Random();
    private final AudioManager audio;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // ---------- Ekranda gösterilen durum ----------
    public boolean serviceConnected;
    public MediaController controller;
    public String nowTitle, nowArtist;
    public long nowDurationMs, nowPositionMs;
    public boolean playing;
    public Track seed;
    public Track pick;
    public List<Track> alternatives = new ArrayList<>();
    public boolean computing;
    public String problem;
    public final LinkedList<String> log = new LinkedList<>();

    // Kuyruk: kullanıcının sıraya koyduğu şarkılar (otomatik öneriden önce çalar)
    public final List<Track> queue = new ArrayList<>();
    public int queueVersion;
    private Track autoPick;

    // Uyku zamanlayıcı
    private long sleepUntil;
    public boolean sleepFinishing;

    // Kalıcı veriler, bildirim/widget
    public final Store store;
    private final Notifier notifier;
    private String lastLoggedKey;
    private boolean carActive;

    // Keşfet
    public boolean discoverLoading;
    public List<Track> discoverResults = new ArrayList<>();
    public String discoverProblem;
    public String discoverLabel = "";

    // ---------- İç durum ----------
    private final LinkedHashSet<String> history = new LinkedHashSet<>();
    private final LinkedList<String> recentArtists = new LinkedList<>();
    private final LinkedList<Integer> recentLangs = new LinkedList<>();
    private final Map<Long, Integer> artistLang = new HashMap<>();
    private List<Track> plan = new ArrayList<>();     // Keşfet'ten başlatılan liste
    private List<Track> allRanked = new ArrayList<>();
    private String nowKey;
    private int generation, discoverGeneration;
    private boolean firedForCurrent;
    private long expectSwitchUntil;
    private long lastRemainingMs = Long.MAX_VALUE;
    private boolean uiVisible;

    // Geçiş
    private Track transTarget;
    private int transMethod = M_NONE;
    private long verifyDeadline;
    private boolean fallbackDone;
    private int savedVolume = -1;
    private long restoreDeadline;
    private boolean fadingOut;
    private final Runnable fadeTick = this::fadeStep;
    private long fadeStart, fadeLen;
    private int fadeFrom, fadeTo;

    private Engine(Context app) {
        this.app = app;
        store = new Store(app);
        notifier = new Notifier(app);
        prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
        pairs = app.getSharedPreferences("oneriler", Context.MODE_PRIVATE);
        audio = app.getSystemService(AudioManager.class);
    }

    // =====================================================================
    // Ayarlar
    // =====================================================================

    public boolean isAuto() { return prefs.getBoolean("auto", true); }
    public void setAuto(boolean on) { prefs.edit().putBoolean("auto", on).apply(); notifyChanged(); }

    /** -1 sakinleştir, 0 aynı hava, +1 hareketlendir */
    public int getMode() { return prefs.getInt("mode", 0); }
    public void setMode(int m) {
        if (m == getMode()) return;
        prefs.edit().putInt("mode", m).apply();
        if (nowKey != null) recompute();
        notifyChanged();
    }

    public boolean isTurkish() { return prefs.getBoolean("turkish", true); }
    public void setTurkish(boolean tr) {
        if (tr == isTurkish()) return;
        prefs.edit().putBoolean("turkish", tr).apply();
        log(tr ? "Dil: sadece Türkçe" : "Dil: Global (karışık)");
        if (nowKey != null) recompute();
        notifyChanged();
    }

    public boolean isFade() { return prefs.getBoolean("fade", true); }
    public void setFade(boolean on) { prefs.edit().putBoolean("fade", on).apply(); notifyChanged(); }

    public int getFadeSec() { return prefs.getInt("fadeSec", 4); }
    public void setFadeSec(int s) { prefs.edit().putInt("fadeSec", s).apply(); notifyChanged(); }

    public boolean isReturnToApp() { return prefs.getBoolean("returnToApp", true); }
    public void setReturnToApp(boolean on) { prefs.edit().putBoolean("returnToApp", on).apply(); notifyChanged(); }

    public boolean isNotif() { return prefs.getBoolean("notif", true); }
    public void setNotif(boolean on) { prefs.edit().putBoolean("notif", on).apply(); notifyChanged(); }

    public boolean isNightCalm() { return prefs.getBoolean("nightCalm", true); }
    public void setNightCalm(boolean on) { prefs.edit().putBoolean("nightCalm", on).apply(); refresh(); notifyChanged(); }

    public boolean isCarMode() { return prefs.getBoolean("carMode", true); }
    public void setCarMode(boolean on) { prefs.edit().putBoolean("carMode", on).apply(); notifyChanged(); }

    public boolean isCarActive() { return carActive; }

    public boolean notifAllowed() { return notifier.canPost(); }

    public void forceWidgets() { notifier.forceWidgets(this); }

    public void refreshNotification() { notifier.reset(); notifyChanged(); }

    /** Hava ayarı + bağlam: gece ya da uyku zamanlayıcısı açıkken sakinleştir. */
    public int effectiveMode() {
        int m = getMode();
        if (sleepUntil > 0 || sleepFinishing) return -1;
        int h = hourNow();
        if (m == 0 && isNightCalm() && getVibe() == null && getMoodId() == null && (h >= 23 || h < 6)) return -1;
        return m;
    }

    public boolean nightCalmActive() {
        int h = hourNow();
        return getMode() == 0 && isNightCalm() && getVibe() == null && getMoodId() == null && (h >= 23 || h < 6);
    }

    public int getSilentState() { return prefs.getInt("silent", SILENT_UNKNOWN); }
    public void resetSilent() {
        prefs.edit().putInt("silent", SILENT_UNKNOWN).apply();
        log("Sessiz geçiş bir sonraki geçişte yeniden denenecek");
        notifyChanged();
    }

    public String getMoodId() { return prefs.getString("mood", null); }
    public String getPlaceId() { return prefs.getString("place", null); }

    public String getVibe() { return prefs.getString("vibe", null); }

    public Moods.Target sessionTarget() {
        String v = getVibe();
        if (v != null) return Vibe.parse(v, hourNow()).target(isTurkish());
        return Moods.target(Moods.mood(getMoodId()), Moods.place(getPlaceId()), isTurkish());
    }

    private static int hourNow() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    public void setSession(String moodId, String placeId) {
        prefs.edit().putString("mood", moodId).putString("place", placeId).apply();
        notifyChanged();
    }

    public void clearSession() {
        prefs.edit().remove("mood").remove("place").remove("vibe").apply();
        plan = new ArrayList<>();
        log("Ruh hali modu kapatıldı");
        if (nowKey != null) recompute();
        notifyChanged();
    }

    public void setUiVisible(boolean v) { uiVisible = v; }

    // =====================================================================
    // Servisten gelenler
    // =====================================================================

    public void setServiceConnected(boolean c) { serviceConnected = c; notifyChanged(); }

    public void setController(MediaController c) {
        controller = c;
        if (c == null) playing = false;
        log(c != null ? "YT Music'e bağlandı" : "YT Music bağlantısı kapandı");
        notifyChanged();
    }

    /** YT Music'te şarkı değişti (ya da aynı şarkının bilgisi güncellendi). */
    public void onSongChanged(String title, String artist, long durationMs) {
        if (title == null || title.trim().isEmpty()) return;
        if (artist == null) artist = "";
        String key = Text.key(artist, title);

        if (key.equals(nowKey)) {
            if (durationMs > 0) nowDurationMs = durationMs;
            notifyChanged();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean inSwitch = now < expectSwitchUntil && transTarget != null;
        boolean ytmAutoAdvanced = nowKey != null && !firedForCurrent && lastRemainingMs <= NEAR_END_MS;
        Track planned = pick;

        nowKey = key;
        nowTitle = title;
        nowArtist = artist;
        nowDurationMs = durationMs;
        nowPositionMs = 0;
        firedForCurrent = false;
        lastRemainingMs = Long.MAX_VALUE;

        if (inSwitch) {
            boolean match = titlesMatch(title, transTarget.title);
            if (!match && transMethod == M_SILENT && !fallbackDone) {
                // Sessiz yöntem işe yaramadı; YT Music kendi şarkısına geçti → bağlantıyla düzelt
                prefs.edit().putInt("silent", SILENT_NO).apply();
                log("Sessiz geçiş desteklenmiyor, YT Music açılarak geçiliyor");
                fallbackDone = true;
                firedForCurrent = true;
                expectSwitchUntil = now + 15000;
                launchIntent(transTarget);
                return;
            }
            if (match && transMethod == M_SILENT && getSilentState() != SILENT_YES) {
                prefs.edit().putInt("silent", SILENT_YES).apply();
                log("Sessiz geçiş çalışıyor ✓");
            }
            expectSwitchUntil = 0;
            transMethod = M_NONE;
            remember(key, artist, transTarget);
            log(match ? "▶ " + title : "▶ YT Music farklı bir sürüm açtı: " + title);
            transTarget = null;
            fadeIn();
            recompute();
            return;
        }

        if (ytmAutoAdvanced && isAuto() && planned != null && !key.equals(planned.key())) {
            log("YT Music kendi şarkısına geçti, düzeltiliyor");
            fire(planned, false);
            return;
        }

        // Kullanıcı kendisi bir şarkı açtı ya da geçti
        restoreVolumeNow();
        remember(key, artist, null);
        log("♪ " + artist + " – " + title);
        recompute();
    }

    /** Servis her yarım saniyede bir çalma konumunu bildirir. */
    public void onPoll(long positionMs, long durationMs, boolean isPlaying) {
        nowPositionMs = positionMs;
        if (durationMs > 0) nowDurationMs = durationMs;
        playing = isPlaying;
        if (nowDurationMs > 0) lastRemainingMs = nowDurationMs - positionMs;
        long now = SystemClock.elapsedRealtime();

        if (!isPlaying && fadingOut) restoreVolumeNow();   // kullanıcı durdurdu

        if (sleepUntil > 0 && !sleepFinishing && SystemClock.elapsedRealtime() >= sleepUntil) {
            sleepFinishing = true;
            log("😴 Uyku zamanı: bu şarkı bitince müzik duracak");
        }

        boolean stopHere = sleepFinishing;
        if (isPlaying && !firedForCurrent && nowDurationMs > 30000 && (stopHere || (isAuto() && pick != null))) {
            long fadeMs = isFade() || stopHere ? Math.max(getFadeSec(), stopHere ? 6 : 0) * 1000L : 0;
            if (fadeMs > 0 && !fadingOut && savedVolume < 0 && lastRemainingMs <= fadeMs + 250) {
                startFadeOut(Math.max(500, lastRemainingMs - 350));
            }
            long switchAt = fadeMs > 0 ? 350 : 1500;
            if (lastRemainingMs <= switchAt) {
                if (stopHere) stopForSleep();
                else fire(pick, false);
            }
        }

        // Sessiz yöntem cevap vermediyse bağlantıyla geç
        if (transMethod == M_SILENT && !fallbackDone && verifyDeadline > 0 && now > verifyDeadline) {
            prefs.edit().putInt("silent", SILENT_NO).apply();
            log("Sessiz geçişe yanıt gelmedi, YT Music açılarak geçiliyor");
            fallbackDone = true;
            launchIntent(transTarget);
        }

        // Güvenlik: ses bir şekilde kısık kaldıysa geri aç
        if (savedVolume >= 0 && restoreDeadline > 0 && now > restoreDeadline) restoreVolumeNow();
        notifyChanged();
    }

    // =====================================================================
    // Kullanıcıdan gelenler
    // =====================================================================

    /** Önerilerden birini hemen sıradaki yap (kuyruğun en başına koyar). */
    public void choose(Track t) {
        removeFromQueueByKey(t.key());
        queue.add(0, t);
        queueVersion++;
        List<Track> rest = new ArrayList<>();
        for (Track a : alternatives) if (a != t) rest.add(a);
        alternatives = rest;
        if (seed != null) rememberPair(seed.key(), t.key());
        log("Sıradaki: " + t);
        refreshPick();
        notifyChanged();
    }

    /** Kuyruğun sonuna ekle. */
    public void addToQueue(Track t) {
        if (indexInQueue(t.key()) >= 0) return;
        queue.add(t);
        queueVersion++;
        List<Track> rest = new ArrayList<>();
        for (Track a : alternatives) if (a != t) rest.add(a);
        alternatives = rest;
        log("Sıraya eklendi: " + t);
        refreshPick();
        notifyChanged();
    }

    public void moveInQueue(int i, int delta) {
        int j = i + delta;
        if (i < 0 || j < 0 || i >= queue.size() || j >= queue.size()) return;
        Collections.swap(queue, i, j);
        queueVersion++;
        refreshPick();
        notifyChanged();
    }

    public void removeFromQueue(int i) {
        if (i < 0 || i >= queue.size()) return;
        queue.remove(i);
        queueVersion++;
        refreshPick();
        notifyChanged();
    }

    public void clearQueue() {
        queue.clear();
        queueVersion++;
        refreshPick();
        notifyChanged();
    }

    public boolean pickFromQueue() {
        return !queue.isEmpty() && pick == queue.get(0);
    }

    private int indexInQueue(String key) {
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).key().equals(key)) return i;
        return -1;
    }

    private void removeFromQueueByKey(String key) {
        int i = indexInQueue(key);
        if (i >= 0) { queue.remove(i); queueVersion++; }
    }

    private void refreshPick() {
        pick = !queue.isEmpty() ? queue.get(0) : autoPick;
        if (pick != null) prefetchVideoId(pick);
    }

    /** 👎 Sıradakini beğenmedim: bir daha önerme, yerine başkasını koy. */
    public void dislikeNext() {
        Track t = pick;
        if (t == null) return;
        store.dislike(t.key(), t.artist, t.title);
        log("👎 " + t + " (bir daha önerilmeyecek)");
        if (pickFromQueue()) {
            queue.remove(0);
            queueVersion++;
        } else {
            autoPick = alternatives.isEmpty() ? null : alternatives.get(0);
            List<Track> rest = new ArrayList<>(alternatives);
            if (!rest.isEmpty()) rest.remove(0);
            alternatives = rest;
            if (autoPick == null && nowKey != null) { recompute(); return; }
        }
        refreshPick();
        notifyChanged();
    }

    /** 👎 Çalanı beğenmedim: bir daha önerme ve hemen sıradakine geç. */
    public void dislikeNow() {
        if (nowTitle == null) return;
        store.dislike(nowKey, nowArtist, nowTitle);
        log("👎 " + nowArtist + " – " + nowTitle + " (geçiliyor)");
        if (pick != null) fire(pick, true);
        notifyChanged();
    }

    public void undislike(String key) {
        store.undislike(key);
        notifyChanged();
    }

    // ---- Uyku zamanlayıcı ----

    public void setSleep(int minutes) {
        if (minutes <= 0) {
            sleepUntil = 0;
            sleepFinishing = false;
            log("Uyku zamanlayıcı kapatıldı");
        } else {
            sleepUntil = SystemClock.elapsedRealtime() + minutes * 60000L;
            sleepFinishing = false;
            log("😴 Uyku zamanlayıcı: " + minutes + " dk");
            refresh();   // sakin şarkılara yönel
        }
        notifyChanged();
    }

    /** Kalan süre (ms); kapalıysa -1, süre dolduysa 0. */
    public long sleepRemainingMs() {
        if (sleepFinishing) return 0;
        if (sleepUntil <= 0) return -1;
        return Math.max(0, sleepUntil - SystemClock.elapsedRealtime());
    }

    private void stopForSleep() {
        firedForCurrent = true;
        sleepUntil = 0;
        sleepFinishing = false;
        try {
            if (controller != null) controller.getTransportControls().pause();
        } catch (Exception ignored) {}
        log("😴 İyi uykular, müzik durduruldu");
        main.postDelayed(this::restoreVolumeNow, 1500);
        notifyChanged();
    }

    // ---- Araba ----

    public void onCarConnected(String name) {
        if (!isCarMode() || carActive) return;
        carActive = true;
        if ("araba".equals(getPlaceId()) && getVibe() == null) return;
        prefs.edit()
                .putString("carPrevMood", getMoodId())
                .putString("carPrevPlace", getPlaceId())
                .putString("carPrevVibe", getVibe())
                .putBoolean("carSwitched", true)
                .remove("vibe")
                .putString("mood", getMoodId() != null ? getMoodId() : "mutlu")
                .putString("place", "araba")
                .apply();
        log("🚗 " + name + " bağlandı: Arabada modu");
        refresh();
        notifyChanged();
    }

    public void onCarDisconnected() {
        if (!carActive) return;
        carActive = false;
        if (prefs.getBoolean("carSwitched", false)) {
            SharedPreferences.Editor ed = prefs.edit();
            putOrRemove(ed, "mood", prefs.getString("carPrevMood", null));
            putOrRemove(ed, "place", prefs.getString("carPrevPlace", null));
            putOrRemove(ed, "vibe", prefs.getString("carPrevVibe", null));
            ed.putBoolean("carSwitched", false).apply();
            log("🚗 Araba bağlantısı kesildi: önceki moda dönüldü");
            refresh();
        }
        notifyChanged();
    }

    private static void putOrRemove(SharedPreferences.Editor ed, String k, String v) {
        if (v == null) ed.remove(k); else ed.putString(k, v);
    }

    /** Durdur / devam et (YT Music'te). */
    public void togglePlay() {
        if (controller == null) return;
        try {
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
            playing = !playing;
        } catch (Exception ignored) {}
        notifyChanged();
    }

    /** YT Music'te önceki şarkı (ya da şarkının başı). */
    public void previous() {
        if (controller == null) return;
        try { controller.getTransportControls().skipToPrevious(); } catch (Exception ignored) {}
    }

    public void seekTo(long ms) {
        if (controller == null || nowDurationMs <= 0) return;
        long t = Math.max(0, Math.min(nowDurationMs - 2000, ms));
        try { controller.getTransportControls().seekTo(t); } catch (Exception ignored) {}
        nowPositionMs = t;
        lastRemainingMs = nowDurationMs - t;
        if (fadingOut || savedVolume >= 0) restoreVolumeNow();
        notifyChanged();
    }

    public void skipNow() {
        if (pick != null) fire(pick, true);
    }

    public void refresh() {
        if (nowKey != null) recompute();
    }

    /** Keşfet listesinden bir şarkıyı hemen başlatır; listenin geri kalanı sıraya öncelikli girer. */
    public void playNow(Track t, List<Track> fromList) {
        List<Track> rest = new ArrayList<>(fromList);
        rest.remove(t);
        plan = new ArrayList<>();
        queue.clear();
        queue.addAll(Scorer.flowOrder(t, rest));   // tempo ve enerjiye göre akıcı sıra
        queueVersion++;
        fire(t, true);
        refreshPick();
        notifyChanged();
    }

    /** Geçişe kalan süre (ms); bilinmiyorsa -1. */
    public long msToSwitch() {
        if (pick == null || !isAuto() || nowDurationMs <= 0 || lastRemainingMs == Long.MAX_VALUE) return -1;
        return Math.max(0, lastRemainingMs - (isFade() ? 350 : 1500));
    }

    // =====================================================================
    // Geçiş: sesi kıs → sıradakini çal → sesi aç
    // =====================================================================

    private void fire(Track t, boolean userInitiated) {
        firedForCurrent = true;
        expectSwitchUntil = SystemClock.elapsedRealtime() + 20000;
        transTarget = t;
        fallbackDone = false;
        verifyDeadline = 0;
        if (t.videoId != null) {
            doSwitch(t, userInitiated);
        } else {
            net.submit(() -> {
                if (t.videoId == null) t.videoId = YtMusic.findVideoId(t.artist, t.title);
                main.post(() -> doSwitch(t, userInitiated));
            });
        }
    }

    private void doSwitch(Track t, boolean userInitiated) {
        // Kullanıcı kendisi başlattıysa doğrudan bağlantıyı aç (en güvenilir yol)
        boolean trySilent = !userInitiated && controller != null && t.videoId != null
                && getSilentState() != SILENT_NO;
        if (trySilent) {
            try {
                controller.getTransportControls().playFromUri(Uri.parse(YtMusic.watchUrl(t.videoId)), new Bundle());
                transMethod = M_SILENT;
                verifyDeadline = SystemClock.elapsedRealtime() + 3000;
                log("→ " + t + " (sessiz geçiş)");
                notifyChanged();
                return;
            } catch (Exception e) {
                prefs.edit().putInt("silent", SILENT_NO).apply();
            }
        }
        launchIntent(t);
    }

    private void launchIntent(Track t) {
        if (t == null) return;
        if (t.videoId != null) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(YtMusic.watchUrl(t.videoId)));
                i.setPackage(YTM);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(i);
                transMethod = M_INTENT;
                log("→ " + t);
                if (uiVisible && isReturnToApp()) main.postDelayed(this::bringAppBack, 1800);
                notifyChanged();
                return;
            } catch (Exception e) {
                log("YT Music açılamadı: " + e.getMessage());
            }
        } else {
            log("YT Music'te bulunamadı, arama ile deneniyor: " + t);
        }
        // Son çare: aramayla çal
        if (controller != null) {
            String q = Text.mainArtist(t.artist) + " " + Text.cleanTitle(t.title);
            Bundle ex = new Bundle();
            ex.putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE);
            ex.putString(MediaStore.EXTRA_MEDIA_TITLE, t.title);
            ex.putString(MediaStore.EXTRA_MEDIA_ARTIST, t.artist);
            ex.putString(SearchManager.QUERY, q);
            try {
                controller.getTransportControls().playFromSearch(q, ex);
                transMethod = M_SEARCH;
            } catch (Exception e) {
                log("Geçilemedi: " + e.getMessage());
                expectSwitchUntil = 0;
                restoreVolumeNow();
            }
        } else {
            log("YT Music bağlı değil, geçilemedi");
            expectSwitchUntil = 0;
            restoreVolumeNow();
        }
        notifyChanged();
    }

    private void bringAppBack() {
        try {
            Intent i = new Intent(app, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            app.startActivity(i);
        } catch (Exception ignored) {}
    }

    // ---------- Ses yumuşatma ----------

    private void startFadeOut(long ms) {
        if (audio == null) return;
        try {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Exception e) { savedVolume = -1; return; }
        if (savedVolume <= 1) { savedVolume = -1; return; }
        fadingOut = true;
        restoreDeadline = SystemClock.elapsedRealtime() + ms + 20000;
        startRamp(savedVolume, Math.max(1, (int) Math.round(savedVolume * 0.2)), ms);
    }

    private void fadeIn() {
        if (savedVolume < 0 || audio == null) return;
        fadingOut = false;
        int cur;
        try { cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC); } catch (Exception e) { cur = savedVolume; }
        startRamp(cur, savedVolume, 1800);
    }

    private void restoreVolumeNow() {
        main.removeCallbacks(fadeTick);
        if (savedVolume >= 0 && audio != null) {
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0); } catch (Exception ignored) {}
        }
        savedVolume = -1;
        fadingOut = false;
        restoreDeadline = 0;
    }

    private void startRamp(int from, int to, long ms) {
        main.removeCallbacks(fadeTick);
        fadeFrom = from;
        fadeTo = to;
        fadeLen = Math.max(200, ms);
        fadeStart = SystemClock.elapsedRealtime();
        main.post(fadeTick);
    }

    private void fadeStep() {
        if (audio == null) return;
        double f = Math.min(1.0, (SystemClock.elapsedRealtime() - fadeStart) / (double) fadeLen);
        double eased = f * f * (3 - 2 * f);   // yumuşak eğri
        int v = (int) Math.round(fadeFrom + (fadeTo - fadeFrom) * eased);
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Exception ignored) {}
        if (f < 1.0) {
            main.postDelayed(fadeTick, 60);
        } else if (!fadingOut) {
            savedVolume = -1;      // ses açma tamamlandı
            restoreDeadline = 0;
        }
    }

    // =====================================================================
    // Sıradakini hesaplama
    // =====================================================================

    private void recompute() {
        final int gen = ++generation;
        final String title = nowTitle, artist = nowArtist;
        final Set<String> hist = new HashSet<>(history);
        hist.addAll(store.dislikedKeys());
        for (Track q : queue) hist.add(q.key());
        final Scorer.Ctx ctx = new Scorer.Ctx();
        ctx.mode = effectiveMode();
        ctx.artistDislikes = store.dislikedArtists();
        ctx.target = sessionTarget();
        ctx.recentArtists = new ArrayList<>(recentArtists);
        final boolean turkish = isTurkish();
        if (!turkish) ctx.preferLang = balancingLang();
        final List<Track> planCopy = new ArrayList<>(plan);

        computing = true;
        problem = null;
        autoPick = null;
        refreshPick();
        seed = null;
        alternatives = new ArrayList<>();
        allRanked = new ArrayList<>();
        notifyChanged();

        worker.submit(() -> {
            try {
                Track s = deezer.findSeed(title, artist);
                if (s == null) {
                    s = new Track();
                    s.title = title;
                    s.artist = artist;
                }
                deezer.fillDetails(s);
                s.lang = Lang.detect(s.title, s.artist);
                ctx.seed = s;
                List<Track> ranked = buildQueue(s, ctx, hist, planCopy, turkish);
                final Track seedF = s;
                main.post(() -> {
                    if (gen != generation) return;
                    computing = false;
                    seed = seedF;
                    allRanked = ranked;
                    logPlay(seedF);
                    if (ranked.isEmpty()) {
                        problem = turkish ? "Uygun Türkçe şarkı bulunamadı. Global moda geçmeyi dene."
                                : "Uygun şarkı bulunamadı";
                    } else {
                        autoPick = ranked.get(0);
                        alternatives = new ArrayList<>(ranked.subList(1, Math.min(9, ranked.size())));
                        rememberPair(seedF.key(), autoPick.key());
                        if (queue.isEmpty()) log("Sıradaki: " + autoPick);
                    }
                    refreshPick();
                    notifyChanged();
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                main.post(() -> {
                    if (gen != generation) return;
                    computing = false;
                    problem = "İnternet bağlantısını kontrol et (" + msg + ")";
                    Track bare = new Track();
                    bare.title = title;
                    bare.artist = artist;
                    logPlay(bare);
                    log("Hesaplanamadı: " + msg);
                    notifyChanged();
                });
            }
        });
    }

    /** Arka planda: adayları topla, dili ayıkla, detaylarını çek, puanla. */
    private List<Track> buildQueue(Track s, Scorer.Ctx ctx, Set<String> hist, List<Track> planList,
                                   boolean turkish) {
        List<Track> pool = new ArrayList<>(planList);
        if (s.artistId > 0) {
            pool.addAll(safe(() -> deezer.artistRadio(s.artistId, 30)));
            // Her seferinde farklı 6 benzer sanatçı, her birinin ilk 12 şarkısından rastgele 4'ü
            List<Long> rels = new ArrayList<>(safe(() -> deezer.related(s.artistId, 15)));
            Collections.shuffle(rels, rnd);
            for (Long rel : rels.subList(0, Math.min(6, rels.size()))) {
                pool.addAll(sample(safe(() -> deezer.artistTop(rel, 12, "related")), 4));
            }
            pool.addAll(sample(safe(() -> deezer.artistTop(s.artistId, 12, "same")), 3));
        }
        // Ruh hali ya da Türkçe mod için ek kaynak: çalma listeleri
        List<String> queries = new ArrayList<>();
        if (ctx.target != null) queries.addAll(ctx.target.queries);
        else if (turkish) queries.addAll(Moods.defaultTurkishQueries(s));
        boolean seedForeign = turkish && s.lang == Lang.FOREIGN;
        int maxQ = seedForeign ? 3 : 2;
        for (int i = 0; i < queries.size() && i < maxQ; i++) {
            pool.addAll(sample(playlistPool(queries.get(i), turkish, 1, 80), 30));
        }

        List<Track> list = dedupe(pool, s.key(), hist);
        classifyLanguages(list);
        if (turkish) list = keepTurkish(list);

        list = preselect(list, 40);
        deezer.fillDetailsAll(list);
        return applyVariety(Scorer.rank(ctx, list), s.key());
    }

    // =====================================================================
    // Çeşitlilik: aynı şarkıya hep aynı öneri gelmesin
    // =====================================================================

    /** Ön eleme: kaynak + popülerlik + rastgele pay; hep aynı hitler öne çıkmasın. */
    private List<Track> preselect(List<Track> list, int n) {
        Map<Track, Double> w = new HashMap<>();
        for (Track t : list) w.put(t, Scorer.preScore(t) + rnd.nextDouble() * 14);
        List<Track> out = new ArrayList<>(list);
        out.sort((a, b) -> Double.compare(w.get(b), w.get(a)));
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    /**
     * 1) Bu şarkıdan sonra daha önce önerilenlere ceza (en yenisi en ağır)
     * 2) Puanlara küçük rastgele pay
     * 3) En iyiye yakın (10 puan içinde) en fazla 5 aday arasından ağırlıklı rastgele seçim
     */
    private List<Track> applyVariety(List<Track> ranked, String seedKey) {
        if (ranked.isEmpty()) return ranked;
        List<String> before = pairList(seedKey);
        for (Track t : ranked) {
            int idx = before.indexOf(t.key());
            if (idx >= 0) t.score -= Math.max(8, 22 - idx * 3);
            t.score += rnd.nextDouble() * 6 - 3;
        }
        List<Track> out = new ArrayList<>(ranked);
        out.sort((a, b) -> Double.compare(b.score, a.score));

        double top = out.get(0).score;
        int window = 0;
        while (window < out.size() && window < 5 && out.get(window).score >= top - 10) window++;
        double sum = 0;
        double[] wts = new double[window];
        for (int i = 0; i < window; i++) { wts[i] = Math.exp((out.get(i).score - top) / 4.0); sum += wts[i]; }
        double r = rnd.nextDouble() * sum;
        int chosen = 0;
        for (int i = 0; i < window; i++) { r -= wts[i]; if (r <= 0) { chosen = i; break; } }
        if (chosen > 0) out.add(0, out.remove(chosen));
        return out;
    }

    private List<String> pairList(String seedKey) {
        String v = pairs.getString(seedKey, null);
        List<String> out = new ArrayList<>();
        if (v != null && !v.isEmpty()) Collections.addAll(out, v.split("\t"));
        return out;
    }

    private void rememberPair(String seedKey, String nextKey) {
        List<String> l = pairList(seedKey);
        l.remove(nextKey);
        l.add(0, nextKey);
        while (l.size() > 8) l.remove(l.size() - 1);
        SharedPreferences.Editor ed = pairs.edit();
        if (pairs.getAll().size() > 600) ed.clear();   // sınırsız büyümesin
        ed.putString(seedKey, String.join("\t", l)).apply();
    }

    private <T> List<T> sample(List<T> l, int n) {
        List<T> c = new ArrayList<>(l);
        Collections.shuffle(c, rnd);
        return c.size() > n ? new ArrayList<>(c.subList(0, n)) : c;
    }

    private List<Track> playlistPool(String query, boolean turkish, int playlists, int perList) {
        List<Track> out = new ArrayList<>();
        List<Long> found = new ArrayList<>(safe(() -> deezer.searchPlaylists(query, 5)));
        Collections.shuffle(found, rnd);
        for (Long pid : found.subList(0, Math.min(playlists, found.size()))) {
            for (Track t : safe(() -> deezer.playlistTracks(pid, perList, "mood"))) {
                t.fromTurkishList = turkish;
                out.add(t);
            }
        }
        return out;
    }

    private static List<Track> dedupe(List<Track> pool, String excludeKey, Set<String> hist) {
        Map<String, Track> uniq = new LinkedHashMap<>();
        for (Track t : pool) {
            String k = t.key();
            if (k.equals(excludeKey) || hist.contains(k)) continue;
            Track ex = uniq.get(k);
            if (ex == null) uniq.put(k, t);
            else {
                ex.sources.addAll(t.sources);
                ex.fromTurkishList |= t.fromTurkishList;
            }
        }
        return new ArrayList<>(uniq.values());
    }

    /** Her şarkının dilini belirler; emin olunamayanlar için sanatçının diğer şarkılarına bakar. */
    private void classifyLanguages(List<Track> list) {
        Map<Long, List<String>> titlesByArtist = new HashMap<>();
        for (Track t : list) {
            t.lang = Lang.detect(t.title, t.artist);
            titlesByArtist.computeIfAbsent(t.artistId, k -> new ArrayList<>()).add(t.title);
        }
        int lookups = 0;
        for (Track t : list) {
            if (t.lang != Lang.UNKNOWN) continue;
            Integer cached;
            synchronized (artistLang) { cached = artistLang.get(t.artistId); }
            if (cached == null) {
                List<String> titles = new ArrayList<>(titlesByArtist.get(t.artistId));
                int v = Lang.vote(titles, t.artist);
                if (v == Lang.UNKNOWN && t.artistId > 0 && lookups < 12) {
                    lookups++;
                    for (Track x : safe(() -> deezer.artistTop(t.artistId, 8, null))) titles.add(x.title);
                    v = Lang.vote(titles, t.artist);
                }
                cached = v;
                synchronized (artistLang) { artistLang.put(t.artistId, v); }
            }
            t.lang = cached;
        }
    }

    private static List<Track> keepTurkish(List<Track> list) {
        List<Track> out = new ArrayList<>();
        for (Track t : list) {
            if (t.lang == Lang.TR || (t.lang == Lang.UNKNOWN && t.fromTurkishList)) out.add(t);
        }
        return out;
    }

    /** Global modda son iki şarkı aynı dildeyse diğer dili öne çıkar. */
    private int balancingLang() {
        if (recentLangs.size() < 2) return Lang.UNKNOWN;
        int a = recentLangs.get(0), b = recentLangs.get(1);
        if (a == b && a != Lang.UNKNOWN) return a == Lang.TR ? Lang.FOREIGN : Lang.TR;
        return Lang.UNKNOWN;
    }

    private void prefetchVideoId(Track t) {
        if (t == null || t.videoId != null) return;
        net.submit(() -> {
            String id = YtMusic.findVideoId(t.artist, t.title);
            t.videoId = id;
            main.post(this::notifyChanged);
        });
    }

    // =====================================================================
    // Keşfet: ruh hali + mekan + dil → öneri listesi
    // =====================================================================

    public void discover(String moodId, String placeId) {
        Moods.Mood m = Moods.mood(moodId);
        if (m == null) return;
        prefs.edit().remove("vibe").apply();
        setSession(moodId, placeId);
        final boolean turkish = isTurkish();
        runDiscover(Moods.target(m, Moods.place(placeId), turkish), turkish);
    }

    /** Serbest cümleden ("akşamüstü sahilde sakin bir şeyler") öneri listesi. */
    public Vibe.Result discoverText(String text) {
        Vibe.Result r = Vibe.parse(text, hourNow());
        prefs.edit().putString("vibe", text).putString("mood", r.moodId).putString("place", r.placeId).apply();
        final boolean turkish = isTurkish();
        log("Anlaşılan: " + r.summary());
        runDiscover(r.target(turkish), turkish);
        return r;
    }

    private void runDiscover(Moods.Target target, boolean turkish) {
        final Set<String> hist = new HashSet<>(history);
        hist.addAll(store.dislikedKeys());
        final int gen = ++discoverGeneration;
        discoverLoading = true;
        discoverProblem = null;
        discoverResults = new ArrayList<>();
        discoverLabel = target.label + "  ·  " + (turkish ? "Türkçe" : "Global");
        notifyChanged();

        discoverWorker.submit(() -> {
            try {
                List<Track> pool = new ArrayList<>();
                for (int i = 0; i < target.queries.size() && i < 3; i++) {
                    pool.addAll(playlistPool(target.queries.get(i), turkish, 2, 40));
                }
                if (pool.isEmpty()) throw new Exception("Deezer'den liste alınamadı");
                List<Track> list = dedupe(pool, "", hist);
                classifyLanguages(list);
                if (turkish) list = keepTurkish(list);
                list = preselect(list, 45);   // her seferinde biraz farklı olsun
                deezer.fillDetailsAll(list);

                Scorer.Ctx ctx = new Scorer.Ctx();
                ctx.target = target;
                ctx.strictArtist = false;
                List<Track> ranked = Scorer.rank(ctx, list);
                for (Track t : ranked) t.score += rnd.nextDouble() * 8 - 4;
                ranked.sort((x, y) -> Double.compare(y.score, x.score));
                // Aynı sanatçıdan en fazla 2 şarkı
                List<Track> out = new ArrayList<>();
                Map<String, Integer> perArtist = new HashMap<>();
                for (Track t : ranked) {
                    String a = Text.norm(Text.mainArtist(t.artist));
                    int n = perArtist.getOrDefault(a, 0);
                    if (n >= 2) continue;
                    perArtist.put(a, n + 1);
                    out.add(t);
                    if (out.size() >= 20) break;
                }
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    discoverResults = out;
                    if (out.isEmpty()) discoverProblem = "Bu seçimle şarkı bulunamadı, başka bir mekan dene.";
                    else prefetchVideoId(out.get(0));
                    notifyChanged();
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    discoverProblem = "Öneriler alınamadı: " + msg;
                    notifyChanged();
                });
            }
        });
    }

    // =====================================================================
    // Yardımcılar
    // =====================================================================

    private interface IoCall<T> { T call() throws Exception; }

    private static <T> List<T> safe(IoCall<List<T>> c) {
        try {
            List<T> r = c.call();
            return r != null ? r : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static boolean titlesMatch(String a, String b) {
        String x = Text.norm(a), y = Text.norm(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    private void remember(String key, String artist, Track known) {
        if (removeMatchingFromQueue(nowTitle)) refreshPick();
        history.remove(key);
        history.add(key);
        while (history.size() > 300) history.remove(history.iterator().next());
        recentArtists.addFirst(Text.norm(Text.mainArtist(artist)));
        while (recentArtists.size() > 4) recentArtists.removeLast();
        int l = known != null && known.lang != Lang.UNKNOWN ? known.lang : Lang.detect(nowTitle, artist);
        recentLangs.addFirst(l);
        while (recentLangs.size() > 3) recentLangs.removeLast();
        List<Track> p = new ArrayList<>();
        for (Track t : plan) if (!t.key().equals(key)) p.add(t);
        plan = p;
    }

    public void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        log.addFirst(ts + "  " + msg);
        while (log.size() > 60) log.removeLast();
    }

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    void notifyChanged() {
        for (Listener l : listeners) l.onEngineChanged();
        notifier.update(this);
    }

    private boolean removeMatchingFromQueue(String title) {
        boolean changed = false;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (titlesMatch(title, queue.get(i).title)) { queue.remove(i); changed = true; }
        }
        if (changed) queueVersion++;
        return changed;
    }

    /** Çalan şarkıyı geçmişe bir kez yaz. */
    private void logPlay(Track s) {
        if (nowKey == null || nowKey.equals(lastLoggedKey)) return;
        lastLoggedKey = nowKey;
        Store.Play p = new Store.Play();
        p.ts = System.currentTimeMillis();
        p.title = nowTitle;
        p.artist = nowArtist;
        p.cover = s.coverUrl;
        p.bpm = s.hasBpm() ? s.bpm : 0;
        p.energy = s.energy();
        p.lang = s.lang != Lang.UNKNOWN ? s.lang : Lang.detect(nowTitle, nowArtist);
        p.durMs = nowDurationMs;
        Moods.Target t = sessionTarget();
        p.mood = t != null ? t.label : null;
        net.submit(() -> store.addPlay(p));
    }
}
