package com.akillisira.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Alarm ekranı: kilit ekranının üstünde açılır, uyanma listesini başlatır, sesi yavaşça yükseltir.
 * Müzik 45 saniyede başlamazsa (ör. internet yoksa) telefonun alarm sesi devreye girer.
 */
public class WakeActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private Engine e;
    private TextView clock, status;
    private Ringtone tone;
    private long started;
    private boolean finishedByUser;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date()));
            if (e.playing && e.nowTitle != null) {
                status.setText("▶  " + e.nowTitle + "\n" + (e.nowArtist == null ? "" : e.nowArtist));
                stopTone();
            } else if (tone == null) {
                long s = (System.currentTimeMillis() - started) / 1000;
                status.setText(s < 6 ? "Uyanma listen hazırlanıyor…" : "Müzik başlatılıyor…");
                if (s >= 45) startTone();
            } else {
                status.setText("Müzik başlatılamadı, alarm çalıyor.\nKilidi açarsan müziği başlatabilirim.");
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(WakeReceiver.NOTIF_ID);

        e = Engine.get(this);
        started = System.currentTimeMillis();
        setContentView(build());
        getWindow().setStatusBarColor(0xFFFF9A44);
        e.startWake();
        h.post(tick);
    }

    private LinearLayout build() {
        LinearLayout root = Ui.vbox(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFF9A44, 0xFFFC6076, 0xFF7B5CFF, 0xFF0B0B10});
        root.setBackground(g);
        int p = Ui.dp(this, 28);
        root.setPadding(p, Ui.dp(this, 90), p, Ui.dp(this, 40));

        TextView sun = Ui.text(this, "☀️", 56, 0xFFFFFFFF, Ui.REGULAR);
        root.addView(sun);
        clock = Ui.text(this, "", 84, 0xFFFFFFFF, Ui.BLACK);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);
        TextView hello = Ui.text(this, "Günaydın", 30, 0xFFFFFFFF, Ui.BOLD);
        root.addView(hello);
        TextView date = Ui.text(this, new SimpleDateFormat("d MMMM EEEE", Locale.forLanguageTag("tr")).format(new Date()),
                16, 0xDDFFFFFF, Ui.MEDIUM);
        date.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 36));
        root.addView(date);

        status = Ui.text(this, "", 16, 0xFFFFFFFF, Ui.MEDIUM);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0, 1.2f);
        root.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP));

        android.view.View spacer = new android.view.View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView awake = Ui.text(this, "☀️  Uyandım", 18, 0xFF0B0B10, Ui.BOLD);
        awake.setGravity(Gravity.CENTER);
        awake.setBackground(Ui.ripple(this, Ui.round(this, 0xFFFFFFFF, 30), 30));
        awake.setMinHeight(Ui.dp(this, 60));
        awake.setOnClickListener(v -> {
            finishedByUser = true;
            stopTone();
            e.stopWake(false);
            dismissKeyguard();
            finish();
        });
        root.addView(awake, Ui.lp(Ui.MATCH, Ui.WRAP));

        TextView snooze = Ui.text(this, "😴  10 dakika ertele", 16, 0xFFFFFFFF, Ui.BOLD);
        snooze.setGravity(Gravity.CENTER);
        snooze.setBackground(Ui.ripple(this, Ui.stroke(this, 0x33FFFFFF, 0x66FFFFFF, 30, 1), 30));
        snooze.setMinHeight(Ui.dp(this, 54));
        snooze.setOnClickListener(v -> {
            finishedByUser = true;
            stopTone();
            e.stopWake(true);
            WakeScheduler.snooze(this, 10);
            finish();
        });
        root.addView(snooze, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), this, 0, 12, 0, 0));
        return root;
    }

    private void dismissKeyguard() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) km.requestDismissKeyguard(this, null);
    }

    /** Yedek: telefonun alarm sesi + titreşim. */
    private void startTone() {
        if (tone != null) return;
        try {
            android.net.Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            tone = RingtoneManager.getRingtone(this, u);
            if (tone != null) {
                tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                if (Build.VERSION.SDK_INT >= 28) tone.setLooping(true);
                tone.play();
            }
            Vibrator vb = getSystemService(Vibrator.class);
            if (vb != null) vb.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 900}, 0));
        } catch (Exception ignored) {}
    }

    private void stopTone() {
        if (tone != null) {
            try { tone.stop(); } catch (Exception ignored) {}
            tone = null;
            Vibrator vb = getSystemService(Vibrator.class);
            if (vb != null) vb.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        stopTone();
        if (!finishedByUser) e.stopWake(false);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // geri tuşu alarmı kapatmasın; "Uyandım" ya da "Ertele" kullanılmalı
    }
}
