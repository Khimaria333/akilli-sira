package com.akillisira.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Instagram hikâyesi boyutunda (1080×1920) haftalık özet görseli üretir ve paylaşır. */
public final class ShareCard {
    private ShareCard() {}

    private static final int W = 1080, H = 1920;

    /** Kartlardaki marka işareti (Ahenk logosu). */
    private static volatile Bitmap logo;

    private static void loadLogo(Activity a) {
        if (logo == null) {
            try {
                logo = android.graphics.BitmapFactory.decodeResource(a.getResources(), R.drawable.logo_mark);
            } catch (Throwable ignored) {}
        }
    }

    /** Logo + marka yazısı; yazının başladığı x'i döndürmez, kendisi çizer. */
    private static void brand(Canvas c, Paint t, String text, float x, float baseline) {
        float tx = x;
        Bitmap l = logo;
        if (l != null) {
            float s = 76;
            c.drawBitmap(l, null, new android.graphics.RectF(x - 6, baseline - 52, x - 6 + s, baseline - 52 + s), null);
            tx = x + s + 6;
        }
        c.drawText(text, tx, baseline, t);
    }

    public static void shareWeek(Activity a, Store store) {
        loadLogo(a);
        new Thread(() -> {
            try {
                Bitmap bmp = render(store);
                Uri uri = save(a.getContentResolver(), bmp);
                a.runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.putExtra(Intent.EXTRA_TEXT, "Bu haftaki müziğim 🎧 #AkıllıSıra");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, "Haftanı paylaş"));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, "Görsel oluşturulamadı: " + e.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // =====================================================================
    // Keşfet listesini paylaş
    // =====================================================================

    public static void shareList(Activity a, String label, List<Track> list) {
        loadLogo(a);
        if (list == null || list.isEmpty()) return;
        new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Listeyi paylaş")
                .setItems(new String[]{"🖼  Görsel olarak (hikâye boyutu)", "🔗  YouTube Music linkleriyle metin"},
                        (d, which) -> {
                            android.widget.Toast.makeText(a, "Hazırlanıyor…", android.widget.Toast.LENGTH_SHORT).show();
                            if (which == 0) shareListImage(a, label, list);
                            else shareListText(a, label, list);
                        })
                .show();
    }

    private static void shareListText(Activity a, String label, List<Track> list) {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder("🎧 " + label + "\n\n");
            int n = 0;
            for (Track t : list) {
                if (n >= 20) break;
                n++;
                sb.append(n).append(". ").append(t.artist).append(" – ").append(t.title).append("\n");
                String id = t.videoId != null ? t.videoId : YtMusic.findVideoId(t.artist, t.title);
                if (id != null) {
                    t.videoId = id;
                    sb.append(YtMusic.watchUrl(id)).append("\n");
                }
            }
            sb.append("\nAhenk ile hazırlandı ✨");
            a.runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, sb.toString());
                a.startActivity(Intent.createChooser(i, "Listeyi paylaş"));
            });
        }).start();
    }

    private static void shareListImage(Activity a, String label, List<Track> list) {
        new Thread(() -> {
            try {
                Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(new LinearGradient(0, 0, 0, H, 0xFF120A22, 0xFF0B0B10, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, p);
                glow(c, p, 900, 200, 600, 0x88FF4D6D);
                glow(c, p, 100, 1500, 650, 0x667B5CFF);
                p.setShader(null);
                float x = 90;
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(0xFFFF4D6D);
                t.setTypeface(Ui.BOLD);
                t.setTextSize(34);
                t.setLetterSpacing(0.25f);
                brand(c, t, "AHENK · LİSTE", x, 170);
                t.setLetterSpacing(0);
                t.setColor(0xFFFFFFFF);
                t.setTypeface(Ui.BLACK);
                t.setTextSize(64);
                c.drawText(fit(label.replace("  ·  ", " · "), t, W - 2 * x), x, 260, t);

                float y = 340;
                float size = 112;
                Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
                title.setColor(0xFFF4F4F8);
                title.setTypeface(Ui.BOLD);
                title.setTextSize(42);
                Paint artist = new Paint(Paint.ANTI_ALIAS_FLAG);
                artist.setColor(0xFFA3A3B5);
                artist.setTypeface(Ui.MEDIUM);
                artist.setTextSize(34);
                Paint cov = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                int n = Math.min(10, list.size());
                for (int i = 0; i < n; i++) {
                    Track tr = list.get(i);
                    RectF r = new RectF(x, y, x + size, y + size);
                    Bitmap cover = ImageLoader.getSync(tr.coverUrl);
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(r, 22, 22, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    if (cover != null) c.drawBitmap(cover, null, r, cov);
                    else {
                        Paint ph = new Paint();
                        ph.setColor(0xFF2A2A3A);
                        c.drawRect(r, ph);
                    }
                    c.restore();
                    float tx = x + size + 36, maxW = W - tx - x;
                    c.drawText(fit(tr.title, title, maxW), tx, y + 50, title);
                    c.drawText(fit(tr.artist, artist, maxW), tx, y + 96, artist);
                    y += size + streak(n);
                }
                Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
                f.setColor(0xFF6E6E82);
                f.setTypeface(Ui.MEDIUM);
                f.setTextSize(32);
                String foot = list.size() > n ? "+" + (list.size() - n) + " şarkı daha · Ahenk ✨" : "Ahenk ile hazırlandı ✨";
                c.drawText(foot, (W - f.measureText(foot)) / 2f, H - 80, f);

                Uri uri = save(a.getContentResolver(), bmp);
                a.runOnUiThread(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, "Listeyi paylaş"));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> android.widget.Toast.makeText(a, "Görsel oluşturulamadı: " + e.getMessage(),
                        android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static float streak(int n) {
        return n >= 10 ? 26 : 40;
    }

    // =====================================================================

    static Bitmap render(Store store) {
        // ---- Veriler ----
        long now = System.currentTimeMillis();
        long weekStart = now - 7L * 24 * 3600 * 1000;
        int songs = 0, bpmN = 0, tr = 0, known = 0;
        long ms = 0;
        double bpmSum = 0;
        Map<String, Integer> artists = new HashMap<>();
        Map<String, Integer> moods = new HashMap<>();
        for (Store.Play p : store.plays()) {
            if (p.ts < weekStart) continue;
            songs++;
            ms += p.durMs;
            if (p.bpm > 1) { bpmSum += p.bpm; bpmN++; }
            if (p.lang == Lang.TR) { tr++; known++; } else if (p.lang == Lang.FOREIGN) known++;
            String ar = Text.mainArtist(p.artist);
            if (!ar.isEmpty()) artists.put(ar, artists.getOrDefault(ar, 0) + 1);
            if (p.mood != null && !p.mood.isEmpty()) moods.put(p.mood, moods.getOrDefault(p.mood, 0) + 1);
        }
        List<Map.Entry<String, Integer>> topArtists = sortDesc(artists);
        List<Map.Entry<String, Integer>> topMoods = sortDesc(moods);

        // ---- Çizim ----
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setShader(new LinearGradient(0, 0, 0, H, 0xFF120A22, 0xFF0B0B10, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        glow(c, p, 900, 260, 620, 0x88FF4D6D);
        glow(c, p, 120, 980, 700, 0x667B5CFF);
        glow(c, p, 950, 1700, 520, 0x55FF4D6D);
        p.setShader(null);

        float x = 96;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFFF4D6D);
        t.setTypeface(Ui.BOLD);
        t.setTextSize(34);
        t.setLetterSpacing(0.25f);
        brand(c, t, "AHENK", x, 190);

        t.setLetterSpacing(0);
        t.setColor(0xFFFFFFFF);
        t.setTypeface(Ui.BLACK);
        t.setTextSize(150);
        c.drawText("Haftam", x, 350, t);

        SimpleDateFormat df = new SimpleDateFormat("d MMMM", Locale.forLanguageTag("tr"));
        t.setTypeface(Ui.MEDIUM);
        t.setTextSize(40);
        t.setColor(0xFFA3A3B5);
        c.drawText(df.format(new Date(weekStart)) + " – " + df.format(new Date(now)), x, 420, t);

        // Büyük süre
        long min = ms / 60000;
        String big = min >= 60 ? (min / 60) + " sa " + (min % 60) + " dk" : min + " dk";
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setTypeface(Ui.BLACK);
        g.setTextSize(170);
        g.setShader(new LinearGradient(x, 0, x + 820, 0, 0xFFFF4D6D, 0xFF7B5CFF, Shader.TileMode.CLAMP));
        c.drawText(fit(big, g, W - 2 * x), x, 650, g);
        t.setTextSize(46);
        t.setColor(0xFFF4F4F8);
        t.setTypeface(Ui.MEDIUM);
        c.drawText("müzik dinledim  ·  " + songs + " şarkı", x, 730, t);

        // En sevdiğim sanatçı
        float y = 880;
        label(c, "EN ÇOK DİNLEDİĞİM", x, y);
        t.setTypeface(Ui.BLACK);
        t.setTextSize(92);
        t.setColor(0xFFFFFFFF);
        String top = topArtists.isEmpty() ? "—" : topArtists.get(0).getKey();
        c.drawText(fit(top, t, W - 2 * x), x, y + 110, t);

        // İlk 3 sanatçı
        y += 200;
        t.setTextSize(44);
        for (int i = 1; i < topArtists.size() && i < 4; i++) {
            Map.Entry<String, Integer> en = topArtists.get(i);
            t.setTypeface(Ui.BOLD);
            t.setColor(0xFFFF4D6D);
            c.drawText((i + 1) + ".", x, y, t);
            t.setColor(0xFFF4F4F8);
            t.setTypeface(Ui.MEDIUM);
            c.drawText(fit(en.getKey(), t, W - 2 * x - 260), x + 70, y, t);
            t.setColor(0xFFA3A3B5);
            String cnt = en.getValue() + " kez";
            c.drawText(cnt, W - x - t.measureText(cnt), y, t);
            y += 72;
        }

        // Kutucuklar
        y = Math.max(y + 40, 1400);
        float bw = (W - 2 * x - 40) / 2f;
        tile(c, x, y, bw, "ORT. TEMPO", bpmN > 0 ? Math.round(bpmSum / bpmN) + " BPM" : "—");
        tile(c, x + bw + 40, y, bw, "TÜRKÇE", known > 0 ? "%" + Math.round(100.0 * tr / known) : "—");
        y += 230;
        String mood = topMoods.isEmpty() ? "Keşfet'te ruh halini seç" : topMoods.get(0).getKey();
        tile(c, x, y, W - 2 * x, "EN ÇOK RUH HALİM", mood);

        // Alt yazı
        t.setTypeface(Ui.MEDIUM);
        t.setTextSize(34);
        t.setColor(0xFF6E6E82);
        String foot = "Ahenk ile seçildi ✨";
        c.drawText(foot, (W - t.measureText(foot)) / 2f, H - 90, t);
        return bmp;
    }

    private static void glow(Canvas c, Paint p, float cx, float cy, float r, int color) {
        p.setShader(new RadialGradient(cx, cy, r, color, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    private static void label(Canvas c, String s, float x, float y) {
        Paint l = new Paint(Paint.ANTI_ALIAS_FLAG);
        l.setColor(0xFF8A8AA0);
        l.setTypeface(Ui.BOLD);
        l.setTextSize(30);
        l.setLetterSpacing(0.18f);
        c.drawText(s, x, y, l);
    }

    private static void tile(Canvas c, float x, float y, float w, String label, String value) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0x1FFFFFFF);
        c.drawRoundRect(new RectF(x, y, x + w, y + 190), 44, 44, bg);
        label(c, label, x + 44, y + 70);
        Paint v = new Paint(Paint.ANTI_ALIAS_FLAG);
        v.setColor(0xFFFFFFFF);
        v.setTypeface(Ui.BLACK);
        v.setTextSize(62);
        c.drawText(fit(value, v, w - 88), x + 44, y + 150, v);
    }

    /** Sığmazsa sonunu "…" ile kısalt. */
    private static String fit(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        String t = s;
        while (t.length() > 1 && p.measureText(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static List<Map.Entry<String, Integer>> sortDesc(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l;
    }

    private static Uri save(ContentResolver r, Bitmap bmp) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "akilli_sira_haftam_" + System.currentTimeMillis() + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ahenk");
        Uri uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("Galeriye kaydedilemedi");
        try (OutputStream out = r.openOutputStream(uri)) {
            if (out == null) throw new Exception("Dosya açılamadı");
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return uri;
    }
}
