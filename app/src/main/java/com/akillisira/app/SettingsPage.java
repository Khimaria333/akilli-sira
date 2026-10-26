package com.akillisira.app;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Ayarlar, izin durumu, hakkında ve geliştirici kaydı. */
public class SettingsPage extends Page {

    private Switch swAuto, swFade, swBack;
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
        body.addView(o, full());

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

    @Override
    public void onShow() {
        swAuto.setChecked(e.isAuto());
        swFade.setChecked(e.isFade());
        swBack.setChecked(e.isReturnToApp());
        langSeg.select(e.isTurkish() ? 0 : 1);
        modeSeg.select(e.getMode() + 1);
    }

    @Override
    public void render() {
        int s = e.getSilentState();
        methodText.setText(s == Engine.SILENT_YES
                ? "Sessiz ✓  YouTube Music arka planda kalarak şarkı değiştiriyor."
                : s == Engine.SILENT_NO
                ? "YouTube Music öne gelerek geçiyor. Telefonundaki YT Music sürümü sessiz geçişi desteklemiyor."
                : "İlk otomatik geçişte en iyi yöntem denenip öğrenilecek.");
        retrySilent.setVisibility(s == Engine.SILENT_NO ? View.VISIBLE : View.GONE);
        setPerm(permNotif, a.hasNotificationAccess());
        setPerm(permOverlay, a.canOverlay());
        setPerm(permBattery, a.batteryUnrestricted());
        if (logOpen) logText.setText(String.join("\n", e.log));
    }
}
