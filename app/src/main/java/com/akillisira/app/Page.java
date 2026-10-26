package com.akillisira.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Bir ekran: bir kez kurulur, motor her değiştiğinde render() ile güncellenir. */
public abstract class Page {
    protected final MainActivity a;
    protected final Engine e;
    protected ScrollView scroll;
    protected LinearLayout body;
    private View root;

    protected Page(MainActivity a, Engine e) {
        this.a = a;
        this.e = e;
    }

    public final View view() {
        if (root == null) {
            scroll = new ScrollView(a);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            body = Ui.vbox(a);
            int p = Ui.dp(a, 20);
            body.setPadding(p, Ui.dp(a, 18), p, Ui.dp(a, 32));
            scroll.addView(body);
            build();
            root = scroll;
        }
        return root;
    }

    protected abstract void build();

    public abstract void render();

    public void onShow() {}

    protected LinearLayout.LayoutParams full() {
        return Ui.lp(Ui.MATCH, Ui.WRAP);
    }
}
