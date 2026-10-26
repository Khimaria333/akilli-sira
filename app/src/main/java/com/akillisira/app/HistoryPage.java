package com.akillisira.app;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dinleme geçmişi, istatistikler ve beğenilmeyenler. */
public class HistoryPage extends Page {

    private LinearLayout content;
    private int renderedVersion = -1;
    private boolean showAllDisliked, showAllLiked;

    public HistoryPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        body.addView(Ui.text(a, "Geçmiş", 28, Ui.TEXT, Ui.BLACK));
        TextView sub = Ui.text(a, "Ne dinledin, nasıl hissettin", 14, Ui.TEXT2, Ui.REGULAR);
        sub.setPadding(0, Ui.dp(a, 2), 0, 0);
        body.addView(sub);
        content = Ui.vbox(a);
        body.addView(content, full());
    }

    @Override
    public void onShow() {
        renderedVersion = -1;
        render();
    }

    @Override
    public void render() {
        int v = e.store.version() * 31 + e.profile().hashCode();
        if (v == renderedVersion) return;
        renderedVersion = v;
        content.removeAllViews();

        List<Store.Play> plays = e.store.plays();
        long now = System.currentTimeMillis();
        long todayStart = startOfDay(now);
        long weekStart = now - 7L * 24 * 3600 * 1000;

        int today = 0, week = 0, bpmN = 0, tr = 0, known = 0;
        long weekMs = 0;
        double bpmSum = 0;
        Map<String, Integer> artists = new HashMap<>();
        Map<String, Integer> moods = new HashMap<>();
        for (Store.Play p : plays) {
            if (p.ts >= todayStart) today++;
            if (p.ts < weekStart) continue;
            week++;
            weekMs += p.durMs;
            if (p.bpm > 1) { bpmSum += p.bpm; bpmN++; }
            if (p.lang == Lang.TR) { tr++; known++; } else if (p.lang == Lang.FOREIGN) known++;
            String a0 = Text.mainArtist(p.artist);
            if (!a0.isEmpty()) artists.put(a0, artists.getOrDefault(a0, 0) + 1);
            if (p.mood != null && !p.mood.isEmpty()) moods.put(p.mood, moods.getOrDefault(p.mood, 0) + 1);
        }

        // ---- İstatistik kutucukları ----
        content.addView(Ui.section(a, "Son 7 gün"));
        LinearLayout r1 = Ui.hbox(a);
        r1.addView(stat("BUGÜN", today + " şarkı"), weightL());
        r1.addView(stat("BU HAFTA", formatDuration(weekMs)), weightR());
        content.addView(r1, full());
        LinearLayout r2 = Ui.hbox(a);
        r2.addView(stat("ORT. TEMPO", bpmN > 0 ? Math.round(bpmSum / bpmN) + " BPM" : "—"), weightL());
        r2.addView(stat("TÜRKÇE ORANI", known > 0 ? "%" + Math.round(100.0 * tr / known) : "—"), weightR());
        content.addView(r2, Ui.margins(full(), a, 0, 10, 0, 0));

        if (week > 0) {
            TextView share = Ui.primaryButton(a, "📤  Haftalık özetimi paylaş");
            share.setOnClickListener(x -> {
                a.toast("Görsel hazırlanıyor…");
                ShareCard.shareWeek(a, e.store);
            });
            content.addView(share, Ui.margins(full(), a, 0, 14, 0, 0));
        }

        if (plays.isEmpty()) {
            LinearLayout c = Ui.card(a);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = Ui.text(a, "Henüz geçmiş yok", 16, Ui.TEXT, Ui.BOLD);
            t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
            c.addView(t);
            TextView d = Ui.text(a, "Dinlediğin şarkılar burada görünecek.", 13.5f, Ui.TEXT2, Ui.REGULAR);
            d.setPadding(0, 0, 0, Ui.dp(a, 8));
            c.addView(d);
            content.addView(c, Ui.margins(full(), a, 0, 20, 0, 0));
        }

        // ---- En çok dinlenen sanatçılar ----
        if (!artists.isEmpty()) {
            content.addView(Ui.section(a, "En çok dinlediğin sanatçılar"));
            LinearLayout c = Ui.card(a);
            List<Map.Entry<String, Integer>> top = sortDesc(artists);
            int max = top.get(0).getValue();
            for (int i = 0; i < top.size() && i < 5; i++) {
                Map.Entry<String, Integer> en = top.get(i);
                c.addView(barRow((i + 1) + ".  " + en.getKey(), en.getValue() + " kez", en.getValue() / (float) max),
                        Ui.margins(full(), a, 0, i == 0 ? 0 : 12, 0, 0));
            }
            content.addView(c, full());
        }

        // ---- Ruh hali geçmişi ----
        if (!moods.isEmpty()) {
            content.addView(Ui.section(a, "Ruh hallerin"));
            LinearLayout c = Ui.card(a);
            List<Map.Entry<String, Integer>> top = sortDesc(moods);
            int max = top.get(0).getValue();
            for (int i = 0; i < top.size() && i < 4; i++) {
                Map.Entry<String, Integer> en = top.get(i);
                c.addView(barRow(en.getKey(), en.getValue() + " şarkı", en.getValue() / (float) max),
                        Ui.margins(full(), a, 0, i == 0 ? 0 : 12, 0, 0));
            }
            content.addView(c, full());
        }

        // ---- Tarz profili ----
        Map<String, Integer> prof = e.profile();
        if (!prof.isEmpty()) {
            content.addView(Ui.section(a, "Tarz profilin"));
            LinearLayout c = Ui.card(a);
            TextView info = Ui.text(a, "❤️ ile beğendiğin şarkılardan çıkarıldı; bütün öneriler buna doğru hafifçe kayar.",
                    12.5f, Ui.TEXT2, Ui.REGULAR);
            info.setLineSpacing(0, 1.15f);
            info.setPadding(0, 0, 0, Ui.dp(a, 10));
            c.addView(info);
            int i = 0;
            for (Map.Entry<String, Integer> en : prof.entrySet()) {
                if (i >= 6) break;
                c.addView(barRow(en.getKey(), "%" + en.getValue(), en.getValue() / 100f),
                        Ui.margins(full(), a, 0, i == 0 ? 0 : 12, 0, 0));
                i++;
            }
            TextView go = Ui.secondaryButton(a, "🧭  Tarzıma göre liste hazırla");
            go.setOnClickListener(x -> {
                e.discoverProfile();
                a.showTab(MainActivity.TAB_DISCOVER);
            });
            c.addView(go, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 14, 0, 0));
            content.addView(c, full());
        }

        // ---- Beğenilenler ----
        List<Store.Liked> liked = e.store.liked();
        if (!liked.isEmpty()) {
            content.addView(Ui.section(a, "Beğendiklerin · " + liked.size()));
            LinearLayout c = Ui.card(a);
            TextView info = Ui.text(a, "Bunlar ve bu sanatçılar daha sık önerilir. Keşfet'teki "
                    + "\"Favorilerimden karışık\" ile hepsini bir arada dinleyebilirsin.", 12.5f, Ui.TEXT2, Ui.REGULAR);
            info.setLineSpacing(0, 1.15f);
            info.setPadding(0, 0, 0, Ui.dp(a, 8));
            c.addView(info);
            int limit = showAllLiked ? liked.size() : Math.min(5, liked.size());
            for (int i = 0; i < limit; i++) {
                Store.Liked l = liked.get(i);
                LinearLayout row = Ui.hbox(a);
                row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
                ImageView cv = Ui.cover(a, 10);
                row.addView(cv, Ui.margins(Ui.lp(Ui.dp(a, 40), Ui.dp(a, 40)), a, 0, 0, 12, 0));
                ImageLoader.load(l.cover, cv);
                LinearLayout t = Ui.vbox(a);
                t.addView(Ui.oneLine(Ui.text(a, l.title, 14.5f, Ui.TEXT, Ui.BOLD)));
                t.addView(Ui.oneLine(Ui.text(a, l.artist, 12.5f, Ui.TEXT2, Ui.REGULAR)));
                row.addView(t, Ui.lpw(1));
                TextView rm = Ui.text(a, "❤️", 16, Ui.TEXT, Ui.REGULAR);
                rm.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6));
                rm.setOnClickListener(x -> {
                    e.unlike(l.key);
                    a.toast("Beğendiklerinden çıkarıldı: " + l.title);
                });
                row.addView(rm);
                c.addView(row, full());
            }
            if (liked.size() > 5) {
                TextView more = Ui.text(a, showAllLiked ? "Daha az göster" : "Tümünü göster (" + liked.size() + ")",
                        13, Ui.TEXT2, Ui.BOLD);
                more.setPadding(0, Ui.dp(a, 8), 0, 0);
                more.setOnClickListener(x -> {
                    showAllLiked = !showAllLiked;
                    renderedVersion = -1;
                    render();
                });
                c.addView(more);
            }
            content.addView(c, full());
        }

        // ---- Beğenilmeyenler ----
        List<Store.Disliked> dis = e.store.disliked();
        if (!dis.isEmpty()) {
            content.addView(Ui.section(a, "Beğenmediklerin · " + dis.size()));
            LinearLayout c = Ui.card(a);
            TextView info = Ui.text(a, "Bunlar bir daha önerilmez. Aynı sanatçıdan birden fazla şarkı "
                    + "beğenmezsen o sanatçı da daha az önerilir.", 12.5f, Ui.TEXT2, Ui.REGULAR);
            info.setLineSpacing(0, 1.15f);
            info.setPadding(0, 0, 0, Ui.dp(a, 8));
            c.addView(info);
            int limit = showAllDisliked ? dis.size() : Math.min(5, dis.size());
            for (int i = 0; i < limit; i++) {
                Store.Disliked d = dis.get(i);
                LinearLayout row = Ui.hbox(a);
                row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
                LinearLayout t = Ui.vbox(a);
                t.addView(Ui.oneLine(Ui.text(a, "👎  " + d.title, 14.5f, Ui.TEXT, Ui.BOLD)));
                t.addView(Ui.oneLine(Ui.text(a, d.artist, 12.5f, Ui.TEXT2, Ui.REGULAR)));
                row.addView(t, Ui.lpw(1));
                TextView undo = Ui.text(a, "Geri al", 13, Ui.ACCENT, Ui.BOLD);
                undo.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6));
                undo.setOnClickListener(x -> {
                    e.undislike(d.key);
                    a.toast("Tekrar önerilebilir: " + d.title);
                });
                row.addView(undo);
                c.addView(row, full());
            }
            if (dis.size() > 5) {
                TextView more = Ui.text(a, showAllDisliked ? "Daha az göster" : "Tümünü göster (" + dis.size() + ")",
                        13, Ui.TEXT2, Ui.BOLD);
                more.setPadding(0, Ui.dp(a, 8), 0, 0);
                more.setOnClickListener(x -> {
                    showAllDisliked = !showAllDisliked;
                    renderedVersion = -1;
                    render();
                });
                c.addView(more);
            }
            content.addView(c, full());
        }

        // ---- Son dinlenenler (güne göre) ----
        if (!plays.isEmpty()) {
            String lastDay = null;
            int shown = 0;
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ROOT);
            for (Store.Play p : plays) {
                String day = dayLabel(p.ts, todayStart);
                if (!day.equals(lastDay)) {
                    content.addView(Ui.section(a, day));
                    lastDay = day;
                }
                content.addView(playRow(p, hm.format(new Date(p.ts))), Ui.margins(full(), a, 0, 0, 0, 8));
                if (++shown >= 60) break;
            }

            TextView clear = Ui.secondaryButton(a, "Geçmişi temizle");
            clear.setOnClickListener(x -> new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Geçmiş silinsin mi?")
                    .setMessage("Dinleme geçmişi ve istatistikler silinir. Beğenmediklerin korunur.")
                    .setPositiveButton("Sil", (dd, w) -> {
                        e.store.clearHistory();
                        render();
                    })
                    .setNegativeButton("Vazgeç", null)
                    .show());
            content.addView(clear, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 20, 0, 0));
        }
    }

    // ---------------------------------------------------------------------

    private LinearLayout stat(String label, String value) {
        LinearLayout t = Ui.vbox(a);
        t.setBackground(Ui.stroke(a, Ui.SURFACE, Ui.STROKE, 18, 1));
        t.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        TextView l = Ui.text(a, label, 10.5f, Ui.TEXT3, Ui.BOLD);
        l.setLetterSpacing(0.12f);
        t.addView(l);
        TextView v = Ui.text(a, value, 21, Ui.TEXT, Ui.BOLD);
        v.setPadding(0, Ui.dp(a, 4), 0, 0);
        t.addView(v);
        return t;
    }

    private LinearLayout.LayoutParams weightL() {
        LinearLayout.LayoutParams p = Ui.lpw(1);
        p.setMargins(0, 0, Ui.dp(a, 5), 0);
        return p;
    }

    private LinearLayout.LayoutParams weightR() {
        LinearLayout.LayoutParams p = Ui.lpw(1);
        p.setMargins(Ui.dp(a, 5), 0, 0, 0);
        return p;
    }

    private View barRow(String label, String value, float frac) {
        LinearLayout box = Ui.vbox(a);
        LinearLayout top = Ui.hbox(a);
        top.addView(Ui.oneLine(Ui.text(a, label, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lpw(1));
        top.addView(Ui.text(a, value, 12.5f, Ui.TEXT2, Ui.MEDIUM));
        box.addView(top, full());
        LinearLayout bar = Ui.hbox(a);
        View fill = new View(a);
        fill.setBackground(Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 4));
        View rest = new View(a);
        bar.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(a, 6), Math.max(0.04f, frac)));
        bar.addView(rest, new LinearLayout.LayoutParams(0, Ui.dp(a, 6), Math.max(0.0001f, 1 - frac)));
        bar.setBackground(Ui.round(a, 0x1AFFFFFF, 4));
        box.addView(bar, Ui.margins(full(), a, 0, 6, 0, 0));
        return box;
    }

    private View playRow(Store.Play p, String time) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        row.setBackground(Ui.round(a, Ui.SURFACE, 18));
        ImageView cv = Ui.cover(a, 12);
        row.addView(cv, Ui.margins(Ui.lp(Ui.dp(a, 48), Ui.dp(a, 48)), a, 0, 0, 12, 0));
        ImageLoader.load(p.cover, cv);
        LinearLayout t = Ui.vbox(a);
        t.addView(Ui.oneLine(Ui.text(a, p.title, 14.5f, Ui.TEXT, Ui.BOLD)));
        String sub = p.artist + (p.bpm > 1 ? "  ·  " + Math.round(p.bpm) + " BPM" : "");
        t.addView(Ui.oneLine(Ui.text(a, sub, 12.5f, Ui.TEXT2, Ui.REGULAR)));
        if (p.mood != null) {
            TextView m = Ui.oneLine(Ui.text(a, p.mood, 11.5f, Ui.ACCENT, Ui.MEDIUM));
            m.setPadding(0, Ui.dp(a, 2), 0, 0);
            t.addView(m);
        }
        row.addView(t, Ui.lpw(1));
        row.addView(Ui.text(a, time, 12, Ui.TEXT3, Ui.MEDIUM));
        return row;
    }

    private static List<Map.Entry<String, Integer>> sortDesc(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        return l;
    }

    private static long startOfDay(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String dayLabel(long ts, long todayStart) {
        if (ts >= todayStart) return "Bugün";
        if (ts >= todayStart - 24L * 3600 * 1000) return "Dün";
        return new SimpleDateFormat("d MMMM EEEE", Locale.forLanguageTag("tr")).format(new Date(ts));
    }

    private static String formatDuration(long ms) {
        long min = ms / 60000;
        if (min < 60) return min + " dk";
        return (min / 60) + " sa " + (min % 60) + " dk";
    }
}
