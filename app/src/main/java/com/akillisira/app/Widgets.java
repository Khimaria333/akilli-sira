package com.akillisira.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ana ekran widget'ı. Boyuta göre dört düzen:
 * küçük (1 satır) · orta (2 satır) · büyük (3 satır: sıradaki kartı + ruh hali düğmeleri) · çok büyük (+ "Sonra" listesi).
 * Android 12+ boyut eşlemesini kendisi yapar; daha eskilerde widget seçeneklerinden seçilir.
 */
final class Widgets {

    private static final int SMALL = 0, MEDIUM = 1, LARGE = 2, XL = 3;
    private static final int[] LAYOUTS = {R.layout.widget_small, R.layout.widget_medium, R.layout.widget_large, R.layout.widget_xl};
    /** Düzenlerin sığdığı en küçük boyutlar (dp). */
    private static final float[][] SIZES = {{160, 40}, {220, 168}, {220, 274}, {220, 364}};

    private static final long TICK_MS = 5000;

    private final Context app;
    private String lastSig;
    private long lastTick;

    // Kapak ve renk önbelleği (her güncellemede yeniden hesaplanmasın)
    private String coverKey, nextKey;
    private Bitmap coverRound, nextRound;
    private int glowColor = Ui.ACCENT;
    private final java.util.Set<String> requested = new java.util.HashSet<>();   // her kapağı bir kez indir

    Widgets(Context app) {
        this.app = app;
    }

    private int[] ids() {
        try {
            int[] ids = AppWidgetManager.getInstance(app).getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
            return ids != null ? ids : new int[0];
        } catch (Exception e) {
            return new int[0];
        }
    }

    /** Çalarken birkaç saniyede bir: ilerleme çubuğu ve geri sayımlar için. Ekran kapalıyken çalışmaz. */
    void tick(Engine e) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTick < TICK_MS) return;
        lastTick = now;
        PowerManager pm = app.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return;
        update(e, true);
    }

    void update(Engine e, boolean force) {
        int[] ids = ids();
        if (ids.length == 0) return;
        Track p = e.pick;

        // ---- Kapak: önce YouTube Music'in gösterdiği görsel, yoksa Deezer kapağı ----
        String ck = e.nowTitle + "|" + e.nowArtist;
        Bitmap art = e.nowArt();
        String deezerCover = e.seed != null ? (e.seed.coverBigUrl != null ? e.seed.coverBigUrl : e.seed.coverUrl) : null;
        if (art == null && deezerCover != null) {
            art = ImageLoader.cached(deezerCover);
            if (art == null && requested.add(deezerCover)) ImageLoader.fetch(deezerCover, b -> { lastSig = null; update(e, false); });
        }
        String ckFull = ck + "|" + (art != null ? art.getGenerationId() + "x" + art.getWidth() : "-");
        if (!ckFull.equals(coverKey)) {
            coverKey = ckFull;
            coverRound = art != null ? rounded(art, Ui.dp(app, 72), Ui.dp(app, 14)) : null;
            glowColor = art != null ? Palette.vibrant(art, Ui.ACCENT) : Ui.ACCENT;
        }
        if (p != null) {
            String nk = p.key() + "|" + p.coverUrl;
            Bitmap nb = ImageLoader.cached(p.coverUrl);
            if (nb == null && p.coverUrl != null && requested.add(p.coverUrl)) ImageLoader.fetch(p.coverUrl, b -> { lastSig = null; update(e, false); });
            nk += "|" + (nb != null);
            if (!nk.equals(nextKey)) {
                nextKey = nk;
                nextRound = nb != null ? rounded(nb, Ui.dp(app, 40), Ui.dp(app, 10)) : null;
            }
        } else {
            nextKey = null;
            nextRound = null;
        }

        State s = new State(e);
        String sig = s.sig() + "|" + coverKey + "|" + nextKey;
        if (!force && sig.equals(lastSig)) return;
        lastSig = sig;

        AppWidgetManager wm = AppWidgetManager.getInstance(app);
        if (Build.VERSION.SDK_INT >= 31) {
            Map<SizeF, RemoteViews> m = new LinkedHashMap<>();
            for (int i = 0; i < LAYOUTS.length; i++) m.put(new SizeF(SIZES[i][0], SIZES[i][1]), build(i, s));
            wm.updateAppWidget(ids, new RemoteViews(m));
        } else {
            for (int id : ids) wm.updateAppWidget(id, build(sizeFor(wm.getAppWidgetOptions(id)), s));
        }
    }

    /** Android 11 ve öncesi: widget'ın dikey boyutuna göre düzen seç. */
    private static int sizeFor(Bundle o) {
        if (o == null) return MEDIUM;
        int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120);
        int best = SMALL;
        for (int i = 0; i < SIZES.length; i++) if (w >= SIZES[i][0] && h >= SIZES[i][1]) best = i;
        return best;
    }

    // =====================================================================

    /** Bir güncellemede gösterilecek her şey. */
    private final class State {
        final boolean has, playing, liked, auto;
        final String title, artist, nextLine, meta, mood, status, remain;
        final int progress;
        final Track pick;
        final List<Track> upcoming = new ArrayList<>();

        State(Engine e) {
            has = e.nowTitle != null;
            playing = e.playing;
            auto = e.isAuto();
            liked = has && e.isNowLiked();
            pick = e.pick;
            title = has ? e.nowTitle : "Bir şarkı aç";
            artist = has ? (e.nowArtist != null ? e.nowArtist : "") : "YouTube Music'te çal, sıradakini Ahenk seçsin";

            if (pick != null) nextLine = "Sıradaki · " + pick.title + " — " + pick.artist;
            else if (!has) nextLine = "Aşağıdan bir ruh hali seç, liste hazırlansın";
            else if (e.computing) nextLine = "Sıradaki seçiliyor…";
            else if (!auto) nextLine = "Otomatik geçiş kapalı";
            else nextLine = "Sıradaki hazırlanıyor…";

            List<String> parts = new ArrayList<>();
            Track sd = e.seed;
            if (sd != null && sd.hasBpm()) parts.add(Math.round(sd.bpm) + " BPM");
            if (sd != null && sd.genre != null && !sd.genre.isEmpty()) parts.add(sd.genre);
            if (sd != null && sd.year > 0) parts.add(String.valueOf(sd.year));
            meta = has ? String.join(" · ", parts) : "";

            Moods.Target t = e.sessionTarget();
            mood = t != null && t.label != null && !t.label.isEmpty() ? t.label.replace("  ·  ", " · ") : "Serbest akış";

            long sleep = e.sleepRemainingMs();
            long sw = e.msToSwitch();
            if (sleep >= 0) status = "😴 " + Math.max(1, (sleep + 59999) / 60000) + " dk";
            else if (!auto) status = "Otomatik kapalı";
            else if (e.computing && pick == null) status = "Seçiliyor…";
            else if (sw >= 0 && has) status = "Geçiş " + clock(sw);
            else status = "";

            long dur = e.nowDurationMs, pos = e.nowPositionMs;
            progress = has && dur > 0 ? (int) Math.max(0, Math.min(1000, pos * 1000 / dur)) : 0;
            remain = has && dur > 0 ? "−" + clock(Math.max(0, dur - pos)) : "";

            // "Sonra": kullanıcının sırası, sonra alternatifler (sıradaki hariç)
            List<Track> src = new ArrayList<>(e.queue);
            src.addAll(e.alternatives);
            for (Track x : src) {
                if (x == null || (pick != null && x.key().equals(pick.key()))) continue;
                boolean dup = false;
                for (Track y : upcoming) if (y.key().equals(x.key())) dup = true;
                if (!dup) upcoming.add(x);
                if (upcoming.size() >= 3) break;
            }
        }

        String sig() {
            StringBuilder b = new StringBuilder();
            b.append(title).append('|').append(artist).append('|').append(playing).append('|').append(liked)
                    .append('|').append(auto).append('|').append(nextLine).append('|').append(meta).append('|').append(mood)
                    .append('|').append(status).append('|').append(pick != null ? pick.reason : "");
            for (Track x : upcoming) b.append('|').append(x.key());
            return b.toString();
        }
    }

    private RemoteViews build(int size, State s) {
        RemoteViews rv = new RemoteViews(app.getPackageName(), LAYOUTS[size]);

        // Kapak renginde ışıma
        if (s.has && coverRound != null) {
            rv.setViewVisibility(R.id.w_glow, View.VISIBLE);
            rv.setInt(R.id.w_glow, "setColorFilter", glowColor);
            rv.setInt(R.id.w_glow, "setImageAlpha", 70);
        } else {
            rv.setViewVisibility(R.id.w_glow, View.GONE);
        }

        if (coverRound != null && s.has) rv.setImageViewBitmap(R.id.w_cover, coverRound);
        else rv.setImageViewResource(R.id.w_cover, R.drawable.logo_mark);
        rv.setTextViewText(R.id.w_title, s.title);
        rv.setTextViewText(R.id.w_next_line, s.nextLine);
        rv.setImageViewResource(R.id.w_play, s.playing ? R.drawable.ic_w_pause : R.drawable.ic_w_play);
        rv.setOnClickPendingIntent(R.id.w_root, openApp());
        rv.setOnClickPendingIntent(R.id.w_play, s.has ? action(ActionReceiver.PLAY_PAUSE) : openYtMusic());
        rv.setOnClickPendingIntent(R.id.w_next, action(ActionReceiver.SKIP));
        rv.setViewVisibility(R.id.w_next, s.pick != null ? View.VISIBLE : View.INVISIBLE);
        if (size == SMALL) return rv;

        rv.setTextViewText(R.id.w_mood, "· " + s.mood);
        rv.setTextViewText(R.id.w_status, s.status);
        rv.setTextViewText(R.id.w_artist, s.artist);
        rv.setTextViewText(R.id.w_eta, s.remain);
        rv.setProgressBar(R.id.w_progress, 1000, s.progress, false);
        rv.setViewVisibility(R.id.w_progress, s.has ? View.VISIBLE : View.INVISIBLE);
        rv.setImageViewResource(R.id.w_like, s.liked ? R.drawable.ic_w_heart_on : R.drawable.ic_w_heart);
        rv.setViewVisibility(R.id.w_like, s.has ? View.VISIBLE : View.GONE);
        rv.setOnClickPendingIntent(R.id.w_like, action(ActionReceiver.LIKE));
        rv.setOnClickPendingIntent(R.id.w_prev, action(ActionReceiver.PREV));
        rv.setOnClickPendingIntent(R.id.w_dislike, action(ActionReceiver.DISLIKE_NOW));
        int ctl = s.has ? View.VISIBLE : View.INVISIBLE;
        rv.setViewVisibility(R.id.w_prev, ctl);
        rv.setViewVisibility(R.id.w_dislike, ctl);
        if (size == MEDIUM) return rv;

        // ---- Büyük: sıradaki kartı + ruh hali düğmeleri ----
        rv.setTextViewText(R.id.w_meta, s.meta);
        rv.setViewVisibility(R.id.w_meta, s.meta.isEmpty() ? View.GONE : View.VISIBLE);
        Track p = s.pick;
        if (p != null) {
            rv.setViewVisibility(R.id.w_next_box, View.VISIBLE);
            rv.setTextViewText(R.id.w_next_title, p.title);
            String why = p.reason != null && !p.reason.isEmpty() ? "  ·  " + p.reason : "";
            rv.setTextViewText(R.id.w_next_artist, p.artist + why);
            if (nextRound != null) rv.setImageViewBitmap(R.id.w_next_cover, nextRound);
            else rv.setImageViewResource(R.id.w_next_cover, R.drawable.logo_mark);
            rv.setTextViewText(R.id.w_next_label, s.status.startsWith("Geçiş") ? "SIRADAKİ · " + s.status.substring(6) : "SIRADAKİ");
            rv.setOnClickPendingIntent(R.id.w_next_skip, action(ActionReceiver.SKIP));
            rv.setOnClickPendingIntent(R.id.w_next_dislike, action(ActionReceiver.DISLIKE_NEXT));
            rv.setOnClickPendingIntent(R.id.w_next_box, openApp());
        } else {
            rv.setViewVisibility(R.id.w_next_box, s.has ? View.VISIBLE : View.GONE);
            rv.setTextViewText(R.id.w_next_label, "SIRADAKİ");
            rv.setTextViewText(R.id.w_next_title, s.nextLine.replace("Sıradaki · ", ""));
            rv.setTextViewText(R.id.w_next_artist, "");
            rv.setImageViewResource(R.id.w_next_cover, R.drawable.logo_mark);
            rv.setViewVisibility(R.id.w_next_skip, View.GONE);
            rv.setViewVisibility(R.id.w_next_dislike, View.GONE);
        }
        rv.setOnClickPendingIntent(R.id.w_chip_sakin, start("sakin"));
        rv.setOnClickPendingIntent(R.id.w_chip_enerjik, start("enerjik"));
        rv.setOnClickPendingIntent(R.id.w_chip_gunun, start("gunun"));
        rv.setOnClickPendingIntent(R.id.w_chip_fav, start("favoriler"));
        if (size == LARGE) return rv;

        // ---- Çok büyük: "Sonra" listesi ----
        int[] rows = {R.id.w_up1, R.id.w_up2, R.id.w_up3};
        rv.setViewVisibility(R.id.w_up_box, s.upcoming.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < rows.length; i++) {
            if (i < s.upcoming.size()) {
                Track x = s.upcoming.get(i);
                rv.setViewVisibility(rows[i], View.VISIBLE);
                rv.setTextViewText(rows[i], (i + 2) + "   " + x.title + "  ·  " + x.artist);
            } else {
                rv.setViewVisibility(rows[i], View.GONE);
            }
        }
        return rv;
    }

    // =====================================================================

    private static String clock(long ms) {
        long sec = (ms + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    /** Ortadan kare kırp, köşeleri yuvarla (widget'larda ImageView kırpılamıyor). */
    static Bitmap rounded(Bitmap src, int size, float radius) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && src.getConfig() == Bitmap.Config.HARDWARE) {
                src = src.copy(Bitmap.Config.ARGB_8888, false);
            }
            int w = src.getWidth(), h = src.getHeight(), s = Math.min(w, h);
            Bitmap sq = Bitmap.createBitmap(src, (w - s) / 2, (h - s) / 2, s, s);
            Bitmap scaled = Bitmap.createScaledBitmap(sq, size, size, true);
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private PendingIntent action(String act) {
        Intent i = new Intent(app, ActionReceiver.class).setAction(act);
        return PendingIntent.getBroadcast(app, act.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent start(String what) {
        Intent i = new Intent(app, ActionReceiver.class).setAction(ActionReceiver.START).putExtra("what", what);
        return PendingIntent.getBroadcast(app, ("start:" + what).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(app, 2, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openYtMusic() {
        Intent i = app.getPackageManager().getLaunchIntentForPackage("com.google.android.apps.youtube.music");
        if (i == null) return openApp();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(app, 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
