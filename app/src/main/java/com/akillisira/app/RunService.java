package com.akillisira.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayDeque;

/**
 * Koşu modu: telefonun adım sensöründen dakikadaki adım sayısını (kadans) ölçer.
 * Ahenk sıradaki şarkıyı bu tempoya uyan BPM'den seçer. Ekran kapalıyken de çalışsın diye ön plan servisi.
 */
public class RunService extends Service implements SensorEventListener {

    static final String STOP = "com.akillisira.app.RUN_STOP";
    private static final String CHANNEL = "kosu";
    private static final int NOTIF_ID = 31;
    private static final long WINDOW_MS = 12000;

    private SensorManager sm;
    private final ArrayDeque<Long> steps = new ArrayDeque<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private int lastShown = -1;

    public static boolean hasSensor(Context c) {
        SensorManager s = c.getSystemService(SensorManager.class);
        return s != null && s.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null;
    }

    public static void start(Context c) {
        Intent i = new Intent(c, RunService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, RunService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            Engine.get(this).stopRun();
            stopSelf();
            return START_NOT_STICKY;
        }
        Engine.get(this).onRunServiceStarted();
        Notification n = build(0);
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            else startForeground(NOTIF_ID, n);
        } catch (Exception ex) {   // izin yok ya da arka plandan başlatılamadı
            Engine.get(this).log("🏃 Koşu modu başlatılamadı: " + ex.getMessage());
            Engine.get(this).stopRun();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (sm == null) {
            sm = getSystemService(SensorManager.class);
            Sensor s = sm != null ? sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) : null;
            if (s == null) {
                Engine.get(this).log("🏃 Adım sensörü bulunamadı, koşu modu kapatıldı");
                Engine.get(this).stopRun();
                stopSelf();
                return START_NOT_STICKY;
            }
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
            h.postDelayed(tick, 3000);
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent ev) {
        long now = SystemClock.elapsedRealtime();
        int n = Math.max(1, Math.round(ev.values.length > 0 ? ev.values[0] : 1));
        for (int i = 0; i < n; i++) steps.addLast(now);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /** Her 3 saniyede kadansı hesapla, motora ve bildirime bildir. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            while (!steps.isEmpty() && now - steps.peekFirst() > WINDOW_MS) steps.pollFirst();
            int cad = 0;
            if (steps.size() >= 8) {
                long span = Math.max(1, steps.peekLast() - steps.peekFirst());
                if (now - steps.peekLast() < 3000) cad = (int) Math.round((steps.size() - 1) * 60000.0 / span);
            }
            if (cad > 0 && (cad < 60 || cad > 230)) cad = 0;   // anlamsız ölçüm
            Engine.get(RunService.this).setCadence(cad);
            if (Math.abs(cad - lastShown) >= 2 || (cad == 0) != (lastShown == 0)) {
                lastShown = cad;
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIF_ID, build(cad));
            }
            h.postDelayed(this, 3000);
        }
    };

    private Notification build(int cad) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Koşu modu", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Koşu modu açıkken adım temponu gösterir");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Engine e = Engine.get(this);
        int target = e.runBpm();
        String title = cad > 0 ? "🏃 " + cad + " adım/dk · hedef " + target + " BPM" : "🏃 Koşu modu: adım bekleniyor…";
        String text = e.pick != null ? "Sıradaki: " + e.pick.title + " · " + e.pick.artist
                : "Tempona uyan şarkılar seçiliyor";
        PendingIntent open = PendingIntent.getActivity(this, 31,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 32, new Intent(this, RunService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColor(0xFFFF4D6D)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Koşuyu bitir", stop).build());
        return b.build();
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (sm != null) sm.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
