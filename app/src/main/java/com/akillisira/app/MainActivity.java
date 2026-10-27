package com.akillisira.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/** Tek aktivite: kurulum ya da alt menülü üç sekme (Keşfet / Şimdi / Ayarlar). */
public class MainActivity extends Activity implements Engine.Listener {

    public static final int TAB_DISCOVER = 0, TAB_NOW = 1, TAB_HISTORY = 2, TAB_SETTINGS = 3;
    private static final int TABS = 4;
    private static final int REQ_NOTIF = 43;
    private static final int REQ_ACT = 46, REQ_LOC = 47;
    private Runnable afterPermission;
    private static final int REQ_EXPORT = 44, REQ_IMPORT = 45;
    private static final int REQ_SPEECH = 42;

    private Engine engine;
    private FrameLayout container;
    private LinearLayout nav;
    private Page setup;
    private Page[] tabs;
    private Page current;
    private int currentTab = -1;
    private final LinearLayout[] navItems = new LinearLayout[TABS];
    private final Views.Icon[] navIcons = new Views.Icon[TABS];
    private final TextView[] navLabels = new TextView[TABS];
    private boolean setupDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);   // açılış temasından (logo) normal temaya geç
        super.onCreate(savedInstanceState);
        installCrashLog();
        WakeScheduler.schedule(this);   // alarm açıksa bir sonrakini garanti et
        NewReleases.schedule(this);     // yeni çıkanlar: günlük kontrol
        engine = Engine.get(this);
        String crash = getSharedPreferences("ayarlar", MODE_PRIVATE).getString("lastCrash", null);
        if (crash != null) {
            engine.log("⚠️ Son çökme: " + crash);
            getSharedPreferences("ayarlar", MODE_PRIVATE).edit().remove("lastCrash").apply();
        }
        themeSig = Ui.applyTheme(this, coverColor());
        Window w = getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.SURFACE);

        setup = new SetupPage(this, engine);
        tabs = new Page[]{new DiscoverPage(this, engine), new NowPage(this, engine),
                new HistoryPage(this, engine), new SettingsPage(this, engine)};

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        nav = buildNav();
        root.addView(nav, Ui.lp(Ui.MATCH, Ui.WRAP));
        setContentView(root);
        if (savedInstanceState == null && isLauncherIntent(getIntent())) Splash.show(this);

        setupDone = getSharedPreferences("ayarlar", MODE_PRIVATE).getBoolean("setupDone", false);
        pendingIntent = getIntent();
    }

    private Intent pendingIntent;
    private int themeSig;

    /** "Kapak rengi" teması için çalan şarkının baskın rengi (yoksa 0). */
    private int coverColor() {
        if (Ui.themeAccent(this) != Ui.ACCENT_COVER || engine == null) return 0;
        android.graphics.Bitmap b = engine.nowArt();
        if (b == null && engine.seed != null) b = ImageLoader.cached(engine.seed.coverUrl);
        return b != null ? Palette.vibrant(b, 0) : 0;
    }

    /** Tema değişince ekranı yeni renklerle yeniden kur. */
    public void applyThemeNow() {
        recreate();
    }

    /** Ana ekrandan açıldı mı (kısayol, bildirim, alarm değil): açılış animasyonu sadece o zaman. */
    private static boolean isLauncherIntent(Intent i) {
        return i != null && Intent.ACTION_MAIN.equals(i.getAction())
                && (i.getCategories() == null || i.hasCategory(Intent.CATEGORY_LAUNCHER));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingIntent = intent;
    }

    private void installCrashLog() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev instanceof CrashSaver) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashSaver(getApplicationContext(), prev));
    }

    /** Çökmenin nedenini kaydeder, sonra sistemin normal davranışına bırakır. */
    private static final class CrashSaver implements Thread.UncaughtExceptionHandler {
        private final android.content.Context ctx;
        private final Thread.UncaughtExceptionHandler next;

        CrashSaver(android.content.Context ctx, Thread.UncaughtExceptionHandler next) {
            this.ctx = ctx;
            this.next = next;
        }

        @Override
        public void uncaughtException(Thread t, Throwable ex) {
            try {
                StringBuilder sb = new StringBuilder(ex.toString());
                StackTraceElement[] st = ex.getStackTrace();
                for (int i = 0; i < st.length && i < 6; i++) sb.append("\n   at ").append(st[i]);
                Throwable cause = ex.getCause();
                if (cause != null) sb.append("\n   neden: ").append(cause);
                ctx.getSharedPreferences("ayarlar", MODE_PRIVATE).edit()
                        .putString("lastCrash", sb.toString()).commit();
            } catch (Throwable ignored) {}
            if (next != null) next.uncaughtException(t, ex);
        }
    }

    /** Simge kısayolları: akillisira://start/{sakin|enerjik|yol|favoriler} */
    private void handleShortcut() {
        Intent i = pendingIntent;
        pendingIntent = null;
        if (i != null && i.getBooleanExtra(NewReleases.EXTRA_OPEN, false)) {
            engine.discoverNew();
            showTab(TAB_DISCOVER);
            return;
        }
        if (i == null || i.getData() == null || !"akillisira".equals(i.getData().getScheme())) return;
        String what = i.getData().getLastPathSegment();
        if (what == null) return;
        switch (what) {
            case "sakin": engine.discoverAndPlay("sakin", null); break;
            case "enerjik": engine.discoverAndPlay("enerjik", null); break;
            case "yol": engine.discoverAndPlay("mutlu", "araba"); break;
            case "favoriler": engine.favoritesAndPlay(); break;
            case "gunun": engine.dailyAndPlay(); break;
            default: return;
        }
        toast("Liste hazırlanıyor, birazdan başlıyor…");
        showTab(TAB_DISCOVER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kapak rengi teması: şarkı değiştiyse renkleri tazele
        if (Ui.themeAccent(this) == Ui.ACCENT_COVER) {
            int c = coverColor();
            if (c != 0) {
                int saved = themeSig;
                int now = Ui.applyTheme(this, c);
                if (now != saved) { recreate(); return; }
            }
        }
        engine.addListener(this);
        engine.setUiVisible(true);
        route();
    }

    /** Hangi ekran gösterilecek: tanıtım → kurulum → sekmeler. */
    private void route() {
        android.content.SharedPreferences sp = getSharedPreferences("ayarlar", MODE_PRIVATE);
        if (!sp.getBoolean("introDone", setupDone)) {
            nav.setVisibility(View.GONE);
            if (intro == null) intro = new IntroPage(this, engine);
            swap(intro);
            currentTab = -1;
            return;
        }
        boolean ready = hasNotificationAccess() && canOverlay();
        if (!ready || !setupDone) {
            showSetup();
            pendingIntent = null;
        } else if (currentTab < 0) {
            showTab(engine.nowTitle != null ? TAB_NOW : TAB_DISCOVER);
            askNotificationsOnce();
        } else {
            current.onShow();
            current.render();
        }
        if (ready && setupDone) {
            handleShortcut();
            checkUpdate();
        }
    }

    @Override
    protected void onPause() {
        engine.removeListener(this);
        engine.setUiVisible(false);
        super.onPause();
    }

    @Override
    public void onEngineChanged() {
        if (current != null) current.render();
    }

    @Override
    public void onBackPressed() {
        if (current != setup && currentTab != TAB_NOW && engine.nowTitle != null) {
            showTab(TAB_NOW);
        } else {
            super.onBackPressed();
        }
    }

    // ================= Gezinme =================

    private void showSetup() {
        nav.setVisibility(View.GONE);
        swap(setup);
        currentTab = -1;
    }

    private Page intro;

    public void finishIntro() {
        getSharedPreferences("ayarlar", MODE_PRIVATE).edit().putBoolean("introDone", true).apply();
        route();
    }

    /** Ayarlar'dan "Tanıtımı tekrar göster". */
    public void replayIntro() {
        getSharedPreferences("ayarlar", MODE_PRIVATE).edit().putBoolean("introDone", false).apply();
        intro = null;
        route();
    }

    public void finishSetup() {
        setupDone = true;
        getSharedPreferences("ayarlar", MODE_PRIVATE).edit().putBoolean("setupDone", true).apply();
        showTab(engine.nowTitle != null ? TAB_NOW : TAB_DISCOVER);
        askNotificationsOnce();
    }

    /** Bildirim izni hiç sorulmadıysa bir kez sor. */
    private void askNotificationsOnce() {
        android.content.SharedPreferences sp = getSharedPreferences("ayarlar", MODE_PRIVATE);
        if (sp.getBoolean("notifAsked", false) || !engine.isNotif() || notificationsAllowed()) return;
        sp.edit().putBoolean("notifAsked", true).apply();
        requestNotifications();
    }

    public void showTab(int tab) {
        nav.setVisibility(View.VISIBLE);
        currentTab = tab;
        swap(tabs[tab]);
        for (int i = 0; i < TABS; i++) {
            boolean on = i == tab;
            navIcons[i].setColor(on ? Ui.TEXT : Ui.TEXT3);
            navLabels[i].setTextColor(on ? Ui.TEXT : Ui.TEXT3);
            navLabels[i].setTypeface(on ? Ui.BOLD : Ui.MEDIUM);
            View pillBg = navItems[i].getChildAt(0);
            pillBg.setBackground(on ? Ui.gradient(this, 0x55FF4D6D, 0x557B5CFF, 16) : null);
        }
    }

    public boolean isShowing(Page p) {
        return current == p;
    }

    public void setStatusBar(int color) {
        getWindow().setStatusBarColor(color);
    }

    private void swap(Page p) {
        getWindow().setStatusBarColor(Ui.BG);
        View v = p.view();
        if (current != p) {
            container.removeAllViews();
            container.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 8));
            v.animate().alpha(1f).translationY(0).setDuration(200).start();
            current = p;
        }
        p.onShow();
        p.render();
    }

    private LinearLayout buildNav() {
        LinearLayout n = Ui.hbox(this);
        n.setBackgroundColor(Ui.SURFACE);
        n.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 10));
        String[] labels = {"Keşfet", "Şimdi", "Geçmiş", "Ayarlar"};
        int[] icons = {Views.Icon.DISCOVER, Views.Icon.NOW, Views.Icon.HISTORY, Views.Icon.SETTINGS};
        for (int i = 0; i < TABS; i++) {
            final int idx = i;
            LinearLayout item = Ui.vbox(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setClickable(true);
            item.setOnClickListener(v -> showTab(idx));
            LinearLayout pill = Ui.vbox(this);
            pill.setGravity(Gravity.CENTER);
            Views.Icon ic = new Views.Icon(this, icons[i], Ui.TEXT3);
            pill.addView(ic, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
            item.addView(pill, Ui.lp(Ui.dp(this, 60), Ui.dp(this, 32)));
            TextView l = Ui.text(this, labels[i], 12, Ui.TEXT3, Ui.MEDIUM);
            l.setPadding(0, Ui.dp(this, 4), 0, 0);
            item.addView(l);
            n.addView(item, Ui.lpw(1));
            navItems[i] = item;
            navIcons[i] = ic;
            navLabels[i] = l;
        }
        return n;
    }

    // ================= İzinler ve dış ekranlar =================

    public boolean hasNotificationAccess() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(
                new ComponentName(this, MusicListenerService.class));
    }

    public boolean canOverlay() {
        return Settings.canDrawOverlays(this);
    }

    public boolean batteryUnrestricted() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    public void openNotificationAccess() {
        safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    public void openOverlayPermission() {
        safeStart(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    public void openBatterySettings() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        if (!safeStart(i)) safeStart(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    public void openAppDetails() {
        safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    public boolean notificationsAllowed() {
        return engine.notifAllowed();
    }

    /** Android 13+ bildirim izni; daha eskilerde bildirim ayarlarını açar. */
    public void requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            safeStart(i);
        }
    }

    /** Koşu modu: adım sensörü ve "fiziksel aktivite" izni. */
    public void startRunMode(Runnable then) {
        if (!RunService.hasSensor(this)) {
            toast("Bu telefonda adım sensörü yok, koşu modu çalışamıyor");
            return;
        }
        withPermission(android.Manifest.permission.ACTIVITY_RECOGNITION, REQ_ACT, () -> {
            engine.discoverRun();
            toast("🏃 Koşu modu açık: koşmaya başla, tempo birkaç saniyede ölçülür");
            if (then != null) then.run();
        });
    }

    public void withLocationPermission(Runnable r) {
        withPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, REQ_LOC, r);
    }

    private void withPermission(String perm, int req, Runnable r) {
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            r.run();
            return;
        }
        afterPermission = r;
        requestPermissions(new String[]{perm}, req);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_ACT || requestCode == REQ_LOC) {
            Runnable r = afterPermission;
            afterPermission = null;
            boolean ok = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (ok && r != null) r.run();
            else if (!ok) toast(requestCode == REQ_ACT
                    ? "Koşu modu adım sayımı için \"Fiziksel aktivite\" iznine ihtiyaç duyar"
                    : "Konum izni verilmedi; şehrini arayarak seçebilirsin");
            return;
        }
        if (requestCode == REQ_NOTIF) {
            if (current != null) current.render();
            engine.refreshNotification();
        }
    }

    public void openYtMusic() {
        Intent i = getPackageManager().getLaunchIntentForPackage(Engine.YTM);
        if (i == null) toast("YouTube Music bu telefonda bulunamadı");
        else safeStart(i);
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Sesle anlat: Android'in konuşma tanıma ekranını Türkçe açar. */
    public void startSpeech() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nasıl hissediyorsun, neredesin?");
        try {
            startActivityForResult(i, REQ_SPEECH);
        } catch (Exception ex) {
            toast("Bu telefonda sesle yazma bulunamadı");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQ_EXPORT || requestCode == REQ_IMPORT) && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            final boolean export = requestCode == REQ_EXPORT;
            new Thread(() -> {
                String msg;
                try {
                    if (export) {
                        Backup.write(this, engine.store, uri);
                        msg = "✓ Yedek kaydedildi";
                    } else {
                        int[] r = Backup.read(this, engine.store, uri);
                        msg = "✓ İçe aktarıldı: " + r[0] + " beğeni, " + r[1] + " beğenmeme, " + r[2] + " geçmiş kaydı";
                    }
                } catch (Exception ex) {
                    msg = (export ? "Yedeklenemedi: " : "İçe aktarılamadı: ") + ex.getMessage();
                }
                final String m = msg;
                runOnUiThread(() -> {
                    toast(m);
                    WakeScheduler.schedule(this);
                    engine.refreshNotification();
                    if (current != null) current.render();
                });
            }).start();
            return;
        }
        if (requestCode != REQ_SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (res == null || res.isEmpty()) return;
        showTab(TAB_DISCOVER);
        ((DiscoverPage) tabs[TAB_DISCOVER]).onSpeech(res.get(0));
    }

    /** Yeni sürüm varsa (sürüm başına bir kez) sor. */
    private void checkUpdate() {
        Updater.check(this, false, () -> {
            Updater.Release r = Updater.available;
            android.content.SharedPreferences sp = getSharedPreferences("ayarlar", MODE_PRIVATE);
            if (r != null && !r.tag.equals(sp.getString("updateShownTag", ""))) {
                sp.edit().putString("updateShownTag", r.tag).apply();
                Updater.showDialog(this);
            }
            if (current != null) current.render();
        }, null);
    }

    /** Yedek dosyasını kaydet (kullanıcı yeri seçer). */
    public void exportBackup() {
        String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "akilli_sira_yedek_" + date + ".json");
        try { startActivityForResult(i, REQ_EXPORT); } catch (Exception ex) { toast("Dosya seçici açılamadı"); }
    }

    /** Yedek dosyasını seç ve içe aktar. */
    public void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { startActivityForResult(i, REQ_IMPORT); } catch (Exception ex) { toast("Dosya seçici açılamadı"); }
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ex) {
            return "";
        }
    }
}
