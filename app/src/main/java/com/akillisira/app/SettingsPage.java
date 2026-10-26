package com.akillisira.app;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** Ayarlar, izin durumu, hakkında ve geliştirici kaydı. */
public class SettingsPage extends Page {

    private Switch swAuto, swFade, swBack, swNotif, swNight, swCar;
    private TextView notifPermBtn;
    private TextView lfmStatus, lfmRemove;
    private android.widget.EditText lfmInput;
    private Ui.Segmented exploreSeg2, wakeVolSeg;
    private Switch swWake;
    private TextView wakeTime, wakeNext, cacheInfo;
    private final List<TextView> dayChips = new java.util.ArrayList<>();
    private Ui.Segmented fadeSeg, langSeg, modeSeg;
    private TextView methodText, retrySilent;
    private TextView permNotif, permOverlay, permBattery;
    private TextView logText;
    private boolean logOpen;

    public SettingsPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        body.addView(Ui.text(a, "Ayarlar", 28, Ui.TEXT, Ui.BLACK));

        // ---- Last.fm ----
        body.addView(Ui.section(a, "Daha isabetli öneriler"));
        LinearLayout lf = Ui.card(a);
        LinearLayout lfh = Ui.hbox(a);
        lfh.addView(Ui.text(a, "Last.fm bağlantısı", 15, Ui.TEXT, Ui.BOLD), Ui.lpw(1));
        lfmStatus = Ui.pill(a, "", Ui.TEXT3);
        lfh.addView(lfmStatus);
        lf.addView(lfh, full());
        TextView lfd = Ui.text(a, "Last.fm, dinleyicilerin sanatçılara verdiği \"arabesk\", \"türk sanat müziği\", "
                + "\"turkish rap\" gibi etiketleri sağlar. Bağlanınca sıradaki şarkı tarza göre çok daha isabetli seçilir, "
                + "dil kesin anlaşılır ve \"rakı masası\" gibi sahneler gerçek fasıl/arabesk listeleri getirir.\n\n"
                + "Anahtar almak (ücretsiz, 2 dk):\n"
                + "1) Aşağıdaki düğmeyle Last.fm sayfasını aç, hesabın yoksa ücretsiz kaydol\n"
                + "2) Formda Application name: Akıllı Sıra yaz, diğer alanları boş bırakabilirsin\n"
                + "3) Submit'e bas, çıkan \"API key\" satırını kopyala ve buraya yapıştır",
                13, Ui.TEXT2, Ui.REGULAR);
        lfd.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        lfd.setLineSpacing(0, 1.15f);
        lf.addView(lfd);
        TextView getKey = Ui.secondaryButton(a, "🔑  Last.fm'de anahtar al");
        getKey.setOnClickListener(v -> {
            try {
                a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.last.fm/api/account/create")));
            } catch (Exception ex) {
                a.toast("Tarayıcı açılamadı");
            }
        });
        lf.addView(getKey, full());
        lfmInput = new android.widget.EditText(a);
        lfmInput.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        lfmInput.setHint("API anahtarını buraya yapıştır");
        lfmInput.setHintTextColor(Ui.TEXT3);
        lfmInput.setTextColor(Ui.TEXT);
        lfmInput.setTextSize(14);
        lfmInput.setSingleLine(true);
        lfmInput.setTypeface(Typeface.MONOSPACE);
        int ip = Ui.dp(a, 12);
        lfmInput.setPadding(ip, ip, ip, ip);
        String cur = e.getLastFmKey();
        if (cur != null) lfmInput.setText(cur);
        lf.addView(lfmInput, Ui.margins(full(), a, 0, 10, 0, 0));
        LinearLayout lfb = Ui.hbox(a);
        TextView save = Ui.primaryButton(a, "Kaydet ve dene");
        save.setOnClickListener(v -> {
            String k = lfmInput.getText().toString().trim();
            if (k.length() < 20) {
                a.toast("Anahtar eksik görünüyor (32 karakter olmalı)");
                return;
            }
            a.toast("Deneniyor…");
            new Thread(() -> {
                String err = LastFm.test(k);
                a.runOnUiThread(() -> {
                    if (err == null) {
                        e.setLastFmKey(k);
                        a.toast("✓ Last.fm bağlandı");
                    } else {
                        a.toast("Olmadı: " + err);
                    }
                    render();
                });
            }).start();
        });
        lfb.addView(save, Ui.lpw(1));
        lfmRemove = Ui.secondaryButton(a, "Kaldır");
        lfmRemove.setOnClickListener(v -> {
            e.setLastFmKey(null);
            lfmInput.setText("");
            render();
        });
        lfb.addView(lfmRemove, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 10, 0, 0, 0));
        lf.addView(lfb, Ui.margins(full(), a, 0, 10, 0, 0));
        body.addView(lf, full());

        // ---- Geçiş ----
        body.addView(Ui.section(a, "Geçiş"));
        LinearLayout g = Ui.card(a);
        swAuto = switchRow(g, "Otomatik geçiş", "Şarkı bitince sıradakine kendiliğinden geç",
                e.isAuto(), (b, on) -> e.setAuto(on));
        divider(g);
        swFade = switchRow(g, "Yumuşak geçiş", "Şarkının sonunda sesi yavaşça kısar, yeni şarkıda geri açar",
                e.isFade(), (b, on) -> e.setFade(on));
        TextView fl = Ui.text(a, "Kısma süresi", 13, Ui.TEXT2, Ui.MEDIUM);
        fl.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        g.addView(fl);
        int fs = e.getFadeSec();
        fadeSeg = Ui.segmented(a, new String[]{"2 sn", "4 sn", "6 sn"}, fs <= 2 ? 0 : fs <= 4 ? 1 : 2,
                i -> e.setFadeSec(i == 0 ? 2 : i == 1 ? 4 : 6));
        g.addView(fadeSeg.view, full());
        divider(g);
        swBack = switchRow(g, "Uygulamaya geri dön",
                "Geçişte YouTube Music öne gelirse ve Akıllı Sıra açıksa, birkaç saniye sonra geri döner",
                e.isReturnToApp(), (b, on) -> e.setReturnToApp(on));
        divider(g);
        g.addView(Ui.text(a, "Geçiş yöntemi", 15, Ui.TEXT, Ui.BOLD));
        methodText = Ui.text(a, "", 13, Ui.TEXT2, Ui.REGULAR);
        methodText.setPadding(0, Ui.dp(a, 4), 0, 0);
        methodText.setLineSpacing(0, 1.15f);
        g.addView(methodText);
        retrySilent = Ui.secondaryButton(a, "Sessiz geçişi yeniden dene");
        retrySilent.setOnClickListener(v -> e.resetSilent());
        g.addView(retrySilent, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        body.addView(g, full());

        // ---- Bildirim ve widget ----
        body.addView(Ui.section(a, "Bildirim ve widget"));
        LinearLayout nb = Ui.card(a);
        swNotif = switchRow(nb, "Bildirimde sıradakini göster",
                "Kilit ekranında ve bildirimde sıradaki şarkı; Şimdi geç ve 👎 düğmeleriyle",
                e.isNotif(), (b, on) -> {
                    e.setNotif(on);
                    if (on && !a.notificationsAllowed()) a.requestNotifications();
                });
        notifPermBtn = Ui.secondaryButton(a, "Bildirim iznini ver");
        notifPermBtn.setOnClickListener(v -> a.requestNotifications());
        nb.addView(notifPermBtn, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 8, 0, 0));
        divider(nb);
        nb.addView(Ui.text(a, "Ana ekran widget'ı", 15, Ui.TEXT, Ui.BOLD));
        TextView wd = Ui.text(a, "Ana ekranda boş bir yere uzun bas → Widget'lar → Akıllı Sıra. "
                + "Çalanı ve sıradakini gösterir, ⏭ ve 👎 düğmeleri vardır.\n\n"
                + "Kısayollar: uygulama simgesine uzun bas → Sakin, Enerjik, Yol modu, Favoriler.\n\n"
                + "Hızlı ayarlar: bildirim panelini aşağı çek → kalem simgesi → \"Akıllı Sıra\" kutucuğunu ekle. "
                + "Dokununca otomatik geçiş açılıp kapanır.", 13, Ui.TEXT2, Ui.REGULAR);
        wd.setPadding(0, Ui.dp(a, 4), 0, 0);
        wd.setLineSpacing(0, 1.15f);
        nb.addView(wd);
        body.addView(nb, full());

        // ---- Akıllı bağlam ----
        body.addView(Ui.section(a, "Akıllı bağlam"));
        LinearLayout cb = Ui.card(a);
        swNight = switchRow(cb, "Gece otomatik sakinleş",
                "23:00 – 06:00 arası, ruh hali seçmediysen sıradakiler daha sakin seçilir",
                e.isNightCalm(), (b, on) -> e.setNightCalm(on));
        divider(cb);
        swCar = switchRow(cb, "Arabaya bağlanınca Arabada modu",
                "Bluetooth ile arabaya bağlanınca yol şarkılarına geçer, ayrılınca önceki moda döner",
                e.isCarMode(), (b, on) -> e.setCarMode(on));
        divider(cb);
        cb.addView(Ui.text(a, "Uyku zamanlayıcı", 15, Ui.TEXT, Ui.BOLD));
        TextView sd = Ui.text(a, "Şimdi Çalıyor ekranındaki 😴 düğmesinden kur. Süre dolunca çalan şarkı biter, "
                + "ses yavaşça kısılır ve müzik durur. Bu sürede sakin şarkılar seçilir.", 13, Ui.TEXT2, Ui.REGULAR);
        sd.setPadding(0, Ui.dp(a, 4), 0, 0);
        sd.setLineSpacing(0, 1.15f);
        cb.addView(sd);
        body.addView(cb, full());

        // ---- Öneriler ----
        body.addView(Ui.section(a, "Öneriler"));
        LinearLayout o = Ui.card(a);
        o.addView(Ui.text(a, "Dil", 15, Ui.TEXT, Ui.BOLD));
        TextView ld = Ui.text(a, "Türkçe seçiliyken sadece Türkçe şarkılar gelir; Global'de karışık ve dengeli gider.",
                13, Ui.TEXT2, Ui.REGULAR);
        ld.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
        o.addView(ld);
        langSeg = Ui.segmented(a, new String[]{"🇹🇷  Türkçe", "🌍  Global"}, e.isTurkish() ? 0 : 1,
                i -> e.setTurkish(i == 0));
        o.addView(langSeg.view, full());
        divider(o);
        o.addView(Ui.text(a, "Hava", 15, Ui.TEXT, Ui.BOLD));
        TextView md = Ui.text(a, "Sıradaki şarkı tempo ve enerji olarak nereye gitsin?", 13, Ui.TEXT2, Ui.REGULAR);
        md.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
        o.addView(md);
        modeSeg = Ui.segmented(a, new String[]{"🌙 Sakin", "〰 Aynı", "⚡ Hareketli"}, e.getMode() + 1,
                i -> e.setMode(i - 1));
        o.addView(modeSeg.view, full());
        divider(o);
        o.addView(Ui.text(a, "Keşif", 15, Ui.TEXT, Ui.BOLD));
        TextView exd = Ui.text(a, "Tanıdık: bildiğin hitler ağırlıkta · Yeni: az bilinen şarkılar öne çıkar",
                13, Ui.TEXT2, Ui.REGULAR);
        exd.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
        o.addView(exd);
        exploreSeg2 = Ui.segmented(a, new String[]{"Tanıdık", "Dengeli", "Yeni"}, e.getExplore(), e::setExplore);
        o.addView(exploreSeg2.view, full());
        body.addView(o, full());

        // ---- Müzikle uyan ----
        body.addView(Ui.section(a, "Müzikle uyan"));
        LinearLayout w = Ui.card(a);
        swWake = switchRow(w, "⏰  Alarm", "Sabah sakin şarkılarla, çok kısık başlar; 2,5 dakikada ses ve tempo yükselir",
                WakeScheduler.isOn(a), (b, on) -> saveWake(on));
        LinearLayout tr = Ui.hbox(a);
        tr.setPadding(0, Ui.dp(a, 10), 0, 0);
        wakeTime = Ui.text(a, "", 34, Ui.TEXT, Ui.BLACK);
        wakeTime.setOnClickListener(v -> pickTime());
        tr.addView(wakeTime, Ui.lpw(1));
        TextView change = Ui.secondaryButton(a, "Saati değiştir");
        change.setOnClickListener(v -> pickTime());
        tr.addView(change);
        w.addView(tr, full());
        LinearLayout days = Ui.hbox(a);
        String[] dn = {"Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"};
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView ch = Ui.chip(a, dn[i], false);
            ch.setTextSize(13);
            ch.setPadding(0, Ui.dp(a, 9), 0, Ui.dp(a, 9));
            ch.setOnClickListener(v -> {
                int d = WakeScheduler.days(a) ^ (1 << bit);
                WakeScheduler.set(a, WakeScheduler.isOn(a), WakeScheduler.hour(a), WakeScheduler.minute(a), d,
                        WakeScheduler.volumePct(a));
                render();
            });
            LinearLayout.LayoutParams lp = Ui.lpw(1);
            lp.setMargins(i == 0 ? 0 : Ui.dp(a, 3), 0, 0, 0);
            days.addView(ch, lp);
            dayChips.add(ch);
        }
        w.addView(days, Ui.margins(full(), a, 0, 12, 0, 0));
        TextView vl = Ui.text(a, "Uyanma sesi", 13, Ui.TEXT2, Ui.MEDIUM);
        vl.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 8));
        w.addView(vl);
        int vp = WakeScheduler.volumePct(a);
        wakeVolSeg = Ui.segmented(a, new String[]{"Kısık", "Orta", "Yüksek"}, vp <= 45 ? 0 : vp <= 65 ? 1 : 2, i -> {
            int pct = i == 0 ? 40 : i == 1 ? 60 : 80;
            WakeScheduler.set(a, WakeScheduler.isOn(a), WakeScheduler.hour(a), WakeScheduler.minute(a),
                    WakeScheduler.days(a), pct);
        });
        w.addView(wakeVolSeg.view, full());
        wakeNext = Ui.text(a, "", 13, Ui.ACCENT, Ui.MEDIUM);
        wakeNext.setPadding(0, Ui.dp(a, 12), 0, 0);
        w.addView(wakeNext);
        TextView test = Ui.secondaryButton(a, "Şimdi dene");
        test.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, WakeActivity.class)));
        w.addView(test, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        TextView wn = Ui.text(a, "Not: Müzik 45 saniyede başlamazsa (ör. internet yoksa) telefonun alarm sesi çalar; "
                + "uyuya kalmazsın. Gün seçmezsen alarm bir kez çalar.", 12, Ui.TEXT3, Ui.REGULAR);
        wn.setPadding(0, Ui.dp(a, 10), 0, 0);
        wn.setLineSpacing(0, 1.12f);
        w.addView(wn);
        body.addView(w, full());

        // ---- Yedekleme ----
        body.addView(Ui.section(a, "Yedekleme"));
        LinearLayout bk = Ui.card(a);
        TextView bd = Ui.text(a, "Beğenilerin, beğenmediklerin, geçmişin ve ayarların tek bir dosyaya kaydedilir. "
                + "Telefon değiştirince bu dosyayı içe aktararak kaldığın yerden devam edebilirsin. "
                + "(Last.fm anahtarı güvenlik için yedeğe girmez.)", 13, Ui.TEXT2, Ui.REGULAR);
        bd.setLineSpacing(0, 1.15f);
        bk.addView(bd);
        LinearLayout bb = Ui.hbox(a);
        TextView exp = Ui.primaryButton(a, "Dışa aktar");
        exp.setOnClickListener(v -> a.exportBackup());
        bb.addView(exp, Ui.lpw(1));
        TextView imp = Ui.secondaryButton(a, "İçe aktar");
        imp.setOnClickListener(v -> a.importBackup());
        LinearLayout.LayoutParams ilp = Ui.lpw(1);
        ilp.setMargins(Ui.dp(a, 10), 0, 0, 0);
        bb.addView(imp, ilp);
        bk.addView(bb, Ui.margins(full(), a, 0, 12, 0, 0));
        body.addView(bk, full());

        // ---- İzinler ----
        body.addView(Ui.section(a, "İzinler"));
        LinearLayout p = Ui.card(a);
        permNotif = permRow(p, "Bildirim erişimi", a::openNotificationAccess);
        divider(p);
        permOverlay = permRow(p, "Diğer uygulamaların üzerinde göster", a::openOverlayPermission);
        divider(p);
        permBattery = permRow(p, "Pil kısıtlaması kaldırıldı", a::openBatterySettings);
        body.addView(p, full());

        // ---- Hakkında ----
        body.addView(Ui.section(a, "Hakkında"));
        LinearLayout h = Ui.card(a);
        h.addView(Ui.text(a, "Akıllı Sıra " + a.versionName(), 15, Ui.TEXT, Ui.BOLD));
        TextView hd = Ui.text(a, "Tempo (BPM) ve enerji verisi Deezer'den alınır, şarkılar YouTube Music'te çalınır. "
                + "Bu kişisel bir uygulamadır; YouTube Music'in resmi bir parçası değildir.", 13, Ui.TEXT2, Ui.REGULAR);
        hd.setPadding(0, Ui.dp(a, 6), 0, 0);
        hd.setLineSpacing(0, 1.15f);
        h.addView(hd);
        TextView yt = Ui.secondaryButton(a, "YouTube Music'i aç");
        yt.setOnClickListener(v -> a.openYtMusic());
        h.addView(yt, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 12, 0, 0));
        divider(h);
        h.addView(Ui.text(a, "Önbellek", 15, Ui.TEXT, Ui.BOLD));
        cacheInfo = Ui.text(a, "", 13, Ui.TEXT2, Ui.REGULAR);
        cacheInfo.setPadding(0, Ui.dp(a, 4), 0, 0);
        h.addView(cacheInfo);
        TextView clearC = Ui.secondaryButton(a, "Önbelleği temizle");
        clearC.setOnClickListener(v -> {
            DiskCache.clearAll();
            a.toast("Önbellek temizlendi");
            render();
        });
        h.addView(clearC, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        body.addView(h, full());

        // ---- Kayıt ----
        body.addView(Ui.section(a, "Geliştirici kaydı"));
        TextView toggle = Ui.secondaryButton(a, "Kaydı göster");
        toggle.setOnClickListener(v -> {
            logOpen = !logOpen;
            toggle.setText(logOpen ? "Kaydı gizle" : "Kaydı göster");
            logText.setVisibility(logOpen ? View.VISIBLE : View.GONE);
            render();
        });
        body.addView(toggle, Ui.lp(Ui.WRAP, Ui.WRAP));
        logText = Ui.text(a, "", 11.5f, Ui.TEXT2, Typeface.MONOSPACE);
        logText.setBackground(Ui.round(a, Ui.SURFACE, 16));
        logText.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        logText.setTextIsSelectable(true);
        logText.setVisibility(View.GONE);
        body.addView(logText, Ui.margins(full(), a, 0, 10, 0, 0));
    }

    private Switch switchRow(LinearLayout parent, String title, String desc, boolean on,
                             CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        LinearLayout t = Ui.vbox(a);
        t.addView(Ui.text(a, title, 15, Ui.TEXT, Ui.BOLD));
        TextView d = Ui.text(a, desc, 13, Ui.TEXT2, Ui.REGULAR);
        d.setPadding(0, Ui.dp(a, 3), Ui.dp(a, 8), 0);
        d.setLineSpacing(0, 1.12f);
        t.addView(d);
        row.addView(t, Ui.lpw(1));
        Switch sw = new Switch(a);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFBDBDC8}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{Ui.ACCENT, 0x44FFFFFF}));
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        parent.addView(row, full());
        return sw;
    }

    private TextView permRow(LinearLayout parent, String title, Runnable open) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        row.addView(Ui.text(a, title, 14.5f, Ui.TEXT, Ui.MEDIUM), Ui.lpw(1));
        TextView st = Ui.text(a, "", 13, Ui.GOOD, Ui.BOLD);
        st.setGravity(Gravity.CENTER);
        st.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        st.setOnClickListener(v -> open.run());
        row.addView(st);
        parent.addView(row, full());
        return st;
    }

    private void divider(LinearLayout parent) {
        View d = new View(a);
        d.setBackgroundColor(Ui.STROKE);
        parent.addView(d, Ui.margins(Ui.lp(Ui.MATCH, 1), a, 0, 14, 0, 14));
    }

    private void setPerm(TextView t, boolean ok) {
        t.setText(ok ? "✓ Açık" : "Aç →");
        t.setTextColor(ok ? Ui.GOOD : 0xFFFFFFFF);
        t.setBackground(ok ? null : Ui.ripple(a, Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 14), 14));
        t.setClickable(!ok);
    }

    private void saveWake(boolean on) {
        if (on && !WakeScheduler.exactAllowed(a)) {
            a.toast("Tam zamanlı alarm izni gerekiyor");
            try {
                a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {}
        }
        WakeScheduler.set(a, on, WakeScheduler.hour(a), WakeScheduler.minute(a), WakeScheduler.days(a),
                WakeScheduler.volumePct(a));
        if (on) a.toast("⏰ Alarm kuruldu: " + WakeScheduler.describeNext(a));
        render();
    }

    private void pickTime() {
        new android.app.TimePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert, (tp, hh, mm) -> {
            WakeScheduler.set(a, true, hh, mm, WakeScheduler.days(a), WakeScheduler.volumePct(a));
            swWake.setChecked(true);
            a.toast("⏰ Alarm kuruldu: " + WakeScheduler.describeNext(a));
            render();
        }, WakeScheduler.hour(a), WakeScheduler.minute(a), true).show();
    }

    @Override
    public void onShow() {
        exploreSeg2.select(e.getExplore());
        swWake.setChecked(WakeScheduler.isOn(a));
        swAuto.setChecked(e.isAuto());
        swFade.setChecked(e.isFade());
        swBack.setChecked(e.isReturnToApp());
        swNotif.setChecked(e.isNotif());
        swNight.setChecked(e.isNightCalm());
        swCar.setChecked(e.isCarMode());
        langSeg.select(e.isTurkish() ? 0 : 1);
        modeSeg.select(e.getMode() + 1);
    }

    @Override
    public void render() {
        wakeTime.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", WakeScheduler.hour(a), WakeScheduler.minute(a)));
        wakeTime.setAlpha(WakeScheduler.isOn(a) ? 1f : 0.45f);
        int dm = WakeScheduler.days(a);
        for (int i = 0; i < dayChips.size(); i++) Ui.setChip(a, dayChips.get(i), (dm & (1 << i)) != 0);
        wakeNext.setText(WakeScheduler.isOn(a) ? "Sonraki alarm: " + WakeScheduler.describeNext(a)
                + (dm == 0 ? " (bir kez)" : "") : "Alarm kapalı");
        cacheInfo.setText(DiskCache.totalEntries() + " kayıt telefonda saklanıyor. Etiketler ve şarkı bilgileri "
                + "tekrar sorulmadığı için öneriler daha hızlı hazırlanır.");
        boolean lfOn = e.lastfm.enabled();
        String st = lfOn ? "✓ Bağlı" : "Bağlı değil";
        if (!st.contentEquals(lfmStatus.getText())) {
            lfmStatus.setText(st);
            lfmStatus.setTextColor(lfOn ? Ui.GOOD : Ui.WARN);
            lfmStatus.setBackground(Ui.round(a, ((lfOn ? Ui.GOOD : Ui.WARN) & 0x00FFFFFF) | 0x22000000, 12));
        }
        lfmRemove.setVisibility(lfOn ? View.VISIBLE : View.GONE);
        int s = e.getSilentState();
        methodText.setText(s == Engine.SILENT_YES
                ? "Sessiz ✓  YouTube Music arka planda kalarak şarkı değiştiriyor."
                : s == Engine.SILENT_NO
                ? "YouTube Music öne gelerek geçiyor. Telefonundaki YT Music sürümü sessiz geçişi desteklemiyor."
                : "İlk otomatik geçişte en iyi yöntem denenip öğrenilecek.");
        retrySilent.setVisibility(s == Engine.SILENT_NO ? View.VISIBLE : View.GONE);
        notifPermBtn.setVisibility(e.isNotif() && !a.notificationsAllowed() ? View.VISIBLE : View.GONE);
        setPerm(permNotif, a.hasNotificationAccess());
        setPerm(permOverlay, a.canOverlay());
        setPerm(permBattery, a.batteryUnrestricted());
        if (logOpen) logText.setText(String.join("\n", e.log));
    }
}
