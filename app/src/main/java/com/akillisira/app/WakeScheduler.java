package com.akillisira.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** "Müzikle uyan" alarmını kurar: seçilen saat ve günler. */
public final class WakeScheduler {
    private WakeScheduler() {}

    public static final String ACTION_WAKE = "com.akillisira.app.WAKE";
    public static final String ACTION_SNOOZE = "com.akillisira.app.WAKE_SNOOZE";
    private static final int RC_WAKE = 100, RC_SHOW = 101, RC_SNOOZE = 102;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context c) { return prefs(c).getBoolean("wakeOn", false); }
    public static int hour(Context c) { return prefs(c).getInt("wakeH", 7); }
    public static int minute(Context c) { return prefs(c).getInt("wakeM", 30); }
    /** Bit 0 = Pazartesi ... bit 6 = Pazar; 0 = bir kez. */
    public static int days(Context c) { return prefs(c).getInt("wakeDays", 0b0011111); }
    public static int volumePct(Context c) { return prefs(c).getInt("wakeVol", 60); }

    public static void set(Context c, boolean on, int h, int m, int days, int vol) {
        prefs(c).edit().putBoolean("wakeOn", on).putInt("wakeH", h).putInt("wakeM", m)
                .putInt("wakeDays", days).putInt("wakeVol", vol).apply();
        schedule(c);
    }

    /** Bir sonraki alarmı kurar (kapalıysa iptal eder). */
    public static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent fire = firePi(c, ACTION_WAKE, RC_WAKE);
        am.cancel(fire);
        if (!isOn(c)) return;
        long t = next(c);
        if (t <= 0) return;
        setAlarm(c, am, t, fire);
    }

    public static void snooze(Context c, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        setAlarm(c, am, System.currentTimeMillis() + minutes * 60000L, firePi(c, ACTION_SNOOZE, RC_SNOOZE));
    }

    private static void setAlarm(Context c, AlarmManager am, long t, PendingIntent fire) {
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            Intent show = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent showPi = PendingIntent.getActivity(c, RC_SHOW, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, showPi), fire);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, fire);
        }
    }

    private static PendingIntent firePi(Context c, String action, int rc) {
        Intent i = new Intent(c, WakeReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean exactAllowed(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am == null || Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    static int dayIndex(Calendar cal) {
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Pazartesi = 0
    }

    /** Bir sonraki çalma zamanı (ms), yoksa 0. */
    public static long next(Context c) {
        int h = hour(c), m = minute(c), d = days(c);
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < 8; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, i);
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now.getTimeInMillis() + 20000) continue;
            if (d == 0 || (d & (1 << dayIndex(cal))) != 0) return cal.getTimeInMillis();
        }
        return 0;
    }

    /** "Yarın 07:30", "Cuma 07:30" gibi. */
    public static String describeNext(Context c) {
        long t = next(c);
        if (t <= 0) return "—";
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        Calendar today = Calendar.getInstance();
        String hm = String.format(Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        int diff = cal.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR);
        if (diff < 0) diff += today.getActualMaximum(Calendar.DAY_OF_YEAR);
        if (diff == 0) return "Bugün " + hm;
        if (diff == 1) return "Yarın " + hm;
        return new SimpleDateFormat("EEEE", Locale.forLanguageTag("tr")).format(cal.getTime()) + " " + hm;
    }
}
