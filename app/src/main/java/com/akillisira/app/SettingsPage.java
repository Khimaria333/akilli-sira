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
    private TextView lfmStatus, lfmRemove, updText, verText, badInfo;
    private LinearLayout updCard;
    private android.widget.EditText lfmInput;
    private Ui.Segmented exploreSeg2, wakeVolSeg;
    private Switch swWake;
    private TextView wakeTime, wakeNext, cacheInfo;
    private final List<TextView> dayChips = new java.util.ArrayList<>();
    private Ui.Segmented fadeSeg, langSeg, modeSeg;
    private TextView methodText, retrySilent;
    private TextView permNotif, permOverlay, permBattery;
    private TextView logText, weatherRow, spStatus, spRemove;
    private android.widget.EditText spInput;

    private void refreshWeatherRow() {
        if (weatherRow == null) return;
        String place = Weather.placeName(a);
        Weather.Now w = Weather.current();
        weatherRow.setText(place == null ? "📍 Şehir seçilmedi"
                : "📍 " + place + (w != null ? "  ·  " + w.label() : "") + (Weather.isOn(a) ? "" : "  ·  kapalı"));
        if (place != null && w == null) Weather.refresh(a, false, () -> a.runOnUiThread(() -> {
            if (Weather.current() != null) refreshWeatherRow();
        }));
    }
    private boolean logOpen;

    public SettingsPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        body.addView(Ui.text(a, "Ayarlar", 28, Ui.TEXT, Ui.BLACK));

        // ---- Güncelleme ----
        updCard = Ui.hbox(a);
        updCard.setBackground(Ui.gradient(a, 0x33FF4D6D, 0x337B5CFF, 18));
        updCard.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14));
        updText = Ui.text(a, "", 14, Ui.TEXT, Ui.MEDIUM);
        updCard.addView(updText, Ui.lpw(1));
        TextView updBtn = Ui.text(a, "Güncelle", 13.5f, 0xFFFFFFFF, Ui.BOLD);
        updBtn.setGravity(android.view.Gravity.CENTER);
        updBtn.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        updBtn.setBackground(Ui.ripple(a, Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 16), 16));
        updBtn.setOnClickListener(v -> Updater.showDialog(a));
        updCard.addView(updBtn, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 10, 0, 0, 0));
        body.addView(updCard, Ui.margins(full(), a, 0, 14, 0, 0));

        // ---- Müzik uygulaması ----
        body.addView(Ui.section(a, "Müzik uygulaması"));
        LinearLayout mp = Ui.card(a);
        String pref = e.getPlayerPref();
        int pi = Engine.PLAYER_YTM.equals(pref) ? 1 : Engine.PLAYER_SPOTIFY.equals(pref) ? 2 : 0;
        Ui.Segmented playerSeg = Ui.segmented(a, new String[]{"Otomatik", "YouTube Music", "Spotify"}, pi, i -> {
            e.setPlayerPref(i == 1 ? Engine.PLAYER_YTM : i == 2 ? Engine.PLAYER_SPOTIFY : Engine.PLAYER_AUTO);
            render();
        });
        mp.addView(playerSeg.view, full());
        TextView mpd = Ui.text(a, "Otomatik: hangisinde müzik çalıyorsa ona uyum sağlar.", 12.5f, Ui.TEXT3, Ui.REGULAR);
        mpd.setPadding(Ui.dp(a, 2), Ui.dp(a, 6), 0, Ui.dp(a, 4));
        mp.addView(mpd);
        divider(mp);

        LinearLayout sph = Ui.hbox(a);
        sph.addView(Ui.text(a, "Spotify hesabı", 15, Ui.TEXT, Ui.BOLD), Ui.lpw(1));
        spStatus = Ui.pill(a, "", Ui.TEXT3);
        sph.addView(spStatus);
        mp.addView(sph, full());
        TextView spd = Ui.text(a, "Spotify'ı Ahenk'e bağlarsan sıradaki şarkı doğrudan Spotify'ın sırasına eklenir; "
                + "geçiş Spotify'ın kendi yumuşak geçişiyle olur. Bağlamadan da çalışır ama geçişler daha kaba olur.\n\n"
                + "Spotify'ın kuralı gereği herkes kendi ücretsiz anahtarını alır ve Spotify Premium gerekir. Bir kere yapılır (3 dk):\n"
                + "1) Aşağıdaki düğmeyle Spotify geliştirici panelini aç, Spotify hesabınla giriş yap\n"
                + "2) \"Create app\" → App name: Ahenk, açıklama: kişisel\n"
                + "3) Redirect URI kutusuna aşağıdaki adresi yapıştır ve \"Add\" de\n"
                + "4) \"Web API\" kutusunu işaretle, kaydet\n"
                + "5) Uygulamanın sayfasındaki \"Client ID\"yi kopyala, buraya yapıştır, Bağlan'a bas\n\n"
                + "Arkadaşın da kendi anahtarını alabilir ya da sen panelde \"User Management\"a onun Spotify e-postasını "
                + "ekleyebilirsin (en fazla 5 kişi).", 13, Ui.TEXT2, Ui.REGULAR);
        spd.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        spd.setLineSpacing(0, 1.15f);
        mp.addView(spd);
        LinearLayout spb1 = Ui.hbox(a);
        TextView dash = Ui.secondaryButton(a, "🔑  Geliştirici paneli");
        dash.setOnClickListener(v -> {
            try {
                a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://developer.spotify.com/dashboard")));
            } catch (Exception ex) {
                a.toast("Tarayıcı açılamadı");
            }
        });
        spb1.addView(dash, Ui.lpw(1));
        TextView copy = Ui.secondaryButton(a, "📋  Adresi kopyala");
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = a.getSystemService(android.content.ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Redirect URI", SpotifyApi.REDIRECT));
            a.toast("Kopyalandı: " + SpotifyApi.REDIRECT);
        });
        spb1.addView(copy, Ui.margins(Ui.lpw(1), a, 10, 0, 0, 0));
        mp.addView(spb1, full());
        TextView redir = Ui.text(a, SpotifyApi.REDIRECT, 12.5f, Ui.TEXT3, Ui.MEDIUM);
        redir.setTypeface(Typeface.MONOSPACE);
        redir.setTextIsSelectable(true);
        redir.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), 0, 0);
        mp.addView(redir);
        spInput = new android.widget.EditText(a);
        spInput.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        spInput.setHint("Client ID'yi buraya yapıştır");
        spInput.setHintTextColor(Ui.TEXT3);
        spInput.setTextColor(Ui.TEXT);
        spInput.setTextSize(14);
        spInput.setSingleLine(true);
        spInput.setTypeface(Typeface.MONOSPACE);
        int sip = Ui.dp(a, 12);
        spInput.setPadding(sip, sip, sip, sip);
        if (e.spotify.clientId() != null) spInput.setText(e.spotify.clientId());
        mp.addView(spInput, Ui.margins(full(), a, 0, 10, 0, 0));
        LinearLayout spb2 = Ui.hbox(a);
        TextView spGo = Ui.primaryButton(a, "Spotify'a bağlan");
        spGo.setOnClickListener(v -> {
            String cid = spInput.getText().toString().trim();
            if (!cid.matches("[0-9a-fA-F]{32}")) {
                a.toast("Client ID 32 karakter olmalı (harf ve rakam)");
                return;
            }
            a.toast("Spotify açılıyor; izin verince Ahenk'e dönülecek");
            e.spotify.login(a, cid, (ok, msg) -> a.runOnUiThread(() -> {
                a.toast(ok ? "✓ " + msg : "Olmadı: " + msg);
                if (ok) {
                    e.log("🎧 Spotify bağlandı" + (e.spotify.userName() != null ? ": " + e.spotify.userName() : ""));
                    try {
                        a.startActivity(new android.content.Intent(a, MainActivity.class)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    } catch (Exception ignored) {}
                }
                render();
            }));
        });
        spb2.addView(spGo, Ui.lpw(1));
        spRemove = Ui.secondaryButton(a, "Çıkış");
        spRemove.setOnClickListener(v -> {
            e.spotify.disconnect();
            a.toast("Spotify bağlantısı kaldırıldı");
            render();
        });
        spb2.addView(spRemove, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 10, 0, 0, 0));
        mp.addView(spb2, Ui.margins(full(), a, 0, 10, 0, 0));
        body.addView(mp, full());

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
                + "2) Formda Application name: Ahenk yaz, diğer alanları boş bırakabilirsin\n"
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
                "Geçişte müzik uygulaması öne gelirse ve Ahenk açıksa, birkaç saniye sonra geri döner",
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
        TextView wd = Ui.text(a, "Ana ekranda boş bir yere uzun bas → Widget'lar → Ahenk. "
                + "Çalanı ve sıradakini gösterir, ⏭ ve 👎 düğmeleri vardır.\n\n"
                + "Kısayollar: uygulama simgesine uzun bas → Sakin, Enerjik, Yol modu, Favoriler.\n\n"
                + "Hızlı ayarlar: bildirim panelini aşağı çek → kalem simgesi → \"Ahenk\" kutucuğunu ekle. "
                + "Dokununca otomatik geçiş açılıp kapanır.", 13, Ui.TEXT2, Ui.REGULAR);
        wd.setPadding(0, Ui.dp(a, 4), 0, 0);
        wd.setLineSpacing(0, 1.15f);
        nb.addView(wd);
        body.addView(nb, full());

        // ---- Akıllı bağlam ----
        // ---- Görünüm ----
        body.addView(Ui.section(a, "Görünüm"));
        LinearLayout th = Ui.card(a);
        th.addView(Ui.text(a, "Zemin", 13, Ui.TEXT2, Ui.MEDIUM));
        Ui.Segmented baseSeg = Ui.segmented(a, Ui.BASES, Ui.themeBase(a), i -> {
            Ui.setTheme(a, i, Ui.themeAccent(a));
            a.applyThemeNow();
        });
        th.addView(baseSeg.view, Ui.margins(full(), a, 0, 8, 0, 0));
        th.addView(Ui.text(a, "Vurgu rengi", 13, Ui.TEXT2, Ui.MEDIUM), Ui.margins(full(), a, 0, 16, 0, 0));
        LinearLayout accRow = Ui.hbox(a);
        int curAcc = Ui.themeAccent(a);
        for (int i = 0; i < Ui.ACCENT_NAMES.length; i++) {
            final int idx = i;
            LinearLayout chip = Ui.vbox(a);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setClickable(true);
            View dot = new View(a);
            if (i < Ui.ACCENTS.length) dot.setBackground(Ui.gradient(a, Ui.ACCENTS[i][0], Ui.ACCENTS[i][1], 18));
            else {
                android.graphics.drawable.GradientDrawable sweep = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFFFF4D6D, 0xFFFFC94D, 0xFF3DDC97, 0xFF22C1EE, 0xFF7B5CFF, 0xFFFF4D6D});
                sweep.setGradientType(android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
                sweep.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dot.setBackground(sweep);
            }
            FrameLayoutHolder.ring(a, chip, dot, idx == curAcc);
            TextView n = Ui.text(a, Ui.ACCENT_NAMES[i], 11, idx == curAcc ? Ui.TEXT : Ui.TEXT2, idx == curAcc ? Ui.BOLD : Ui.MEDIUM);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, Ui.dp(a, 6), 0, 0);
            chip.addView(n);
            chip.setOnClickListener(v -> {
                if (idx == Ui.themeAccent(a)) return;
                Ui.setTheme(a, Ui.themeBase(a), idx);
                a.applyThemeNow();
            });
            accRow.addView(chip, Ui.lpw(1));
        }
        th.addView(accRow, Ui.margins(full(), a, 0, 10, 0, 0));
        TextView thHint = Ui.text(a, "Kapak rengi: uygulamayı her açtığında çalan şarkının rengini alır.",
                12, Ui.TEXT3, Ui.REGULAR);
        thHint.setPadding(0, Ui.dp(a, 10), 0, 0);
        th.addView(thHint);
        body.addView(th, full());

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

        // Hava durumu
        switchRow(cb, "🌦  Havaya göre ruh hali",
                "Ruh hali seçmediysen yağmurda daha sakin, güneşte daha neşeli şarkılara hafifçe kayar",
                Weather.isOn(a), (b, on) -> {
                    if (on && !Weather.hasPlace(a)) {
                        b.setChecked(false);
                        WeatherDialog.show(a, this::refreshWeatherRow);
                        return;
                    }
                    Weather.setOn(a, on);
                    e.refresh();
                    refreshWeatherRow();
                });
        LinearLayout wr = Ui.hbox(a);
        wr.setGravity(Gravity.CENTER_VERTICAL);
        weatherRow = Ui.text(a, "", 13, Ui.TEXT2, Ui.MEDIUM);
        wr.addView(weatherRow, Ui.lpw(1));
        TextView wchg = Ui.text(a, "Şehri değiştir", 13, Ui.ACCENT, Ui.BOLD);
        wchg.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), 0, Ui.dp(a, 6));
        wchg.setOnClickListener(v -> WeatherDialog.show(a, this::refreshWeatherRow));
        wr.addView(wchg);
        cb.addView(wr, Ui.margins(full(), a, 0, 2, 0, 0));
        refreshWeatherRow();
        divider(cb);

        // Kulaklık
        cb.addView(Ui.text(a, "🎧  Kulaklık takınca", 15, Ui.TEXT, Ui.BOLD));
        TextView hpd = Ui.text(a, "Müzik çalmıyorken kulaklık bağlanırsa saate, rutine ve havaya uygun liste başlat",
                13, Ui.TEXT2, Ui.REGULAR);
        hpd.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 10));
        hpd.setLineSpacing(0, 1.12f);
        cb.addView(hpd);
        Ui.Segmented hpSeg = Ui.segmented(a, new String[]{"Kapalı", "Bildirimle sor", "Hemen başlat"}, e.getHpMode(), e::setHpMode);
        cb.addView(hpSeg.view, full());
        divider(cb);

        // Yeni çıkanlar
        switchRow(cb, "🆕  Yeni çıkan bildirimi",
                "Beğendiğin sanatçılar yeni şarkı ya da albüm çıkarınca günde en fazla bir kez haber ver",
                NewReleases.isOn(a), (b, on) -> NewReleases.setOn(a, on));
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
        divider(o);
        o.addView(Ui.text(a, "Öğrenilen kötü geçişler", 15, Ui.TEXT, Ui.BOLD));
        badInfo = Ui.text(a, "", 13, Ui.TEXT2, Ui.REGULAR);
        badInfo.setPadding(0, Ui.dp(a, 4), 0, 0);
        o.addView(badInfo);
        TextView clrBad = Ui.secondaryButton(a, "Sıfırla");
        clrBad.setOnClickListener(v -> {
            e.store.clearBadTransitions();
            a.toast("Kötü geçiş kayıtları silindi");
            render();
        });
        o.addView(clrBad, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        body.addView(o, full());

        // ---- Otomatik ruh hali planı ----
        body.addView(Ui.section(a, "Otomatik ruh hali planı"));
        LinearLayout rc = Ui.card(a);
        TextView rd = Ui.text(a, "Belirli gün ve saatlerde sıradaki şarkılar kendiliğinden o havaya geçsin. "
                + "Elle seçtiğin bir mod varsa o önce gelir.", 13, Ui.TEXT2, Ui.REGULAR);
        rd.setLineSpacing(0, 1.15f);
        rc.addView(rd);
        routineList = Ui.vbox(a);
        rc.addView(routineList, Ui.margins(full(), a, 0, 6, 0, 0));
        TextView addR = Ui.primaryButton(a, "＋  Rutin ekle");
        addR.setOnClickListener(v -> RoutineDialog.show(a, this::refreshRoutines));
        rc.addView(addR, Ui.margins(full(), a, 0, 12, 0, 0));
        TextView tl = Ui.text(a, "Hazır şablonlar", 13, Ui.TEXT2, Ui.MEDIUM);
        tl.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 8));
        rc.addView(tl);
        LinearLayout tpl = Ui.hbox(a);
        for (int i = 0; i < Routines.TEMPLATE_NAMES.length; i++) {
            final int idx = i;
            TextView ch = Ui.chip(a, Routines.TEMPLATE_NAMES[i], false);
            ch.setTextSize(12.5f);
            ch.setOnClickListener(v -> {
                Routines.add(a, Routines.template(idx));
                a.toast("🗓 Eklendi: " + Routines.TEMPLATE_NAMES[idx]);
                refreshRoutines();
            });
            tpl.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
        }
        rc.addView(Ui.hscroll(a, tpl), full());
        body.addView(rc, full());

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

        // ---- Sağlık kontrolü ----
        body.addView(Ui.section(a, "Sağlık kontrolü"));
        LinearLayout hc = Ui.card(a);
        TextView hcd = Ui.text(a, "Müzik servisleri, izinler ve son hatalar tek bakışta. Bir şey ters gidince önce buraya bak.",
                13, Ui.TEXT2, Ui.REGULAR);
        hcd.setLineSpacing(0, 1.15f);
        hc.addView(hcd);
        healthList = Ui.vbox(a);
        hc.addView(healthList, full());
        TextView runHc = Ui.primaryButton(a, "🩺  Kontrol et");
        runHc.setOnClickListener(v -> {
            healthList.removeAllViews();
            TextView wait = Ui.text(a, "Kontrol ediliyor…", 13, Ui.TEXT2, Ui.MEDIUM);
            wait.setPadding(0, Ui.dp(a, 10), 0, 0);
            healthList.addView(wait);
            Diagnostics.run(a, e, items -> {
                healthList.removeAllViews();
                for (Diagnostics.Item it : items) {
                    LinearLayout row = Ui.hbox(a);
                    row.setGravity(android.view.Gravity.TOP);
                    row.setPadding(0, Ui.dp(a, 10), 0, 0);
                    String mark = it.ok ? "✓" : it.warnOnly ? "!" : "✕";
                    int col = it.ok ? Ui.GOOD : it.warnOnly ? Ui.WARN : 0xFFFF6B6B;
                    TextView m = Ui.text(a, mark, 15, col, Ui.BOLD);
                    m.setGravity(android.view.Gravity.CENTER);
                    row.addView(m, Ui.lp(Ui.dp(a, 22), Ui.WRAP));
                    LinearLayout tt = Ui.vbox(a);
                    tt.addView(Ui.text(a, it.title, 14, Ui.TEXT, Ui.BOLD));
                    TextView d = Ui.text(a, it.detail, 12.5f, Ui.TEXT2, Ui.REGULAR);
                    d.setLineSpacing(0, 1.1f);
                    tt.addView(d);
                    row.addView(tt, Ui.lpw(1));
                    healthList.addView(row, full());
                }
            });
        });
        hc.addView(runHc, Ui.margins(full(), a, 0, 12, 0, 0));
        body.addView(hc, full());

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
        verText = Ui.text(a, "Ahenk " + a.versionName(), 15, Ui.TEXT, Ui.BOLD);
        h.addView(verText);
        TextView tour = Ui.secondaryButton(a, "Tanıtımı tekrar göster");
        tour.setOnClickListener(v -> a.replayIntro());
        h.addView(tour, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 0));
        TextView chk = Ui.secondaryButton(a, "Güncellemeleri kontrol et");
        chk.setOnClickListener(v -> {
            a.toast("Kontrol ediliyor…");
            Updater.check(a, true, () -> { Updater.showDialog(a); render(); },
                    () -> a.toast("✓ En güncel sürümü kullanıyorsun"));
        });
        h.addView(chk, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 10, 0, 6));
        TextView hd = Ui.text(a, "Tempo (BPM) ve enerji verisi Deezer'den, tarz etiketleri Last.fm'den alınır; şarkılar YouTube Music'te ya da Spotify'da çalınır. "
                + "Bu kişisel bir uygulamadır; YouTube Music'in ya da Spotify'ın resmi bir parçası değildir.", 13, Ui.TEXT2, Ui.REGULAR);
        hd.setPadding(0, Ui.dp(a, 6), 0, 0);
        hd.setLineSpacing(0, 1.15f);
        h.addView(hd);
        TextView yt = Ui.secondaryButton(a, e.playerAcc() + " aç");
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

    /** Vurgu rengi seçimi: renkli daire, seçiliyse çevresinde halka. */
    private static final class FrameLayoutHolder {
        static void ring(MainActivity a, LinearLayout parent, View dot, boolean selected) {
            android.widget.FrameLayout f = new android.widget.FrameLayout(a);
            int s = Ui.dp(a, 44);
            if (selected) {
                android.graphics.drawable.GradientDrawable r = new android.graphics.drawable.GradientDrawable();
                r.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                r.setStroke(Ui.dp(a, 2), Ui.TEXT);
                f.setBackground(r);
            }
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34), Gravity.CENTER);
            f.addView(dot, lp);
            parent.addView(f, Ui.lp(s, s));
        }
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

    private LinearLayout routineList, healthList;

    private void refreshRoutines() {
        routineList.removeAllViews();
        List<Routines.Routine> list = Routines.all(a);
        Routines.Routine active = e.activeRoutine();
        if (list.isEmpty()) {
            TextView none = Ui.text(a, "Henüz rutin yok.", 13, Ui.TEXT3, Ui.REGULAR);
            none.setPadding(0, Ui.dp(a, 8), 0, 0);
            routineList.addView(none);
        }
        for (Routines.Routine r : list) {
            LinearLayout row = Ui.hbox(a);
            row.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 2));
            LinearLayout t = Ui.vbox(a);
            boolean isActive = active != null && active.id.equals(r.id);
            t.addView(Ui.oneLine(Ui.text(a, r.label() + (isActive ? "   ● şu an" : ""), 15, isActive ? Ui.GOOD : Ui.TEXT, Ui.BOLD)));
            t.addView(Ui.oneLine(Ui.text(a, r.timeText(), 12.5f, Ui.TEXT2, Ui.REGULAR)));
            row.addView(t, Ui.lpw(1));
            Switch sw = new Switch(a);
            int[][] st = {{android.R.attr.state_checked}, {}};
            sw.setThumbTintList(new ColorStateList(st, new int[]{0xFFFFFFFF, 0xFFBDBDC8}));
            sw.setTrackTintList(new ColorStateList(st, new int[]{Ui.ACCENT, 0x44FFFFFF}));
            sw.setChecked(r.on);
            sw.setOnCheckedChangeListener((b, on) -> {
                Routines.setOn(a, r.id, on);
                e.refresh();
                refreshRoutines();
            });
            row.addView(sw);
            TextView del = Ui.text(a, "✕", 15, Ui.TEXT3, Ui.BOLD);
            del.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
            del.setOnClickListener(v -> {
                Routines.remove(a, r.id);
                e.refresh();
                refreshRoutines();
            });
            row.addView(del);
            routineList.addView(row, full());
        }
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
        refreshRoutines();
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
        int nb = e.store.badTransitionCount();
        badInfo.setText(nb == 0 ? "Henüz yok. Şimdi Çalıyor ekranında bir geçiş kötü gelirse \"👎 Kötü geçiş\"e bas."
                : nb + " kayıt: bu tarz ve sanatçı geçişlerinden kaçınılıyor.");
        Updater.Release up = Updater.available;
        updCard.setVisibility(up != null ? View.VISIBLE : View.GONE);
        if (up != null) updText.setText("✨ Yeni sürüm hazır: " + up.tag);
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
        boolean spOn = e.spotify.connected();
        String sst = !spOn ? "Bağlı değil" : e.spotify.playerBlocked() ? "Bağlı · Premium yok" : "✓ Bağlı"
                + (e.spotify.userName() != null && !e.spotify.userName().isEmpty() ? " · " + e.spotify.userName() : "");
        if (!sst.contentEquals(spStatus.getText())) {
            int col = !spOn ? Ui.TEXT3 : e.spotify.playerBlocked() ? Ui.WARN : Ui.GOOD;
            spStatus.setText(sst);
            spStatus.setTextColor(col);
            spStatus.setBackground(Ui.round(a, (col & 0x00FFFFFF) | 0x22000000, 12));
        }
        spRemove.setVisibility(spOn ? View.VISIBLE : View.GONE);
        int s = e.getSilentState();
        if (e.isSpotify()) methodText.setText(e.spotify.connected() && !e.spotify.playerBlocked()
                ? "Spotify ✓  Sıradaki şarkı Spotify'ın kendi sırasına ekleniyor."
                : "Spotify uygulamasına çal komutu gönderilerek geçiliyor. Hesabını bağlarsan daha yumuşak olur.");
        else methodText.setText(s == Engine.SILENT_YES
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
