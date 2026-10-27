package com.akillisira.app;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Keşfet: dört ayrı yol, birbirine karışmaz.
 *   ✍️ Anlat   — kendi cümlenle (yazı ya da ses)
 *   🎭 Ruh hali — ruh hali + mekan seç
 *   🎵 Tür     — tek dokunuşla tür listesi
 *   🔎 Ara     — şarkı/sanatçıdan başlat
 * Üstte hızlı başlatlar (Favorilerim, Tarzıma göre), altta ortak seçenekler ve sonuçlar.
 */
public class DiscoverPage extends Page {

    private static final int TAB_TELL = 0, TAB_MOOD = 1, TAB_GENRE = 2, TAB_SEARCH = 3;

    private SharedPreferences sp;
    private TextView greeting, profileSub, weatherSub, runSub, runTitle;
    private Ui.Segmented tabs;
    private final LinearLayout[] panels = new LinearLayout[4];
    private int tab;

    // Anlat
    private EditText input;
    private TextView preview;

    // Ruh hali
    private String moodId, placeId;
    private final List<LinearLayout> moodTiles = new ArrayList<>();
    private final List<TextView> placeChips = new ArrayList<>();
    private TextView moodGo;

    // Tür
    private LinearLayout genreGrid;
    private TextView genreHint;
    private Boolean genreGridTurkish;
    private final List<LinearLayout> genreTiles = new ArrayList<>();
    private final List<String> genreIds = new ArrayList<>();

    // Ara
    private EditText searchBox;
    private LinearLayout searchList;
    private List<Track> renderedSearch;
    private boolean renderedSearchLoading;
    private String renderedSearchProblem;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // Seçenekler
    private Ui.Segmented langSeg, exploreSeg;
    private Switch djSwitch;

    // Sonuçlar
    private LinearLayout results;
    private TextView stageText;
    private List<Track> renderedResults;
    private boolean renderedLoading;
    private String renderedProblem;

    public DiscoverPage(MainActivity a, Engine e) {
        super(a, e);
    }

    @Override
    protected void build() {
        sp = a.getSharedPreferences("ayarlar", android.content.Context.MODE_PRIVATE);
        tab = sp.getInt("discoverTab", TAB_TELL);

        greeting = Ui.text(a, "", 14, Ui.TEXT2, Ui.MEDIUM);
        body.addView(greeting);
        TextView h = Ui.text(a, "Ne dinlemek\nistersin?", 30, Ui.TEXT, Ui.BLACK);
        h.setLineSpacing(0, 1.05f);
        h.setPadding(0, Ui.dp(a, 4), 0, 0);
        body.addView(h);

        // ---- Hızlı başlat (yana kaydırılır) ----
        LinearLayout quick = Ui.hbox(a);
        LinearLayout daily = quickCard("☀️", "Günün karışımı", "her gün yeni, sana özel", 0xFFFFB347, 0xFFFF5E62);
        daily.setOnClickListener(v -> { e.discoverDaily(); scrollToResults(); });
        LinearLayout fav = quickCard("❤️", "Favorilerim", "beğendiklerinden karışık", 0xFFFF4D6D, 0xFFB24592);
        fav.setOnClickListener(v -> { e.discoverFavorites(); scrollToResults(); });
        LinearLayout prof = quickCard("🧭", "Tarzıma göre", "", 0xFF7B5CFF, 0xFF4FACFE);
        profileSub = (TextView) prof.getChildAt(2);
        prof.setOnClickListener(v -> { e.discoverProfile(); scrollToResults(); });
        LinearLayout fresh = quickCard("🆕", "Yeni çıkanlar", "sevdiğin sanatçılardan", 0xFF11998E, 0xFF38EF7D);
        fresh.setOnClickListener(v -> { e.discoverNew(); scrollToResults(); });
        LinearLayout weather = quickCard("🌦", "Havaya göre", "", 0xFF4FACFE, 0xFF00C6FB);
        weatherSub = (TextView) weather.getChildAt(2);
        weather.setOnClickListener(v -> {
            if (!Weather.hasPlace(a)) {
                WeatherDialog.show(a, () -> { e.discoverWeather(); scrollToResults(); });
            } else {
                e.discoverWeather();
                scrollToResults();
            }
        });
        LinearLayout run = quickCard("🏃", "Koşu modu", "", 0xFFF7971E, 0xFFE53935);
        runTitle = (TextView) run.getChildAt(1);
        runSub = (TextView) run.getChildAt(2);
        run.setOnClickListener(v -> {
            if (e.isRunActive()) e.stopRun();
            else a.startRunMode(() -> scrollToResults());
        });
        LinearLayout blend = quickCard("🤝", "Blend", "arkadaşınla ortak liste", 0xFF8E2DE2, 0xFFDA4453);
        blend.setOnClickListener(v -> BlendDialog.show(a, e, this::scrollToResults));
        LinearLayout[] cards = {daily, fav, prof, fresh, weather, run, blend};
        for (int i = 0; i < cards.length; i++) {
            quick.addView(cards[i], Ui.margins(Ui.lp(Ui.dp(a, 148), Ui.WRAP), a, i == 0 ? 0 : 10, 0, 0, 0));
        }
        android.widget.HorizontalScrollView qs = Ui.hscroll(a, quick);
        qs.setClipToPadding(false);
        body.addView(qs, Ui.margins(full(), a, 0, 20, 0, 0));

        // ---- Sekmeler ----
        tabs = Ui.segmented(a, new String[]{"✍️ Anlat", "🎭 Ruh hali", "🎵 Tür", "🔎 Ara"}, tab, this::showPanel);
        body.addView(tabs.view, Ui.margins(full(), a, 0, 22, 0, 0));

        LinearLayout panelBox = Ui.vbox(a);
        panels[TAB_TELL] = buildTell();
        panels[TAB_MOOD] = buildMood();
        panels[TAB_GENRE] = buildGenre();
        panels[TAB_SEARCH] = buildSearch();
        for (LinearLayout p : panels) panelBox.addView(p, full());
        body.addView(panelBox, Ui.margins(full(), a, 0, 14, 0, 0));

        // ---- Ortak seçenekler ----
        body.addView(Ui.section(a, "Seçenekler"));
        LinearLayout opt = Ui.card(a);
        opt.addView(label("Dil"));
        langSeg = Ui.segmented(a, new String[]{"🇹🇷  Türkçe", "🌍  Global"}, e.isTurkish() ? 0 : 1, i -> {
            e.setTurkish(i == 0);
            refreshGenreGrid();
        });
        opt.addView(langSeg.view, full());
        opt.addView(label("Keşif"));
        exploreSeg = Ui.segmented(a, new String[]{"Tanıdık", "Dengeli", "Yeni"}, e.getExplore(), e::setExplore);
        opt.addView(exploreSeg.view, full());
        TextView exHint = Ui.text(a, "Tanıdık: bildiğin hitler · Yeni: az bilinen şarkılar", 12, Ui.TEXT3, Ui.REGULAR);
        exHint.setPadding(Ui.dp(a, 2), Ui.dp(a, 6), 0, 0);
        opt.addView(exHint);
        LinearLayout dj = Ui.hbox(a);
        dj.setPadding(0, Ui.dp(a, 14), 0, 0);
        LinearLayout djT = Ui.vbox(a);
        djT.addView(Ui.text(a, "🎚  DJ modu", 14.5f, Ui.TEXT, Ui.BOLD));
        TextView djD = Ui.text(a, "Liste sakin başlar, zirveye çıkar, yavaşça iner", 12.5f, Ui.TEXT2, Ui.REGULAR);
        djD.setPadding(0, Ui.dp(a, 2), Ui.dp(a, 8), 0);
        djT.addView(djD);
        dj.addView(djT, Ui.lpw(1));
        djSwitch = new Switch(a);
        int[][] st = {{android.R.attr.state_checked}, {}};
        djSwitch.setThumbTintList(new ColorStateList(st, new int[]{0xFFFFFFFF, 0xFFBDBDC8}));
        djSwitch.setTrackTintList(new ColorStateList(st, new int[]{Ui.ACCENT, 0x44FFFFFF}));
        djSwitch.setChecked(e.isDj());
        djSwitch.setOnCheckedChangeListener((b, on) -> e.setDj(on));
        dj.addView(djSwitch);
        opt.addView(dj, full());
        body.addView(opt, full());

        // ---- Sonuçlar ----
        results = Ui.vbox(a);
        body.addView(results, Ui.margins(full(), a, 0, 8, 0, 0));

        showPanel(tab);
    }

    // =====================================================================
    // Paneller
    // =====================================================================

    private LinearLayout buildTell() {
        LinearLayout p = Ui.vbox(a);
        LinearLayout c = Ui.card(a);
        TextView t = Ui.text(a, "Nasıl hissettiğini, nerede olduğunu anlat", 13.5f, Ui.TEXT2, Ui.MEDIUM);
        t.setPadding(0, 0, 0, Ui.dp(a, 10));
        c.addView(t);
        input = new EditText(a);
        input.setBackground(Ui.round(a, Ui.SURFACE2, 16));
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.TEXT3);
        input.setHint("örn. akşamüstü sahilde sakin bir şeyler");
        input.setTextSize(15);
        int ip = Ui.dp(a, 14);
        input.setPadding(ip, ip, ip, ip);
        input.setMaxLines(3);
        input.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) { submitText(); return true; }
            return false;
        });
        c.addView(input, full());
        preview = Ui.text(a, "", 13, Ui.ACCENT, Ui.MEDIUM);
        preview.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, 0);
        preview.setLineSpacing(0, 1.15f);
        c.addView(preview);
        // izleyici en son: önizleme alanı hazır olmadan tetiklenmesin
        String prev = e.getVibe();
        if (prev != null) input.setText(prev);
        input.addTextChangedListener(new SimpleWatcher(this::updatePreview));

        LinearLayout row = Ui.hbox(a);
        LinearLayout mic = Ui.vbox(a);
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        mic.setClickable(true);
        mic.addView(new Views.Icon(a, Views.Icon.MIC, Ui.TEXT), Ui.lp(Ui.dp(a, 24), Ui.dp(a, 24)));
        mic.setOnClickListener(v -> a.startSpeech());
        row.addView(mic, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 0, 0, 10, 0));
        TextView go = Ui.primaryButton(a, "Anla ve hazırla");
        go.setOnClickListener(v -> submitText());
        row.addView(go, Ui.lpw(1));
        c.addView(row, Ui.margins(full(), a, 0, 12, 0, 0));
        p.addView(c, full());

        LinearLayout ex = Ui.hbox(a);
        String[][] examples = {
            {"🏖️ Akşamüstü sahil", "akşamüstü sahilde sakin bir şeyler"},
            {"🥂 Rakı masası", "rakı masası"},
            {"🏋️ Sporda gaz", "sporda gaz şarkılar lazım"},
            {"🌧️ Yağmur ve hüzün", "yağmurlu bir gün, biraz hüzünlüyüm"},
            {"🍷 Akşam yemeği", "sevgilimle akşam yemeği"},
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
        p.addView(Ui.hscroll(a, ex), Ui.margins(full(), a, 0, 12, 0, 0));
        updatePreview();
        return p;
    }

    private LinearLayout buildMood() {
        LinearLayout p = Ui.vbox(a);
        LinearLayout row = null;
        for (int i = 0; i < Moods.MOODS.length; i++) {
            Moods.Mood m = Moods.MOODS[i];
            if (i % 2 == 0) {
                row = Ui.hbox(a);
                p.addView(row, Ui.margins(full(), a, 0, 0, 0, 10));
            }
            LinearLayout tile = tile(m.emoji, m.label, 92);
            tile.setOnClickListener(v -> {
                moodId = m.id;
                paintMoods();
                updateMoodGo();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 92), 1);
            lp.setMargins(i % 2 == 0 ? 0 : Ui.dp(a, 5), 0, i % 2 == 0 ? Ui.dp(a, 5) : 0, 0);
            row.addView(tile, lp);
            moodTiles.add(tile);
        }
        p.addView(label("Neredesin? (isteğe bağlı)"));
        LinearLayout chips = Ui.hbox(a);
        for (Moods.Place pl : Moods.PLACES) {
            TextView ch = Ui.chip(a, pl.emoji + "  " + pl.label, false);
            ch.setOnClickListener(v -> {
                placeId = pl.id.equals(placeId) ? null : pl.id;
                paintPlaces();
            });
            chips.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
            placeChips.add(ch);
        }
        p.addView(Ui.hscroll(a, chips), full());
        moodGo = Ui.primaryButton(a, "");
        moodGo.setOnClickListener(v -> {
            if (moodId == null) return;
            e.discover(moodId, placeId);
            scrollToResults();
        });
        p.addView(moodGo, Ui.margins(full(), a, 0, 16, 0, 0));
        paintMoods();
        paintPlaces();
        updateMoodGo();
        return p;
    }

    private LinearLayout buildGenre() {
        LinearLayout p = Ui.vbox(a);
        genreHint = Ui.text(a, "", 12.5f, Ui.WARN, Ui.MEDIUM);
        genreHint.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 10));
        genreHint.setOnClickListener(v -> a.showTab(MainActivity.TAB_SETTINGS));
        p.addView(genreHint);
        genreGrid = Ui.vbox(a);
        p.addView(genreGrid, full());
        refreshGenreGrid();
        return p;
    }

    private void refreshGenreGrid() {
        if (genreGrid == null) return;
        boolean tr = e.isTurkish();
        if (genreGridTurkish != null && genreGridTurkish == tr) return;
        genreGridTurkish = tr;
        genreGrid.removeAllViews();
        genreTiles.clear();
        genreIds.clear();
        Genres.Genre[] list = tr ? Genres.TR : Genres.GLOBAL;
        LinearLayout row = null;
        for (int i = 0; i < list.length; i++) {
            Genres.Genre g = list[i];
            if (i % 2 == 0) {
                row = Ui.hbox(a);
                genreGrid.addView(row, Ui.margins(full(), a, 0, 0, 0, 10));
            }
            LinearLayout tile = tile(g.emoji, g.label, 78);
            tile.setTag(new int[]{g.c1, g.c2});
            tile.setOnClickListener(v -> {
                e.discoverGenre(g.id);
                paintGenres();
                scrollToResults();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 78), 1);
            lp.setMargins(i % 2 == 0 ? 0 : Ui.dp(a, 5), 0, i % 2 == 0 ? Ui.dp(a, 5) : 0, 0);
            row.addView(tile, lp);
            genreTiles.add(tile);
            genreIds.add(g.id);
        }
        if (list.length % 2 == 1 && row != null) {
            row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        }
        paintGenres();
    }

    private LinearLayout buildSearch() {
        LinearLayout p = Ui.vbox(a);
        LinearLayout sb = Ui.hbox(a);
        sb.setBackground(Ui.stroke(a, Ui.SURFACE, Ui.STROKE, 20, 1));
        sb.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 6), 0);
        sb.addView(Ui.text(a, "🔎", 16, Ui.TEXT, Ui.REGULAR));
        searchBox = new EditText(a);
        searchBox.setBackground(null);
        searchBox.setHint("Şarkı ya da sanatçı ara");
        searchBox.setHintTextColor(Ui.TEXT3);
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setTextSize(15);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14));
        searchBox.addTextChangedListener(new SimpleWatcher(() -> {
            if (pendingSearch != null) debounce.removeCallbacks(pendingSearch);
            String q = searchBox.getText().toString();
            pendingSearch = () -> e.search(q);
            debounce.postDelayed(pendingSearch, 450);
        }));
        searchBox.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(searchBox);
                e.search(searchBox.getText().toString());
                return true;
            }
            return false;
        });
        sb.addView(searchBox, Ui.lpw(1));
        TextView clr = Ui.text(a, "✕", 15, Ui.TEXT3, Ui.BOLD);
        clr.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        clr.setOnClickListener(v -> {
            searchBox.setText("");
            e.clearSearch();
            hideKeyboard(searchBox);
        });
        sb.addView(clr);
        p.addView(sb, full());
        TextView hint = Ui.text(a, "Bir şarkıya dokun: " + e.playerIn() + " başlar, Ahenk oradan devam eder.",
                12, Ui.TEXT3, Ui.REGULAR);
        hint.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), 0, 0);
        p.addView(hint);
        searchList = Ui.vbox(a);
        p.addView(searchList, full());
        return p;
    }

    // =====================================================================

    private void showPanel(int t) {
        tab = t;
        sp.edit().putInt("discoverTab", t).apply();
        for (int i = 0; i < panels.length; i++) panels[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
        if (t != TAB_TELL) hideKeyboard(input);
        if (t != TAB_SEARCH) hideKeyboard(searchBox);
        panels[t].setAlpha(0f);
        panels[t].animate().alpha(1f).setDuration(180).start();
    }

    private LinearLayout quickCard(String emoji, String title, String sub, int c1, int c2) {
        LinearLayout c = Ui.vbox(a);
        c.setBackground(Ui.ripple(a, Ui.gradient(a, c1, c2, 22), 22));
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
        c.setClickable(true);
        c.addView(Ui.text(a, emoji, 22, 0xFFFFFFFF, Ui.REGULAR));
        TextView t = Ui.text(a, title, 15.5f, 0xFFFFFFFF, Ui.BOLD);
        t.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 2));
        c.addView(t);
        c.addView(Ui.oneLine(Ui.text(a, sub, 12, 0xDDFFFFFF, Ui.MEDIUM)));
        return c;
    }

    private LinearLayout tile(String emoji, String text, int heightDp) {
        LinearLayout t = Ui.vbox(a);
        t.setGravity(Gravity.BOTTOM | Gravity.START);
        int p = Ui.dp(a, 13);
        t.setPadding(p, p, p, p);
        t.setClickable(true);
        t.addView(Ui.text(a, emoji, heightDp > 85 ? 26 : 22, Ui.TEXT, Ui.REGULAR));
        TextView l = Ui.oneLine(Ui.text(a, text, 15, 0xFFFFFFFF, Ui.BOLD));
        l.setPadding(0, Ui.dp(a, 4), 0, 0);
        t.addView(l);
        return t;
    }

    private TextView label(String s) {
        TextView t = Ui.text(a, s, 13, Ui.TEXT2, Ui.MEDIUM);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 14), 0, Ui.dp(a, 8));
        return t;
    }

    private void paintMoods() {
        for (int i = 0; i < moodTiles.size(); i++) {
            Moods.Mood m = Moods.MOODS[i];
            paintTile(moodTiles.get(i), m.color1, m.color2, m.id.equals(moodId), moodId != null);
        }
    }

    private void paintGenres() {
        String sel = e.getGenreId();
        boolean any = sel != null && genreIds.contains(sel);
        for (int i = 0; i < genreTiles.size(); i++) {
            int[] c = (int[]) genreTiles.get(i).getTag();
            paintTile(genreTiles.get(i), c[0], c[1], genreIds.get(i).equals(sel), any);
        }
    }

    private void paintTile(LinearLayout t, int c1, int c2, boolean sel, boolean anySelected) {
        GradientDrawable g = Ui.gradient(a, c1, c2, 22);
        if (sel) g.setStroke(Ui.dp(a, 2.5f), 0xFFFFFFFF);
        t.setBackground(Ui.ripple(a, g, 22));
        t.setAlpha(!anySelected || sel ? 1f : 0.5f);
        t.animate().scaleX(sel ? 1.03f : 1f).scaleY(sel ? 1.03f : 1f).setDuration(150).start();
    }

    private void paintPlaces() {
        for (int i = 0; i < placeChips.size(); i++) {
            Ui.setChip(a, placeChips.get(i), Moods.PLACES[i].id.equals(placeId));
        }
    }

    private void updateMoodGo() {
        Ui.setEnabledLook(moodGo, moodId != null);
        moodGo.setText(moodId == null ? "Önce ruh halini seç" : "✨  Şarkılarımı hazırla");
    }

    private void updatePreview() {
        if (input == null || preview == null) return;
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
        hideKeyboard(input);
        input.clearFocus();
        e.discoverText(t);
        scrollToResults();
    }

    /** Mikrofondan gelen cümle. */
    public void onSpeech(String text) {
        tabs.select(TAB_TELL);
        showPanel(TAB_TELL);
        input.setText(text);
        input.setSelection(text.length());
        submitText();
    }

    private void scrollToResults() {
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, results.getTop() - Ui.dp(a, 12)), 200);
    }

    private void hideKeyboard(View v) {
        if (v == null) return;
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // =====================================================================
    // Ekranı güncelle
    // =====================================================================

    @Override
    public void onShow() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? "Günaydın ☀️" : h >= 12 && h < 18 ? "İyi günler 👋"
                : h >= 18 && h < 23 ? "İyi akşamlar 🌆" : "İyi geceler 🌙");
        langSeg.select(e.isTurkish() ? 0 : 1);
        exploreSeg.select(e.getExplore());
        djSwitch.setChecked(e.isDj());
        refreshGenreGrid();
        paintGenres();
    }

    @Override
    public void render() {
        renderQuick();
        renderProfileCard();
        renderSearch();
        renderGenreHint();
        renderResults();
    }

    private void renderProfileCard() {
        Map<String, Integer> p = e.profile();
        String sub;
        if (!e.lastfm.enabled()) sub = "Last.fm bağlayınca öğrenir";
        else if (p.isEmpty()) sub = e.profileComputing() ? "öğreniliyor…" : "❤️ ile beğendikçe öğrenir";
        else {
            List<String> top = new ArrayList<>();
            for (String k : p.keySet()) { top.add(k); if (top.size() >= 2) break; }
            sub = String.join(" · ", top);
        }
        if (!sub.contentEquals(profileSub.getText())) profileSub.setText(sub);
    }

    private void renderQuick() {
        Weather.Now w = Weather.current();
        String ws = !Weather.hasPlace(a) ? "dokun, şehrini seç" : w != null ? w.label() : Weather.placeName(a);
        if (ws == null) ws = "";
        if (!ws.contentEquals(weatherSub.getText())) weatherSub.setText(ws);
        if (Weather.hasPlace(a) && w == null) Weather.refresh(a, false, () -> a.runOnUiThread(this::renderQuick));
        boolean on = e.isRunActive();
        String rt = on ? "Koşuyu bitir" : "Koşu modu";
        String rs = on ? (e.getCadence() > 0 ? e.getCadence() + " adım/dk · " + e.runBpm() + " BPM" : "adım bekleniyor…")
                : "adım tempona göre BPM";
        if (!rt.contentEquals(runTitle.getText())) runTitle.setText(rt);
        if (!rs.contentEquals(runSub.getText())) runSub.setText(rs);
    }

    private void renderGenreHint() {
        boolean on = e.lastfm.enabled();
        genreHint.setVisibility(on ? View.GONE : View.VISIBLE);
        if (!on) genreHint.setText("💡 Tür listeleri Last.fm bağlıyken çok daha isabetli olur. Bağlamak için dokun →");
    }

    private void renderResults() {
        if (renderedResults == e.discoverResults && renderedLoading == e.discoverLoading
                && eq(renderedProblem, e.discoverProblem)) {
            if (e.discoverLoading && stageText != null && !e.discoverStage.contentEquals(stageText.getText())) {
                stageText.setText(e.discoverStage);
            }
            return;
        }
        renderedResults = e.discoverResults;
        renderedLoading = e.discoverLoading;
        renderedProblem = e.discoverProblem;
        results.removeAllViews();
        stageText = null;

        if (e.discoverLoading) {
            results.addView(Ui.section(a, "Hazırlanıyor"));
            LinearLayout c = Ui.card(a);
            c.setGravity(Gravity.CENTER);
            c.setPadding(Ui.dp(a, 16), Ui.dp(a, 26), Ui.dp(a, 16), Ui.dp(a, 26));
            ProgressBar pb = new ProgressBar(a);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
            c.addView(pb, Ui.lp(Ui.dp(a, 40), Ui.dp(a, 40)));
            TextView t1 = Ui.text(a, e.discoverLabel, 15, Ui.TEXT, Ui.BOLD);
            t1.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
            t1.setGravity(Gravity.CENTER);
            c.addView(t1);
            stageText = Ui.text(a, e.discoverStage, 13, Ui.TEXT2, Ui.REGULAR);
            stageText.setGravity(Gravity.CENTER);
            c.addView(stageText);
            results.addView(c, full());
            return;
        }
        if (e.discoverProblem != null) {
            results.addView(Ui.section(a, "Sonuç"));
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, e.discoverProblem, 14, Ui.WARN, Ui.MEDIUM));
            if (e.canRedo()) {
                TextView retry = Ui.secondaryButton(a, "Tekrar dene");
                retry.setOnClickListener(v -> e.redoDiscover());
                c.addView(retry, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 12, 0, 0));
            }
            results.addView(c, full());
            return;
        }
        List<Track> list = e.discoverResults;
        if (list.isEmpty()) return;

        results.addView(Ui.section(a, "Senin için hazır · " + list.size() + " şarkı"));
        TextView lbl = Ui.text(a, e.discoverLabel, 14, Ui.TEXT2, Ui.MEDIUM);
        lbl.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 12));
        results.addView(lbl);

        if (e.discoverDj && e.discoverCurve != null && e.discoverCurve.length > 2) {
            LinearLayout cc = Ui.card(a);
            TextView ct = Ui.text(a, "🎚  Enerji eğrisi: ısınma → zirve → iniş", 13, Ui.TEXT2, Ui.MEDIUM);
            ct.setPadding(0, 0, 0, Ui.dp(a, 8));
            cc.addView(ct);
            Views.Curve curve = new Views.Curve(a);
            curve.setValues(e.discoverCurve);
            cc.addView(curve, Ui.lp(Ui.MATCH, Ui.dp(a, 90)));
            results.addView(cc, Ui.margins(full(), a, 0, 0, 0, 12));
        }

        LinearLayout btns = Ui.hbox(a);
        TextView startAll = Ui.primaryButton(a, "▶  Listeyi başlat");
        startAll.setOnClickListener(v -> start(list.get(0), list));
        btns.addView(startAll, Ui.lpw(1));
        LinearLayout again = Ui.vbox(a);
        again.setGravity(Gravity.CENTER);
        again.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        again.setClickable(true);
        again.addView(new Views.Icon(a, Views.Icon.REFRESH, Ui.TEXT), Ui.lp(Ui.dp(a, 22), Ui.dp(a, 22)));
        again.setOnClickListener(v -> {
            e.redoDiscover();
            a.toast("Yeni bir liste hazırlanıyor");
        });
        btns.addView(again, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 10, 0, 0, 0));
        TextView share = Ui.text(a, "📤", 18, Ui.TEXT, Ui.REGULAR);
        share.setGravity(Gravity.CENTER);
        share.setBackground(Ui.ripple(a, Ui.stroke(a, Ui.SURFACE2, Ui.STROKE, 26, 1), 26));
        share.setOnClickListener(v -> ShareCard.shareList(a, e.discoverLabel, list));
        btns.addView(share, Ui.margins(Ui.lp(Ui.dp(a, 52), Ui.dp(a, 52)), a, 10, 0, 0, 0));
        results.addView(btns, Ui.margins(full(), a, 0, 0, 0, 10));
        final String expLabel = "▶  " + e.playerName() + (e.isSpotify() ? "'da" : "'te") + " liste olarak aç";
        LinearLayout expRow = Ui.hbox(a);
        TextView exp = Ui.secondaryButton(a, expLabel);
        exp.setGravity(Gravity.CENTER);
        exp.setOnClickListener(v -> {
            if (e.exporting) return;
            exp.setText(e.playerName() + " hazırlanıyor…");
            e.exportList(list, msg -> {
                exp.setText(expLabel);
                a.toast(msg);
            });
        });
        expRow.addView(exp, Ui.lpw(1));
        if (e.isSpotify() && e.spotify.connected()) {
            TextView save = Ui.secondaryButton(a, "💾");
            save.setOnClickListener(v -> {
                if (e.exporting) return;
                String name = "Ahenk · " + e.discoverLabel.replace("  ·  ", " · ");
                if (name.length() > 90) name = name.substring(0, 90);
                a.toast("Spotify'a kaydediliyor…");
                e.saveToSpotify(list, name, a::toast);
            });
            expRow.addView(save, Ui.margins(Ui.lp(Ui.dp(a, 56), Ui.WRAP), a, 10, 0, 0, 0));
        }
        results.addView(expRow, Ui.margins(full(), a, 0, 0, 0, 12));

        int i = 1;
        for (Track t : list) {
            LinearLayout r = Ui.songRow(a, t, t.reason, i++);
            r.setOnClickListener(v -> start(t, list));
            results.addView(r, Ui.margins(full(), a, 0, 0, 0, 8));
        }
        TextView note = Ui.text(a, "Listedeki şarkılar sırayla çalar; bitince Ahenk aynı havada devam eder. "
                + "İstediğin şarkıya dokunarak oradan başlayabilirsin.", 12, Ui.TEXT3, Ui.REGULAR);
        note.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), 0, 0);
        note.setLineSpacing(0, 1.12f);
        results.addView(note);
    }

    private void renderSearch() {
        if (renderedSearch == e.searchResults && renderedSearchLoading == e.searchLoading
                && eq(renderedSearchProblem, e.searchProblem)) return;
        renderedSearch = e.searchResults;
        renderedSearchLoading = e.searchLoading;
        renderedSearchProblem = e.searchProblem;
        searchList.removeAllViews();
        if (e.searchLoading) {
            TextView t = Ui.text(a, "Aranıyor…", 13, Ui.TEXT2, Ui.MEDIUM);
            t.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), 0, 0);
            searchList.addView(t);
            return;
        }
        if (e.searchProblem != null) {
            TextView t = Ui.text(a, e.searchProblem, 13, Ui.TEXT2, Ui.MEDIUM);
            t.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), 0, 0);
            searchList.addView(t);
            return;
        }
        int n = 0;
        for (Track t : e.searchResults) {
            LinearLayout r = Ui.songRow(a, t, "▶  Buradan başlat", 0);
            r.setOnClickListener(v -> {
                if (!a.canOverlay() || !a.hasNotificationAccess()) {
                    a.toast("Önce kurulumdaki izinleri tamamla");
                    return;
                }
                hideKeyboard(searchBox);
                e.startFrom(t);
                a.toast(e.playerIn() + " açılıyor: " + t.title);
                a.showTab(MainActivity.TAB_NOW);
            });
            searchList.addView(r, Ui.margins(full(), a, 0, n == 0 ? 12 : 0, 0, 8));
            if (++n >= 8) break;
        }
    }

    private void start(Track t, List<Track> list) {
        if (!a.canOverlay() || !a.hasNotificationAccess()) {
            a.toast("Önce kurulumdaki izinleri tamamla");
            return;
        }
        e.playNow(t, list, e.discoverDj);
        a.toast(e.playerIn() + " açılıyor: " + t.title);
        a.showTab(MainActivity.TAB_NOW);
    }

    private static boolean eq(String x, String y) {
        return x == null ? y == null : x.equals(y);
    }

    /** Sadece "metin değişti" ile ilgilenen kısa TextWatcher. */
    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable r;
        SimpleWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable e) { r.run(); }
    }
}
