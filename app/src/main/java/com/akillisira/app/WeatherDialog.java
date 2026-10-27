package com.akillisira.app;

import android.app.AlertDialog;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Hava durumu için şehir seçimi: arama ya da yaklaşık konum. */
public final class WeatherDialog {
    private WeatherDialog() {}

    public static void show(MainActivity a, Runnable onSet) {
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 4));

        TextView info = Ui.text(a, "Hava durumu Open-Meteo'dan alınır (ücretsiz, hesap gerekmez). "
                + "Konumun sadece hava durumunu sormak için kullanılır.", 13, Ui.TEXT2, Ui.REGULAR);
        info.setLineSpacing(0, 1.12f);
        box.addView(info);

        TextView here = Ui.secondaryButton(a, "📍  Konumumu kullan");
        box.addView(here, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 14, 0, 0));

        EditText q = new EditText(a);
        q.setHint("ya da şehir ara: İzmir, Kadıköy…");
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setTextColor(Ui.TEXT);
        q.setHintTextColor(Ui.TEXT3);
        q.setTextSize(15);
        q.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        int ip = Ui.dp(a, 12);
        q.setPadding(ip, ip, ip, ip);
        box.addView(q, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 12, 0, 0));

        LinearLayout results = Ui.vbox(a);
        box.addView(results, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 8, 0, 0));

        AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("🌦 Şehrini seç")
                .setView(box)
                .setNegativeButton("Vazgeç", null)
                .create();

        Runnable done = () -> {
            Weather.setOn(a, true);
            d.dismiss();
            Weather.refresh(a, true, () -> a.runOnUiThread(() -> {
                Weather.Now w = Weather.current();
                if (w != null) a.toast("🌦 " + Weather.placeName(a) + ": " + w.label());
                if (onSet != null) onSet.run();
            }));
        };

        Runnable search = () -> {
            String s = q.getText().toString().trim();
            if (s.length() < 2) return;
            results.removeAllViews();
            TextView wait = Ui.text(a, "Aranıyor…", 13, Ui.TEXT3, Ui.REGULAR);
            results.addView(wait);
            new Thread(() -> {
                List<Weather.Place> l = Weather.search(s);
                a.runOnUiThread(() -> {
                    results.removeAllViews();
                    if (l.isEmpty()) {
                        results.addView(Ui.text(a, "Bulunamadı. Başka bir yazımla dene.", 13, Ui.WARN, Ui.MEDIUM));
                        return;
                    }
                    for (Weather.Place pl : l) {
                        TextView r = Ui.text(a, "📍  " + pl.name, 14.5f, Ui.TEXT, Ui.MEDIUM);
                        r.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));
                        r.setBackground(Ui.ripple(a, Ui.round(a, 0x00000000, 10), 10));
                        r.setOnClickListener(v -> {
                            Weather.setPlace(a, pl.name.split(" · ")[0], pl.lat, pl.lon);
                            done.run();
                        });
                        results.addView(r, Ui.lp(Ui.MATCH, Ui.WRAP));
                    }
                });
            }).start();
        };
        q.setOnEditorActionListener((v, id, ev) -> {
            search.run();
            return true;
        });

        here.setOnClickListener(v -> a.withLocationPermission(() -> {
            here.setText("Konum alınıyor…");
            locate(a, loc -> {
                if (loc == null) {
                    here.setText("📍  Konumumu kullan");
                    a.toast("Konum alınamadı. Konum açık mı? İstersen şehir ara.");
                    return;
                }
                Weather.setPlace(a, "Konumum", loc.getLatitude(), loc.getLongitude());
                done.run();
            });
        }));

        d.show();
    }

    /** Yaklaşık konum (en taze bilinen; yoksa bir kez ister). */
    @SuppressWarnings("MissingPermission")
    static void locate(MainActivity a, java.util.function.Consumer<Location> cb) {
        LocationManager lm = a.getSystemService(LocationManager.class);
        if (lm == null) { cb.accept(null); return; }
        Location best = null;
        try {
            for (String pr : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
                Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (Exception ignored) {}
        if (best != null && System.currentTimeMillis() - best.getTime() < 6L * 3600 * 1000) {
            cb.accept(best);
            return;
        }
        final Location fallback = best;
        if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, a.getMainExecutor(),
                        l -> cb.accept(l != null ? l : fallback));
                return;
            } catch (Exception ignored) {}
        }
        cb.accept(fallback);
    }
}
