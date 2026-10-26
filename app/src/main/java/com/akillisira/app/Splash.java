package com.akillisira.app;

import android.app.Activity;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Açılış ekranı. Sistemin açılışta gösterdiği "a" ile aynı boyut ve konumdan başlar
 * (geçiş fark edilmez), sonra neon çerçeve yanar, logo yukarı kayar ve "Ahenk" yazısı belirir.
 * Dokununca hemen kapanır.
 */
final class Splash {
    private Splash() {}

    static final int PINK = 0xFFFF2E63, VIOLET = 0xFF8B3DFF, BLUE = 0xFF2F6BFF, CYAN = 0xFF2BD9FF;

    private static final long HOLD_MS = 1650;

    static void show(Activity a) {
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        FrameLayout ov = new FrameLayout(a);
        ov.setBackgroundColor(Ui.BG);
        ov.setClickable(true);
        ov.setContentDescription("Ahenk");

        // Logo: önce sadece glif (sistem açılışıyla birebir), üstüne çerçeveli hali yanar
        FrameLayout logo = new FrameLayout(a);
        ImageView mark = new ImageView(a);
        mark.setImageResource(R.drawable.splash_logo);
        ImageView frame = new ImageView(a);
        frame.setImageResource(R.drawable.splash_frame);
        logo.addView(frame, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        logo.addView(mark, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        ov.addView(logo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));

        TextView word = Ui.text(a, "Ahenk", 46, 0xFFFFFFFF, Ui.BLACK);
        word.setLetterSpacing(0.01f);
        word.setGravity(Gravity.CENTER);
        word.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            word.getPaint().setShader(new LinearGradient(0, 0, r - l, 0,
                    new int[]{PINK, VIOLET, BLUE, CYAN}, new float[]{0.15f, 0.45f, 0.7f, 0.95f}, Shader.TileMode.CLAMP));
            word.invalidate();
        });
        ov.addView(word, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        TextView tag = Ui.text(a, "Şarkıdan şarkıya, ahenkle.", 15, Ui.TEXT2, Ui.REGULAR);
        tag.setLetterSpacing(0.02f);
        ov.addView(tag, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        decor.addView(ov, new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));

        // Başlangıç durumu
        float rise = Ui.dp(a, 80);   // çerçevenin altıyla yazı arasında ~24dp boşluk kalsın
        frame.setAlpha(0f);
        frame.setScaleX(0.9f);
        frame.setScaleY(0.9f);
        word.setAlpha(0f);
        word.setTranslationY(rise + Ui.dp(a, 20));
        tag.setAlpha(0f);
        tag.setTranslationY(rise + Ui.dp(a, 66));

        PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f);
        frame.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(560)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        mark.animate().alpha(0f).setStartDelay(420).setDuration(380).start();
        logo.animate().translationY(-rise).setStartDelay(520).setDuration(650).setInterpolator(ease).start();
        word.animate().alpha(1f).translationY(rise).setStartDelay(700).setDuration(560).setInterpolator(ease).start();
        tag.animate().alpha(1f).translationY(rise + Ui.dp(a, 50)).setStartDelay(860).setDuration(520)
                .setInterpolator(ease).start();

        final boolean[] gone = {false};
        Runnable dismiss = () -> {
            if (gone[0]) return;
            gone[0] = true;
            ov.setClickable(false);
            ov.animate().cancel();
            ov.animate().alpha(0f).setStartDelay(0).setDuration(320).withEndAction(() -> {
                ov.setVisibility(View.GONE);
                decor.removeView(ov);
            }).start();
        };
        ov.setOnClickListener(v -> dismiss.run());
        ov.postDelayed(dismiss, HOLD_MS);
    }
}
