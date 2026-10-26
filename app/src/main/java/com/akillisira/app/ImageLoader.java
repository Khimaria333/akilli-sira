package com.akillisira.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Albüm kapaklarını indirir, bellekte saklar ve ImageView'a koyar. */
public final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void load(String url, ImageView iv) {
        iv.setTag(url);
        if (url == null) {
            iv.setImageDrawable(null);
            return;
        }
        Bitmap b = CACHE.get(url);
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }
        iv.setImageDrawable(null);
        POOL.submit(() -> {
            Bitmap bmp = download(url);
            if (bmp == null) return;
            CACHE.put(url, bmp);
            MAIN.post(() -> {
                if (url.equals(iv.getTag())) {
                    iv.setAlpha(0f);
                    iv.setImageBitmap(bmp);
                    iv.animate().alpha(1f).setDuration(220).start();
                }
            });
        });
    }

    /** Arka plan iş parçacığında: önbellekten ya da indirerek (bulamazsa null). */
    public static Bitmap getSync(String url) {
        if (url == null) return null;
        Bitmap b = CACHE.get(url);
        if (b != null) return b;
        b = download(url);
        if (b != null) CACHE.put(url, b);
        return b;
    }

    /** Önbellekte varsa döndürür, yoksa null. */
    public static Bitmap cached(String url) {
        return url == null ? null : CACHE.get(url);
    }

    /** Arka planda indirir, bitince ana iş parçacığında haber verir. */
    public static void fetch(String url, java.util.function.Consumer<Bitmap> done) {
        if (url == null) return;
        POOL.submit(() -> {
            Bitmap bmp = CACHE.get(url);
            if (bmp == null) {
                bmp = download(url);
                if (bmp != null) CACHE.put(url, bmp);
            }
            final Bitmap out = bmp;
            if (out != null) MAIN.post(() -> done.accept(out));
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            InputStream in = c.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
