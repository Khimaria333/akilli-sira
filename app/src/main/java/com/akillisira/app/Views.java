package com.akillisira.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Elle çizilen küçük görsel bileşenler: ikonlar, ilerleme çizgisi, enerji göstergesi. */
public final class Views {
    private Views() {}

    // =====================================================================
    // İkon
    // =====================================================================
    public static final class Icon extends View {
        public static final int DISCOVER = 0, NOW = 1, SETTINGS = 2, CHECK = 3, NEXT = 4, BELL = 5,
                LAYERS = 6, BATTERY = 7, REFRESH = 8, MUSIC = 9, MIC = 10, HISTORY = 11, PLAY = 12, PAUSE = 13, PREV = 14;

        private int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();
        private int color;

        public Icon(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
        }

        public void setType(int t) {
            if (t != type) {
                type = t;
                invalidate();
            }
        }

        public void setColor(int color) {
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            float cx = w / 2, cy = h / 2, u = s / 24f;   // 24'lük ızgara
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(2f * u);
            path.reset();
            switch (type) {
                case DISCOVER: { // dört köşeli parıltı + küçük yıldız
                    p.setStyle(Paint.Style.FILL);
                    star(cx - 1.5f * u, cy + 1.5f * u, 8f * u, 2.2f * u);
                    cv.drawPath(path, p);
                    path.reset();
                    star(cx + 6f * u, cy - 6f * u, 3.5f * u, 1f * u);
                    cv.drawPath(path, p);
                    break;
                }
                case NOW: { // ekolayzer çubukları
                    p.setStyle(Paint.Style.FILL);
                    float[] hs = {8, 14, 10, 16, 6};
                    float bw = 2.4f * u, gap = 1.6f * u;
                    float total = hs.length * bw + (hs.length - 1) * gap;
                    float x = cx - total / 2;
                    for (float hh : hs) {
                        r.set(x, cy + 8 * u - hh * u, x + bw, cy + 8 * u);
                        cv.drawRoundRect(r, bw / 2, bw / 2, p);
                        x += bw + gap;
                    }
                    break;
                }
                case SETTINGS: { // dişli
                    p.setStyle(Paint.Style.STROKE);
                    cv.drawCircle(cx, cy, 3f * u, p);
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 8; i++) {
                        cv.save();
                        cv.rotate(i * 45, cx, cy);
                        r.set(cx - 1.6f * u, cy - 10f * u, cx + 1.6f * u, cy - 6f * u);
                        cv.drawRoundRect(r, u, u, p);
                        cv.restore();
                    }
                    p.setStyle(Paint.Style.STROKE);
                    cv.drawCircle(cx, cy, 6.8f * u, p);
                    break;
                }
                case CHECK: {
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(cx, cy, 11f * u, p);
                    p.setColor(0xFF0B0B10);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.4f * u);
                    path.moveTo(cx - 5 * u, cy);
                    path.lineTo(cx - 1.5f * u, cy + 3.5f * u);
                    path.lineTo(cx + 5.5f * u, cy - 4 * u);
                    cv.drawPath(path, p);
                    break;
                }
                case NEXT: { // sonraki
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(cx - 7 * u, cy - 7 * u);
                    path.lineTo(cx + 4 * u, cy);
                    path.lineTo(cx - 7 * u, cy + 7 * u);
                    path.close();
                    cv.drawPath(path, p);
                    r.set(cx + 4.5f * u, cy - 7 * u, cx + 7.5f * u, cy + 7 * u);
                    cv.drawRoundRect(r, u, u, p);
                    break;
                }
                case BELL: {
                    p.setStyle(Paint.Style.STROKE);
                    path.moveTo(cx - 7 * u, cy + 5 * u);
                    path.lineTo(cx + 7 * u, cy + 5 * u);
                    path.moveTo(cx - 5.5f * u, cy + 5 * u);
                    path.lineTo(cx - 5.5f * u, cy - 1 * u);
                    path.quadTo(cx - 5.5f * u, cy - 8 * u, cx, cy - 8 * u);
                    path.quadTo(cx + 5.5f * u, cy - 8 * u, cx + 5.5f * u, cy - 1 * u);
                    path.lineTo(cx + 5.5f * u, cy + 5 * u);
                    cv.drawPath(path, p);
                    cv.drawLine(cx - 2 * u, cy + 8.5f * u, cx + 2 * u, cy + 8.5f * u, p);
                    break;
                }
                case LAYERS: {
                    p.setStyle(Paint.Style.STROKE);
                    r.set(cx - 8 * u, cy - 7 * u, cx + 4 * u, cy + 3 * u);
                    cv.drawRoundRect(r, 2 * u, 2 * u, p);
                    r.set(cx - 4 * u, cy - 3 * u, cx + 8 * u, cy + 7 * u);
                    p.setStyle(Paint.Style.FILL);
                    cv.drawRoundRect(r, 2 * u, 2 * u, p);
                    break;
                }
                case BATTERY: {
                    p.setStyle(Paint.Style.STROKE);
                    r.set(cx - 9 * u, cy - 5 * u, cx + 7 * u, cy + 5 * u);
                    cv.drawRoundRect(r, 2 * u, 2 * u, p);
                    cv.drawLine(cx + 9.5f * u, cy - 2 * u, cx + 9.5f * u, cy + 2 * u, p);
                    p.setStyle(Paint.Style.FILL);
                    r.set(cx - 6.5f * u, cy - 2.5f * u, cx + 2 * u, cy + 2.5f * u);
                    cv.drawRoundRect(r, u, u, p);
                    break;
                }
                case REFRESH: {
                    p.setStyle(Paint.Style.STROKE);
                    r.set(cx - 7 * u, cy - 7 * u, cx + 7 * u, cy + 7 * u);
                    cv.drawArc(r, -60, 300, false, p);
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(cx + 3.5f * u, cy - 10.5f * u);
                    path.lineTo(cx + 8.5f * u, cy - 6.5f * u);
                    path.lineTo(cx + 2.5f * u, cy - 4 * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                }
                case MUSIC: {
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(cx - 4 * u, cy + 5 * u, 3 * u, p);
                    cv.drawCircle(cx + 6 * u, cy + 3 * u, 3 * u, p);
                    p.setStyle(Paint.Style.STROKE);
                    path.moveTo(cx - 1 * u, cy + 5 * u);
                    path.lineTo(cx - 1 * u, cy - 8 * u);
                    path.lineTo(cx + 9 * u, cy - 10 * u);
                    path.lineTo(cx + 9 * u, cy + 3 * u);
                    cv.drawPath(path, p);
                    break;
                }
                case HISTORY: { // saat
                    p.setStyle(Paint.Style.STROKE);
                    cv.drawCircle(cx, cy, 9 * u, p);
                    cv.drawLine(cx, cy, cx, cy - 5.5f * u, p);
                    cv.drawLine(cx, cy, cx + 4 * u, cy + 2.5f * u, p);
                    break;
                }
                case PLAY: {
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(cx - 5 * u, cy - 8 * u);
                    path.lineTo(cx + 8 * u, cy);
                    path.lineTo(cx - 5 * u, cy + 8 * u);
                    path.close();
                    cv.drawPath(path, p);
                    break;
                }
                case PAUSE: {
                    p.setStyle(Paint.Style.FILL);
                    r.set(cx - 7 * u, cy - 8 * u, cx - 2 * u, cy + 8 * u);
                    cv.drawRoundRect(r, 1.5f * u, 1.5f * u, p);
                    r.set(cx + 2 * u, cy - 8 * u, cx + 7 * u, cy + 8 * u);
                    cv.drawRoundRect(r, 1.5f * u, 1.5f * u, p);
                    break;
                }
                case PREV: {
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(cx + 7 * u, cy - 7 * u);
                    path.lineTo(cx - 4 * u, cy);
                    path.lineTo(cx + 7 * u, cy + 7 * u);
                    path.close();
                    cv.drawPath(path, p);
                    r.set(cx - 7.5f * u, cy - 7 * u, cx - 4.5f * u, cy + 7 * u);
                    cv.drawRoundRect(r, u, u, p);
                    break;
                }
                case MIC: {
                    p.setStyle(Paint.Style.FILL);
                    r.set(cx - 3.5f * u, cy - 10 * u, cx + 3.5f * u, cy + 2 * u);
                    cv.drawRoundRect(r, 3.5f * u, 3.5f * u, p);
                    p.setStyle(Paint.Style.STROKE);
                    r.set(cx - 7 * u, cy - 9 * u, cx + 7 * u, cy + 5 * u);
                    cv.drawArc(r, 0, 180, false, p);
                    cv.drawLine(cx, cy + 5 * u, cx, cy + 9.5f * u, p);
                    cv.drawLine(cx - 4 * u, cy + 9.5f * u, cx + 4 * u, cy + 9.5f * u, p);
                    break;
                }
                default: break;
            }
        }

        private void star(float x, float y, float R, float r0) {
            path.moveTo(x, y - R);
            path.quadTo(x + r0 * 0.3f, y - r0 * 0.3f, x + R, y);
            path.quadTo(x + r0 * 0.3f, y + r0 * 0.3f, x, y + R);
            path.quadTo(x - r0 * 0.3f, y + r0 * 0.3f, x - R, y);
            path.quadTo(x - r0 * 0.3f, y - r0 * 0.3f, x, y - R);
            path.close();
        }
    }

    // =====================================================================
    // İlerleme çizgisi (degrade dolgu)
    // =====================================================================
    public static final class ProgressLine extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float value;

        public ProgressLine(Context c) {
            super(c);
            track.setColor(0x26FFFFFF);
        }

        public void setValue(float v) {
            float nv = Math.max(0, Math.min(1, v));
            if (Math.abs(nv - value) > 0.0005f) {
                value = nv;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            // Görünen çizgi 6dp; yükseklik dokunma alanı için daha büyük olabilir
            float w = getWidth(), full = getHeight();
            float h = Math.min(full, 6 * getResources().getDisplayMetrics().density);
            float top = (full - h) / 2, rad = h / 2;
            r.set(0, top, w, top + h);
            cv.drawRoundRect(r, rad, rad, track);
            if (value <= 0) return;
            fill.setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT, Ui.ACCENT2, Shader.TileMode.CLAMP));
            float end = Math.max(h, w * value);
            r.set(0, top, end, top + h);
            cv.drawRoundRect(r, rad, rad, fill);
            // tutamaç
            cv.drawCircle(Math.min(w - h, Math.max(h, end)), top + h / 2, h * 1.1f, fill);
        }
    }

    // =====================================================================
    // Enerji eğrisi grafiği (DJ modu)
    // =====================================================================
    public static final class Curve extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private double[] values = new double[0];

        public Curve(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            dot.setColor(0xFFFFFFFF);
        }

        public void setValues(double[] v) {
            values = v != null ? v : new double[0];
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int n = values.length;
            if (n < 2) return;
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight(), pad = 6 * d;
            double lo = 1, hi = 0;
            for (double v : values) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (hi - lo < 0.05) { lo -= 0.1; hi += 0.1; }
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pad + (w - 2 * pad) * i / (n - 1);
                ys[i] = (float) (h - pad - (h - 2 * pad) * (values[i] - lo) / (hi - lo));
            }
            path.reset();
            path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                float mx = (xs[i - 1] + xs[i]) / 2;
                path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i]);
            }
            Path fill = new Path(path);
            fill.lineTo(xs[n - 1], h);
            fill.lineTo(xs[0], h);
            fill.close();
            area.setShader(new LinearGradient(0, 0, 0, h, 0x66FF4D6D, 0x007B5CFF, Shader.TileMode.CLAMP));
            cv.drawPath(fill, area);
            line.setStrokeWidth(3 * d);
            line.setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT, Ui.ACCENT2, Shader.TileMode.CLAMP));
            cv.drawPath(path, line);
            for (int i = 0; i < n; i++) cv.drawCircle(xs[i], ys[i], 2.5f * d, dot);
        }
    }

    // =====================================================================
    // Enerji göstergesi (çubuklar)
    // =====================================================================
    public static final class Meter extends View {
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float value = -1;
        private static final int BARS = 8;

        public Meter(Context c) {
            super(c);
            off.setColor(0x26FFFFFF);
        }

        /** 0..1, bilinmiyorsa -1 */
        public void setValue(double v) {
            float nv = (float) v;
            if (nv != value) {
                value = nv;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float gap = w * 0.04f;
            float bw = (w - gap * (BARS - 1)) / BARS;
            on.setShader(new LinearGradient(0, 0, w, 0, Ui.GOOD, Ui.ACCENT, Shader.TileMode.CLAMP));
            int lit = value < 0 ? 0 : Math.max(1, Math.round(value * BARS));
            for (int i = 0; i < BARS; i++) {
                float bh = h * (0.35f + 0.65f * (i + 1) / BARS);
                float x = i * (bw + gap);
                r.set(x, h - bh, x + bw, h);
                cv.drawRoundRect(r, bw / 2, bw / 2, i < lit ? on : off);
            }
        }
    }

    /**
     * Dinleme haritası: 7 gün (Pzt–Paz) × 12 iki saatlik dilim. Koyu = hiç, parlak = çok.
     */
    public static final class HeatMap extends View {
        private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[][] data = new int[7][12];
        private int max = 1;
        static final String[] DAYS = {"Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"};

        public HeatMap(Context c) {
            super(c);
            txt.setColor(Ui.TEXT3);
            txt.setTypeface(Ui.MEDIUM);
        }

        public void setData(int[][] d) {
            data = d;
            max = 1;
            for (int[] r : d) for (int v : r) max = Math.max(max, v);
            invalidate();
        }

        /** Son 'days' günün çalmalarından harita. */
        public static int[][] compute(java.util.List<Store.Play> plays, int days) {
            int[][] d = new int[7][12];
            long since = System.currentTimeMillis() - days * 86400000L;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (Store.Play p : plays) {
                if (p.ts < since) continue;
                cal.setTimeInMillis(p.ts);
                int dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;   // Pzt = 0
                d[dow][cal.get(java.util.Calendar.HOUR_OF_DAY) / 2]++;
            }
            return d;
        }

        /** "Cuma 20–22" gibi en yoğun dilim (yoksa null). */
        public static String peak(int[][] d) {
            int bd = -1, bh = -1, bv = 0;
            for (int i = 0; i < 7; i++) for (int j = 0; j < 12; j++) if (d[i][j] > bv) { bv = d[i][j]; bd = i; bh = j; }
            if (bd < 0) return null;
            String[] full = {"Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"};
            return full[bd] + " " + String.format(java.util.Locale.ROOT, "%02d–%02d", bh * 2, (bh * 2 + 2) % 24);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float dd = getResources().getDisplayMetrics().density;
            float left = 26 * dd, top = 16 * dd, gap = 3 * dd;
            float cw = (getWidth() - left - gap * 11) / 12f;
            float ch = (getHeight() - top - gap * 6) / 7f;
            txt.setTextSize(10.5f * dd);
            for (int j = 0; j < 12; j += 3) {
                cv.drawText(String.format(java.util.Locale.ROOT, "%02d", j * 2), left + j * (cw + gap), 11 * dd, txt);
            }
            for (int i = 0; i < 7; i++) {
                float y = top + i * (ch + gap);
                cv.drawText(DAYS[i], 0, y + ch * 0.72f, txt);
                for (int j = 0; j < 12; j++) {
                    float x = left + j * (cw + gap);
                    int v = data[i][j];
                    if (v == 0) cell.setColor(Ui.SURFACE2);
                    else {
                        float f = (float) Math.sqrt(v / (double) max);
                        cell.setColor(Palette.mix(Palette.mix(Ui.SURFACE2, Ui.ACCENT2, 0.35f), Ui.ACCENT, f));
                    }
                    cv.drawRoundRect(x, y, x + cw, y + ch, 4 * dd, 4 * dd, cell);
                }
            }
        }
    }
}
