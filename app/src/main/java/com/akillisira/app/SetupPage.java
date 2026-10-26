package com.akillisira.app;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** İlk açılışta izinleri adım adım aldıran ekran. */
public class SetupPage extends Page {

    private Step notif, overlay, battery, notifs;
    private TextView startBtn;

    public SetupPage(MainActivity a, Engine e) {
        super(a, e);
    }

    private final class Step {
        final LinearLayout card;
        final Views.Icon done;
        final TextView action;
        final TextView number;

        Step(int n, int icon, String title, String desc, String actionLabel, Runnable onAction) {
            card = Ui.hbox(a);
            card.setBackground(Ui.stroke(a, Ui.SURFACE, Ui.STROKE, 22, 1));
            int p = Ui.dp(a, 16);
            card.setPadding(p, p, p, p);
            card.setGravity(Gravity.TOP);

            LinearLayout iconBox = Ui.vbox(a);
            iconBox.setGravity(Gravity.CENTER);
            iconBox.setBackground(Ui.gradient(a, 0x33FF4D6D, 0x337B5CFF, 14));
            iconBox.addView(new Views.Icon(a, icon, Ui.TEXT), Ui.lp(Ui.dp(a, 24), Ui.dp(a, 24)));
            card.addView(iconBox, Ui.margins(Ui.lp(Ui.dp(a, 46), Ui.dp(a, 46)), a, 0, 0, 14, 0));

            LinearLayout texts = Ui.vbox(a);
            number = Ui.text(a, n + ". ADIM", 11, Ui.ACCENT, Ui.BOLD);
            number.setLetterSpacing(0.1f);
            texts.addView(number);
            texts.addView(Ui.text(a, title, 16, Ui.TEXT, Ui.BOLD));
            TextView d = Ui.text(a, desc, 13, Ui.TEXT2, Ui.REGULAR);
            d.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
            d.setLineSpacing(0, 1.15f);
            texts.addView(d);
            action = Ui.secondaryButton(a, actionLabel);
            action.setOnClickListener(v -> onAction.run());
            texts.addView(action, Ui.lp(Ui.WRAP, Ui.WRAP));
            card.addView(texts, Ui.lpw(1));

            done = new Views.Icon(a, Views.Icon.CHECK, Ui.GOOD);
            card.addView(done, Ui.margins(Ui.lp(Ui.dp(a, 26), Ui.dp(a, 26)), a, 8, 0, 0, 0));
        }

        void set(boolean ok) {
            done.setVisibility(ok ? View.VISIBLE : View.INVISIBLE);
            action.setVisibility(ok ? View.GONE : View.VISIBLE);
            card.setAlpha(ok ? 0.75f : 1f);
        }
    }

    @Override
    protected void build() {
        body.setPadding(Ui.dp(a, 20), Ui.dp(a, 36), Ui.dp(a, 20), Ui.dp(a, 32));

        android.widget.ImageView logo = new android.widget.ImageView(a);
        logo.setImageResource(R.drawable.logo_mark);
        logo.setContentDescription("Ahenk");
        body.addView(logo, Ui.lp(Ui.dp(a, 76), Ui.dp(a, 76)));

        TextView h = Ui.text(a, "Ahenk'e\nhoş geldin", 30, Ui.TEXT, Ui.BLACK);
        h.setPadding(0, Ui.dp(a, 20), 0, Ui.dp(a, 8));
        h.setLineSpacing(0, 1.05f);
        body.addView(h);
        TextView sub = Ui.text(a, "YouTube Music'te çaldığın şarkının temposuna, enerjisine ve ruh haline göre "
                + "sıradakini seçer, şarkı bitince yumuşak bir geçişle çalar. Başlamak için iki izin gerekiyor.",
                14.5f, Ui.TEXT2, Ui.REGULAR);
        sub.setLineSpacing(0, 1.2f);
        body.addView(sub);
        body.addView(Ui.space(a, 24));

        notif = new Step(1, Views.Icon.BELL, "Bildirim erişimi",
                "Hangi şarkının çaldığını ve kaçıncı saniyede olduğunu görebilmek için. "
                        + "Anahtar gri ve basılamıyorsa aşağıdaki \"Kısıtlı ayarlar\" yolunu kullan.",
                "İzin ver", a::openNotificationAccess);
        body.addView(notif.card, full());

        TextView restricted = Ui.text(a, "Anahtar gri mi? Kısıtlı ayarlara izin ver →", 13, Ui.ACCENT, Ui.MEDIUM);
        restricted.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), 0, Ui.dp(a, 4));
        restricted.setOnClickListener(v -> {
            a.openAppDetails();
            a.toast("Sağ üstteki ⋮ menüden \"Kısıtlanmış ayarlara izin ver\"i seç");
        });
        body.addView(restricted);
        body.addView(Ui.space(a, 12));

        overlay = new Step(2, Views.Icon.LAYERS, "Diğer uygulamaların üzerinde göster",
                "Şarkı bitince, uygulama arka plandayken bile sıradaki şarkıyı YT Music'te açabilmek için.",
                "İzin ver", a::openOverlayPermission);
        body.addView(overlay.card, full());
        body.addView(Ui.space(a, 12));

        battery = new Step(3, Views.Icon.BATTERY, "Pil kısıtlamasını kaldır (önerilir)",
                "Telefon pil tasarrufu için uygulamayı kapatırsa geçişler kaçabilir.",
                "Kaldır", a::openBatterySettings);
        body.addView(battery.card, full());
        body.addView(Ui.space(a, 12));

        notifs = new Step(4, Views.Icon.BELL, "Bildirimlere izin ver (önerilir)",
                "Sıradaki şarkıyı kilit ekranında görmek ve uygulamayı açmadan geçmek için.",
                "İzin ver", a::requestNotifications);
        body.addView(notifs.card, full());
        body.addView(Ui.space(a, 28));

        startBtn = Ui.primaryButton(a, "Başla");
        startBtn.setOnClickListener(v -> a.finishSetup());
        body.addView(startBtn, full());
    }

    @Override
    public void render() {
        boolean n = a.hasNotificationAccess(), o = a.canOverlay();
        notif.set(n);
        overlay.set(o);
        battery.set(a.batteryUnrestricted());
        notifs.set(a.notificationsAllowed());
        Ui.setEnabledLook(startBtn, n && o);
        startBtn.setText(n && o ? "Başla" : "Önce 1. ve 2. adımı tamamla");
    }
}
