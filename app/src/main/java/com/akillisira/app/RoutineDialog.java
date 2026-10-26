package com.akillisira.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Rutin ekle" penceresi: tür (ruh hali / tür / cümle), değer, günler ve saat aralığı. */
public final class RoutineDialog {
    private RoutineDialog() {}

    public static void show(MainActivity a, Runnable onSaved) {
        Routines.Routine r = new Routines.Routine();
        ScrollView sv = new ScrollView(a);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 8));
        sv.addView(box);

        box.addView(label(a, "Ne çalsın?"));
        LinearLayout valueBox = Ui.vbox(a);
        final String[] kinds = {"mood", "genre", "vibe"};
        final List<TextView> chips = new ArrayList<>();
        final EditText vibe = new EditText(a);
        vibe.setHint("örn. akşamüstü sahilde sakin bir şeyler");
        vibe.setTextColor(Ui.TEXT);
        vibe.setHintTextColor(Ui.TEXT3);
        vibe.setTextSize(14);
        vibe.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        int ip = Ui.dp(a, 12);
        vibe.setPadding(ip, ip, ip, ip);

        Runnable[] fill = new Runnable[1];
        Ui.Segmented kindSeg = Ui.segmented(a, new String[]{"🎭 Ruh hali", "🎵 Tür", "✍️ Cümle"}, 0, i -> {
            r.kind = kinds[i];
            r.value = i == 0 ? "odak" : i == 1 ? Genres.TR[0].id : "";
            fill[0].run();
        });
        box.addView(kindSeg.view, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(valueBox, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 10, 0, 0));

        fill[0] = () -> {
            valueBox.removeAllViews();
            chips.clear();
            if ("vibe".equals(r.kind)) {
                valueBox.addView(vibe, Ui.lp(Ui.MATCH, Ui.WRAP));
                return;
            }
            LinearLayout row = Ui.hbox(a);
            List<String[]> items = new ArrayList<>();
            if ("mood".equals(r.kind)) for (Moods.Mood m : Moods.MOODS) items.add(new String[]{m.id, m.emoji + " " + m.label});
            else {
                for (Genres.Genre g : Genres.TR) items.add(new String[]{g.id, g.emoji + " " + g.label});
                for (Genres.Genre g : Genres.GLOBAL) items.add(new String[]{g.id, g.emoji + " " + g.label + " (global)"});
            }
            for (String[] it : items) {
                TextView ch = Ui.chip(a, it[1], it[0].equals(r.value));
                ch.setTextSize(13);
                ch.setTag(it[0]);
                ch.setOnClickListener(v -> {
                    r.value = it[0];
                    for (TextView c : chips) Ui.setChip(a, c, c.getTag().equals(r.value));
                });
                chips.add(ch);
                row.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
            }
            valueBox.addView(Ui.hscroll(a, row), Ui.lp(Ui.MATCH, Ui.WRAP));
        };
        fill[0].run();

        box.addView(label(a, "Hangi günler?"));
        LinearLayout days = Ui.hbox(a);
        String[] dn = {"Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"};
        List<TextView> dayChips = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView ch = Ui.chip(a, dn[i], true);
            ch.setTextSize(12.5f);
            ch.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            ch.setOnClickListener(v -> {
                r.days ^= (1 << bit);
                Ui.setChip(a, ch, (r.days & (1 << bit)) != 0);
            });
            LinearLayout.LayoutParams lp = Ui.lpw(1);
            lp.setMargins(i == 0 ? 0 : Ui.dp(a, 3), 0, 0, 0);
            days.addView(ch, lp);
            dayChips.add(ch);
        }
        box.addView(days, Ui.lp(Ui.MATCH, Ui.WRAP));

        box.addView(label(a, "Saat aralığı"));
        LinearLayout times = Ui.hbox(a);
        TextView start = Ui.secondaryButton(a, "");
        TextView end = Ui.secondaryButton(a, "");
        Runnable paintTimes = () -> {
            start.setText(String.format(Locale.ROOT, "Başlangıç  %02d:%02d", r.sh, r.sm));
            end.setText(String.format(Locale.ROOT, "Bitiş  %02d:%02d", r.eh, r.em));
        };
        paintTimes.run();
        start.setOnClickListener(v -> new TimePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert,
                (tp, h, m) -> { r.sh = h; r.sm = m; paintTimes.run(); }, r.sh, r.sm, true).show());
        end.setOnClickListener(v -> new TimePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert,
                (tp, h, m) -> { r.eh = h; r.em = m; paintTimes.run(); }, r.eh, r.em, true).show());
        times.addView(start, Ui.lpw(1));
        LinearLayout.LayoutParams elp = Ui.lpw(1);
        elp.setMargins(Ui.dp(a, 8), 0, 0, 0);
        times.addView(end, elp);
        box.addView(times, Ui.lp(Ui.MATCH, Ui.WRAP));

        TextView note = Ui.text(a, "Elle seçtiğin bir mod (Keşfet, tür, araba) varsa o önce gelir; rutin onun yokken devreye girer.",
                12, Ui.TEXT3, Ui.REGULAR);
        note.setPadding(0, Ui.dp(a, 12), 0, 0);
        note.setLineSpacing(0, 1.12f);
        box.addView(note);

        AlertDialog d = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("🗓 Rutin ekle")
                .setView(sv)
                .setPositiveButton("Kaydet", null)
                .setNegativeButton("Vazgeç", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if ("vibe".equals(r.kind)) r.value = vibe.getText().toString().trim();
            if (r.value == null || r.value.isEmpty()) {
                a.toast("Bir ruh hali, tür ya da cümle seç");
                return;
            }
            if (r.days == 0) {
                a.toast("En az bir gün seç");
                return;
            }
            if (r.sh == r.eh && r.sm == r.em) {
                a.toast("Başlangıç ve bitiş aynı olamaz");
                return;
            }
            Routines.add(a, r);
            a.toast("🗓 Rutin eklendi: " + r.label());
            d.dismiss();
            if (onSaved != null) onSaved.run();
        }));
        d.show();
    }

    private static TextView label(MainActivity a, String s) {
        TextView t = Ui.text(a, s, 13, Ui.TEXT2, Ui.MEDIUM);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 16), 0, Ui.dp(a, 8));
        return t;
    }
}
