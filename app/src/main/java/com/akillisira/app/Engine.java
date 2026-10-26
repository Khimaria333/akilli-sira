package com.akillisira.app;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
 * Uygulamanın beyni. Servisten "şarkı değişti / şu saniyedeyiz" haberlerini alır,
 * sıradaki şarkıyı hesaplar ve zamanı gelince YT Music'e o şarkıyı çaldırır.
 * Tüm durum ana (UI) iş parçacığında tutulur; ağ işleri arka planda yapılır.
 */
public final class Engine {

    public interface Listener { void onEngineChanged(); }

    public static final String YTM = "com.google.android.apps.youtube.music";
    private static final long SWITCH_BEFORE_END_MS = 1200;   // bitişe bu kadar kala geç
    private static final long NEAR_END_MS = 6000;            // YT Music kendi şarkısına geçtiyse anlamak için

    private static Engine instance;

    public static synchronized Engine get(Context c) {
        if (instance == null) instance = new Engine(c.getApplicationContext());
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Deezer deezer = new Deezer();
    private final SharedPreferences prefs;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // ---- Ekranda gösterilen durum ----
    public boolean serviceConnected;
    public MediaController controller;
    public String nowTitle, nowArtist;
    public long nowDurationMs, nowPositionMs;
    public boolean playing;
    public Track seed;                       // çalan şarkının Deezer bilgisi (BPM vb.)
    public Track pick;                       // sıradaki
    public List<Track> alternatives = new ArrayList<>();
    public boolean computing;
    public String problem;                   // hesaplanamadıysa nedeni
    public final LinkedList<String> log = new LinkedList<>();

    // ---- İç durum ----
    private final LinkedHashSet<String> history = new LinkedHashSet<>();
    private final LinkedList<String> recentArtists = new LinkedList<>();
    private String nowKey;
    private int generation;
    private boolean firedForCurrent;
    private long expectSwitchUntil;
    private long lastRemainingMs = Long.MAX_VALUE;

    private Engine(Context app) {
        prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    // ================= Ayarlar =================

    public boolean isAuto() { return prefs.getBoolean("auto", true); }

    public void setAuto(boolean on) {
        prefs.edit().putBoolean("auto", on).apply();
        log(on ? "Otomatik geçiş açık" : "Otomatik geçiş kapalı");
        notifyChanged();
    }

    /** -1 sakinleştir, 0 aynı hava, +1 hareketlendir */
    public int getMode() { return prefs.getInt("mode", 0); }

    public void setMode(int m) {
        if (m == getMode()) return;
        prefs.edit().putInt("mode", m).apply();
        if (nowKey != null) recompute();
    }

    // ================= Servisten gelenler =================

    public void setServiceConnected(boolean c) {
        serviceConnected = c;
        notifyChanged();
    }

    public void setController(MediaController c) {
        controller = c;
        if (c == null) playing = false;
        log(c != null ? "YT Music'e bağlandı" : "YT Music bağlantısı kapandı");
        notifyChanged();
    }

    public boolean supportsPlayFromSearch() {
        if (controller == null) return false;
        PlaybackState st = controller.getPlaybackState();
        return st != null && (st.getActions() & PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0;
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

        boolean inSwitch = SystemClock.elapsedRealtime() < expectSwitchUntil;
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
            // Bizim seçtiğimiz şarkı başladı
            expectSwitchUntil = 0;
            remember(key, artist);
            boolean match = planned != null && (Text.norm(title).contains(Text.norm(planned.title))
                    || Text.norm(planned.title).contains(Text.norm(title)));
            log(match ? "▶ Sıradaki başladı: " + title
                      : "▶ YT Music farklı bir sürüm/şarkı açtı: " + title);
            recompute();
            return;
        }

        if (ytmAutoAdvanced && isAuto() && planned != null && !key.equals(planned.key())) {
            // Şarkı bitti ve YT Music kendi seçtiğine geçti: bizim seçtiğimizle değiştir
            log("YT Music kendi şarkısına geçti, düzeltiliyor");
            fire(planned, null);
            return;
        }

        // Kullanıcı kendisi bir şarkı açtı ya da geçti
        remember(key, artist);
        log("♪ " + artist + " – " + title);
        recompute();
    }

    /** Servis her yarım saniyede bir çalma konumunu bildirir. */
    public void onPoll(long positionMs, long durationMs, boolean isPlaying) {
        nowPositionMs = positionMs;
        if (durationMs > 0) nowDurationMs = durationMs;
        playing = isPlaying;
        if (nowDurationMs > 0) lastRemainingMs = nowDurationMs - positionMs;

        if (isAuto() && isPlaying && pick != null && !firedForCurrent
                && nowDurationMs > 30000 && lastRemainingMs <= SWITCH_BEFORE_END_MS) {
            fire(pick, null);
        }
        notifyChanged();
    }

    // ================= Kullanıcıdan gelenler =================

    public void choose(Track t) {
        pick = t;
        List<Track> rest = new ArrayList<>();
        if (seed != null) {
            for (Track a : allRanked) if (a != t && rest.size() < 6) rest.add(a);
        }
        alternatives = rest;
        log("Sıradaki olarak seçildi: " + t);
        notifyChanged();
    }

    /** "Şimdi geç" düğmesi. Uygulama ön plandayken çağrılır. */
    public void skipNow(Activity a) {
        if (pick == null) return;
        fire(pick, a);
    }

    // ================= YT Music'e çaldırma =================

    private void fire(Track t, Activity foreground) {
        firedForCurrent = true;
        expectSwitchUntil = SystemClock.elapsedRealtime() + 15000;
        String query = Text.mainArtist(t.artist) + " " + Text.cleanTitle(t.title);

        Bundle extras = new Bundle();
        extras.putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE);
        extras.putString(MediaStore.EXTRA_MEDIA_TITLE, t.title);
        extras.putString(MediaStore.EXTRA_MEDIA_ARTIST, t.artist);
        extras.putString(SearchManager.QUERY, query);

        boolean viaSession = controller != null && (supportsPlayFromSearch() || foreground == null);
        if (viaSession) {
            try {
                controller.getTransportControls().playFromSearch(query, extras);
                log("→ YT Music'e gönderildi: " + t);
                notifyChanged();
                return;
            } catch (Exception e) {
                log("Oturum üzerinden çalınamadı: " + e.getMessage());
            }
        }
        if (foreground != null) {
            try {
                Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                i.setPackage(YTM);
                i.putExtras(extras);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                foreground.startActivity(i);
                log("→ YT Music'e (arama ile) gönderildi: " + t);
            } catch (Exception e) {
                log("YT Music açılamadı: " + e.getMessage());
                expectSwitchUntil = 0;
            }
        } else if (controller == null) {
            log("YT Music bağlı değil, geçilemedi");
            expectSwitchUntil = 0;
        }
        notifyChanged();
    }

    // ================= Sıradakini hesaplama =================

    private List<Track> allRanked = new ArrayList<>();

    private void recompute() {
        final int gen = ++generation;
        final String title = nowTitle, artist = nowArtist;
        final int mode = getMode();
        final Set<String> hist = new HashSet<>(history);
        final List<String> recent = new ArrayList<>(recentArtists);

        computing = true;
        problem = null;
        pick = null;
        seed = null;
        alternatives = new ArrayList<>();
        allRanked = new ArrayList<>();
        notifyChanged();

        worker.submit(() -> {
            try {
                Track s = deezer.findSeed(title, artist);
                if (s == null) throw new Exception("Bu şarkı Deezer'de bulunamadı");
                deezer.fillDetails(s);
                List<Track> ranked = build(s, hist, recent, mode);
                main.post(() -> {
                    if (gen != generation) return;
                    computing = false;
                    seed = s;
                    allRanked = ranked;
                    if (ranked.isEmpty()) {
                        problem = "Uygun aday bulunamadı";
                    } else {
                        pick = ranked.get(0);
                        alternatives = new ArrayList<>(ranked.subList(1, Math.min(7, ranked.size())));
                        log("Sıradaki: " + pick + "  (" + pick.reason + ")");
                    }
                    notifyChanged();
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                main.post(() -> {
                    if (gen != generation) return;
                    computing = false;
                    problem = msg;
                    log("Hesaplanamadı: " + msg);
                    notifyChanged();
                });
            }
        });
    }

    /** Arka planda: adayları topla, detaylarını çek, puanla. */
    private List<Track> build(Track s, Set<String> hist, List<String> recent, int mode) throws Exception {
        List<Track> pool = new ArrayList<>();
        if (s.artistId > 0) {
            pool.addAll(safe(() -> deezer.artistRadio(s.artistId, 25)));
            for (Long rel : safe(() -> deezer.related(s.artistId, 6))) {
                pool.addAll(safe(() -> deezer.artistTop(rel, 4, "related")));
            }
            pool.addAll(safe(() -> deezer.artistTop(s.artistId, 5, "same")));
        }

        // Tekrarları birleştir, yakında çalınanları ve çalan şarkıyı çıkar
        Map<String, Track> uniq = new LinkedHashMap<>();
        String seedKey = s.key();
        for (Track t : pool) {
            String k = t.key();
            if (k.equals(seedKey) || hist.contains(k)) continue;
            Track ex = uniq.get(k);
            if (ex == null) uniq.put(k, t);
            else ex.sources.addAll(t.sources);
        }

        List<Track> list = new ArrayList<>(uniq.values());
        list.sort((a, b) -> Double.compare(Scorer.preScore(b), Scorer.preScore(a)));
        if (list.size() > 36) list = new ArrayList<>(list.subList(0, 36));

        deezer.fillDetailsAll(list);
        return Scorer.rank(s, list, recent, mode);
    }

    private interface IoCall<T> { T call() throws Exception; }

    private static <T> List<T> safe(IoCall<List<T>> c) {
        try {
            List<T> r = c.call();
            return r != null ? r : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ================= Yardımcılar =================

    private void remember(String key, String artist) {
        history.remove(key);
        history.add(key);
        while (history.size() > 200) {
            String first = history.iterator().next();
            history.remove(first);
        }
        recentArtists.addFirst(Text.norm(Text.mainArtist(artist)));
        while (recentArtists.size() > 3) recentArtists.removeLast();
    }

    public void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        log.addFirst(ts + "  " + msg);
        while (log.size() > 40) log.removeLast();
    }

    public void addListener(Listener l) { listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        for (Listener l : listeners) l.onEngineChanged();
    }
}
