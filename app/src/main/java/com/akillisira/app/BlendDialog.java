package com.akillisira.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Ahenk Blend: kodunu paylaş, arkadaşının kodunu gir, ortak liste. */
public final class BlendDialog {
    private BlendDialog() {}

    public static void show(MainActivity a, Engine e, Runnable onStart) {
        ScrollView sv = new ScrollView(a);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 8));
        sv.addView(box);

        TextView info = Ui.text(a, "Zevk kodunu arkadaşına gönder, onunkini buraya yapıştır. "
                + "Ahenk ikinizin sevdiği sanatçıları ve şarkıları harmanlayıp ortak bir liste çıkarır. "
                + "Kodda sadece şarkı/sanatçı bilgisi var, kişisel bilgi yok.", 13, Ui.TEXT2, Ui.REGULAR);
        info.setLineSpacing(0, 1.12f);
        box.addView(info);

        // ---- Kayıtlı arkadaşlar ----
        List<Blend.Friend> friends = Blend.friends(a);
        AlertDialog[] ref = new AlertDialog[1];
        if (!friends.isEmpty()) {
            box.addView(label(a, "Arkadaşların"));
            LinearLayout row = Ui.hbox(a);
            for (Blend.Friend f : friends) {
                TextView ch = Ui.chip(a, "🤝 " + f.name, false);
                ch.setOnClickListener(v -> {
                    Blend.Taste t = Blend.decode(f.code);
                    if (t == null) { a.toast("Bu kod artık okunamıyor"); return; }
                    t.name = f.name;
                    e.discoverBlend(t);
                    if (ref[0] != null) ref[0].dismiss();
                    if (onStart != null) onStart.run();
                });
                ch.setOnLongClickListener(v -> {
                    Blend.removeFriend(a, f.name);
                    a.toast(f.name + " listeden çıkarıldı");
                    row.removeView(ch);
                    return true;
                });
                row.addView(ch, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), a, 0, 0, 8, 0));
            }
            box.addView(Ui.hscroll(a, row), Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        // ---- Benim kodum ----
        box.addView(label(a, "Senin kodun"));
        EditText myName = field(a, "Adın (arkadaşın listede görsün)", false);
        myName.setText(a.getSharedPreferences("ayarlar", 0).getString("blendName", ""));
        box.addView(myName, Ui.lp(Ui.MATCH, Ui.WRAP));
        TextView share = Ui.primaryButton(a, "📤  Kodumu paylaş");
        share.setOnClickListener(v -> {
            if (e.store.liked().isEmpty()) {
                a.toast("Önce birkaç şarkıyı ❤️ ile beğen; kodun beğenilerinden oluşur");
                return;
            }
            String nm = myName.getText().toString().trim();
            a.getSharedPreferences("ayarlar", 0).edit().putString("blendName", nm).apply();
            String code = Blend.encode(Blend.mine(e, nm));
            if (code == null) { a.toast("Kod oluşturulamadı"); return; }
            String text = "🎧 Ahenk Blend kodum" + (nm.isEmpty() ? "" : " (" + nm + ")") + ":\n\n" + code
                    + "\n\nAhenk'te Keşfet → 🤝 Blend → kodu yapıştır, ortak listemizi görelim.";
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(i, "Blend kodunu paylaş"));
        });
        box.addView(share, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 10, 0, 0));

        // ---- Arkadaşın kodu ----
        box.addView(label(a, "Arkadaşının kodu"));
        EditText code = field(a, "AHENK-… ile başlayan kodu yapıştır", true);
        String clip = clipboard(a);
        if (clip != null && clip.contains(Blend.PREFIX)) code.setText(clip);
        box.addView(code, Ui.lp(Ui.MATCH, Ui.WRAP));
        EditText fname = field(a, "Arkadaşının adı", false);
        box.addView(fname, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 8, 0, 0));
        TextView go = Ui.primaryButton(a, "🤝  Ortak liste hazırla");
        go.setOnClickListener(v -> {
            Blend.Taste t = Blend.decode(code.getText().toString());
            if (t == null) { a.toast("Kod okunamadı. AHENK- ile başlayan kodun tamamını yapıştır."); return; }
            String nm = fname.getText().toString().trim();
            if (nm.isEmpty()) nm = t.name != null && !t.name.isEmpty() ? t.name : "Arkadaşın";
            t.name = nm;
            Blend.saveFriend(a, nm, code.getText().toString().trim());
            e.discoverBlend(t);
            if (ref[0] != null) ref[0].dismiss();
            if (onStart != null) onStart.run();
        });
        box.addView(go, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), a, 0, 10, 0, 0));

        ref[0] = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("🤝 Ahenk Blend")
                .setView(sv)
                .setNegativeButton("Kapat", null)
                .create();
        ref[0].show();
    }

    private static TextView label(MainActivity a, String s) {
        TextView t = Ui.text(a, s, 13, Ui.TEXT2, Ui.MEDIUM);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 18), 0, Ui.dp(a, 8));
        return t;
    }

    private static EditText field(MainActivity a, String hint, boolean multi) {
        EditText t = new EditText(a);
        t.setHint(hint);
        t.setSingleLine(!multi);
        if (multi) t.setMaxLines(3);
        t.setTextColor(Ui.TEXT);
        t.setHintTextColor(Ui.TEXT3);
        t.setTextSize(14);
        t.setBackground(Ui.round(a, Ui.SURFACE2, 14));
        int ip = Ui.dp(a, 12);
        t.setPadding(ip, ip, ip, ip);
        return t;
    }

    private static String clipboard(MainActivity a) {
        try {
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            ClipData c = cm != null ? cm.getPrimaryClip() : null;
            if (c == null || c.getItemCount() == 0) return null;
            CharSequence s = c.getItemAt(0).coerceToText(a);
            return s != null ? s.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
