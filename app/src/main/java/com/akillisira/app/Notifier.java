package com.akillisira.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

/** Bildirimdeki "Sıradaki" kartı ve ana ekran widget'ı. */
public final class Notifier {

    private static final String CHANNEL = "siradaki_v2";   // kilit ekranında görünen kanal
    private static final int NOTIF_ID = 7;

    private final Context app;
    private final NotificationManager nm;
    private String lastNotifSig;
    private boolean channelReady;

    public Notifier(Context app) {
        this.app = app;
        nm = app.getSystemService(NotificationManager.class);
    }

    /** Motor her değiştiğinde çağrılır; içerik değişmediyse hiçbir şey yapmaz. */
    public void update(Engine e) {
        try { updateNotification(e); } catch (Exception ex) { report(e, "Bildirim", ex); }
        try { updateWidgets(e); } catch (Exception ex) { report(e, "Widget", ex); }
    }

    // =====================================================================
    // Bildirim
    // =====================================================================

    public boolean canPost() {
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return nm != null && nm.areNotificationsEnabled();
    }

    private void updateNotification(Engine e) {
        if (nm == null) return;
        boolean show = e.isNotif() && e.controller != null && e.nowTitle != null && canPost();
        if (!show) {
            if (lastNotifSig != null) {
                nm.cancel(NOTIF_ID);
                lastNotifSig = null;
            }
            return;
        }
        Track p = e.pick;
        String sig = e.nowTitle + "|" + e.playing + "|" + (p != null ? p.key() + "|" + (p.coverUrl != null) : "-") + "|"
                + e.computing + "|" + e.isAuto() + "|" + e.sleepFinishing + "|" + (e.sleepRemainingMs() >= 0);
        if (sig.equals(lastNotifSig)) return;
        lastNotifSig = sig;

        if (!channelReady) {
            nm.deleteNotificationChannel("siradaki");   // eski, sessiz kanal
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Sıradaki şarkı", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);          // ses ve titreşim yok, ama kilit ekranında görünür
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.setDescription("Çalan ve sıradaki şarkı, hızlı geçiş düğmeleri");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            channelReady = true;
        }

        String title, text;
        if (e.sleepFinishing) {
            title = "😴 Bu şarkı bitince müzik duracak";
            text = "Şu an: " + e.nowTitle;
        } else if (p != null) {
            title = "Sıradaki: " + p.title;
            text = p.artist + (p.reason.isEmpty() ? "" : "  ·  " + p.reason);
        } else {
            title = e.computing ? "Sıradaki seçiliyor…" : "Sıradaki henüz belli değil";
            text = "Şu an: " + e.nowTitle;
        }

        Notification.Builder b = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(e.isAuto() ? (e.sleepRemainingMs() > 0 ? "😴 Uyku zamanlayıcı açık" : "Otomatik") : "Manuel")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Ui.ACCENT)
                .setContentIntent(openApp())
                .setStyle(new Notification.BigTextStyle().bigText(text + "\nŞu an: " + e.nowTitle + " – " + e.nowArtist));

        b.addAction(new Notification.Action.Builder(null, e.playing ? "⏸  Durdur" : "▶  Devam",
                action(ActionReceiver.PLAY_PAUSE)).build());
        if (p != null && !e.sleepFinishing) {
            b.addAction(new Notification.Action.Builder(null, "⏭  Geç", action(ActionReceiver.SKIP)).build());
            b.addAction(new Notification.Action.Builder(null, "👎  Başka öner", action(ActionReceiver.DISLIKE_NEXT)).build());
        }

        if (p != null && p.coverUrl != null) {
            Bitmap bmp = ImageLoader.cached(p.coverUrl);
            if (bmp != null) b.setLargeIcon(bmp);
            else ImageLoader.fetch(p.coverUrl, x -> { lastNotifSig = null; update(e); });
        }
        nm.notify(NOTIF_ID, b.build());
    }

    // =====================================================================
    // Widget
    // =====================================================================

    private Widgets widgets;

    private Widgets widgets() {
        if (widgets == null) widgets = new Widgets(app);
        return widgets;
    }

    public void updateWidgets(Engine e) {
        widgets().update(e, false);
    }

    /** Çalarken birkaç saniyede bir ilerleme ve geri sayımı tazeler. */
    public void tickWidgets(Engine e) {
        try { widgets().tick(e); } catch (Exception ex) { report(e, "Widget", ex); }
    }

    private String lastError;

    private void report(Engine e, String what, Exception ex) {
        String msg = what + " gösterilemedi: " + ex;
        if (!msg.equals(lastError)) {
            lastError = msg;
            e.log(msg);
        }
    }

    /** Bir sonraki güncellemede bildirimi yeniden kur. */
    public void reset() {
        lastNotifSig = null;
    }

    /** Widget eklendiğinde motor henüz veri almamışsa da bir şey gösterebilmek için. */
    public void forceWidgets(Engine e) {
        try { widgets().update(e, true); } catch (Exception ex) { report(e, "Widget", ex); }
    }

    // =====================================================================

    private PendingIntent action(String act) {
        Intent i = new Intent(app, ActionReceiver.class).setAction(act);
        return PendingIntent.getBroadcast(app, act.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(app, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
