package com.akillisira.app;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** İlk kullanım turu: 5 kısa sayfada uygulamanın ne yaptığı. */
public class IntroPage extends Page {

    private static final String[][] SLIDES = {
        {"🎧", "Sıradakini ben seçerim",
            "YouTube Music'te bir şarkı aç. Tarzına, temposuna ve ruh haline en uygun şarkıyı bulur, "
                + "şarkı bitince sesi yumuşakça kısıp sıradakine geçerim."},
        {"🎯", "Önce tarz gelir",
            "Benzer sanatçılar, aynı tür, aynı dönem; tempo ve enerji ince ayar. Rapten fasıla saçma geçişler yok. "
                + "Last.fm bağlarsan daha da isabetli seçerim."},
        {"✍️", "Ruh halini anlat",
            "\"Akşamüstü sahilde sakin bir şeyler\" yaz ya da söyle. Ya da ruh hali, tür seç; "
                + "listen saniyeler içinde hazır olsun."},
        {"❤️", "Beğen, 👎 ile öğret",
            "Beğendiklerinden tarz profilin çıkar. Beğenmediğin şarkılar ve kötü bulduğun geçişler bir daha gelmez. "
                + "Sen işaretlemedikçe hiçbir şey öğrenmem."},
        {"🔔", "Her yerden kontrol",
            "Kilit ekranı bildirimi, ana ekran widget'ı, hızlı ayarlar, uyku zamanlayıcı, "
                + "otomatik ruh hali planı ve müzikle uyan alarmı."},
    };

    private int index;
    private TextView emoji, title, text, next;
    private LinearLayout dots, slide;

    public IntroPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        body.setPadding(Ui.dp(a, 24), Ui.dp(a, 20), Ui.dp(a, 24), Ui.dp(a, 28));
        body.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout top = Ui.hbox(a);
        top.addView(Ui.text(a, "Akıllı Sıra", 15, Ui.TEXT2, Ui.BOLD), Ui.lpw(1));
        TextView skip = Ui.text(a, "Atla", 15, Ui.TEXT2, Ui.BOLD);
        skip.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
        skip.setOnClickListener(v -> a.finishIntro());
        top.addView(skip);
        body.addView(top, full());

        slide = Ui.vbox(a);
        slide.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout circle = Ui.vbox(a);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 90));
        circle.setElevation(Ui.dp(a, 6));
        emoji = Ui.text(a, "", 64, 0xFFFFFFFF, Ui.REGULAR);
        circle.addView(emoji);
        slide.addView(circle, Ui.margins(Ui.lp(Ui.dp(a, 180), Ui.dp(a, 180)), a, 0, 60, 0, 40));
        title = Ui.text(a, "", 28, Ui.TEXT, Ui.BLACK);
        title.setGravity(Gravity.CENTER);
        slide.addView(title, full());
        text = Ui.text(a, "", 16, Ui.TEXT2, Ui.REGULAR);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.25f);
        text.setPadding(Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6), 0);
        slide.addView(text, full());
        body.addView(slide, full());

        View spacer = new View(a);
        body.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        dots = Ui.hbox(a);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < SLIDES.length; i++) {
            View d = new View(a);
            dots.addView(d, Ui.margins(Ui.lp(Ui.dp(a, 8), Ui.dp(a, 8)), a, 4, 0, 4, 0));
        }
        body.addView(dots, Ui.margins(full(), a, 0, 30, 0, 18));

        next = Ui.primaryButton(a, "");
        next.setOnClickListener(v -> {
            if (index < SLIDES.length - 1) show(index + 1);
            else a.finishIntro();
        });
        body.addView(next, full());

        // kaydırarak da geçilebilsin
        scroll.setOnTouchListener(new View.OnTouchListener() {
            float downX;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent ev) {
                if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) downX = ev.getX();
                else if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx = ev.getX() - downX;
                    if (dx < -Ui.dp(a, 60) && index < SLIDES.length - 1) { show(index + 1); return true; }
                    if (dx > Ui.dp(a, 60) && index > 0) { show(index - 1); return true; }
                }
                return false;
            }
        });
        show(0);
    }

    private void show(int i) {
        index = i;
        emoji.setText(SLIDES[i][0]);
        title.setText(SLIDES[i][1]);
        text.setText(SLIDES[i][2]);
        next.setText(i == SLIDES.length - 1 ? "Başlayalım  →" : "İleri");
        for (int k = 0; k < dots.getChildCount(); k++) {
            View d = dots.getChildAt(k);
            d.setBackground(k == i ? Ui.gradient(a, Ui.ACCENT, Ui.ACCENT2, 4) : Ui.round(a, 0x33FFFFFF, 4));
            d.getLayoutParams().width = Ui.dp(a, k == i ? 22 : 8);
            d.requestLayout();
        }
        slide.setAlpha(0f);
        slide.setTranslationX(Ui.dp(a, 16));
        slide.animate().alpha(1f).translationX(0).setDuration(240).start();
    }

    @Override
    public void render() {}
}
