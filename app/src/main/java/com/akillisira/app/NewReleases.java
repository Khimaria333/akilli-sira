package com.akillisira.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Yeni çıkanlar: beğendiğin sanatçıların son albüm/single'ları.
 * Günde bir kez arka planda bakar; yeni bir şey çıktıysa bildirim gönderir.
 */
public class NewReleases extends JobService {

    private static final int JOB_ID = 4711;
    private static final String CHANNEL = "yeni_cikanlar";
    static final String EXTRA_OPEN = "openNew";

    // ------------------------------------------------------------------
    // Hesaplama
    // ------------------------------------------------------------------

    /** Beğenilen şarkılardan sanatçılar (en çok beğenilen önce): kimlik → ad. */
    static Map<Long, String> likedArtists(Store store, int max) {
        Map<Long, Integer> count = new LinkedHashMap<>();
        Map<Long, String> name = new LinkedHashMap<>();
        for (Store.Liked l : store.liked()) {
            if (l.artistId <= 0) continue;
            count.put(l.artistId, count.getOrDefault(l.artistId, 0) + 1);
            name.put(l.artistId, Text.mainArtist(l.artist));
        }
        List<Long> ids = new ArrayList<>(count.keySet());
        ids.sort((a, b) -> count.get(b) - count.get(a));
        Map<Long, String> out = new LinkedHashMap<>();
        for (Long id : ids) {
            out.put(id, name.get(id));
            if (out.size() >= max) break;
        }
        return out;
    }

    static String daysAgo(int days) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(System.currentTimeMillis() - days * 86400000L));
    }

    /** Son 'days' gün içinde çıkanlar (en yeni önce). Ağ iş parçacığında çağır. */
    static List<Deezer.Album> recent(Deezer dz, Store store, int days, int maxArtists) {
        String since = daysAgo(days);
        String today = daysAgo(-1);
        List<Deezer.Album> out = new ArrayList<>();
        for (Map.Entry<Long, String> a : likedArtists(store, maxArtists).entrySet()) {
            try {
                for (Deezer.Album al : dz.artistAlbums(a.getKey(), a.getValue(), 6)) {
                    if (al.date.compareTo(since) >= 0 && al.date.compareTo(today) <= 0) out.add(al);
                }
            } catch (Exception ignored) {}
        }
        out.sort((x, y) -> y.date.compareTo(x.date));
        return out;
    }

    /** "3 gün önce", "bugün" */
    static String age(String date) {
        try {
            long t = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date).getTime();
            long d = Math.max(0, (System.currentTimeMillis() - t) / 86400000L);
            if (d == 0) return "bugün";
            if (d == 1) return "dün";
            if (d < 7) return d + " gün önce";
            if (d < 30) return (d / 7) + " hafta önce";
            return (d / 30) + " ay önce";
        } catch (Exception e) {
            return date;
        }
    }

    static String typeWord(String type) {
        if ("single".equals(type)) return "single";
        if ("ep".equals(type)) return "EP";
        return "albüm";
    }

    // ------------------------------------------------------------------
    // Zamanlama
    // ------------------------------------------------------------------

    static boolean isOn(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE).getBoolean("newNotif", true);
    }

    static void setOn(Context c, boolean on) {
        c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE).edit().putBoolean("newNotif", on).apply();
        schedule(c);
    }

    /** Günlük kontrolü kur (açıksa) ya da kaldır. */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (!isOn(c)) {
            js.cancel(JOB_ID);
            return;
        }
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, NewReleases.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24L * 3600 * 1000)
                .setPersisted(true)
                .build();
        try { js.schedule(info); } catch (Exception ignored) {}
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                check(getApplicationContext());
            } catch (Exception ignored) {}
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    /** Yeni (daha önce bildirilmemiş, son 10 gün) çıkışları bildir. */
    static void check(Context c) {
        // Motoru ayağa kaldırmadan: sadece beğenileri oku, Deezer'a sor
        DiskCache.init(c.getApplicationContext());
        List<Deezer.Album> list = recent(new Deezer(), new Store(c.getApplicationContext()), 10, 30);
        SharedPreferences p = c.getSharedPreferences("yeni_cikanlar", Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(p.getStringSet("seen", new HashSet<>()));
        boolean first = !p.getBoolean("init", false);
        List<Deezer.Album> fresh = new ArrayList<>();
        for (Deezer.Album al : list) if (seen.add(String.valueOf(al.id))) fresh.add(al);
        p.edit().putStringSet("seen", seen).putBoolean("init", true).apply();
        if (first || fresh.isEmpty()) return;   // ilk çalışmada eskileri bildirme
        notify(c, fresh);
    }

    private static void notify(Context c, List<Deezer.Album> fresh) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Yeni çıkanlar", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Beğendiğin sanatçılar yeni şarkı çıkarınca");
            nm.createNotificationChannel(ch);
        }
        Deezer.Album a = fresh.get(0);
        String title = fresh.size() == 1
                ? "🆕 " + a.artist + " yeni " + typeWord(a.type) + " çıkardı"
                : "🆕 Sevdiğin sanatçılardan " + fresh.size() + " yeni çıkış";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < fresh.size() && i < 5; i++) {
            Deezer.Album x = fresh.get(i);
            if (i > 0) text.append('\n');
            text.append(x.artist).append(" — ").append(x.title);
        }
        Intent open = new Intent(c, MainActivity.class).putExtra(EXTRA_OPEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 4711, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text.toString().split("\n")[0])
                .setStyle(new Notification.BigTextStyle().bigText(text.toString()))
                .setColor(0xFFFF4D6D)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try { nm.notify(4711, b.build()); } catch (Exception ignored) {}
    }
}
