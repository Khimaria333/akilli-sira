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
    public static final String SPOTIFY = SpotifyApi.PACKAGE;
    public static final String PLAYER_AUTO = "auto", PLAYER_YTM = "ytm", PLAYER_SPOTIFY = "spotify";

    // Geçiş yöntemleri
    public static final int SILENT_UNKNOWN = -1, SILENT_NO = 0, SILENT_YES = 1;
    private static final int M_NONE = 0, M_SILENT = 1, M_INTENT = 2, M_SEARCH = 3,
            M_SP_API = 4, M_SP_QUEUE = 5, M_SP_URI = 6;

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
    final Deezer deezer = new Deezer();
    public final LastFm lastfm = new LastFm();
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
    public SpotifyApi spotify;
    /** Şu an bağlı olunan (ya da en son bağlanılan) müzik uygulaması. */
    public volatile String activePlayer;

    public String getPlayerPref() { return prefs.getString("player", PLAYER_AUTO); }

    public void setPlayerPref(String p) {
        prefs.edit().putString("player", p).apply();
        if (PLAYER_YTM.equals(p)) activePlayer = YTM;
        if (PLAYER_SPOTIFY.equals(p)) activePlayer = SPOTIFY;
        MusicListenerService.reattachNow();
        notifyChanged();
    }

    public boolean isSpotify() { return SPOTIFY.equals(activePlayer); }
    public String playerName() { return isSpotify() ? "Spotify" : "YouTube Music"; }
    public String playerPackage() { return isSpotify() ? SPOTIFY : YTM; }
    /** "YouTube Music'te" / "Spotify'da" */
    public String playerIn() { return isSpotify() ? "Spotify'da" : "YouTube Music'te"; }
    /** "YouTube Music'i" / "Spotify'ı" */
    public String playerAcc() { return isSpotify() ? "Spotify'ı" : "YouTube Music'i"; }
    final Notifier notifier;
    private String lastLoggedKey;
    private boolean carActive;

    // ---- Koşu modu ----
    private volatile boolean runActive;
    private volatile int cadence;          // son ölçülen adım/dk (0 = bilinmiyor)
    private double cadenceSmooth;
    private int runBpmUsed;
    private long lastRunRecompute;

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
        DiskCache.init(app);
        store = new Store(app);
        notifier = new Notifier(app);
        prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
        lastfm.setKey(prefs.getString("lastfmKey", null));
        spotify = new SpotifyApi(app);
        activePlayer = prefs.getString("lastPlayer", YTM);
        loadProfile();
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

    public String getLastFmKey() { return prefs.getString("lastfmKey", null); }

    public void setLastFmKey(String k) {
        String v = k == null ? null : k.trim();
        if (v != null && v.isEmpty()) v = null;
        if (v == null) prefs.edit().remove("lastfmKey").apply();
        else prefs.edit().putString("lastfmKey", v).apply();
        lastfm.setKey(v);
        log(v != null ? "Last.fm bağlandı: öneriler etiketlerle seçilecek" : "Last.fm anahtarı kaldırıldı");
        refresh();
        notifyChanged();
    }

    public boolean isNotif() { return prefs.getBoolean("notif", true); }
    public void setNotif(boolean on) { prefs.edit().putBoolean("notif", on).apply(); notifyChanged(); }

    public boolean isNightCalm() { return prefs.getBoolean("nightCalm", true); }
    public void setNightCalm(boolean on) { prefs.edit().putBoolean("nightCalm", on).apply(); refresh(); notifyChanged(); }

    public boolean isCarMode() { return prefs.getBoolean("carMode", true); }
    public void setCarMode(boolean on) { prefs.edit().putBoolean("carMode", on).apply(); notifyChanged(); }

    public boolean isCarActive() { return carActive; }

    public boolean notifAllowed() { return notifier.canPost(); }

    public void forceWidgets() { notifier.forceWidgets(this); }

    /** YouTube Music'in çalan şarkı için gösterdiği kapak (yoksa null). */
    public android.graphics.Bitmap nowArt() {
        try {
            android.media.MediaMetadata m = controller != null ? controller.getMetadata() : null;
            if (m == null) return null;
            android.graphics.Bitmap b = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            return b;
        } catch (Exception ex) {
            return null;
        }
    }

    public void refreshNotification() { notifier.reset(); notifyChanged(); }

    /** Hava ayarı + bağlam: gece ya da uyku zamanlayıcısı açıkken sakinleştir. */
    public int effectiveMode() {
        int m = getMode();
        if (sleepUntil > 0 || sleepFinishing) return -1;
        int h = hourNow();
        if (m == 0 && isNightCalm() && getVibe() == null && getMoodId() == null && getGenreId() == null
                && Routines.active(app) == null && (h >= 23 || h < 6)) return -1;
        return m;
    }

    public boolean nightCalmActive() {
        int h = hourNow();
        return getMode() == 0 && isNightCalm() && getVibe() == null && getMoodId() == null && getGenreId() == null
                && Routines.active(app) == null && (h >= 23 || h < 6);
    }

    public int getSilentState() { return prefs.getInt("silent", SILENT_UNKNOWN); }
    public void resetSilent() {
        prefs.edit().putInt("silent", SILENT_UNKNOWN).apply();
        log("Sessiz geçiş bir sonraki geçişte yeniden denenecek");
        notifyChanged();
    }

    public String getMoodId() { return prefs.getString("mood", null); }
    public String getPlaceId() { return prefs.getString("place", null); }

    public String getGenreId() { return prefs.getString("genre", null); }

    public String getVibe() { return prefs.getString("vibe", null); }

    private static final long SESSION_TTL = 4L * 3600 * 1000;

    /** Ruh hali modu 4 saat sonra kendiliğinden kapanır (arabadayken kapanmaz). */
    private boolean sessionExpired() {
        if (carActive) return false;
        if (getVibe() == null && getMoodId() == null && getGenreId() == null) return false;
        long ts = prefs.getLong("sessionTs", 0);
        if (System.currentTimeMillis() - ts > SESSION_TTL) {
            prefs.edit().remove("vibe").remove("mood").remove("place").remove("genre").remove("sessionTs").apply();
            return true;
        }
        return false;
    }

    private void touchSession() {
        prefs.edit().putLong("sessionTs", System.currentTimeMillis()).apply();
    }

    public Moods.Target sessionTarget() {
        if (runActive) return runTarget();
        return baseTarget();
    }

    /** Koşu modu dışındaki oturum hedefi (tür, cümle, ruh hali, rutin, hava). */
    private Moods.Target baseTarget() {
        if (sessionExpired()) return routineTarget();
        Genres.Genre g = Genres.byId(getGenreId());
        if (g != null) return Genres.target(g);
        String v = getVibe();
        if (v != null) return Vibe.parse(v, hourNow()).target(isTurkish());
        Moods.Target mt = Moods.target(Moods.mood(getMoodId()), Moods.place(getPlaceId()), isTurkish());
        return mt != null ? mt : routineTarget();
    }

    // =====================================================================
    // Koşu modu: adım temposuna uyan BPM
    // =====================================================================

    public boolean isRunActive() { return runActive; }
    public int getCadence() { return cadence; }

    /** Hedef BPM: ölçülen kadans (yumuşatılmış); henüz ölçülmediyse 160. */
    public int runBpm() {
        double c = cadenceSmooth > 0 ? cadenceSmooth : 160;
        return (int) Math.round(Math.max(95, Math.min(195, c)));
    }

    private Moods.Target runTarget() {
        Moods.Target t = new Moods.Target();
        int b = runBpm();
        t.bpmLo = b - 5;
        t.bpmHi = b + 5;
        t.gain = b >= 140 ? -5.5 : -7.0;
        t.tempoStrict = true;
        t.tempoWeight = 2.2;
        Moods.Target base = baseTarget();
        if (base != null && base.hasStyle()) {   // "rap ile koşu": tür korunur
            t.copyStyleFrom(base);
            t.queries.addAll(base.styleQueries);
        }
        if (isTurkish()) { t.queries.add("koşu şarkıları"); t.queries.add("türkçe spor"); t.queries.add("türkçe hareketli"); }
        else { t.queries.add("running"); t.queries.add("workout hits"); t.queries.add("running motivation"); }
        t.tags.add("workout");
        t.tags.add("running");
        t.tags.add("energetic");
        t.label = "🏃 Koşu · " + b + " BPM" + (t.styleLabel != null ? "  ·  " + t.styleLabel : "");
        return t;
    }

    /** Koşu modunu başlat (izinler arayüzde alınmış olmalı). */
    public void startRun() {
        if (runActive) return;
        onRunServiceStarted();
        RunService.start(app);
        log("🏃 Koşu modu açık: şarkılar adım tempona göre seçilecek");
        refresh();
        notifyChanged();
    }

    /** Servis (yeniden) başladığında. */
    void onRunServiceStarted() {
        if (runActive) return;
        runActive = true;
        cadence = 0;
        cadenceSmooth = 0;
        runBpmUsed = runBpm();
        lastRunRecompute = SystemClock.elapsedRealtime();
    }

    public void stopRun() {
        if (!runActive) return;
        runActive = false;
        cadence = 0;
        RunService.stop(app);
        log("🏃 Koşu modu kapandı");
        refresh();
        notifyChanged();
    }

    /** RunService her birkaç saniyede bildirir. Tempo belirgin değişince sıradakini yeniden seç. */
    public void setCadence(int cad) {
        if (!runActive) return;
        cadence = cad;
        if (cad > 0) cadenceSmooth = cadenceSmooth <= 0 ? cad : cadenceSmooth * 0.6 + cad * 0.4;
        int b = runBpm();
        long now = SystemClock.elapsedRealtime();
        if (Math.abs(b - runBpmUsed) >= 8 && now - lastRunRecompute > 20000) {
            log("🏃 Tempo " + runBpmUsed + " → " + b + " BPM: sıradaki yeniden seçiliyor");
            runBpmUsed = b;
            lastRunRecompute = now;
            refresh();
        }
        notifyChanged();
    }

    /** Keşfet'ten: koşu modunu aç ve tempoya uygun liste hazırla. */
    public void discoverRun() {
        startRun();
        lastDiscover = this::discoverRun;
        runDiscover(runTarget(), isTurkish(), null);
    }

    /** Elle seçilmiş mod yokken geçerli rutin (otomatik ruh hali planı). */
    private Moods.Target routineTarget() {
        Routines.Routine r = Routines.active(app);
        return r != null ? r.target(isTurkish()) : weatherTarget();
    }

    /** Serbest akışta hava durumu: tempoya karışmadan ruh halini hafifçe kaydırır. */
    private Moods.Target weatherTarget() {
        if (!Weather.isOn(app)) return null;
        Weather.Now w = Weather.current();
        Weather.refresh(app, false, null);
        if (w == null) return null;
        Moods.Target t = new Moods.Target();
        t.noTempo = true;
        t.bpmLo = 40;
        t.bpmHi = 220;
        t.gain = Double.NaN;
        t.moodWeight = 0.4;
        t.tags.addAll(w.tags());
        t.label = w.label() + "  ·  havaya göre";
        return t;
    }

    /** Ruh hali seçilmemiş, sadece hava durumu etkili mi (arayüzde "Kapat" gösterilmesin). */
    public boolean weatherOnly() {
        return !runActive && getVibe() == null && getMoodId() == null && getGenreId() == null
                && Routines.active(app) == null && weatherTarget() != null;
    }

    public Routines.Routine activeRoutine() {
        if (getVibe() != null || getMoodId() != null || getGenreId() != null) return null;
        return Routines.active(app);
    }

    private static int hourNow() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    public void setSession(String moodId, String placeId) {
        prefs.edit().putString("mood", moodId).putString("place", placeId)
                .putLong("sessionTs", System.currentTimeMillis()).apply();
        notifyChanged();
    }

    public void clearSession() {
        prefs.edit().remove("mood").remove("place").remove("vibe").remove("genre").apply();
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
        if (c != null) {
            String pk = c.getPackageName();
            activePlayer = pk;
            prefs.edit().putString("lastPlayer", pk).apply();
        }
        log(c != null ? playerName() + "'e bağlandı" : playerName() + " bağlantısı kapandı");
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
            transFrom = seed;   // bir önceki şarkı (etiketleriyle)
            transRated = false;
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
            if (!match && transMethod == M_SP_URI && !fallbackDone) {
                fallbackDone = true;
                firedForCurrent = true;
                expectSwitchUntil = now + 15000;
                spotifySearchPlay(transTarget);
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

        int extIdx = externalIndex(title);
        if (extIdx < 0 && !externalTitles.isEmpty() && SystemClock.elapsedRealtime() - externalStarted > 90000) {
            externalTitles.clear();   // kullanıcı başka bir şey açtı: aktarılan liste bitti
        }
        if (ytmAutoAdvanced && isAuto() && planned != null && !key.equals(planned.key()) && extIdx < 0) {
            log("YT Music kendi şarkısına geçti, düzeltiliyor");
            fire(planned, false);
            return;
        }

        // Kullanıcı kendisi bir şarkı açtı ya da geçti
        transFrom = null;
        restoreVolumeNow();
        remember(key, artist, null);
        log("♪ " + artist + " – " + title);
        recompute();
    }

    /** Servis her yarım saniyede bir çalma konumunu bildirir. */
    public void onPoll(long positionMs, long durationMs, boolean isPlaying) {
        if (isPlaying) notifier.tickWidgets(this);
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
        // Spotify + Premium: sıradakini şarkı bitmeden Spotify'ın kendi sırasına ekle (en temiz geçiş)
        if (isSpotify() && spotify.connected() && !spotify.playerBlocked() && isPlaying && !firedForCurrent && !stopHere
                && isAuto() && pick != null && !externalHolds() && nowDurationMs > 30000 && lastRemainingMs <= 15000
                && !spQueueing && !String.valueOf(nowKey).equals(spQueueFailedFor)) {
            queueOnSpotify(pick);
        }
        if (isPlaying && !firedForCurrent && nowDurationMs > 30000 && (stopHere || (isAuto() && pick != null && !externalHolds()))) {
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

        // Spotify'a adresle çal komutu yanıtsız kaldıysa aramayla dene
        if (transMethod == M_SP_URI && !fallbackDone && verifyDeadline > 0 && now > verifyDeadline) {
            fallbackDone = true;
            verifyDeadline = 0;
            spotifySearchPlay(transTarget);
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
        touchSession();
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
        playNow(t, fromList, false);
    }

    /** keepOrder: DJ listesi gibi sırası önemli listelerde seçilen şarkıdan sonrasını aynen çal. */
    public void playNow(Track t, List<Track> fromList, boolean keepOrder) {
        List<Track> rest = new ArrayList<>(fromList);
        plan = new ArrayList<>();
        queue.clear();
        if (keepOrder) {
            int i = rest.indexOf(t);
            queue.addAll(rest.subList(i + 1, rest.size()));
        } else {
            rest.remove(t);
            queue.addAll(Scorer.flowOrder(t, rest));   // tempo ve enerjiye göre akıcı sıra
        }
        queueVersion++;
        fire(t, true);
        refreshPick();
        notifyChanged();
    }

    /** Geçişe kalan süre (ms); bilinmiyorsa -1. */
    public long msToSwitch() {
        if (externalHolds()) return -1;
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
        if (isSpotify()) {
            spotifyFire(t, userInitiated);
            return;
        }
        if (t.videoId != null) {
            doSwitch(t, userInitiated);
        } else {
            net.submit(() -> {
                if (t.videoId == null) t.videoId = YtMusic.findVideoId(t.artist, t.title);
                main.post(() -> doSwitch(t, userInitiated));
            });
        }
    }

    // ---- Spotify ----

    private boolean spQueueing;
    private String spQueueFailedFor;

    /** Sıradakini Spotify'ın kendi sırasına ekler; Spotify geçişi kendisi yapar (kendi yumuşak geçişiyle). */
    private void queueOnSpotify(Track t) {
        spQueueing = true;
        firedForCurrent = true;
        final long remain = lastRemainingMs;
        final String forKey = nowKey;
        net.submit(() -> {
            String uri = t.spotifyUri != null ? t.spotifyUri : spotify.findTrack(t.artist, t.title);
            t.spotifyUri = uri;
            int r = uri == null ? 3 : spotify.addToQueue(uri);
            main.post(() -> {
                spQueueing = false;
                if (!String.valueOf(forKey).equals(nowKey)) return;   // bu arada şarkı değişti
                if (r == 0) {
                    transTarget = t;
                    transMethod = M_SP_QUEUE;
                    fallbackDone = true;
                    verifyDeadline = 0;
                    expectSwitchUntil = SystemClock.elapsedRealtime() + remain + 20000;
                    log("⏭ Spotify sırasına eklendi: " + t);
                } else {
                    firedForCurrent = false;          // şarkı sonunda uygulama üzerinden geç
                    spQueueFailedFor = forKey;
                    if (r == 1) log("Spotify sıraya ekleme için Premium istiyor; şarkı sonunda geçilecek");
                    else if (r == 2) log("Spotify'da açık cihaz bulunamadı; şarkı sonunda geçilecek");
                    else if (uri == null) log("Spotify'da bulunamadı, aramayla denenecek: " + t);
                }
                notifyChanged();
            });
        });
    }

    /** Hemen geçiş: önce Spotify API (Premium), olmazsa uygulamaya adresle, o da olmazsa aramayla. */
    private void spotifyFire(Track t, boolean userInitiated) {
        net.submit(() -> {
            String uri = t.spotifyUri;
            if (uri == null && spotify.connected()) {
                uri = spotify.findTrack(t.artist, t.title);
                t.spotifyUri = uri;
            }
            int r = -1;
            if (uri != null && spotify.connected() && !spotify.playerBlocked()) {
                r = spotify.play(java.util.Collections.singletonList(uri));
            }
            final String u = uri;
            final int res = r;
            main.post(() -> {
                if (res == 0) {
                    transMethod = M_SP_API;
                    log("→ " + t + " (Spotify)");
                    notifyChanged();
                    return;
                }
                if (res == 1) log("Spotify çalma için Premium istiyor; uygulama üzerinden geçiliyor");
                spotifyLocalPlay(t, u);
            });
        });
    }

    private void spotifyLocalPlay(Track t, String uri) {
        if (controller == null) {
            log("Spotify bağlı değil, geçilemedi");
            expectSwitchUntil = 0;
            restoreVolumeNow();
            notifyChanged();
            return;
        }
        if (uri != null) {
            try {
                controller.getTransportControls().playFromUri(Uri.parse(uri), new Bundle());
                transMethod = M_SP_URI;
                verifyDeadline = SystemClock.elapsedRealtime() + 4000;
                log("→ " + t);
                notifyChanged();
                return;
            } catch (Exception ignored) {}
        }
        spotifySearchPlay(t);
    }

    private void spotifySearchPlay(Track t) {
        if (t == null || controller == null) return;
        String q = Text.mainArtist(t.artist) + " " + Text.cleanTitle(t.title);
        Bundle ex = new Bundle();
        ex.putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE);
        ex.putString(MediaStore.EXTRA_MEDIA_TITLE, t.title);
        ex.putString(MediaStore.EXTRA_MEDIA_ARTIST, t.artist);
        ex.putString(SearchManager.QUERY, q);
        try {
            controller.getTransportControls().playFromSearch(q, ex);
            transMethod = M_SEARCH;
            log("→ " + t + " (Spotify araması)");
        } catch (Exception e) {
            log("Spotify'da geçilemedi: " + e.getMessage());
            expectSwitchUntil = 0;
            restoreVolumeNow();
        }
        notifyChanged();
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
        ctx.artistLikes = store.likedArtists();
        ctx.likedKeys = store.likedKeys();
        ctx.profile = profile();
        ctx.explore = getExplore();
        ctx.badPairs = store.badPairs();
        ctx.badArtistPairs = store.badArtistPairs();
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
                deezer.fillGenre(s);
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
        Set<Long> relSet = new HashSet<>(), radioSet = new HashSet<>();
        if (s.artistId > 0) {
            // Sanatçı radyosu: Deezer'in bu sanatçıya benzer bulduğu şarkılar (tarzın en güçlü işareti)
            List<Track> radio = safe(() -> deezer.artistRadio(s.artistId, 30));
            for (Track t : radio) if (t.artistId > 0) radioSet.add(t.artistId);
            pool.addAll(radio);
            // Benzer sanatçılar: her seferinde farklı 7'si, her birinin ilk 12 şarkısından rastgele 4'ü
            List<Long> rels = new ArrayList<>(safe(() -> deezer.related(s.artistId, 15)));
            relSet.addAll(rels);
            Collections.shuffle(rels, rnd);
            for (Long rel : rels.subList(0, Math.min(7, rels.size()))) {
                pool.addAll(topSample(safe(() -> deezer.artistTop(rel, 25, "related")), 4));
            }
            pool.addAll(topSample(safe(() -> deezer.artistTop(s.artistId, 25, "same")), 3));
        }
        // Last.fm: daha isabetli benzer sanatçılar ve tarz etiketleri
        if (lastfm.enabled()) {
            s.tags = lastfm.artistTags(s.artist);
            ctx.seedTags = s.tags;
            int tl = LastFm.langFromTags(s.tags);
            if (tl != Lang.UNKNOWN && s.lang == Lang.UNKNOWN) s.lang = tl;
            List<String> sims = lastfm.similarArtists(s.artist, 25);
            List<String> pickSims = sample(sims.subList(0, Math.min(15, sims.size())), 6);
            for (String name : pickSims) {
                String nn = Text.norm(Text.mainArtist(name));
                List<Track> found = safe(() -> deezer.search("artist:\"" + name + "\"", 10));
                List<Track> mine = new ArrayList<>();
                for (Track t : found) {
                    if (Text.norm(Text.mainArtist(t.artist)).equals(nn)) {
                        t.sources.add("related");
                        mine.add(t);
                        if (t.artistId > 0) relSet.add(t.artistId);
                    }
                }
                pool.addAll(sample(mine, 3));
            }
        }
        radioSet.remove(s.artistId);
        ctx.relatedArtists = relSet;
        ctx.radioArtists = radioSet;

        // Ek kaynak: önce çalan şarkının TÜRÜNE uygun Türkçe liste, sonra (varsa) ruh hali listeleri
        List<String> queries = new ArrayList<>();
        if (ctx.target != null && ctx.target.hasStyle()) queries.addAll(ctx.target.queries);   // istenen tür önce
        if (turkish) for (String q : Scorer.genreQueries(s)) if (!queries.contains(q)) queries.add(q);
        if (ctx.target != null) for (String q : ctx.target.queries) if (!queries.contains(q)) queries.add(q);
        else if (turkish && queries.isEmpty()) queries.addAll(Moods.defaultTurkishQueries(s));
        int maxQ = (turkish && s.lang == Lang.FOREIGN) ? 3 : 2;
        for (int i = 0; i < queries.size() && i < maxQ; i++) {
            pool.addAll(sample(playlistPool(queries.get(i), turkish, 1, 80), 25));
        }

        List<Track> list = dedupe(pool, s.key(), hist);
        classifyLanguages(list);
        if (turkish) list = keepTurkish(list);

        list = preselect(list, 40);
        deezer.fillDetailsAll(list);
        deezer.fillGenresAll(list);      // tür ve çıkış yılı
        if (lastfm.enabled()) {
            fillTags(list, ctx.target != null && (!ctx.target.tags.isEmpty() || ctx.target.hasStyle()) ? 25 : 0);   // tarz + dil (+ruh hali)
            if (turkish) list = keepTurkish(list);
        }
        return applyVariety(Scorer.keepStyle(ctx.target, Scorer.rank(ctx, list), 5), s.key());
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

    /** Last.fm etiketlerini doldurur ve dili etiketlere göre düzeltir. */
    private void fillTags(List<Track> list) {
        fillTags(list, 0);
    }

    /** trackTagCount: ilk kaç şarkı için şarkı düzeyinde etiket de alınsın (ruh hali isabeti). */
    private void fillTags(List<Track> list, int trackTagCount) {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (Track t : list) {
                fs.add(pool.submit(() -> {
                    t.tags = lastfm.artistTags(t.artist);
                    if (list.indexOf(t) < trackTagCount) t.trackTags = lastfm.trackTags(t.artist, t.title);
                    int l = LastFm.langFromTags(t.tags);
                    // başlıkta ğ/ş/ı gibi kesin Türkçe harf yoksa etiket kazanır
                    if (l != Lang.UNKNOWN && Lang.detect(t.title, t.artist) != Lang.TR) t.lang = l;
                    else if (l == Lang.TR) t.lang = Lang.TR;
                }));
            }
            for (java.util.concurrent.Future<?> f : fs) {
                try { f.get(); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdown();
        }
    }

    private static List<Track> keepTurkish(List<Track> list) {
        List<Track> out = new ArrayList<>();
        for (Track t : list) {
            if (t.lang == Lang.TR) out.add(t);
            else if (t.lang == Lang.UNKNOWN && t.fromTurkishList && (t.tags == null || t.tags.isEmpty())) out.add(t);
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
        if (t == null) return;
        if (isSpotify()) {
            if (t.spotifyUri != null || !spotify.connected()) return;
            net.submit(() -> {
                t.spotifyUri = spotify.findTrack(t.artist, t.title);
                main.post(this::notifyChanged);
            });
            return;
        }
        if (t.videoId != null) return;
        net.submit(() -> {
            String id = YtMusic.findVideoId(t.artist, t.title);
            t.videoId = id;
            main.post(this::notifyChanged);
        });
    }

    // =====================================================================
    // Keşfet: ruh hali + mekan + dil → öneri listesi
    // =====================================================================

    public boolean discoverDj;              // son liste DJ enerji eğrisiyle mi dizildi
    public double[] discoverCurve;          // DJ listesinde her şarkının yoğunluğu (0..1)
    private boolean autoStartDiscover;      // kısayoldan gelindiyse liste hazır olunca başlat

    public boolean isDj() { return prefs.getBoolean("dj", false); }
    public void setDj(boolean on) { prefs.edit().putBoolean("dj", on).apply(); notifyChanged(); }

    public void discover(String moodId, String placeId) {
        Moods.Mood m = Moods.mood(moodId);
        if (m == null) return;
        prefs.edit().remove("vibe").remove("genre").apply();
        setSession(moodId, placeId);
        lastDiscover = () -> discover(moodId, placeId);
        final boolean turkish = isTurkish();
        runDiscover(Moods.target(m, Moods.place(placeId), turkish), turkish, null);
    }

    /** Kısayollardan: listeyi hazırla ve hemen başlat. */
    public void discoverAndPlay(String moodId, String placeId) {
        autoStartDiscover = true;
        discover(moodId, placeId);
    }

    /** Serbest cümleden ("akşamüstü sahilde sakin bir şeyler") öneri listesi. */
    public Vibe.Result discoverText(String text) {
        Vibe.Result r = Vibe.parse(text, hourNow());
        lastDiscover = () -> discoverText(text);
        prefs.edit().remove("genre").putString("vibe", text).putString("mood", r.moodId).putString("place", r.placeId)
                .putLong("sessionTs", System.currentTimeMillis()).apply();
        final boolean turkish = isTurkish();
        log("Anlaşılan: " + r.summary());
        runDiscover(r.target(turkish), turkish, null);
        return r;
    }

    /** ❤️ Beğendiklerin + sevdiğin sanatçılardan karışık liste. */
    public void discoverFavorites() {
        List<Store.Liked> liked = store.liked();
        if (liked.isEmpty()) {
            autoStartDiscover = false;
            discoverLoading = false;
            discoverResults = new ArrayList<>();
            discoverCurve = null;
            discoverProblem = "Henüz beğendiğin şarkı yok. Çalan şarkıdaki ❤️ düğmesine basarak başla.";
            notifyChanged();
            return;
        }
        prefs.edit().remove("vibe").remove("mood").remove("place").remove("genre").apply();
        lastDiscover = this::discoverFavorites;
        runDiscover(null, isTurkish(), liked);
    }

    public void favoritesAndPlay() {
        autoStartDiscover = true;
        discoverFavorites();
    }

    // ---- Tür ----

    /** Tür sekmesinden: o türün en sevilen şarkıları; otomatik sıra da bu türde kalır. */
    public void discoverGenre(String id) {
        Genres.Genre g = Genres.byId(id);
        if (g == null) return;
        prefs.edit().remove("vibe").remove("mood").remove("place").putString("genre", id)
                .putLong("sessionTs", System.currentTimeMillis()).apply();
        lastDiscover = () -> discoverGenre(id);
        runDiscover(Genres.target(g), isTurkish(), null);
    }

    // ---- Tarz profili (❤️ beğenilerden) ----

    private volatile Map<String, Integer> profile = new LinkedHashMap<>();
    private int profileForLikes = -1;
    private boolean profileComputing;

    /** Profil: etiket → 0..100. Beğeni sayısı değiştiyse arka planda yeniden hesaplanır. */
    public Map<String, Integer> profile() {
        ensureProfile();
        return profile;
    }

    public boolean profileComputing() { return profileComputing; }

    private void loadProfile() {
        String enc = prefs.getString("profile", null);
        profileForLikes = prefs.getInt("profileLikes", -1);
        Map<String, Integer> m = new LinkedHashMap<>();
        if (enc != null && !enc.isEmpty()) {
            String[] p = enc.split("\t");
            for (int i = 0; i + 1 < p.length; i += 2) {
                try { m.put(p[i], Integer.parseInt(p[i + 1])); } catch (Exception ignored) {}
            }
        }
        profile = m;
    }

    private void ensureProfile() {
        if (!lastfm.enabled() || profileComputing) return;
        final List<Store.Liked> liked = store.liked();
        if (liked.size() == profileForLikes) return;
        profileComputing = true;
        net.submit(() -> {
            Map<String, Double> agg = new HashMap<>();
            Map<String, Integer> perArtist = new HashMap<>();
            for (Store.Liked l : liked) {
                String a = Text.norm(Text.mainArtist(l.artist));
                perArtist.put(a, perArtist.getOrDefault(a, 0) + 1);
            }
            int done = 0;
            Set<String> seen = new HashSet<>();
            for (Store.Liked l : liked) {
                String a = Text.norm(Text.mainArtist(l.artist));
                if (!seen.add(a) || done >= 40) continue;
                done++;
                Map<String, Integer> tags = lastfm.artistTags(l.artist);
                double w = Math.min(3, perArtist.get(a));   // çok beğenilen sanatçı biraz daha ağır
                for (Map.Entry<String, Integer> en : tags.entrySet()) {
                    if (LastFm.isLangTag(en.getKey())) continue;
                    agg.put(en.getKey(), agg.getOrDefault(en.getKey(), 0.0) + w * en.getValue() / 100.0);
                }
            }
            List<Map.Entry<String, Double>> top = new ArrayList<>(agg.entrySet());
            top.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
            double max = top.isEmpty() ? 1 : top.get(0).getValue();
            Map<String, Integer> m = new LinkedHashMap<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < top.size() && i < 12; i++) {
                int v = (int) Math.round(100 * top.get(i).getValue() / max);
                if (v < 8) continue;
                m.put(top.get(i).getKey(), v);
                if (sb.length() > 0) sb.append('\t');
                sb.append(top.get(i).getKey()).append('\t').append(v);
            }
            final int forLikes = liked.size();
            main.post(() -> {
                profile = m;
                profileForLikes = forLikes;
                profileComputing = false;
                prefs.edit().putString("profile", sb.toString()).putInt("profileLikes", forLikes).apply();
                notifyChanged();
            });
        });
    }

    /** Tarz profilindeki en güçlü etiketlerle liste. */
    public void discoverProfile() {
        Map<String, Integer> p = profile();
        if (p.isEmpty()) {
            discoverLoading = false;
            discoverResults = new ArrayList<>();
            discoverCurve = null;
            discoverProblem = !lastfm.enabled()
                    ? "Tarz profili için Ayarlar'dan Last.fm'i bağla."
                    : store.liked().isEmpty() ? "Önce birkaç şarkıyı ❤️ ile beğen, tarzını öğreneyim."
                    : "Tarz profilin hazırlanıyor, birkaç saniye sonra tekrar dene.";
            notifyChanged();
            return;
        }
        prefs.edit().remove("vibe").remove("mood").remove("place").remove("genre").apply();
        lastDiscover = this::discoverProfile;
        Moods.Target t = new Moods.Target();
        t.bpmLo = 40;
        t.bpmHi = 220;
        t.gain = Double.NaN;
        int n = 0;
        for (String tag : p.keySet()) {
            if (n++ >= 5) break;
            t.tags.add(tag);
        }
        t.label = "🧭 Tarzına göre";
        runDiscover(t, isTurkish(), null);
    }

    // ---- Keşif seviyesi: 0 tanıdık, 1 dengeli, 2 yeni ----

    public int getExplore() { return prefs.getInt("explore", 1); }

    public void setExplore(int v) {
        if (v == getExplore()) return;
        prefs.edit().putInt("explore", v).apply();
        refresh();
        notifyChanged();
    }

    /** Sanatçının şarkılarından keşif seviyesine göre örnek: tanıdıkta hitler, yenide az bilinenler. */
    private List<Track> topSample(List<Track> top, int n) {
        int ex = getExplore();
        if (ex == 0) return sample(top.subList(0, Math.min(6, top.size())), n);
        if (ex == 2 && top.size() > 6) return sample(top.subList(4, top.size()), n);
        return sample(top, n);
    }

    // ---- Müzikle uyan ----

    public boolean wakeActive;
    private boolean wakeOrder;
    private int wakeTargetVol;
    private long wakeRampStart;
    private final Runnable wakeRamp = this::wakeRampStep;

    /** Alarm ekranından: sesi en kısığa al, sakin başlayıp hareketlenen bir liste başlat, sesi yavaşça aç. */
    public void startWake() {
        wakeActive = true;
        sleepUntil = 0;
        sleepFinishing = false;
        if (audio != null) {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            wakeTargetVol = Math.max(2, Math.round(max * WakeScheduler.volumePct(app) / 100f));
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0); } catch (Exception ignored) {}
        }
        wakeRampStart = 0;
        wakeOrder = true;
        autoStartDiscover = true;
        log("⏰ Günaydın! Uyanma listesi hazırlanıyor");
        discoverText("sabah keyifli uyanış");
        main.removeCallbacks(wakeRamp);
        main.postDelayed(wakeRamp, 1000);
    }

    private void wakeRampStep() {
        if (!wakeActive || audio == null) return;
        if (playing) {
            long now = SystemClock.elapsedRealtime();
            if (wakeRampStart == 0) wakeRampStart = now;
            double f = Math.min(1, (now - wakeRampStart) / 150000.0);   // 2,5 dakikada tam ses
            int v = 1 + (int) Math.round((wakeTargetVol - 1) * f);
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Exception ignored) {}
            if (f >= 1) { wakeActive = false; return; }
        }
        main.postDelayed(wakeRamp, 2000);
    }

    public void stopWake(boolean pauseMusic) {
        wakeActive = false;
        wakeOrder = false;
        autoStartDiscover = false;
        main.removeCallbacks(wakeRamp);
        if (pauseMusic && controller != null) {
            try { controller.getTransportControls().pause(); } catch (Exception ignored) {}
        }
        notifyChanged();
    }

    // ---- Aynı isteği yeniden üret ----

    private Runnable lastDiscover;
    public String discoverStage = "";

    public boolean canRedo() { return lastDiscover != null; }

    public void redoDiscover() {
        if (lastDiscover != null) lastDiscover.run();
    }

    private void stage(int gen, String text) {
        main.post(() -> {
            if (gen != discoverGeneration) return;
            discoverStage = text;
            notifyChanged();
        });
    }

    private void runDiscover(Moods.Target target, boolean turkish, List<Store.Liked> favorites) {
        final Set<String> hist = new HashSet<>();
        if (favorites == null) hist.addAll(history);
        else {   // favorilerde sadece son 20 çalınanı dışla
            int n = 0;
            List<String> h = new ArrayList<>(history);
            for (int i = h.size() - 1; i >= 0 && n < 20; i--, n++) hist.add(h.get(i));
        }
        hist.addAll(store.dislikedKeys());
        final boolean dj = isDj();
        final Map<String, Integer> artistLikes = store.likedArtists();
        final Set<String> likedKeys = store.likedKeys();
        final Map<String, Integer> artistDislikes = store.dislikedArtists();
        final int gen = ++discoverGeneration;
        final Map<String, Integer> prof = profile();
        final int explore = getExplore();
        discoverLoading = true;
        discoverStage = "Listeler aranıyor…";
        discoverProblem = null;
        discoverResults = new ArrayList<>();
        discoverCurve = null;
        discoverLabel = (favorites != null ? "❤️ Favorilerinden karışık" : target.label)
                + "  ·  " + (turkish ? "Türkçe" : "Global") + (dj ? "  ·  🎚 DJ" : "");
        notifyChanged();

        discoverWorker.submit(() -> {
            try {
                List<Track> pool = new ArrayList<>();
                if (favorites != null) {
                    List<Store.Liked> fav = sample(favorites, 30);
                    for (Store.Liked l : fav) pool.add(l.toTrack());
                    List<Long> artists = new ArrayList<>();
                    for (Store.Liked l : favorites) if (l.artistId > 0 && !artists.contains(l.artistId)) artists.add(l.artistId);
                    Collections.shuffle(artists, rnd);
                    for (int i = 0; i < artists.size() && i < 6; i++) {
                        long aid = artists.get(i);
                        pool.addAll(sample(safe(() -> deezer.artistTop(aid, 10, "same")), 3));
                        if (i < 3) {
                            for (Long rel : sample(safe(() -> deezer.related(aid, 8)), 2)) {
                                pool.addAll(sample(safe(() -> deezer.artistTop(rel, 8, "related")), 2));
                            }
                        }
                    }
                } else {
                    boolean style = target.hasStyle();
                    boolean useTags = lastfm.enabled() && (style || !target.tags.isEmpty());
                    int maxQ = 3;
                    for (int i = 0; i < target.queries.size() && i < maxQ; i++) {
                        pool.addAll(playlistPool(target.queries.get(i), turkish, 2, 40));
                    }
                    // Last.fm: istenen türün / ruh hali etiketlerinin en çok dinlenen şarkıları
                    if (useTags) {
                        List<String> tagList = new ArrayList<>();
                        if (style) {   // tür istendiyse sadece türün etiketleri (ruh hali etiketleri pop getirir)
                            for (String tg : target.style) {
                                boolean tr = tg.contains("turk") || tg.contains("türk") || tg.contains("arabesk")
                                        || tg.contains("anadolu") || tg.contains("fantezi");
                                if (turkish && !tr) tagList.add("turkish " + tg);
                                else tagList.add(tg);
                            }
                        } else {
                            tagList.addAll(target.tags);
                            if (turkish) {   // Türkçe modda önce "turkish ..." birleşik etiketleri dene
                                List<String> trTags = new ArrayList<>();
                                for (String tg : tagList) {
                                    if (!tg.contains("turk") && !tg.contains("türk") && !tg.contains("arabesk")
                                            && !tg.contains("fasil") && !tg.contains("fantezi")) trTags.add("turkish " + tg);
                                }
                                tagList.addAll(0, trTags);
                            }
                        }
                        int used = 0;
                        for (String tg : tagList) {
                            if (used >= 3) break;
                            List<String[]> top = lastfm.tagTopTracks(tg, 60);
                            if (top.size() < 10) continue;
                            used++;
                            for (String[] at : sample(top, 12)) {
                                List<Track> hit = safe(() -> deezer.search("artist:\"" + at[0] + "\" track:\"" + at[1] + "\"", 1));
                                for (Track h : hit) {
                                    h.sources.add("mood");
                                    pool.add(h);
                                }
                            }
                        }
                    }
                }
                int fromPrimary = pool.size();
                // Yeterli aday yoksa genel listelerle tamamla (sıralamayı yine ruh hali etiketleri belirler)
                if (pool.size() < 25 && favorites == null) {
                    stage(gen, "Daha fazla şarkı aranıyor…");
                    String[] fb;
                    if (target.hasStyle()) {   // tür istendiyse yedek de o türden
                        List<String> sq = new ArrayList<>(target.styleQueries);
                        for (String q : target.queries) if (!sq.contains(q)) sq.add(q);
                        fb = sq.toArray(new String[0]);
                    } else {
                        fb = turkish
                                ? new String[]{"türkçe pop hit", "türkçe akustik", "türkçe slow", "türkçe hareketli"}
                                : new String[]{"pop hits", "chill hits", "feel good hits"};
                    }
                    for (String q : fb) {
                        if (pool.size() >= 60) break;
                        pool.addAll(playlistPool(q, turkish, 1, 50));
                    }
                }
                log("Keşfet: " + fromPrimary + " aday (+" + (pool.size() - fromPrimary) + " yedek)");
                if (pool.isEmpty()) throw new Exception("NET");
                List<Track> list = dedupe(pool, "", hist);
                classifyLanguages(list);
                if (turkish) list = keepTurkish(list);
                list = preselect(list, 45);   // her seferinde biraz farklı olsun
                stage(gen, "Tempo ve enerji analiz ediliyor…");
                deezer.fillDetailsAll(list);
                if (target != null && target.hasStyle()) deezer.fillGenresAll(list);   // tür kontrolü için
                if (lastfm.enabled()) {
                    stage(gen, "Tarz etiketleri okunuyor…");
                    boolean moodTags = target != null && (!target.tags.isEmpty() || target.hasStyle());
                    fillTags(list, moodTags ? 30 : 0);
                    if (turkish) list = keepTurkish(list);
                }
                stage(gen, "Sıralanıyor…");

                Scorer.Ctx ctx = new Scorer.Ctx();
                ctx.target = target;
                ctx.strictArtist = false;
                ctx.likedKeys = likedKeys;
                ctx.artistLikes = artistLikes;
                ctx.artistDislikes = artistDislikes;
                ctx.profile = prof;
                ctx.explore = explore;
                if (dj && target != null) {   // DJ'de iniş-çıkış için aralığı genişlet
                    target.bpmLo *= 0.85;
                    target.bpmHi *= 1.15;
                }
                List<Track> ranked = Scorer.rank(ctx, list);
                for (Track t : ranked) t.score += rnd.nextDouble() * 8 - 4;
                ranked.sort((x, y) -> Double.compare(y.score, x.score));
                ranked = Scorer.keepStyle(target, ranked, 8);   // istenen türe uymayanları çıkar

                List<Track> out;
                double[] curve = null;
                if (dj) {
                    curve = new double[16];
                    out = Scorer.djOrder(ranked, 16, curve);
                    if (out.size() < curve.length) curve = java.util.Arrays.copyOf(curve, out.size());
                } else {
                    int perMax = favorites != null ? 3 : 2;
                    out = new ArrayList<>();
                    Map<String, Integer> perArtist = new HashMap<>();
                    for (Track t : ranked) {
                        String a = Text.norm(Text.mainArtist(t.artist));
                        int n = perArtist.getOrDefault(a, 0);
                        if (n >= perMax) continue;
                        perArtist.put(a, n + 1);
                        out.add(t);
                        if (out.size() >= 20) break;
                    }
                }
                final List<Track> outF = out;
                final double[] curveF = curve;
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    discoverResults = outF;
                    discoverDj = dj;
                    discoverCurve = curveF;
                    if (outF.isEmpty()) {
                        discoverProblem = turkish
                                ? "Bu tarife uyan Türkçe şarkı bulunamadı. Biraz farklı anlatmayı ya da Global'i dene."
                                : "Bu tarife uyan şarkı bulunamadı. Biraz farklı anlatmayı dene.";
                        autoStartDiscover = false;
                    } else {
                        prefetchVideoId(outF.get(0));
                        if (autoStartDiscover) {
                            autoStartDiscover = false;
                            if (wakeOrder) {   // uyanma: en sakinden en hareketliye
                                wakeOrder = false;
                                List<Track> ord = new ArrayList<>(outF);
                                ord.sort((x, y) -> Double.compare(Scorer.intensity(x), Scorer.intensity(y)));
                                playNow(ord.get(0), ord, true);
                            } else {
                                playNow(outF.get(0), outF, dj);
                            }
                        }
                    }
                    notifyChanged();
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    autoStartDiscover = false;
                    discoverProblem = "NET".equals(msg)
                            ? "Müzik servislerine şu an ulaşılamıyor. İnternet bağlantını kontrol edip tekrar dene."
                            : "Öneriler alınamadı: " + msg;
                    log("Keşfet hatası: " + msg);
                    notifyChanged();
                });
            }
        });
    }

    // =====================================================================
    // Hazır akışlar: yeni çıkanlar, günün karışımı, hava, kulaklık, Blend
    // =====================================================================

    private volatile String pendingLabel;

    /** Kendi aday listesini üreten Keşfet akışları için ortak çatı. */
    private void runCustomDiscover(String label, java.util.concurrent.Callable<List<Track>> producer, String emptyMsg) {
        final int gen = ++discoverGeneration;
        pendingLabel = null;
        discoverLoading = true;
        discoverStage = "Hazırlanıyor…";
        discoverProblem = null;
        discoverResults = new ArrayList<>();
        discoverCurve = null;
        discoverDj = false;
        discoverLabel = label;
        notifyChanged();
        discoverWorker.submit(() -> {
            try {
                List<Track> out = producer.call();
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    discoverResults = out;
                    if (pendingLabel != null) discoverLabel = pendingLabel;
                    if (out.isEmpty()) {
                        discoverProblem = emptyMsg;
                        autoStartDiscover = false;
                    } else {
                        prefetchVideoId(out.get(0));
                        if (autoStartDiscover) {
                            autoStartDiscover = false;
                            playNow(out.get(0), out, false);
                        }
                    }
                    notifyChanged();
                });
            } catch (Exception ex) {
                final String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                main.post(() -> {
                    if (gen != discoverGeneration) return;
                    discoverLoading = false;
                    autoStartDiscover = false;
                    discoverProblem = msg.startsWith("MSG:") ? msg.substring(4)
                            : "NET".equals(msg) || ex instanceof java.io.IOException
                            ? "Müzik servislerine şu an ulaşılamıyor. İnternet bağlantını kontrol edip tekrar dene."
                            : "Liste hazırlanamadı: " + msg;
                    log("Keşfet hatası: " + msg);
                    notifyChanged();
                });
            }
        });
    }

    private void clearSessionQuiet() {
        prefs.edit().remove("vibe").remove("mood").remove("place").remove("genre").apply();
    }

    private Set<String> recentAndDisliked(int recent) {
        Set<String> out = new HashSet<>(store.dislikedKeys());
        List<String> h = new ArrayList<>(history);
        for (int i = h.size() - 1, n = 0; i >= 0 && n < recent; i--, n++) out.add(h.get(i));
        return out;
    }

    /** Aynı sanatçıdan en fazla 'per' şarkı. */
    private static List<Track> capArtists(List<Track> in, int per, int max) {
        List<Track> out = new ArrayList<>();
        Map<String, Integer> n = new HashMap<>();
        for (Track t : in) {
            String a = Text.norm(Text.mainArtist(t.artist));
            int c = n.getOrDefault(a, 0);
            if (c >= per) continue;
            n.put(a, c + 1);
            out.add(t);
            if (out.size() >= max) break;
        }
        return out;
    }

    // ---- 🆕 Yeni çıkanlar ----

    public void discoverNew() {
        clearSessionQuiet();
        lastDiscover = this::discoverNew;
        runCustomDiscover("🆕 Yeni çıkanlar · sevdiğin sanatçılardan", () -> {
            if (store.liked().isEmpty()) {
                throw new Exception("MSG:Yeni çıkanlar beğendiğin sanatçılardan gelir. Önce birkaç şarkıyı ❤️ ile beğen.");
            }
            stage(discoverGeneration, "Sevdiğin sanatçıların son çıkışlarına bakılıyor…");
            List<Deezer.Album> albums = NewReleases.recent(deezer, store, 75, 30);
            Set<String> skip = new HashSet<>(store.dislikedKeys());
            List<Track> out = new ArrayList<>();
            for (Deezer.Album al : albums) {
                int n = "single".equals(al.type) ? 2 : 3;
                for (Track t : safe(() -> deezer.albumTracks(al, n, "new"))) {
                    if (skip.contains(t.key())) continue;
                    t.reason = "🆕 " + NewReleases.age(al.date) + " · " + NewReleases.typeWord(al.type)
                            + ("single".equals(al.type) ? "" : ": " + al.title);
                    out.add(t);
                }
                if (out.size() >= 30) break;
            }
            return capArtists(out, 4, 30);
        }, "Son 2,5 ayda beğendiğin sanatçılardan yeni çıkış yok. ❤️ ile daha çok sanatçı beğendikçe burası dolar.");
    }

    // ---- ☀️ Günün karışımı ----

    private static String today() {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
    }

    public void discoverDaily() { dailyMix(false); }

    public void dailyAndPlay() {
        autoStartDiscover = true;
        dailyMix(false);
    }

    private void dailyMix(boolean fresh) {
        clearSessionQuiet();
        lastDiscover = () -> dailyMix(true);
        String day = new java.text.SimpleDateFormat("EEEE", java.util.Locale.forLanguageTag("tr")).format(new java.util.Date());
        runCustomDiscover("☀️ Günün karışımı · " + day, () -> {
            String key = today();
            if (!fresh && key.equals(prefs.getString("dailyDay", null))) {
                List<Track> cached = tracksFromJson(prefs.getString("dailyList", "[]"));
                if (!cached.isEmpty()) return cached;
            }
            List<Track> mix = buildDaily(key);
            if (!mix.isEmpty()) prefs.edit().putString("dailyDay", key).putString("dailyList", tracksToJson(mix)).apply();
            return mix;
        }, "Günün karışımı için önce birkaç şarkıyı ❤️ ile beğen ya da biraz müzik dinle.");
    }

    private List<Track> buildDaily(String day) throws Exception {
        java.util.Random r = new java.util.Random(day.hashCode() * 7919L + store.liked().size());
        boolean turkish = isTurkish();
        Map<Long, String> artists = NewReleases.likedArtists(store, 25);
        if (artists.size() < 3) {   // beğeni azsa: en çok dinlenenlerden
            stage(discoverGeneration, "Dinleme geçmişine bakılıyor…");
            Map<String, Integer> cnt = new HashMap<>();
            List<Store.Play> plays = store.plays();
            for (int i = 0; i < plays.size() && i < 400; i++) {
                String a = Text.mainArtist(plays.get(i).artist);
                if (!a.isEmpty()) cnt.put(a, cnt.getOrDefault(a, 0) + 1);
            }
            List<String> top = new ArrayList<>(cnt.keySet());
            top.sort((x, y) -> cnt.get(y) - cnt.get(x));
            for (int i = 0; i < top.size() && artists.size() < 8; i++) {
                String nm = top.get(i);
                for (Track t : safe(() -> deezer.search("artist:\"" + nm + "\"", 1))) if (t.artistId > 0) artists.put(t.artistId, nm);
            }
        }
        if (artists.isEmpty()) return new ArrayList<>();
        List<Long> ids = new ArrayList<>(artists.keySet());
        Collections.shuffle(ids, r);
        Set<String> liked = store.likedKeys();
        List<Track> pool = new ArrayList<>();

        stage(discoverGeneration, "Sevdiğin şarkılardan seçiliyor…");
        List<Store.Liked> lk = new ArrayList<>(store.liked());
        Collections.shuffle(lk, r);
        for (int i = 0; i < lk.size() && i < 6; i++) {
            Track t = lk.get(i).toTrack();
            t.reason = "❤️ sevdiğin bir şarkı";
            pool.add(t);
        }
        stage(discoverGeneration, "Sevdiğin sanatçılara bakılıyor…");
        for (int i = 0; i < ids.size() && i < 5; i++) {
            long id = ids.get(i);
            List<Track> top = new ArrayList<>(safe(() -> deezer.artistTop(id, 12, "same")));
            Collections.shuffle(top, r);
            int n = 0;
            for (Track t : top) {
                if (liked.contains(t.key())) continue;
                t.reason = "🎤 " + Text.mainArtist(t.artist);
                pool.add(t);
                if (++n >= 2) break;
            }
        }
        stage(discoverGeneration, "Benzer sanatçılar keşfediliyor…");
        for (int i = 0; i < ids.size() && i < 5; i++) {
            long id = ids.get(i);
            String from = artists.get(id);
            List<Long> rel = new ArrayList<>(safe(() -> deezer.related(id, 8)));
            Collections.shuffle(rel, r);
            for (int k = 0; k < rel.size() && k < 2; k++) {
                long rid = rel.get(k);
                if (artists.containsKey(rid)) continue;
                List<Track> top = new ArrayList<>(safe(() -> deezer.artistTop(rid, 8, "related")));
                if (top.isEmpty()) continue;
                Track t = top.get(r.nextInt(Math.min(4, top.size())));
                t.reason = "✨ " + from + " sevenlere";
                pool.add(t);
            }
        }
        Map<String, Integer> prof = profile();
        if (lastfm.enabled() && !prof.isEmpty()) {
            stage(discoverGeneration, "Tarzına uygun yeni şarkılar aranıyor…");
            List<String> tags = new ArrayList<>(prof.keySet());
            for (int i = 0; i < tags.size() && i < 2; i++) {
                String tg = tags.get(i);
                List<String[]> top = new ArrayList<>(lastfm.tagTopTracks(tg, 60));
                Collections.shuffle(top, r);
                int n = 0;
                for (String[] at : top) {
                    for (Track h : safe(() -> deezer.search("artist:\"" + at[0] + "\" track:\"" + at[1] + "\"", 1))) {
                        h.reason = "🧭 tarzın: " + tg;
                        pool.add(h);
                        n++;
                    }
                    if (n >= 4) break;
                }
            }
        }
        List<Track> list = dedupe(pool, "", recentAndDisliked(15));
        classifyLanguages(list);
        if (turkish) list = keepTurkish(list);
        stage(discoverGeneration, "Tempo ve enerji analiz ediliyor…");
        deezer.fillDetailsAll(list);
        // Sırala: sakin başla, ortada yüksel, sonda yumuşa (DJ eğrisi gibi ama hafif)
        Collections.shuffle(list, r);
        List<Track> capped = capArtists(list, 2, 26);
        List<Track> ordered = Scorer.djOrder(capped, capped.size(), new double[capped.size()]);
        return ordered.isEmpty() ? capped : ordered;
    }

    private static String tracksToJson(List<Track> list) {
        org.json.JSONArray a = new org.json.JSONArray();
        for (Track t : list) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", t.deezerId).put("ti", t.title).put("ar", t.artist).put("ai", t.artistId)
                        .put("c", t.coverUrl == null ? "" : t.coverUrl).put("cb", t.coverBigUrl == null ? "" : t.coverBigUrl)
                        .put("d", t.durationSec).put("r", t.reason == null ? "" : t.reason).put("y", t.year)
                        .put("v", t.videoId == null ? "" : t.videoId);
                if (t.hasBpm()) o.put("b", t.bpm);
                if (t.hasGain()) o.put("g", t.gain);
                a.put(o);
            } catch (Exception ignored) {}
        }
        return a.toString();
    }

    private static List<Track> tracksFromJson(String s) {
        List<Track> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Track t = new Track();
                t.deezerId = o.optLong("id");
                t.title = o.optString("ti");
                t.artist = o.optString("ar");
                t.artistId = o.optLong("ai");
                t.coverUrl = o.optString("c").isEmpty() ? null : o.optString("c");
                t.coverBigUrl = o.optString("cb").isEmpty() ? null : o.optString("cb");
                t.durationSec = o.optInt("d");
                t.reason = o.optString("r");
                t.year = o.optInt("y");
                t.videoId = o.optString("v").isEmpty() ? null : o.optString("v");
                if (o.has("b")) { t.bpm = o.optDouble("b"); }
                if (o.has("g")) { t.gain = o.optDouble("g"); }
                t.detailsLoaded = o.has("b") || o.has("g");
                t.lang = Lang.detect(t.title, t.artist);
                if (!t.title.isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ---- 🌦 Havaya göre ----

    public void discoverWeather() {
        lastDiscover = this::discoverWeather;
        if (!Weather.hasPlace(app)) {
            discoverLoading = false;
            discoverResults = new ArrayList<>();
            discoverProblem = "Hava durumu için önce Ayarlar → Akıllı bağlam'dan şehrini seç.";
            notifyChanged();
            return;
        }
        final int gen = ++discoverGeneration;
        discoverLoading = true;
        discoverProblem = null;
        discoverResults = new ArrayList<>();
        discoverLabel = "🌦 Havaya göre";
        discoverStage = "Hava durumuna bakılıyor…";
        notifyChanged();
        net.submit(() -> {
            Weather.Now w = Weather.fetchNow(app);
            main.post(() -> {
                if (gen != discoverGeneration) return;
                if (w == null) {
                    discoverLoading = false;
                    autoStartDiscover = false;
                    discoverProblem = "Hava durumu alınamadı. İnternet bağlantını kontrol edip tekrar dene.";
                    notifyChanged();
                    return;
                }
                String sentence = w.sentence();
                Vibe.Result r = Vibe.parse(sentence, hourNow());
                prefs.edit().remove("genre").putString("vibe", sentence).putString("mood", r.moodId)
                        .putString("place", r.placeId).putLong("sessionTs", System.currentTimeMillis()).apply();
                boolean turkish = isTurkish();
                Moods.Target t = r.target(turkish);
                for (String tg : w.tags()) if (!t.tags.contains(tg)) t.tags.add(tg);
                t.label = w.label() + "  ·  " + r.summary();
                log("🌦 " + w.label() + " → " + r.summary());
                runDiscover(t, turkish, null);
            });
        });
    }

    // ---- 🎧 Kulaklık ----

    public static final int HP_OFF = 0, HP_ASK = 1, HP_AUTO = 2;
    public int getHpMode() { return prefs.getInt("hpMode", HP_ASK); }
    public void setHpMode(int m) { prefs.edit().putInt("hpMode", m).apply(); notifyChanged(); }

    /** Şu ana uygun başlangıç: rutin, hava ya da saat. */
    String contextSentence() {
        Weather.Now w = Weather.isOn(app) ? Weather.current() : null;
        if (w != null) return w.sentence();
        int h = hourNow();
        return h < 6 ? "gece sakin" : h < 11 ? "sabah keyifli" : h < 17 ? "hareketli keyifli" : h < 22 ? "akşam" : "gece sakin";
    }

    String contextLabel() {
        Routines.Routine r = Routines.active(app);
        if (r != null) return r.label();
        Weather.Now w = Weather.isOn(app) ? Weather.current() : null;
        Vibe.Result v = Vibe.parse(contextSentence(), hourNow());
        return (w != null ? w.label() + " · " : "") + v.summary();
    }

    /** Şu ana uygun liste (kulaklık takılınca ya da bildirimden). */
    public void discoverForNow(boolean play) {
        autoStartDiscover = play;
        Routines.Routine r = activeRoutine();
        if (r != null && getVibe() == null) {
            if ("mood".equals(r.kind)) { discover(r.value, null); return; }
            if ("genre".equals(r.kind)) { discoverGenre(r.value); return; }
            discoverText(r.value);
            return;
        }
        discoverText(contextSentence());
    }

    public void onHeadphonesConnected(String name) {
        int m = getHpMode();
        if (m == HP_OFF || playing) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("hpLast", 0) < 10 * 60 * 1000) return;
        prefs.edit().putLong("hpLast", now).apply();
        log("🎧 " + name + " bağlandı");
        if (m == HP_AUTO) {
            discoverForNow(true);
        } else {
            notifier.headphonesPrompt(contextLabel());
        }
    }

    // ---- 🤝 Blend ----

    public void discoverBlend(Blend.Taste f) {
        clearSessionQuiet();
        lastDiscover = () -> discoverBlend(f);
        String who = f.name == null || f.name.trim().isEmpty() ? "arkadaşın" : f.name.trim();
        runCustomDiscover("🤝 Blend · Sen + " + who, () -> buildBlend(f, who),
                "Ortak liste çıkarılamadı. İkinizin de birkaç şarkı beğenmiş olması gerekiyor.");
    }

    private List<Track> buildBlend(Blend.Taste f, String who) {
        Blend.Taste me = Blend.mine(this, "");
        Set<Long> myIds = new HashSet<>();
        Set<String> myNames = new HashSet<>();
        for (int i = 0; i < me.artistIds.size(); i++) {
            myIds.add(me.artistIds.get(i)[0]);
            myNames.add(Text.norm(me.artistNames.get(i)));
        }
        List<Long> common = new ArrayList<>(), theirsOnly = new ArrayList<>();
        List<String> commonNames = new ArrayList<>();
        for (int i = 0; i < f.artistIds.size(); i++) {
            long id = f.artistIds.get(i)[0];
            String nm = f.artistNames.get(i);
            if (myIds.contains(id) || myNames.contains(Text.norm(nm))) { common.add(id); commonNames.add(nm); }
            else theirsOnly.add(id);
        }
        List<Long> mineOnly = new ArrayList<>();
        for (long[] a : me.artistIds) if (!common.contains(a[0])) mineOnly.add(a[0]);

        List<Track> both = new ArrayList<>(), theirs = new ArrayList<>(), mine = new ArrayList<>();
        stage(discoverGeneration, "Ortak sanatçılarınız aranıyor…");
        for (int i = 0; i < common.size() && i < 5; i++) {
            long id = common.get(i);
            for (Track t : sample(safe(() -> deezer.artistTop(id, 10, "blend")), 3)) {
                t.reason = "🤝 ikinizin de sevdiği sanatçı";
                both.add(t);
            }
        }
        stage(discoverGeneration, who + " için beğenileri okunuyor…");
        for (Long id : sample(f.tracks, 14)) {
            try {
                Track t = deezer.track(id, "blend");
                if (t != null) { t.reason = "💬 " + who + " seviyor"; theirs.add(t); }
            } catch (Exception ignored) {}
        }
        for (Long id : sample(theirsOnly, 4)) {
            for (Track t : sample(safe(() -> deezer.artistTop(id, 8, "blend")), 2)) {
                t.reason = "💬 " + who + " dinliyor";
                theirs.add(t);
            }
        }
        for (Store.Liked l : sample(store.liked(), 10)) {
            Track t = l.toTrack();
            t.reason = "❤️ senin beğenin";
            mine.add(t);
        }
        for (Long id : sample(mineOnly, 3)) {
            for (Track t : sample(safe(() -> deezer.artistTop(id, 8, "blend")), 2)) {
                t.reason = "🎧 senin sanatçın";
                mine.add(t);
            }
        }
        // Harmanla: önce ortaklar, sonra sırayla onun-senin
        List<Track> mixed = new ArrayList<>();
        Collections.shuffle(both, rnd);
        Collections.shuffle(theirs, rnd);
        Collections.shuffle(mine, rnd);
        for (int i = 0; i < both.size() && i < 4; i++) mixed.add(both.get(i));
        int ti = 0, mi = 0, bi = Math.min(4, both.size());
        while (ti < theirs.size() || mi < mine.size() || bi < both.size()) {
            if (ti < theirs.size()) mixed.add(theirs.get(ti++));
            if (mi < mine.size()) mixed.add(mine.get(mi++));
            if (bi < both.size() && mixed.size() % 4 == 0) mixed.add(both.get(bi++));
            else if (ti >= theirs.size() && mi >= mine.size() && bi < both.size()) mixed.add(both.get(bi++));
        }
        List<Track> list = capArtists(dedupe(mixed, "", new HashSet<>(store.dislikedKeys())), 2, 26);

        // Ortak noktalar: etiketler ve sanatçılar
        List<String> sharedTags = new ArrayList<>();
        for (String tg : me.tags) if (f.tags.contains(tg) && sharedTags.size() < 3) sharedTags.add(tg);
        List<String> parts = new ArrayList<>();
        if (!sharedTags.isEmpty()) parts.add("ortak tarz: " + String.join(", ", sharedTags));
        if (!commonNames.isEmpty()) parts.add("ortak sanatçı: " + String.join(", ", commonNames.subList(0, Math.min(2, commonNames.size()))));
        pendingLabel = "🤝 Sen + " + who + (parts.isEmpty() ? "" : "  ·  " + String.join("  ·  ", parts));
        return list;
    }

    // ---- ▶ YouTube Music'e aktar ----

    private final List<String> externalTitles = new ArrayList<>();
    private long externalStarted;

    private int externalIndex(String title) {
        for (int i = 0; i < externalTitles.size(); i++) if (titlesMatch(title, externalTitles.get(i))) return i;
        return -1;
    }

    /** YouTube Music'te aktarılan liste çalarken (son şarkı hariç) araya girme. */
    private boolean externalHolds() {
        if (externalTitles.isEmpty() || nowTitle == null) return false;
        int i = externalIndex(nowTitle);
        return i >= 0 && i < externalTitles.size() - 1;
    }

    public boolean exporting;

    /**
     * Listeyi YouTube Music'te sıralı bir liste olarak açar. Olmazsa listeyi Ahenk sırasıyla çalar.
     * done: ana iş parçacığında, kullanıcıya gösterilecek kısa mesajla.
     */
    public void exportToYtMusic(List<Track> list, java.util.function.Consumer<String> done) {
        if (list == null || list.isEmpty() || exporting) return;
        exporting = true;
        notifyChanged();
        final List<Track> l = new ArrayList<>(list.subList(0, Math.min(30, list.size())));
        net.submit(() -> {
            List<String> ids = new ArrayList<>();
            List<Track> found = new ArrayList<>();
            for (Track t : l) {
                if (t.videoId == null) t.videoId = YtMusic.findVideoId(t.artist, t.title);
                if (t.videoId != null) { ids.add(t.videoId); found.add(t); }
            }
            String listId = ids.size() >= 2 ? YtMusic.tempPlaylist(ids) : null;
            main.post(() -> {
                exporting = false;
                if (listId != null) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://music.youtube.com/watch?v=" + ids.get(0) + "&list=" + listId));
                        i.setPackage(YTM);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.startActivity(i);
                        externalTitles.clear();
                        for (Track t : found) externalTitles.add(t.title);
                        externalStarted = SystemClock.elapsedRealtime();
                        lastExportLink = YtMusic.shareLink(ids);
                        log("▶ " + found.size() + " şarkı YouTube Music'e liste olarak aktarıldı");
                        if (done != null) done.accept("YouTube Music'te " + found.size()
                                + " şarkılık liste açıldı. Kalıcı kaydetmek için orada ⋮ → Çalma listesine kaydet.");
                        notifyChanged();
                        return;
                    } catch (Exception ex) {
                        log("YouTube Music listesi açılamadı: " + ex.getMessage());
                    }
                }
                if (!ids.isEmpty()) lastExportLink = YtMusic.shareLink(ids);
                if (!found.isEmpty()) playNow(found.get(0), found);
                if (done != null) done.accept("YouTube liste oluşturmadı; şarkılar Ahenk sırasıyla çalınıyor.");
                notifyChanged();
            });
        });
    }

    /** Listeyi çalan uygulamada liste olarak açar (YouTube Music ya da Spotify). */
    public void exportList(List<Track> list, java.util.function.Consumer<String> done) {
        if (isSpotify()) exportToSpotify(list, done);
        else exportToYtMusic(list, done);
    }

    private List<Track> resolveSpotify(List<Track> l, List<String> uris) {
        List<Track> found = new ArrayList<>();
        for (Track t : l) {
            if (t.spotifyUri == null) t.spotifyUri = spotify.findTrack(t.artist, t.title);
            if (t.spotifyUri != null) { uris.add(t.spotifyUri); found.add(t); }
        }
        return found;
    }

    private void exportToSpotify(List<Track> list, java.util.function.Consumer<String> done) {
        if (list == null || list.isEmpty() || exporting) return;
        if (!spotify.connected()) {
            playNow(list.get(0), list);
            if (done != null) done.accept("Liste Ahenk sırasıyla çalınıyor. Spotify'da liste olarak açmak için "
                    + "Ayarlar → Müzik uygulaması'ndan hesabını bağla.");
            return;
        }
        exporting = true;
        notifyChanged();
        final List<Track> l = new ArrayList<>(list.subList(0, Math.min(30, list.size())));
        net.submit(() -> {
            List<String> uris = new ArrayList<>();
            List<Track> found = resolveSpotify(l, uris);
            int r = uris.isEmpty() ? 3 : spotify.playerBlocked() ? 1 : spotify.play(uris);
            main.post(() -> {
                exporting = false;
                if (r == 0) {
                    externalTitles.clear();
                    for (Track t : found) externalTitles.add(t.title);
                    externalStarted = SystemClock.elapsedRealtime();
                    log("▶ " + found.size() + " şarkı Spotify'da liste olarak çalıyor");
                    if (done != null) done.accept("Spotify'da " + found.size() + " şarkılık liste çalıyor.");
                } else {
                    if (!found.isEmpty()) playNow(found.get(0), found);
                    else if (!l.isEmpty()) playNow(l.get(0), l);
                    if (done != null) done.accept(r == 1 ? "Spotify liste çalmak için Premium istiyor; şarkılar Ahenk sırasıyla çalınıyor."
                            : r == 2 ? "Spotify açık değil gibi; önce Spotify'da bir şarkı aç. Şimdilik Ahenk sırasıyla çalınıyor."
                            : "Spotify listeyi başlatmadı; şarkılar Ahenk sırasıyla çalınıyor.");
                }
                notifyChanged();
            });
        });
    }

    /** Listeyi Spotify hesabına çalma listesi olarak kaydeder. */
    public void saveToSpotify(List<Track> list, String name, java.util.function.Consumer<String> done) {
        if (list == null || list.isEmpty() || exporting) return;
        if (!spotify.connected()) {
            if (done != null) done.accept("Önce Ayarlar → Müzik uygulaması'ndan Spotify hesabını bağla.");
            return;
        }
        exporting = true;
        notifyChanged();
        final List<Track> l = new ArrayList<>(list);
        net.submit(() -> {
            List<String> uris = new ArrayList<>();
            resolveSpotify(l, uris);
            String link = uris.isEmpty() ? null : spotify.savePlaylist(name, uris);
            main.post(() -> {
                exporting = false;
                if (done != null) done.accept(link != null ? "💾 Spotify'a kaydedildi: " + name + " (" + uris.size() + " şarkı)"
                        : "Spotify'a kaydedilemedi. Bağlantını Ayarlar'dan yenilemeyi dene.");
                if (link != null) log("💾 Spotify listesi oluşturuldu: " + name);
                notifyChanged();
            });
        });
    }

    /** Son aktarılan listenin tarayıcıda da açılan bağlantısı (paylaşmak için). */
    public volatile String lastExportLink;

    // =====================================================================
    // Arama: şarkı ya da sanatçıdan başlat
    // =====================================================================

    public boolean searchLoading;
    public List<Track> searchResults = new ArrayList<>();
    public String searchProblem;
    private int searchGeneration;

    public void search(String query) {
        final String q = query.trim();
        final int gen = ++searchGeneration;
        if (q.length() < 2) {
            searchLoading = false;
            searchResults = new ArrayList<>();
            searchProblem = null;
            notifyChanged();
            return;
        }
        searchLoading = true;
        searchProblem = null;
        notifyChanged();
        net.submit(() -> {
            List<Track> r;
            String err = null;
            try {
                r = deezer.search(q, 15);
            } catch (Exception e) {
                r = new ArrayList<>();
                err = "Arama yapılamadı: " + e.getMessage();
            }
            final List<Track> rf = r;
            final String ef = err;
            main.post(() -> {
                if (gen != searchGeneration) return;
                searchLoading = false;
                searchResults = rf;
                searchProblem = ef != null ? ef : rf.isEmpty() ? "Sonuç bulunamadı" : null;
                notifyChanged();
            });
        });
    }

    public void clearSearch() {
        ++searchGeneration;
        searchLoading = false;
        searchResults = new ArrayList<>();
        searchProblem = null;
        notifyChanged();
    }

    /** Seçilen şarkıyı hemen başlat; Ahenk ondan devam eder. */
    public void startFrom(Track t) {
        queue.clear();
        queueVersion++;
        plan = new ArrayList<>();
        log("Başlatılıyor: " + t);
        fire(t, true);
        refreshPick();
        notifyChanged();
    }

    // =====================================================================
    // ❤️ Beğendim
    // =====================================================================

    // ---- 👎 Kötü geçiş ----

    private Track transFrom;
    private boolean transRated;

    /** Çalan şarkıya uygulamanın yaptığı bir geçişle gelindiyse, önceki şarkı. */
    public Track lastTransitionFrom() {
        return transRated || nowTitle == null ? null : transFrom;
    }

    /** "A'dan bu şarkıya geçiş kötüydü": bu tarz çiftinden ve sanatçı çiftinden kaçınmayı öğren. */
    public void markBadTransition() {
        Track from = transFrom;
        if (from == null || nowTitle == null) return;
        Track to = seed;
        String sa = Scorer.styleSig(from);
        String sb = to != null ? Scorer.styleSig(to) : null;
        store.addBadTransition(sa, sb, Text.norm(Text.mainArtist(from.artist)), Text.norm(Text.mainArtist(nowArtist)));
        transRated = true;
        log("👎 Kötü geçiş: " + from.artist + " → " + nowArtist
                + (sa != null && sb != null ? "  (" + sa + " → " + sb + " kaçınılacak)" : ""));
        notifyChanged();
    }

    public boolean isNowLiked() {
        return nowKey != null && store.isLiked(nowKey);
    }

    public void toggleLikeNow() {
        if (nowKey == null) return;
        if (store.isLiked(nowKey)) {
            store.unlike(nowKey);
            log("❤️ kaldırıldı: " + nowTitle);
        } else {
            Track s = seed;
            store.like(nowKey, nowArtist, nowTitle, s != null ? s.deezerId : 0, s != null ? s.artistId : 0,
                    s != null ? s.coverUrl : null);
            log("❤️ " + nowArtist + " – " + nowTitle);
        }
        ensureProfile();
        notifyChanged();
    }

    public void unlike(String key) {
        store.unlike(key);
        notifyChanged();
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
