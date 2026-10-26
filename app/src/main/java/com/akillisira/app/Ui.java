package com.akillisira.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Uygulamanın tasarım dili: renkler, yazı tipleri ve hazır bileşenler. */
public final class Ui {
    private Ui() {}

    // ---- Renkler ----
    public static final int BG = 0xFF0B0B10;
    public static final int SURFACE = 0xFF15151D;
    public static final int SURFACE2 = 0xFF1F1F2A;
    public static final int STROKE = 0x22FFFFFF;
    public static final int TEXT = 0xFFF4F4F8;
    public static final int TEXT2 = 0xFFA3A3B5;
    public static final int TEXT3 = 0xFF6E6E82;
    public static final int ACCENT = 0xFFFF4D6D;
    public static final int ACCENT2 = 0xFF7B5CFF;
    public static final int GOOD = 0xFF3DDC97;
    public static final int WARN = 0xFFFFB347;

    // ---- Yazı tipleri ----
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    public static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    // ---- Arka planlar ----

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable stroke(Context c, int fill, int strokeColor, float radiusDp, float strokeDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static GradientDrawable gradient(Context c, int c1, int c2, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Drawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, round(c, Color.WHITE, radiusDp));
    }

    // ---- Yazılar ----

    public static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(true);
        return t;
    }

    public static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.forLanguageTag("tr")), 11.5f, TEXT3, BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(c, 4), dp(c, 26), 0, dp(c, 10));
        return t;
    }

    // ---- Yerleşim ----

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    public static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    public static View space(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(c, dpH)));
        return v;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(stroke(c, SURFACE, STROKE, 22, 1));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    // ---- Düğmeler ----

    public static TextView primaryButton(Context c, String label) {
        TextView b = text(c, label, 15.5f, Color.WHITE, BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(c, gradient(c, ACCENT, ACCENT2, 28), 28));
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setClickable(true);
        b.setFocusable(true);
        b.setElevation(dp(c, 3));
        return b;
    }

    public static TextView secondaryButton(Context c, String label) {
        TextView b = text(c, label, 14.5f, TEXT, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(c, stroke(c, SURFACE2, STROKE, 26, 1), 26));
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static void setEnabledLook(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.4f);
    }

    // ---- Çipler ----

    public static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 14, TEXT, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setClickable(true);
        setChip(c, t, selected);
        return t;
    }

    public static void setChip(Context c, TextView t, boolean selected) {
        Drawable bg = selected ? gradient(c, ACCENT, ACCENT2, 22) : stroke(c, SURFACE2, STROKE, 22, 1);
        t.setBackground(ripple(c, bg, 22));
        t.setTextColor(selected ? Color.WHITE : TEXT2);
    }

    /** Küçük bilgi etiketi: "142 BPM", "Türkçe" gibi. */
    public static TextView pill(Context c, String label, int color) {
        TextView t = text(c, label, 12, color, MEDIUM);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(round(c, (color & 0x00FFFFFF) | 0x22000000, 12));
        return t;
    }

    public static HorizontalScrollView hscroll(Context c, View content) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.addView(content);
        return h;
    }

    // ---- Segmentli seçici ----

    public static final class Segmented {
        public final LinearLayout view;
        private final List<TextView> items = new ArrayList<>();
        private final Context c;
        private int selected = -1;

        Segmented(Context c, String[] labels, IntConsumer onSelect) {
            this.c = c;
            view = hbox(c);
            view.setBackground(round(c, SURFACE2, 16));
            int p = dp(c, 4);
            view.setPadding(p, p, p, p);
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                TextView t = text(c, labels[i], 13.5f, TEXT2, MEDIUM);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
                t.setClickable(true);
                t.setOnClickListener(v -> {
                    select(idx);
                    onSelect.accept(idx);
                });
                view.addView(t, lpw(1));
                items.add(t);
            }
        }

        public void select(int idx) {
            if (idx == selected) return;
            selected = idx;
            for (int i = 0; i < items.size(); i++) {
                TextView t = items.get(i);
                boolean on = i == idx;
                t.setBackground(on ? gradient(c, ACCENT, ACCENT2, 12) : null);
                t.setTextColor(on ? Color.WHITE : TEXT2);
                t.setTypeface(on ? BOLD : MEDIUM);
            }
        }
    }

    public static Segmented segmented(Context c, String[] labels, int selected, IntConsumer onSelect) {
        Segmented s = new Segmented(c, labels, onSelect);
        s.select(selected);
        return s;
    }

    // ---- Kapak resmi ----

    public static ImageView cover(Context c, float radiusDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(gradient(c, 0xFF2A2A3A, 0xFF1A1A24, radiusDp));
        final float r = dp(c, radiusDp);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        iv.setClipToOutline(true);
        return iv;
    }

    /** "Neden bu şarkı?" paneli: puanın kategorilere göre dökümü. */
    public static void showWhy(android.app.Activity a, Track t) {
        if (t == null) return;
        LinearLayout box = vbox(a);
        int p = dp(a, 20);
        box.setPadding(p, dp(a, 8), p, dp(a, 4));
        TextView head = oneLine(text(a, t.artist + " – " + t.title, 14, TEXT2, MEDIUM));
        head.setPadding(0, 0, 0, dp(a, 12));
        box.addView(head);
        if (t.breakdown == null || t.breakdown.isEmpty()) {
            box.addView(text(a, "Bu şarkı için ayrıntılı döküm yok.", 14, TEXT2, REGULAR));
        } else {
            double max = 1;
            for (double v : t.breakdown.values()) max = Math.max(max, Math.abs(v));
            for (java.util.Map.Entry<String, Double> en : t.breakdown.entrySet()) {
                double v = en.getValue();
                LinearLayout row = hbox(a);
                row.addView(text(a, en.getKey(), 13.5f, TEXT, MEDIUM), lp(dp(a, 110), WRAP));
                LinearLayout bar = hbox(a);
                android.view.View fill = new android.view.View(a);
                fill.setBackground(v >= 0 ? gradient(a, ACCENT, ACCENT2, 4) : round(a, WARN, 4));
                float f = (float) Math.max(0.03, Math.abs(v) / max);
                bar.addView(fill, new LinearLayout.LayoutParams(0, dp(a, 8), f));
                bar.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, dp(a, 8), Math.max(0.0001f, 1 - f)));
                row.addView(bar, lpw(1));
                TextView num = text(a, (v >= 0 ? "+" : "") + Math.round(v), 13, v >= 0 ? TEXT : WARN, BOLD);
                num.setGravity(Gravity.END);
                row.addView(num, lp(dp(a, 44), WRAP));
                box.addView(row, margins(lp(MATCH, WRAP), a, 0, 0, 0, 10));
            }
            TextView total = text(a, "Toplam puan: " + Math.round(t.score), 14, TEXT, BOLD);
            total.setPadding(0, dp(a, 6), 0, dp(a, 4));
            box.addView(total);
            TextView why = text(a, t.reason, 12.5f, ACCENT, MEDIUM);
            why.setLineSpacing(0, 1.12f);
            box.addView(why);
        }
        new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Neden bu şarkı?")
                .setView(box)
                .setPositiveButton("Tamam", null)
                .show();
    }

    /** Şarkı satırı: kapak + ad + sanatçı + açıklama. */
    public static LinearLayout songRow(Context c, Track t, String sub, int index) {
        LinearLayout row = hbox(c);
        row.setPadding(dp(c, 10), dp(c, 10), dp(c, 14), dp(c, 10));
        row.setBackground(ripple(c, round(c, SURFACE, 18), 18));
        row.setClickable(true);

        if (index > 0) {
            TextView n = text(c, String.valueOf(index), 13, TEXT3, BOLD);
            n.setGravity(Gravity.CENTER);
            row.addView(n, lp(dp(c, 24), WRAP));
        }
        ImageView cv = cover(c, 12);
        row.addView(cv, margins(lp(dp(c, 52), dp(c, 52)), c, 0, 0, 12, 0));
        ImageLoader.load(t.coverUrl, cv);

        LinearLayout texts = vbox(c);
        texts.addView(oneLine(text(c, t.title, 15, TEXT, BOLD)));
        texts.addView(oneLine(text(c, t.artist, 13, TEXT2, REGULAR)));
        if (sub != null && !sub.isEmpty()) {
            TextView s = oneLine(text(c, sub, 12, ACCENT, MEDIUM));
            s.setPadding(0, dp(c, 3), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, lpw(1));

        if (t.hasBpm()) {
            TextView b = text(c, Math.round(t.bpm) + "\nBPM", 11, TEXT3, BOLD);
            b.setGravity(Gravity.CENTER);
            row.addView(b, margins(lp(WRAP, WRAP), c, 8, 0, 0, 0));
        }
        return row;
    }
}
