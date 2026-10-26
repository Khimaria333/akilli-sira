package com.akillisira.app;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Şu an çalan şarkı, sıradaki ve sonrası. */
public class NowPage extends Page {

    private TextView statusPill;
    private LinearLayout empty, nowCard, sessionCard, nextSection;
    private ImageView heroCover;
    private TextView heroTitle, heroArtist, heroBpm, heroLang, timeLeft, timeRight;
    private TextView tempoValue, energyValue;
    private Views.ProgressLine progress;
    private Views.Meter meter;
    private TextView sessionLabel;

    private LinearLayout nextBody;
    private ImageView nextCover;
    private TextView nextTitle, nextArtist, nextReason, nextCountdown, nextStatus;
    private LinearLayout nextContent, nextLoading;
    private ProgressBar nextSpinner;
    private TextView skipBtn;

    private Ui.Segmented modeSeg, langSeg;
    private LinearLayout altList;
    private TextView altHeader;

    private Track renderedSeed, renderedPick;
    private List<Track> renderedAlts;

    public NowPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        // ---- Başlık ----
        LinearLayout top = Ui.hbox(a);
        top.addView(Ui.text(a, "Şimdi Çalıyor", 28, Ui.TEXT, Ui.BLACK), Ui.lpw(1));
        statusPill = Ui.pill(a, "", Ui.GOOD);
        top.addView(statusPill);
        body.addView(top, Ui.margins(full(), a, 0, 0, 0, 16));

        // ---- Boş durum ----
        empty = Ui.card(a);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(Ui.dp(a, 20), Ui.dp(a, 32), Ui.dp(a, 20), Ui.dp(a, 24));
        LinearLayout ic = Ui.vbox(a);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 24));
        ic.addView(new Views.Icon(a, Views.Icon.MUSIC, 0xFFFFFFFF), Ui.lp(Ui.dp(a, 34), Ui.dp(a, 34)));
        empty.addView(ic, Ui.lp(Ui.dp(a, 72), Ui.dp(a, 72)));
        TextView et = Ui.text(a, "Henüz bir şarkı çalmıyor", 18, Ui.TEXT, Ui.BOLD);
        et.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 6));
        empty.addView(et);
        TextView ed = Ui.text(a, "YouTube Music'te bir şarkı aç ya da ruh haline göre bir liste başlat. "
                + "Sıradakini ben seçeceğim.", 14, Ui.TEXT2, Ui.REGULAR);
        ed.setGravity(Gravity.CENTER);
        ed.setLineSpacing(0, 1.2f);
        empty.addView(ed);
        TextView goDiscover = Ui.primaryButton(a, "✨  Ruh halime göre başlat");
        goDiscover.setOnClickListener(v -> a.showTab(MainActivity.TAB_DISCOVER));
        empty.addView(goDiscover, Ui.margins(full(), a, 0, 22, 0, 10));
        TextView openYtm = Ui.secondaryButton(a, "YouTube Music'i aç");
        openYtm.setOnClickListener(v -> a.openYtMusic());
        empty.addView(openYtm, full());
        body.addView(empty, full());

        // ---- Çalan şarkı kartı ----
        nowCard = Ui.card(a);
        LinearLayout hero = Ui.hbox(a);
        hero.setGravity(Gravity.TOP);
        heroCover = Ui.cover(a, 18);
        hero.addView(heroCover, Ui.margins(Ui.lp(Ui.dp(a, 112), Ui.dp(a, 112)), a, 0, 0, 14, 0));
        LinearLayout ht = Ui.vbox(a);
        TextView now = Ui.text(a, "ŞU AN", 11, Ui.ACCENT, Ui.BOLD);
        now.setLetterSpacing(0.12f);
        ht.addView(now);
        heroTitle = Ui.text(a, "", 19, Ui.TEXT, Ui.BOLD);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroTitle.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 2));
        ht.addView(heroTitle);
        heroArtist = Ui.oneLine(Ui.text(a, "", 14, Ui.TEXT2, Ui.REGULAR));
        ht.addView(heroArtist);
        LinearLayout pills = Ui.hbox(a);
        heroBpm = Ui.pill(a, "", Ui.TEXT);
        heroLang = Ui.pill(a, "", Ui.TEXT);
        pills.addView(heroBpm, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 6, 0));
        pills.addView(heroLang);
        ht.addView(pills, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        hero.addView(ht, Ui.lpw(1));
        nowCard.addView(hero, full());

        progress = new Views.ProgressLine(a);
        nowCard.addView(progress, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(a, 6)), a, 0, 18, 0, 6));
        LinearLayout times = Ui.hbox(a);
        timeLeft = Ui.text(a, "0:00", 12, Ui.TEXT3, Ui.MEDIUM);
        timeRight = Ui.text(a, "0:00", 12, Ui.TEXT3, Ui.MEDIUM);
        times.addView(timeLeft, Ui.lpw(1));
        times.addView(timeRight);
        nowCard.addView(times, full());

        LinearLayout stats = Ui.hbox(a);
        LinearLayout tempo = statTile("TEMPO");
        tempoValue = Ui.text(a, "—", 18, Ui.TEXT, Ui.BOLD);
        tempo.addView(tempoValue);
        LinearLayout energy = statTile("ENERJİ");
        LinearLayout er = Ui.hbox(a);
        meter = new Views.Meter(a);
        er.addView(meter, Ui.margins(Ui.lp(Ui.dp(a, 64), Ui.dp(a, 20)), a, 0, 0, 10, 0));
        energyValue = Ui.text(a, "—", 14, Ui.TEXT, Ui.BOLD);
        er.addView(energyValue);
        energy.addView(er);
        LinearLayout.LayoutParams l1 = Ui.lpw(1);
        l1.setMargins(0, 0, Ui.dp(a, 5), 0);
        LinearLayout.LayoutParams l2 = Ui.lpw(1);
        l2.setMargins(Ui.dp(a, 5), 0, 0, 0);
        stats.addView(tempo, l1);
        stats.addView(energy, l2);
        nowCard.addView(stats, Ui.margins(full(), a, 0, 14, 0, 0));
        body.addView(nowCard, full());

        // ---- Ruh hali oturumu ----
        sessionCard = Ui.hbox(a);
        sessionCard.setBackground(Ui.gradient(a, 0x33FF4D6D, 0x337B5CFF, 18));
        sessionCard.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10));
        sessionLabel = Ui.oneLine(Ui.text(a, "", 14, Ui.TEXT, Ui.MEDIUM));
        sessionCard.addView(sessionLabel, Ui.lpw(1));
        TextView close = Ui.text(a, "Kapat", 13, Ui.TEXT2, Ui.BOLD);
        close.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        close.setBackground(Ui.ripple(a, Ui.round(a, 0x22FFFFFF, 14), 14));
        close.setOnClickListener(v -> e.clearSession());
        sessionCard.addView(close);
        body.addView(sessionCard, Ui.margins(full(), a, 0, 12, 0, 0));

        // ---- Sıradaki ----
        nextSection = Ui.vbox(a);
        nextSection.addView(Ui.section(a, "Sıradaki"));
        LinearLayout border = Ui.vbox(a);
        border.setBackground(Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 24));
        int bw = Ui.dp(a, 1.5f);
        border.setPadding(bw, bw, bw, bw);
        nextBody = Ui.vbox(a);
        nextBody.setBackground(Ui.round(a, Ui.SURFACE, 23));
        nextBody.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        border.addView(nextBody, full());

        nextLoading = Ui.hbox(a);
        nextSpinner = new ProgressBar(a);
        nextSpinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        nextLoading.addView(nextSpinner, Ui.margins(Ui.lp(Ui.dp(a, 26), Ui.dp(a, 26)), a, 0, 0, 12, 0));
        nextStatus = Ui.text(a, "", 14, Ui.TEXT2, Ui.MEDIUM);
        nextLoading.addView(nextStatus, Ui.lpw(1));
        nextBody.addView(nextLoading, full());

        nextContent = Ui.vbox(a);
        LinearLayout nr = Ui.hbox(a);
        nextCover = Ui.cover(a, 14);
        nr.addView(nextCover, Ui.margins(Ui.lp(Ui.dp(a, 66), Ui.dp(a, 66)), a, 0, 0, 12, 0));
        LinearLayout nt = Ui.vbox(a);
        nextTitle = Ui.oneLine(Ui.text(a, "", 17, Ui.TEXT, Ui.BOLD));
        nt.addView(nextTitle);
        nextArtist = Ui.oneLine(Ui.text(a, "", 13.5f, Ui.TEXT2, Ui.REGULAR));
        nt.addView(nextArtist);
        nextReason = Ui.text(a, "", 12.5f, Ui.ACCENT, Ui.MEDIUM);
        nextReason.setPadding(0, Ui.dp(a, 4), 0, 0);
        nt.addView(nextReason);
        nr.addView(nt, Ui.lpw(1));
        nextContent.addView(nr, full());
        nextCountdown = Ui.text(a, "", 13, Ui.TEXT2, Ui.MEDIUM);
        nextCountdown.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        nextContent.addView(nextCountdown);
        LinearLayout btns = Ui.hbox(a);
        skipBtn = Ui.primaryButton(a, "Şimdi geç");
        skipBtn.setOnClickListener(v -> {
            e.skipNow();
            a.toast("Geçiliyor…");
        });
        btns.addView(skipBtn, Ui.lpw(1));
        LinearLayout refresh = Ui.vbox(a);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        refresh.setClickable(true);
        refresh.addView(new Views.Icon(a, Views.Icon.REFRESH, Ui.TEXT), Ui.lp(Ui.dp(a, 22), Ui.dp(a, 22)));
        refresh.setOnClickListener(v -> e.refresh());
        btns.addView(refresh, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 10, 0, 0, 0));
        nextContent.addView(btns, full());
        nextBody.addView(nextContent, full());
        nextSection.addView(border, full());

        // ---- Hava ve dil ----
        nextSection.addView(Ui.section(a, "Hava"));
        modeSeg = Ui.segmented(a, new String[]{"🌙 Sakinleştir", "〰 Aynı", "⚡ Hareketlendir"},
                e.getMode() + 1, i -> e.setMode(i - 1));
        nextSection.addView(modeSeg.view, full());
        nextSection.addView(Ui.section(a, "Dil"));
        langSeg = Ui.segmented(a, new String[]{"🇹🇷  Türkçe", "🌍  Global"}, e.isTurkish() ? 0 : 1,
                i -> e.setTurkish(i == 0));
        nextSection.addView(langSeg.view, full());

        // ---- Alternatifler ----
        altHeader = Ui.section(a, "Sonra çalabilecekler");
        nextSection.addView(altHeader);
        altList = Ui.vbox(a);
        nextSection.addView(altList, full());
        body.addView(nextSection, full());
    }

    private LinearLayout statTile(String label) {
        LinearLayout t = Ui.vbox(a);
        t.setBackground(Ui.round(a, Ui.SURFACE2, 16));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        TextView l = Ui.text(a, label, 10.5f, Ui.TEXT3, Ui.BOLD);
        l.setLetterSpacing(0.12f);
        l.setPadding(0, 0, 0, Ui.dp(a, 6));
        t.addView(l);
        return t;
    }

    @Override
    public void onShow() {
        modeSeg.select(e.getMode() + 1);
        langSeg.select(e.isTurkish() ? 0 : 1);
    }

    @Override
    public void render() {
        boolean has = e.nowTitle != null;
        empty.setVisibility(has ? View.GONE : View.VISIBLE);
        nowCard.setVisibility(has ? View.VISIBLE : View.GONE);
        nextSection.setVisibility(has ? View.VISIBLE : View.GONE);

        // durum etiketi
        if (e.controller == null) {
            setPill(statusPill, "●  Bağlı değil", Ui.TEXT3);
        } else if (!e.isAuto()) {
            setPill(statusPill, "●  Manuel", Ui.WARN);
        } else {
            setPill(statusPill, "●  Otomatik", Ui.GOOD);
        }

        Moods.Target tg = e.sessionTarget();
        sessionCard.setVisibility(has && tg != null ? View.VISIBLE : View.GONE);
        if (tg != null) sessionLabel.setText("🎯  " + tg.label);

        if (!has) return;

        // ---- Çalan ----
        heroTitle.setText(e.nowTitle);
        heroArtist.setText(e.nowArtist);
        Track s = e.seed;
        if (s != renderedSeed) {
            renderedSeed = s;
            ImageLoader.load(s != null ? s.coverUrl : null, heroCover);
        }
        if (s != null && s.hasBpm()) {
            heroBpm.setText(Math.round(s.bpm) + " BPM");
            tempoValue.setText(Math.round(s.bpm) + " BPM");
        } else {
            heroBpm.setText(e.computing ? "Analiz…" : "BPM yok");
            tempoValue.setText("—");
        }
        heroLang.setText(s != null ? Lang.label(s.lang) : "—");
        heroLang.setVisibility(s != null && s.lang != Lang.UNKNOWN ? View.VISIBLE : View.GONE);
        double en = s != null ? s.energy() : -1;
        meter.setValue(en);
        energyValue.setText(en < 0 ? "—" : en < 0.35 ? "Sakin" : en < 0.65 ? "Orta" : "Yüksek");

        progress.setValue(e.nowDurationMs > 0 ? (float) e.nowPositionMs / e.nowDurationMs : 0);
        timeLeft.setText(time(e.nowPositionMs) + (e.playing ? "" : "  ·  duraklatıldı"));
        timeRight.setText(time(e.nowDurationMs));

        // ---- Sıradaki ----
        Track p = e.pick;
        if (e.computing || p == null) {
            nextLoading.setVisibility(View.VISIBLE);
            nextContent.setVisibility(View.GONE);
            nextSpinner.setVisibility(e.computing ? View.VISIBLE : View.GONE);
            nextStatus.setText(e.computing ? "Senin için en uygun şarkı seçiliyor…"
                    : e.problem != null ? e.problem : "Sıradaki henüz belli değil");
        } else {
            nextLoading.setVisibility(View.GONE);
            nextContent.setVisibility(View.VISIBLE);
            if (p != renderedPick) {
                renderedPick = p;
                ImageLoader.load(p.coverUrl, nextCover);
                nextContent.setAlpha(0f);
                nextContent.animate().alpha(1f).setDuration(250).start();
            }
            nextTitle.setText(p.title);
            nextArtist.setText(p.artist + (p.lang != Lang.UNKNOWN ? "  ·  " + Lang.label(p.lang) : ""));
            nextReason.setText(p.reason);
            long ms = e.msToSwitch();
            String cd;
            if (!e.isAuto()) cd = "Otomatik geçiş kapalı · \"Şimdi geç\" ile elle geçebilirsin";
            else if (ms < 0) cd = "Şarkı bitince geçecek";
            else if (ms <= 60000) cd = "⏱  " + Math.max(1, (ms + 999) / 1000) + " sn sonra geçiş"
                    + (e.isFade() ? " · ses yumuşakça kısılacak" : "");
            else cd = "Şarkı bitince geçecek · " + time(ms) + " kaldı";
            if (p.videoId == null) cd += "\nYouTube Music'te aranıyor…";
            nextCountdown.setText(cd);
            Ui.setEnabledLook(skipBtn, true);
        }

        // ---- Alternatifler ----
        if (renderedAlts != e.alternatives) {
            renderedAlts = e.alternatives;
            altList.removeAllViews();
            altHeader.setVisibility(renderedAlts.isEmpty() ? View.GONE : View.VISIBLE);
            for (Track t : renderedAlts) {
                LinearLayout r = Ui.songRow(a, t, t.reason, 0);
                r.setOnClickListener(v -> {
                    e.choose(t);
                    a.toast("Sıradaki: " + t.title);
                    scroll.smoothScrollTo(0, nextSection.getTop());
                });
                altList.addView(r, Ui.margins(full(), a, 0, 0, 0, 8));
            }
        }
    }

    private void setPill(TextView t, String label, int color) {
        if (label.contentEquals(t.getText())) return;
        t.setText(label);
        t.setTextColor(color);
        t.setBackground(Ui.round(a, (color & 0x00FFFFFF) | 0x22000000, 12));
    }

    static String time(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
