package com.akillisira.app;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Şu an çalan şarkı, sıradaki, kuyruk ve öneriler. */
public class NowPage extends Page {

    private TextView statusPill;
    private LinearLayout empty, nowCard, sessionCard, contextRow, nextSection;
    private ImageView heroCover;
    private TextView heroTitle, heroArtist, heroBpm, heroLang, timeLeft, timeRight;
    private TextView tempoValue, energyValue;
    private Views.ProgressLine progress;
    private Views.Icon playIcon;
    private LinearLayout notifBanner;
    private TextView likeBtn, transText;
    private LinearLayout transRow;
    private int bgColor = Ui.BG;            // kapaktan gelen renk (koyulaştırılmış)
    private android.animation.ValueAnimator bgAnim;
    private String colorFor;
    private Views.Meter meter;
    private TextView sessionLabel, sleepChip, nightPill, carPill;

    private ImageView nextCover;
    private TextView nextBadge, nextTitle, nextArtist, nextReason, nextCountdown, nextStatus;
    private LinearLayout nextContent, nextLoading;
    private ProgressBar nextSpinner;
    private TextView skipBtn;

    private Ui.Segmented modeSeg, langSeg;
    private LinearLayout queueBox, queueList, altList;
    private TextView queueHeader, altHeader;

    private Track renderedSeed, renderedPick;
    private List<Track> renderedAlts;
    private int renderedQueueVersion = -1;

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
        LinearLayout nowRow = Ui.hbox(a);
        TextView now = Ui.text(a, "ŞU AN", 11, Ui.ACCENT, Ui.BOLD);
        now.setLetterSpacing(0.12f);
        nowRow.addView(now, Ui.lpw(1));
        TextView dislikeNow = smallRound("👎");
        dislikeNow.setOnClickListener(v -> {
            e.dislikeNow();
            a.toast("Bir daha önerilmeyecek, geçiliyor");
        });
        likeBtn = smallRound("🤍");
        likeBtn.setOnClickListener(v -> {
            boolean was = e.isNowLiked();
            e.toggleLikeNow();
            likeBtn.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120)
                    .withEndAction(() -> likeBtn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            a.toast(was ? "Beğendiklerinden çıkarıldı" : "❤️ Beğendin, benzerleri daha sık gelecek");
        });
        nowRow.addView(likeBtn, Ui.margins(Ui.lp(Ui.dp(a, 34), Ui.dp(a, 34)), a, 0, 0, 8, 0));
        nowRow.addView(dislikeNow, Ui.lp(Ui.dp(a, 34), Ui.dp(a, 34)));
        ht.addView(nowRow, full());
        heroTitle = Ui.text(a, "", 19, Ui.TEXT, Ui.BOLD);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroTitle.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
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

        // Son geçişi değerlendir
        transRow = Ui.hbox(a);
        transRow.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        transRow.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8));
        transText = Ui.oneLine(Ui.text(a, "", 12.5f, Ui.TEXT2, Ui.MEDIUM));
        transRow.addView(transText, Ui.lpw(1));
        TextView bad = Ui.text(a, "👎 Kötü geçiş", 12.5f, Ui.TEXT, Ui.BOLD);
        bad.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        bad.setBackground(Ui.ripple(a, Ui.round(a, 0x22FFFFFF, 12), 12));
        bad.setOnClickListener(v -> {
            e.markBadTransition();
            a.toast("Tamam, bu tür geçişlerden kaçınacağım");
        });
        transRow.addView(bad);
        nowCard.addView(transRow, Ui.margins(full(), a, 0, 12, 0, 0));

        progress = new Views.ProgressLine(a);
        nowCard.addView(progress, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(a, 24)), a, 0, 10, 0, 0));
        LinearLayout times = Ui.hbox(a);
        timeLeft = Ui.text(a, "0:00", 12, Ui.TEXT3, Ui.MEDIUM);
        timeRight = Ui.text(a, "0:00", 12, Ui.TEXT3, Ui.MEDIUM);
        times.addView(timeLeft, Ui.lpw(1));
        times.addView(timeRight);
        nowCard.addView(times, full());

        // Dokunarak / sürükleyerek şarkıda ileri-geri
        progress.setOnTouchListener((v, ev) -> {
            if (e.nowDurationMs <= 0) return false;
            float f = Math.max(0, Math.min(1, ev.getX() / v.getWidth()));
            progress.setValue(f);
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                e.seekTo((long) (f * e.nowDurationMs));
            }
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        });

        // Oynatma düğmeleri: önceki · durdur/başlat · akıllı geç
        LinearLayout controls = Ui.hbox(a);
        controls.setGravity(Gravity.CENTER);
        LinearLayout prev = circleButton(Views.Icon.PREV, false, 52);
        prev.setOnClickListener(v -> e.previous());
        controls.addView(prev, Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)));
        LinearLayout play = circleButton(Views.Icon.PAUSE, true, 68);
        playIcon = (Views.Icon) play.getChildAt(0);
        play.setOnClickListener(v -> e.togglePlay());
        controls.addView(play, Ui.margins(Ui.lp(Ui.dp(a, 68), Ui.dp(a, 68)), a, 28, 0, 28, 0));
        LinearLayout next = circleButton(Views.Icon.NEXT, false, 52);
        next.setOnClickListener(v -> {
            if (e.pick != null) {
                e.skipNow();
                a.toast("Sıradakine geçiliyor…");
            } else {
                a.toast("Sıradaki henüz hazır değil");
            }
        });
        controls.addView(next, Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)));
        nowCard.addView(controls, Ui.margins(full(), a, 0, 8, 0, 4));

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

        // ---- Bildirim kapalı uyarısı ----
        notifBanner = Ui.hbox(a);
        notifBanner.setBackground(Ui.stroke(a, 0x1AFFB347, 0x55FFB347, 18, 1));
        notifBanner.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12));
        TextView nbT = Ui.text(a, "🔔  Bildirimler kapalı. Kilit ekranında sıradakini görmek ve oradan geçmek için aç.",
                13, Ui.TEXT, Ui.MEDIUM);
        nbT.setLineSpacing(0, 1.12f);
        notifBanner.addView(nbT, Ui.lpw(1));
        TextView nbB = Ui.text(a, "Aç", 13.5f, 0xFFFFFFFF, Ui.BOLD);
        nbB.setGravity(Gravity.CENTER);
        nbB.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        nbB.setBackground(Ui.ripple(a, Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 16), 16));
        nbB.setOnClickListener(v -> a.requestNotifications());
        notifBanner.addView(nbB, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 10, 0, 0, 0));
        body.addView(notifBanner, Ui.margins(full(), a, 0, 12, 0, 0));

        // ---- Bağlam: uyku, gece, araba ----
        contextRow = Ui.hbox(a);
        sleepChip = Ui.chip(a, "😴  Uyku zamanlayıcı", false);
        sleepChip.setTextSize(13);
        sleepChip.setOnClickListener(v -> sleepDialog());
        contextRow.addView(sleepChip, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
        nightPill = Ui.pill(a, "🌙 Gece: sakin", Ui.ACCENT2);
        contextRow.addView(nightPill, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
        carPill = Ui.pill(a, "🚗 Arabada", Ui.GOOD);
        contextRow.addView(carPill);
        body.addView(Ui.hscroll(a, contextRow), Ui.margins(full(), a, 0, 12, 0, 0));

        // ---- Ruh hali oturumu ----
        sessionCard = Ui.hbox(a);
        sessionCard.setBackground(Ui.gradient(a, 0x33FF4D6D, 0x337B5CFF, 18));
        sessionCard.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10));
        sessionLabel = Ui.oneLine(Ui.text(a, "", 14, Ui.TEXT, Ui.MEDIUM));
        sessionCard.addView(sessionLabel, Ui.lpw(1));
        TextView close = Ui.text(a, "Kapat", 13, Ui.TEXT2, Ui.BOLD);
        close.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        close.setBackground(Ui.ripple(a, Ui.round(a, 0x22FFFFFF, 14), 14));
        close.setOnClickListener(v -> {
            if (e.activeRoutine() != null) {
                a.toast("Bu bir rutin; Ayarlar → Otomatik ruh hali planı'ndan kapatabilirsin");
                a.showTab(MainActivity.TAB_SETTINGS);
            } else {
                e.clearSession();
            }
        });
        sessionCard.addView(close);
        body.addView(sessionCard, Ui.margins(full(), a, 0, 12, 0, 0));

        // ---- Sıradaki ----
        nextSection = Ui.vbox(a);
        nextSection.addView(Ui.section(a, "Sıradaki"));
        LinearLayout border = Ui.vbox(a);
        border.setBackground(Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 24));
        int bw = Ui.dp(a, 1.5f);
        border.setPadding(bw, bw, bw, bw);
        LinearLayout nextBody = Ui.vbox(a);
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
        nextBadge = Ui.text(a, "", 10.5f, Ui.TEXT3, Ui.BOLD);
        nextBadge.setLetterSpacing(0.1f);
        nextBadge.setPadding(0, 0, 0, Ui.dp(a, 8));
        nextContent.addView(nextBadge);
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
        nr.setClickable(true);
        nr.setOnClickListener(v -> Ui.showWhy(a, e.pick));
        TextView whyLink = Ui.text(a, "ⓘ  Neden bu şarkı?", 12.5f, Ui.TEXT3, Ui.BOLD);
        whyLink.setPadding(0, Ui.dp(a, 8), 0, 0);
        whyLink.setOnClickListener(v -> Ui.showWhy(a, e.pick));
        nextContent.addView(whyLink);
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
        TextView dislikeNext = Ui.secondaryButton(a, "👎");
        dislikeNext.setTextSize(18);
        dislikeNext.setPadding(0, 0, 0, 0);
        dislikeNext.setOnClickListener(v -> {
            e.dislikeNext();
            a.toast("Bir daha önerilmeyecek");
        });
        btns.addView(dislikeNext, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 10, 0, 0, 0));
        LinearLayout refresh = Ui.vbox(a);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        refresh.setClickable(true);
        refresh.addView(new Views.Icon(a, Views.Icon.REFRESH, Ui.TEXT), Ui.lp(Ui.dp(a, 22), Ui.dp(a, 22)));
        refresh.setOnClickListener(v -> {
            e.refresh();
            a.toast("Yeni öneriler hazırlanıyor");
        });
        btns.addView(refresh, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 10, 0, 0, 0));
        nextContent.addView(btns, full());
        nextBody.addView(nextContent, full());
        nextSection.addView(border, full());

        // ---- Kuyruk ----
        queueBox = Ui.vbox(a);
        LinearLayout qh = Ui.hbox(a);
        queueHeader = Ui.section(a, "Sıran");
        qh.addView(queueHeader, Ui.lpw(1));
        TextView clearQ = Ui.text(a, "Temizle", 13, Ui.TEXT2, Ui.BOLD);
        clearQ.setPadding(Ui.dp(a, 10), Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 8));
        clearQ.setOnClickListener(v -> e.clearQueue());
        qh.addView(clearQ);
        queueBox.addView(qh, full());
        queueList = Ui.vbox(a);
        queueBox.addView(queueList, full());
        nextSection.addView(queueBox, full());

        // ---- Hava ve dil ----
        nextSection.addView(Ui.section(a, "Hava"));
        modeSeg = Ui.segmented(a, new String[]{"🌙 Sakinleştir", "〰 Aynı", "⚡ Hareketlendir"},
                e.getMode() + 1, i -> e.setMode(i - 1));
        nextSection.addView(modeSeg.view, full());
        nextSection.addView(Ui.section(a, "Dil"));
        langSeg = Ui.segmented(a, new String[]{"🇹🇷  Türkçe", "🌍  Global"}, e.isTurkish() ? 0 : 1,
                i -> e.setTurkish(i == 0));
        nextSection.addView(langSeg.view, full());

        // ---- Öneriler ----
        altHeader = Ui.section(a, "Önerilen · dokun: sıradaki yap  ·  +: sıraya ekle");
        nextSection.addView(altHeader);
        altList = Ui.vbox(a);
        nextSection.addView(altList, full());
        body.addView(nextSection, full());
    }

    private LinearLayout circleButton(int icon, boolean primary, int sizeDp) {
        LinearLayout b = Ui.vbox(a);
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = primary
                ? Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, sizeDp / 2f)
                : Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, sizeDp / 2f, 1);
        b.setBackground(Ui.ripple(a, bg, sizeDp / 2f));
        b.setClickable(true);
        if (primary) b.setElevation(Ui.dp(a, 4));
        int is = primary ? 30 : 24;
        b.addView(new Views.Icon(a, icon, primary ? 0xFFFFFFFF : Ui.TEXT), Ui.lp(Ui.dp(a, is), Ui.dp(a, is)));
        return b;
    }

    private TextView smallRound(String label) {
        TextView t = Ui.text(a, label, 15, Ui.TEXT, Ui.REGULAR);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(a, Ui.round(a, Ui.SURFACE2, 17), 17));
        t.setClickable(true);
        return t;
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

    private void sleepDialog() {
        String[] items = {"15 dakika", "30 dakika", "45 dakika", "1 saat", "1,5 saat", "Kapat"};
        int[] mins = {15, 30, 45, 60, 90, 0};
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("😴 Uyku zamanlayıcı")
                .setItems(items, (d, which) -> {
                    e.setSleep(mins[which]);
                    if (mins[which] > 0) a.toast("Süre dolunca çalan şarkı bitince müzik duracak");
                })
                .show();
    }

    /** Kapağın baskın rengini alıp arka planı ona doğru yumuşakça boya. */
    private void updateColor(String url) {
        if (url == null || url.equals(colorFor)) return;
        colorFor = url;
        ImageLoader.fetch(url, bmp -> {
            if (!url.equals(colorFor)) return;
            int vib = Palette.vibrant(bmp, Ui.ACCENT2);
            animateBg(Palette.darken(vib, 0.42f));
        });
    }

    private void animateBg(int to) {
        if (bgAnim != null) bgAnim.cancel();
        int from = bgColor;
        bgAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        bgAnim.setDuration(600);
        bgAnim.addUpdateListener(an -> applyBg(Palette.mix(from, to, (float) an.getAnimatedValue())));
        bgAnim.start();
    }

    private void applyBg(int c) {
        bgColor = c;
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c, Palette.mix(c, Ui.BG, 0.65f), Ui.BG, Ui.BG});
        scroll.setBackground(g);
        if (a.isShowing(this)) a.setStatusBar(c);
    }

    @Override
    public void onShow() {
        a.setStatusBar(bgColor);
        modeSeg.select(e.getMode() + 1);
        langSeg.select(e.isTurkish() ? 0 : 1);
    }

    @Override
    public void render() {
        boolean has = e.nowTitle != null;
        empty.setVisibility(has ? View.GONE : View.VISIBLE);
        nowCard.setVisibility(has ? View.VISIBLE : View.GONE);
        nextSection.setVisibility(has ? View.VISIBLE : View.GONE);

        if (e.controller == null) setPill(statusPill, "●  Bağlı değil", Ui.TEXT3);
        else if (!e.isAuto()) setPill(statusPill, "●  Manuel", Ui.WARN);
        else setPill(statusPill, "●  Otomatik", Ui.GOOD);

        Moods.Target tg = e.sessionTarget();
        sessionCard.setVisibility(has && tg != null ? View.VISIBLE : View.GONE);
        if (tg != null) sessionLabel.setText("🎯  " + tg.label);

        // bağlam satırı
        long sl = e.sleepRemainingMs();
        boolean sleepOn = sl >= 0;
        String sleepTxt = !sleepOn ? "😴  Uyku zamanlayıcı"
                : sl == 0 ? "😴  Bu şarkıdan sonra duracak" : "😴  " + Math.max(1, (sl + 59999) / 60000) + " dk sonra duracak";
        if (!sleepTxt.contentEquals(sleepChip.getText())) {
            sleepChip.setText(sleepTxt);
            Ui.setChip(a, sleepChip, sleepOn);
        }
        nightPill.setVisibility(e.nightCalmActive() ? View.VISIBLE : View.GONE);
        carPill.setVisibility(e.isCarActive() ? View.VISIBLE : View.GONE);
        contextRow.setVisibility(has ? View.VISIBLE : View.GONE);
        notifBanner.setVisibility(has && e.isNotif() && !a.notificationsAllowed() ? View.VISIBLE : View.GONE);

        if (!has) return;

        // ---- Çalan ----
        Track from = e.lastTransitionFrom();
        transRow.setVisibility(from != null ? View.VISIBLE : View.GONE);
        if (from != null) transText.setText("↪ " + from.artist + " – " + from.title + " ardından");
        heroTitle.setText(e.nowTitle);
        heroArtist.setText(e.nowArtist);
        Track s = e.seed;
        if (s != renderedSeed) {
            renderedSeed = s;
            ImageLoader.load(s != null ? s.coverUrl : null, heroCover);
            updateColor(s != null ? s.coverUrl : null);
        }
        String heart = e.isNowLiked() ? "❤️" : "🤍";
        if (!heart.contentEquals(likeBtn.getText())) likeBtn.setText(heart);
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
        playIcon.setType(e.playing ? Views.Icon.PAUSE : Views.Icon.PLAY);
        timeLeft.setText(time(e.nowPositionMs) + (e.playing ? "" : "  ·  duraklatıldı"));
        timeRight.setText(time(e.nowDurationMs));

        // ---- Sıradaki ----
        Track p = e.pick;
        if (p == null) {
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
            nextBadge.setText(e.pickFromQueue() ? "SIRANDAN" : "OTOMATİK SEÇİM");
            nextTitle.setText(p.title);
            nextArtist.setText(p.artist + (p.lang != Lang.UNKNOWN ? "  ·  " + Lang.label(p.lang) : ""));
            nextReason.setText(p.reason.isEmpty() ? (e.pickFromQueue() ? "senin seçtiğin" : "") : p.reason);
            long ms = e.msToSwitch();
            String cd;
            if (e.sleepFinishing) cd = "😴 Uyku zamanı: bu şarkı bitince müzik duracak";
            else if (!e.isAuto()) cd = "Otomatik geçiş kapalı · \"Şimdi geç\" ile elle geçebilirsin";
            else if (ms < 0) cd = "Şarkı bitince geçecek";
            else if (ms <= 60000) cd = "⏱  " + Math.max(1, (ms + 999) / 1000) + " sn sonra geçiş"
                    + (e.isFade() ? " · ses yumuşakça kısılacak" : "");
            else cd = "Şarkı bitince geçecek · " + time(ms) + " kaldı";
            if (p.videoId == null) cd += "\nYouTube Music'te aranıyor…";
            nextCountdown.setText(cd);
        }

        // ---- Kuyruk (sıradakinden sonrakiler) ----
        if (renderedQueueVersion != e.queueVersion) {
            renderedQueueVersion = e.queueVersion;
            queueList.removeAllViews();
            int n = e.queue.size();
            boolean showQ = n > 1 || (n == 1 && !e.pickFromQueue());
            queueBox.setVisibility(showQ ? View.VISIBLE : View.GONE);
            queueHeader.setText(("Sıran · " + n + " şarkı").toUpperCase(Locale.forLanguageTag("tr")));
            int start = e.pickFromQueue() ? 1 : 0;
            for (int i = start; i < n; i++) {
                final int idx = i;
                Track t = e.queue.get(i);
                LinearLayout r = Ui.songRow(a, t, t.hasBpm() ? Math.round(t.bpm) + " BPM" : null, i + 2 - start);
                if (t.hasBpm()) r.removeViewAt(r.getChildCount() - 1);   // yer açmak için BPM sütununu kaldır
                r.addView(ctrl("▲", v -> e.moveInQueue(idx, -1), idx > start));
                r.addView(ctrl("▼", v -> e.moveInQueue(idx, 1), idx < n - 1));
                r.addView(ctrl("✕", v -> e.removeFromQueue(idx), true));
                queueList.addView(r, Ui.margins(full(), a, 0, 0, 0, 8));
            }
        }

        // ---- Öneriler ----
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
                r.setOnLongClickListener(v -> {
                    Ui.showWhy(a, t);
                    return true;
                });
                r.addView(ctrl("+", v -> {
                    e.addToQueue(t);
                    a.toast("Sıraya eklendi: " + t.title);
                }, true));
                altList.addView(r, Ui.margins(full(), a, 0, 0, 0, 8));
            }
        }
    }

    /** Satır sonundaki küçük kontrol düğmesi. */
    private TextView ctrl(String label, View.OnClickListener l, boolean enabled) {
        TextView t = Ui.text(a, label, 15, Ui.TEXT, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(a, Ui.round(a, Ui.SURFACE2, 16), 16));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.dp(a, 34), Ui.dp(a, 34));
        lp.setMargins(Ui.dp(a, 6), 0, 0, 0);
        t.setLayoutParams(lp);
        Ui.setEnabledLook(t, enabled);
        return t;
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
