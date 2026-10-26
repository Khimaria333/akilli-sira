package com.akillisira.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Uygulama içi güncelleme: GitHub'daki son sürümü kontrol eder,
 * yeni sürüm varsa "neler yeni" ile sorar, indirir ve kurulum ekranını açar.
 */
public final class Updater {
    private Updater() {}

    private static final String LATEST = "https://api.github.com/repos/Khimaria333/akilli-sira/releases/latest";
    private static final long CHECK_EVERY = 6L * 3600 * 1000;

    public static final class Release {
        public int number;
        public String tag, url, notes;
    }

    /** Son bulunan sürüm (yoksa null). */
    public static volatile Release available;
    private static long downloadId = -1;

    public static int installedNumber(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Arka planda kontrol eder; force değilse 6 saatte bir. Yeni sürüm varsa onFound ana iş parçacığında çalışır. */
    public static void check(Context c, boolean force, Runnable onFound, Runnable onNone) {
        SharedPreferences p = c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
        long last = p.getLong("updateChecked", 0);
        if (!force && System.currentTimeMillis() - last < CHECK_EVERY) {
            if (available != null && onFound != null) onFound.run();
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Release r = fetch();
            p.edit().putLong("updateChecked", System.currentTimeMillis()).apply();
            int mine = installedNumber(c);
            main.post(() -> {
                if (r != null && r.number > mine) {
                    available = r;
                    if (onFound != null) onFound.run();
                } else {
                    available = null;
                    if (onNone != null) onNone.run();
                }
            });
        }).start();
    }

    private static Release fetch() {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(LATEST).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("User-Agent", "AkilliSira");
            if (h.getResponseCode() != 200) return null;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject(bo.toString(StandardCharsets.UTF_8.name()));
            Release r = new Release();
            r.tag = o.optString("tag_name");
            r.number = Integer.parseInt(r.tag.replaceAll("[^0-9]", ""));
            r.notes = o.optString("body");
            JSONArray as = o.optJSONArray("assets");
            for (int i = 0; as != null && i < as.length(); i++) {
                JSONObject a = as.optJSONObject(i);
                if (a != null && a.optString("name").endsWith(".apk")) r.url = a.optString("browser_download_url");
            }
            return r.url != null ? r : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    /** "Yeni sürüm var" diyaloğu. */
    public static void showDialog(Activity a) {
        Release r = available;
        if (r == null) return;
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 900) notes = notes.substring(0, 900) + "…";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("✨ Yeni sürüm hazır (" + r.tag + ")")
                .setMessage(notes.isEmpty() ? "İndirip kurmak ister misin?" : "Neler yeni:\n\n" + notes)
                .setPositiveButton("Güncelle", (d, w) -> download(a))
                .setNegativeButton("Sonra", null)
                .show();
    }

    /** İndirir; bitince kurulum ekranını açar. */
    public static void download(Activity a) {
        Release r = available;
        if (r == null) return;
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            android.widget.Toast.makeText(a, "Önce \"Bu kaynaktan yüklemeye izin ver\"i aç, sonra tekrar Güncelle'ye bas",
                    android.widget.Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {}
            return;
        }
        DownloadManager dm = a.getSystemService(DownloadManager.class);
        if (dm == null) return;
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(r.url))
                .setTitle("Akıllı Sıra " + r.tag)
                .setDescription("Güncelleme indiriliyor")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, "AkilliSira-" + r.tag + ".apk");
        downloadId = dm.enqueue(req);
        android.widget.Toast.makeText(a, "Güncelleme indiriliyor…", android.widget.Toast.LENGTH_SHORT).show();
        watch(a, dm, downloadId);
    }

    private static void watch(Activity a, DownloadManager dm, long id) {
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            int tries;

            @Override
            public void run() {
                int status = -1;
                try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (c != null && c.moveToFirst()) {
                        status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    }
                } catch (Exception ignored) {}
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri u = dm.getUriForDownloadedFile(id);
                    if (u != null) {
                        Intent i = new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(u, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { a.startActivity(i); } catch (Exception ignored) {}
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    android.widget.Toast.makeText(a, "İndirme başarısız oldu, tekrar dene",
                            android.widget.Toast.LENGTH_LONG).show();
                } else if (++tries < 300) {
                    h.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }
}
