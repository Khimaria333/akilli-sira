package com.akillisira.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Tek aktivite: kurulum ya da alt menülü üç sekme (Keşfet / Şimdi / Ayarlar). */
public class MainActivity extends Activity implements Engine.Listener {

    public static final int TAB_DISCOVER = 0, TAB_NOW = 1, TAB_SETTINGS = 2;

    private Engine engine;
    private FrameLayout container;
    private LinearLayout nav;
    private Page setup;
    private Page[] tabs;
    private Page current;
    private int currentTab = -1;
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final Views.Icon[] navIcons = new Views.Icon[3];
    private final TextView[] navLabels = new TextView[3];
    private boolean setupDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = Engine.get(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.SURFACE);

        setup = new SetupPage(this, engine);
        tabs = new Page[]{new DiscoverPage(this, engine), new NowPage(this, engine), new SettingsPage(this, engine)};

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        nav = buildNav();
        root.addView(nav, Ui.lp(Ui.MATCH, Ui.WRAP));
        setContentView(root);

        setupDone = getSharedPreferences("ayarlar", MODE_PRIVATE).getBoolean("setupDone", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.addListener(this);
        engine.setUiVisible(true);
        boolean ready = hasNotificationAccess() && canOverlay();
        if (!ready || !setupDone) {
            showSetup();
        } else if (currentTab < 0) {
            showTab(engine.nowTitle != null ? TAB_NOW : TAB_DISCOVER);
        } else {
            current.onShow();
            current.render();
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

    public void finishSetup() {
        setupDone = true;
        getSharedPreferences("ayarlar", MODE_PRIVATE).edit().putBoolean("setupDone", true).apply();
        showTab(engine.nowTitle != null ? TAB_NOW : TAB_DISCOVER);
    }

    public void showTab(int tab) {
        nav.setVisibility(View.VISIBLE);
        currentTab = tab;
        swap(tabs[tab]);
        for (int i = 0; i < 3; i++) {
            boolean on = i == tab;
            navIcons[i].setColor(on ? Ui.TEXT : Ui.TEXT3);
            navLabels[i].setTextColor(on ? Ui.TEXT : Ui.TEXT3);
            navLabels[i].setTypeface(on ? Ui.BOLD : Ui.MEDIUM);
            View pillBg = navItems[i].getChildAt(0);
            pillBg.setBackground(on ? Ui.gradient(this, 0x55FF4D6D, 0x557B5CFF, 16) : null);
        }
    }

    private void swap(Page p) {
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
        String[] labels = {"Keşfet", "Şimdi", "Ayarlar"};
        int[] icons = {Views.Icon.DISCOVER, Views.Icon.NOW, Views.Icon.SETTINGS};
        for (int i = 0; i < 3; i++) {
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
