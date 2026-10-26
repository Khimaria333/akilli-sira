package com.akillisira.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Tek ekranlık arayüz: durum, çalan şarkı, sıradaki, alternatifler, ayarlar, kayıt. */
public class MainActivity extends Activity implements Engine.Listener {

    private static final int ACCENT = 0xFFE8505B;

    private Engine engine;

    private LinearLayout setupCard;
    private TextView setupText;
    private Button overlayBtn;
    private TextView statusText;
    private TextView nowTitle, nowInfo;
    private ProgressBar nowProgress;
    private TextView pickTitle, pickInfo;
    private Button skipBtn;
    private LinearLayout altBox;
    private TextView logText;
    private Switch autoSwitch;
    private RadioGroup modeGroup;

    private List<Track> renderedAlts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = Engine.get(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.addListener(this);
        render();
    }

    @Override
    protected void onPause() {
        engine.removeListener(this);
        super.onPause();
    }

    @Override
    public void onEngineChanged() {
        render();
    }

    // ================= Arayüzü kur =================

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, dp(20), p, dp(32));
        scroll.addView(root);

        TextView h = text("Akıllı Sıra", 26, true);
        root.addView(h);
        statusText = text("", 13, false);
        statusText.setAlpha(0.75f);
        root.addView(statusText);

        // Kurulum kartı (izin yoksa görünür)
        setupCard = card();
        setupText = text("", 14, false);
        setupCard.addView(setupText);
        Button permBtn = button("Bildirim erişimini aç");
        permBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        setupCard.addView(permBtn);
        Button appInfoBtn = button("Uygulama bilgisi (kısıtlı ayarlar)");
        appInfoBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        setupCard.addView(appInfoBtn);
        overlayBtn = button("Diğer uygulamaların üzerinde göster iznini aç");
        overlayBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        setupCard.addView(overlayBtn);
        root.addView(setupCard);

        // Şu an çalan
        LinearLayout nowCard = card();
        nowCard.addView(label("ŞU AN ÇALAN"));
        nowTitle = text("—", 18, true);
        nowCard.addView(nowTitle);
        nowInfo = text("", 13, false);
        nowInfo.setAlpha(0.8f);
        nowCard.addView(nowInfo);
        nowProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        nowProgress.setMax(1000);
        nowProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        nowCard.addView(nowProgress, new LinearLayout.LayoutParams(-1, dp(12)));
        Button openYtm = button("YT Music'i aç");
        openYtm.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(Engine.YTM);
            if (i != null) startActivity(i);
            else engine.log("YT Music bu telefonda bulunamadı");
        });
        nowCard.addView(openYtm);
        root.addView(nowCard);

        // Sıradaki
        LinearLayout pickCard = card();
        GradientDrawable bg = (GradientDrawable) pickCard.getBackground();
        bg.setStroke(dp(2), ACCENT);
        pickCard.addView(label("SIRADAKİ"));
        pickTitle = text("—", 18, true);
        pickCard.addView(pickTitle);
        pickInfo = text("", 13, false);
        pickInfo.setAlpha(0.8f);
        pickCard.addView(pickInfo);
        skipBtn = button("Şimdi geç ▶");
        skipBtn.setOnClickListener(v -> engine.skipNow(this));
        pickCard.addView(skipBtn);
        root.addView(pickCard);

        // Alternatifler
        root.addView(label("DİĞER UYGUN ŞARKILAR (dokun → sıradaki yap)"));
        altBox = new LinearLayout(this);
        altBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(altBox);

        // Ayarlar
        LinearLayout settings = card();
        settings.addView(label("AYARLAR"));
        autoSwitch = new Switch(this);
        autoSwitch.setText("Şarkı bitince otomatik geç");
        autoSwitch.setChecked(engine.isAuto());
        autoSwitch.setOnCheckedChangeListener((b, on) -> engine.setAuto(on));
        settings.addView(autoSwitch);

        TextView modeLbl = text("Hava", 14, true);
        modeLbl.setPadding(0, dp(12), 0, dp(4));
        settings.addView(modeLbl);
        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String[] names = {"Sakinleştir", "Aynı kalsın", "Hareketlendir"};
        int[] modes = {-1, 0, 1};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(View.generateViewId());
            rb.setTag(modes[i]);
            modeGroup.addView(rb);
            if (modes[i] == engine.getMode()) rb.setChecked(true);
        }
        modeGroup.setOnCheckedChangeListener((g, id) -> {
            View v = g.findViewById(id);
            if (v != null) engine.setMode((Integer) v.getTag());
        });
        settings.addView(modeGroup);
        root.addView(settings);

        // Kayıt
        root.addView(label("KAYIT"));
        logText = text("", 12, false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setAlpha(0.75f);
        root.addView(logText);

        return scroll;
    }

    // ================= Ekranı güncelle =================

    private void render() {
        boolean perm = hasNotificationAccess();
        boolean overlay = Settings.canDrawOverlays(this);
        setupCard.setVisibility(perm && overlay && engine.serviceConnected ? View.GONE : View.VISIBLE);
        overlayBtn.setVisibility(overlay ? View.GONE : View.VISIBLE);
        StringBuilder st = new StringBuilder();
        if (!perm) {
            st.append("1) \"Bildirim erişimini aç\" düğmesine bas ve Akıllı Sıra'yı aç.\n"
                    + "Anahtar gri ise: \"Uygulama bilgisi\" → sağ üst ⋮ → \"Kısıtlanmış ayarlara izin ver\", sonra tekrar dene.\n\n");
        } else if (!engine.serviceConnected) {
            st.append("Bildirim izni verildi, servis başlatılıyor… Düzelmezse izni kapatıp tekrar aç.\n\n");
        }
        if (!overlay) {
            st.append("2) Şarkı bitince YT Music'te sıradakini açabilmesi için "
                    + "\"Diğer uygulamaların üzerinde göster\" iznini aç.");
        }
        setupText.setText(st.toString().trim());

        String status;
        if (!perm) status = "Kurulum gerekiyor";
        else if (engine.controller == null) status = "YT Music'te bir şarkı aç";
        else status = (engine.isAuto() ? "Otomatik mod açık" : "Otomatik mod kapalı")
                + (engine.supportsPlayFromSearch() ? "" : " · YT Music arama ile çalmayı bildirmiyor, yine de denenecek");
        statusText.setText(status);

        // Şu an çalan
        if (engine.nowTitle != null) {
            nowTitle.setText(engine.nowTitle);
            StringBuilder sb = new StringBuilder(engine.nowArtist == null ? "" : engine.nowArtist);
            if (engine.seed != null) {
                sb.append("\n").append(describe(engine.seed));
            }
            sb.append("\n").append(time(engine.nowPositionMs)).append(" / ").append(time(engine.nowDurationMs));
            if (!engine.playing) sb.append("  (duraklatıldı)");
            nowInfo.setText(sb.toString());
            nowProgress.setProgress(engine.nowDurationMs > 0
                    ? (int) (1000 * engine.nowPositionMs / engine.nowDurationMs) : 0);
        } else {
            nowTitle.setText("—");
            nowInfo.setText("YT Music'te çalan şarkı burada görünecek.");
            nowProgress.setProgress(0);
        }

        // Sıradaki
        Track pk = engine.pick;
        if (engine.computing) {
            pickTitle.setText("Hesaplanıyor…");
            pickInfo.setText("BPM ve enerji bilgileri alınıyor");
        } else if (pk != null) {
            pickTitle.setText(pk.artist + " – " + pk.title);
            pickInfo.setText(pk.reason + "\n" + describe(pk)
                    + (pk.videoId != null ? " · YT Music bağlantısı hazır ✓" : " · YT Music'te aranıyor…"));
        } else {
            pickTitle.setText("—");
            pickInfo.setText(engine.problem != null ? engine.problem : "");
        }
        skipBtn.setEnabled(pk != null && engine.controller != null);

        // Alternatifler (sadece liste değişince yeniden çiz)
        if (renderedAlts != engine.alternatives) {
            renderedAlts = engine.alternatives;
            altBox.removeAllViews();
            for (Track t : renderedAlts) {
                Button b = button(t.artist + " – " + t.title + "\n" + t.reason);
                b.setAllCaps(false);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setOnClickListener(v -> engine.choose(t));
                altBox.addView(b);
            }
        }

        autoSwitch.setChecked(engine.isAuto());
        logText.setText(String.join("\n", engine.log));
    }

    private static String describe(Track t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.hasBpm() ? String.format(Locale.ROOT, "%d BPM", Math.round(t.bpm)) : "BPM bilinmiyor");
        if (t.hasGain()) sb.append(String.format(Locale.ROOT, " · ses %.1f dB", t.gain));
        return sb.toString();
    }

    private boolean hasNotificationAccess() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(
                new ComponentName(this, MusicListenerService.class));
    }

    // ================= Küçük yardımcılar =================

    private static String time(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, true);
        t.setTextColor(ACCENT);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(4), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.argb(28, 128, 128, 128));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }
}
