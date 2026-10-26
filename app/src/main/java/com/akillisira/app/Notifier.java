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

    private static final String CHANNEL = "siradaki";
    private static final int NOTIF_ID = 7;

    private final Context app;
    private final NotificationManager nm;
    private String lastNotifSig, lastWidgetSig;
    private boolean channelReady;

    public Notifier(Context app) {
        this.app = app;
        nm = app.getSystemService(NotificationManager.class);
    }

    /** Motor her değiştiğinde çağrılır; içerik değişmediyse hiçbir şey yapmaz. */
    public void update(Engine e) {
        try { updateNotification(e); } catch (Exception ignored) {}
        try { updateWidgets(e); } catch (Exception ignored) {}
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
        String sig = e.nowTitle + "|" + (p != null ? p.key() + "|" + (p.coverUrl != null) : "-") + "|"
                + e.computing + "|" + e.isAuto() + "|" + e.sleepFinishing + "|" + (e.sleepRemainingMs() >= 0);
        if (sig.equals(lastNotifSig)) return;
        lastNotifSig = sig;

        if (!channelReady) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Sıradaki şarkı", NotificationManager.IMPORTANCE_LOW);
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

        if (p != null && !e.sleepFinishing) {
            b.addAction(new Notification.Action.Builder(null, "⏭  Şimdi geç", action(ActionReceiver.SKIP)).build());
            b.addAction(new Notification.Action.Builder(null, "👎  Başka öner", action(ActionReceiver.DISLIKE_NEXT)).build());
        }
        b.addAction(new Notification.Action.Builder(null, "👎  Çalanı geç", action(ActionReceiver.DISLIKE_NOW)).build());

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

    public void updateWidgets(Engine e) {
        AppWidgetManager wm = AppWidgetManager.getInstance(app);
        ComponentName cn = new ComponentName(app, WidgetProvider.class);
        int[] ids = wm.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;
        Track p = e.pick;
        String sig = e.nowTitle + "|" + e.nowArtist + "|" + (p != null ? p.key() : "-") + "|" + e.computing
                + "|" + (e.seed != null && e.seed.coverUrl != null) + "|" + (p != null && p.coverUrl != null);
        if (sig.equals(lastWidgetSig)) return;
        lastWidgetSig = sig;

        RemoteViews rv = new RemoteViews(app.getPackageName(), R.layout.widget);
        if (e.nowTitle == null) {
            rv.setTextViewText(R.id.w_now_title, "Bir şarkı aç");
            rv.setTextViewText(R.id.w_now_artist, "YouTube Music'te çalmaya başla");
            rv.setTextViewText(R.id.w_next_title, "—");
            rv.setImageViewResource(R.id.w_cover, R.drawable.ic_launcher_foreground);
        } else {
            rv.setTextViewText(R.id.w_now_title, e.nowTitle);
            rv.setTextViewText(R.id.w_now_artist, e.nowArtist);
            rv.setTextViewText(R.id.w_next_title, p != null ? p.title + " · " + p.artist
                    : e.computing ? "Seçiliyor…" : "—");
            String cover = e.seed != null ? e.seed.coverUrl : null;
            Bitmap bmp = cover != null ? ImageLoader.cached(cover) : null;
            if (bmp != null) rv.setImageViewBitmap(R.id.w_cover, bmp);
            else {
                rv.setImageViewResource(R.id.w_cover, R.drawable.ic_launcher_foreground);
                if (cover != null) ImageLoader.fetch(cover, x -> { lastWidgetSig = null; updateWidgets(e); });
            }
        }
        boolean hasNext = p != null;
        rv.setViewVisibility(R.id.w_skip, hasNext ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.w_dislike, hasNext ? View.VISIBLE : View.GONE);
        rv.setOnClickPendingIntent(R.id.w_skip, action(ActionReceiver.SKIP));
        rv.setOnClickPendingIntent(R.id.w_dislike, action(ActionReceiver.DISLIKE_NEXT));
        rv.setOnClickPendingIntent(R.id.w_root, openApp());
        wm.updateAppWidget(ids, rv);
    }

    /** Bir sonraki güncellemede bildirimi yeniden kur. */
    public void reset() {
        lastNotifSig = null;
        lastWidgetSig = null;
    }

    /** Widget eklendiğinde motor henüz veri almamışsa da bir şey gösterebilmek için. */
    public void forceWidgets(Engine e) {
        lastWidgetSig = null;
        updateWidgets(e);
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
