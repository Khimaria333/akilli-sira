package com.akillisira.app;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Albüm kapağından canlı, baskın rengi çıkarır (arka plan için). */
public final class Palette {
    private Palette() {}

    /** Bulamazsa fallback döner. */
    public static int vibrant(Bitmap bmp, int fallback) {
        if (bmp == null) return fallback;
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(bmp, 24, 24, true);
        } catch (Exception e) {
            return fallback;
        }
        // Renk tonlarını 12 kovaya ayır, doygunluk ve parlaklığa göre ağırlıklandır
        double[] weight = new double[12];
        double[] sumH = new double[12], sumS = new double[12], sumV = new double[12];
        float[] hsv = new float[3];
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                int c = small.getPixel(x, y);
                Color.colorToHSV(c, hsv);
                if (hsv[2] < 0.15f) continue;       // çok koyu
                double w = hsv[1] * hsv[1] * (0.4 + hsv[2]);
                if (w < 0.02) continue;             // gri
                int b = Math.min(11, (int) (hsv[0] / 30f));
                weight[b] += w;
                sumH[b] += hsv[0] * w;
                sumS[b] += hsv[1] * w;
                sumV[b] += hsv[2] * w;
            }
        }
        if (small != bmp) small.recycle();
        int best = -1;
        for (int i = 0; i < 12; i++) if (best < 0 || weight[i] > weight[best]) best = i;
        if (best < 0 || weight[best] < 0.5) return fallback;
        float h = (float) (sumH[best] / weight[best]);
        float s = (float) Math.min(1, sumS[best] / weight[best] * 1.1);
        float v = (float) Math.min(1, Math.max(0.55, sumV[best] / weight[best]));
        return Color.HSVToColor(new float[]{h, s, v});
    }

    /** Arka plan için koyulaştırılmış hali (yazılar okunabilsin). */
    public static int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(hsv[2], 1f) * factor;
        hsv[1] = Math.min(1f, hsv[1] * 0.9f);
        return Color.HSVToColor(hsv);
    }

    /** İki renk arası geçiş. */
    public static int mix(int a, int b, float t) {
        float u = 1 - t;
        return Color.argb(
                Math.round(Color.alpha(a) * u + Color.alpha(b) * t),
                Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t),
                Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }
}
