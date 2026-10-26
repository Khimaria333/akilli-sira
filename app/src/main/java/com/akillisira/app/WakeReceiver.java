package com.akillisira.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm zamanı geldiğinde (ya da telefon yeniden başlayınca) çalışır. */
public class WakeReceiver extends BroadcastReceiver {
    static final int NOTIF_ID = 31;

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (WakeScheduler.ACTION_WAKE.equals(a) || WakeScheduler.ACTION_SNOOZE.equals(a)) {
            if (WakeScheduler.ACTION_WAKE.equals(a) && WakeScheduler.days(c) == 0) {
                // tek seferlik alarm: kapat
                c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE).edit().putBoolean("wakeOn", false).apply();
            }
            WakeScheduler.schedule(c);   // bir sonraki günü kur
            Intent w = new Intent(c, WakeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                c.startActivity(w);
            } catch (Exception ignored) {}
            postFullScreen(c, w);   // ekran açılmadıysa bildirim üzerinden aç
        } else {
            // açılış, saat değişimi, güncelleme
            WakeScheduler.schedule(c);
        }
    }

    private static void postFullScreen(Context c, Intent w) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel("uyan", "Müzikle uyan", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(c, 33, w,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "uyan")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("☀️ Günaydın")
                .setContentText("Uyanma listen başlıyor")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify(NOTIF_ID, n); } catch (Exception ignored) {}
    }
}
