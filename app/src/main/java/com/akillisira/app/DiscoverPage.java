package com.akillisira.app;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Ruh hali + mekan + dil seç → sana özel şarkı listesi. */
public class DiscoverPage extends Page {

    private String moodId, placeId;
    private final List<LinearLayout> moodTiles = new ArrayList<>();
    private final List<TextView> placeChips = new ArrayList<>();
    private Ui.Segmented langSeg;
    private TextView goBtn, greeting;
    private LinearLayout results;
    private List<Track> renderedResults;
    private boolean renderedLoading;
    private String renderedProblem;
    private EditText input;
    private TextView preview;

    public DiscoverPage(MainActivity a, Engine e) {
        super(a, e);
        moodId = e.getMoodId();
        placeId = e.getPlaceId();
    }

    @Override
    protected void build() {
        greeting = Ui.text(a, "", 14, Ui.TEXT2, Ui.MEDIUM);
        body.addView(greeting);
        TextView h = Ui.text(a, "Bugün nasıl\nhissediyorsun?", 30, Ui.TEXT, Ui.BLACK);
        h.setLineSpacing(0, 1.05f);
        h.setPadding(0, Ui.dp(a, 4), 0, 0);
        body.addView(h);

        // ---- Kendi cümlenle anlat ----
        body.addView(Ui.section(a, "Kendi cümlenle anlat"));
        LinearLayout ask = Ui.card(a);
        input = new EditText(a);
        input.setBackground(Ui.round(a, Ui.SURFACE2, 16));
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.TEXT3);
        input.setHint("örn. akşamüstü sahilde sakin bir şeyler");
        input.setTextSize(15);
        input.setTypeface(Ui.REGULAR);
        int ip = Ui.dp(a, 14);
        input.setPadding(ip, ip, ip, ip);
        input.setMaxLines(3);
        input.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) { submitText(); return true; }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int st, int co, int af) {}
            @Override public void onTextChanged(CharSequence c, int st, int b, int co) {}
            @Override public void afterTextChanged(Editable ed) { updatePreview(); }
        });
        String prev = e.getVibe();
        if (prev != null) input.setText(prev);
        ask.addView(input, full());

        preview = Ui.text(a, "", 13, Ui.ACCENT, Ui.MEDIUM);
        preview.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        preview.setLineSpacing(0, 1.15f);
        ask.addView(preview);

        LinearLayout askRow = Ui.hbox(a);
        LinearLayout mic = Ui.vbox(a);
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        mic.setClickable(true);
        mic.addView(new Views.Icon(a, Views.Icon.MIC, Ui.TEXT), Ui.lp(Ui.dp(a, 24), Ui.dp(a, 24)));
        mic.setOnClickListener(v -> a.startSpeech());
        askRow.addView(mic, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 0, 0, 10, 0));
        TextView understand = Ui.primaryButton(a, "Anla ve hazırla");
        understand.setOnClickListener(v -> submitText());
        askRow.addView(understand, Ui.lpw(1));
        ask.addView(askRow, Ui.margins(full(), a, 0, 12, 0, 0));
        body.addView(ask, full());

        LinearLayout ex = Ui.hbox(a);
        String[][] examples = {
            {"🏖️ Akşamüstü sahil", "akşamüstü sahilde sakin bir şeyler"},
            {"🏋️ Sporda gaz", "sporda gaz şarkılar lazım"},
            {"🌧️ Yağmur ve hüzün", "yağmurlu bir gün, biraz hüzünlüyüm"},
            {"🍷 Akşam yemeği", "sevgilimle akşam yemeği"},
            {"🥂 Rakı masası", "rakı masası"},
            {"🚗 Gece yolculuğu", "gece arabada yolculuk"},
            {"📚 Ders", "ders çalışıyorum, odaklanmam lazım"},
        };
        for (String[] exm : examples) {
            TextView ch = Ui.chip(a, exm[0], false);
            ch.setTextSize(13);
            ch.setOnClickListener(v -> {
                input.setText(exm[1]);
                input.setSelection(exm[1].length());
                submitText();
            });
            ex.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
        }
        body.addView(Ui.hscroll(a, ex), Ui.margins(full(), a, 0, 12, 0, 0));

        // ---- Ruh hali kutucukları (2 sütun) ----
        body.addView(Ui.section(a, "Ya da ruh halini seç"));
        LinearLayout row = null;
        for (int i = 0; i < Moods.MOODS.length; i++) {
            Moods.Mood m = Moods.MOODS[i];
            if (i % 2 == 0) {
                row = Ui.hbox(a);
                body.addView(row, Ui.margins(full(), a, 0, 0, 0, 10));
            }
            LinearLayout tile = Ui.vbox(a);
            tile.setGravity(Gravity.BOTTOM | Gravity.START);
            int p = Ui.dp(a, 14);
            tile.setPadding(p, p, p, p);
            tile.setClickable(true);
            tile.setTag(m.id);
            TextView emo = Ui.text(a, m.emoji, 26, Ui.TEXT, Ui.REGULAR);
            tile.addView(emo);
            TextView lbl = Ui.text(a, m.label, 16, 0xFFFFFFFF, Ui.BOLD);
            lbl.setPadding(0, Ui.dp(a, 6), 0, 0);
            tile.addView(lbl);
            tile.setOnClickListener(v -> {
                moodId = m.id;
                paintMoods();
                updateGo();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 96), 1);
            if (i % 2 == 0) lp.setMargins(0, 0, Ui.dp(a, 5), 0);
            else lp.setMargins(Ui.dp(a, 5), 0, 0, 0);
            row.addView(tile, lp);
            moodTiles.add(tile);
        }

        // ---- Mekan ----
        body.addView(Ui.section(a, "Neredesin?"));
        LinearLayout chips = Ui.hbox(a);
        for (Moods.Place pl : Moods.PLACES) {
            TextView ch = Ui.chip(a, pl.emoji + "  " + pl.label, pl.id.equals(placeId));
            ch.setOnClickListener(v -> {
                placeId = pl.id.equals(placeId) ? null : pl.id;
                paintPlaces();
            });
            chips.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
            placeChips.add(ch);
        }
        body.addView(Ui.hscroll(a, chips), full());

        // ---- Dil ----
        body.addView(Ui.section(a, "Dil"));
        langSeg = Ui.segmented(a, new String[]{"🇹🇷  Türkçe", "🌍  Global"}, e.isTurkish() ? 0 : 1,
                i -> e.setTurkish(i == 0));
        body.addView(langSeg.view, full());
        TextView langHint = Ui.text(a, "Türkçe: sadece Türkçe şarkılar · Global: Türkçe ve yabancı karışık",
                12, Ui.TEXT3, Ui.REGULAR);
        langHint.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
        body.addView(langHint);

        body.addView(Ui.space(a, 22));
        goBtn = Ui.primaryButton(a, "✨  Şarkılarımı hazırla");
        goBtn.setOnClickListener(v -> {
            if (moodId == null) return;
            e.discover(moodId, placeId);
            scroll.postDelayed(() -> scroll.smoothScrollTo(0, results.getTop() - Ui.dp(a, 12)), 150);
        });
        body.addView(goBtn, full());

        results = Ui.vbox(a);
        body.addView(results, Ui.margins(full(), a, 0, 8, 0, 0));

        paintMoods();
        paintPlaces();
        updateGo();
        updatePreview();
    }

    private void updatePreview() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) {
            preview.setVisibility(View.GONE);
            return;
        }
        Vibe.Result r = Vibe.parse(t, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        preview.setVisibility(View.VISIBLE);
        preview.setText("Anladığım: " + r.summary() + (r.guessedMood ? "\n(ruh halini cümleden tahmin ettim)" : ""));
    }

    private void submitText() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) {
            a.toast("Bir şeyler yaz ya da mikrofona söyle");
            return;
        }
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        Vibe.Result r = e.discoverText(t);
        moodId = r.moodId;
        placeId = r.placeId;
        paintMoods();
        paintPlaces();
        updateGo();
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, results.getTop() - Ui.dp(a, 12)), 150);
    }

    /** Mikrofondan gelen cümle. */
    public void onSpeech(String text) {
        input.setText(text);
        input.setSelection(text.length());
        submitText();
    }

    private void paintMoods() {
        for (int i = 0; i < moodTiles.size(); i++) {
            Moods.Mood m = Moods.MOODS[i];
            LinearLayout t = moodTiles.get(i);
            boolean sel = m.id.equals(moodId);
            GradientDrawable g = Ui.gradient(a, m.color1, m.color2, 22);
            if (sel) g.setStroke(Ui.dp(a, 2.5f), 0xFFFFFFFF);
            t.setBackground(Ui.ripple(a, g, 22));
            t.setAlpha(moodId == null || sel ? 1f : 0.45f);
            t.animate().scaleX(sel ? 1.03f : 1f).scaleY(sel ? 1.03f : 1f).setDuration(150).start();
        }
    }

    private void paintPlaces() {
        for (int i = 0; i < placeChips.size(); i++) {
            Ui.setChip(a, placeChips.get(i), Moods.PLACES[i].id.equals(placeId));
        }
    }

    private void updateGo() {
        Ui.setEnabledLook(goBtn, moodId != null);
        goBtn.setText(moodId == null ? "Önce ruh halini seç" : "✨  Şarkılarımı hazırla");
    }

    @Override
    public void onShow() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? "Günaydın ☀️" : h < 18 && h >= 12 ? "İyi günler 👋"
                : h >= 18 && h < 23 ? "İyi akşamlar 🌆" : "İyi geceler 🌙");
        langSeg.select(e.isTurkish() ? 0 : 1);
    }

    @Override
    public void render() {
        if (renderedResults == e.discoverResults && renderedLoading == e.discoverLoading
                && eq(renderedProblem, e.discoverProblem)) return;
        renderedResults = e.discoverResults;
        renderedLoading = e.discoverLoading;
        renderedProblem = e.discoverProblem;
        results.removeAllViews();

        if (e.discoverLoading) {
            LinearLayout c = Ui.card(a);
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(a, 16), Ui.dp(a, 28), Ui.dp(a, 16), Ui.dp(a, 28));
            ProgressBar pb = new ProgressBar(a);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
            c.addView(pb, Ui.lp(Ui.dp(a, 40), Ui.dp(a, 40)));
            TextView t1 = Ui.text(a, "Ruh haline uygun şarkılar aranıyor…", 15, Ui.TEXT, Ui.BOLD);
            t1.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
            t1.setGravity(Gravity.CENTER);
            c.addView(t1);
            TextView t2 = Ui.text(a, "Tempo ve enerji analiz ediliyor", 13, Ui.TEXT2, Ui.REGULAR);
            t2.setGravity(Gravity.CENTER);
            c.addView(t2);
            results.addView(c, Ui.margins(full(), a, 0, 14, 0, 0));
            return;
        }
        if (e.discoverProblem != null) {
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, e.discoverProblem, 14, Ui.WARN, Ui.MEDIUM));
            results.addView(c, Ui.margins(full(), a, 0, 14, 0, 0));
            return;
        }
        List<Track> list = e.discoverResults;
        if (list.isEmpty()) return;

        results.addView(Ui.section(a, "Senin için hazır · " + list.size() + " şarkı"));
        TextView lbl = Ui.text(a, e.discoverLabel, 14, Ui.TEXT2, Ui.MEDIUM);
        lbl.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 12));
        results.addView(lbl);

        TextView startAll = Ui.primaryButton(a, "▶  Listeyi başlat");
        startAll.setOnClickListener(v -> start(list.get(0), list));
        results.addView(startAll, Ui.margins(full(), a, 0, 0, 0, 12));

        int i = 1;
        for (Track t : list) {
            LinearLayout r = Ui.songRow(a, t, t.reason, i++);
            r.setOnClickListener(v -> start(t, list));
            results.addView(r, Ui.margins(full(), a, 0, 0, 0, 8));
        }
        TextView note = Ui.text(a, "Listedeki her şarkı otomatik sıraya öncelikli girer. "
                + "İstediğine dokunarak oradan başlayabilirsin.", 12, Ui.TEXT3, Ui.REGULAR);
        note.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), 0, 0);
        results.addView(note);
    }

    private void start(Track t, List<Track> list) {
        if (!a.canOverlay() || !a.hasNotificationAccess()) {
            a.toast("Önce kurulumdaki izinleri tamamla");
            return;
        }
        e.playNow(t, list);
        a.toast("YouTube Music'te açılıyor: " + t.title);
        a.showTab(MainActivity.TAB_NOW);
    }

    private static boolean eq(String x, String y) {
        return x == null ? y == null : x.equals(y);
    }
}
