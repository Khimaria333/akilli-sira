package com.akillisira.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bildirim ve widget düğmeleri. */
public class ActionReceiver extends BroadcastReceiver {
    public static final String SKIP = "com.akillisira.app.SKIP";
    public static final String DISLIKE_NEXT = "com.akillisira.app.DISLIKE_NEXT";
    public static final String DISLIKE_NOW = "com.akillisira.app.DISLIKE_NOW";
    public static final String PLAY_PAUSE = "com.akillisira.app.PLAY_PAUSE";
    public static final String PREV = "com.akillisira.app.PREV";
    public static final String LIKE = "com.akillisira.app.LIKE";
    public static final String START = "com.akillisira.app.START";   // extra "what": sakin|enerjik|yol|favoriler

    @Override
    public void onReceive(Context context, Intent intent) {
        Engine e = Engine.get(context);
        String a = intent.getAction();
        if (SKIP.equals(a)) e.skipNow();
        else if (DISLIKE_NEXT.equals(a)) e.dislikeNext();
        else if (DISLIKE_NOW.equals(a)) e.dislikeNow();
        else if (PLAY_PAUSE.equals(a)) e.togglePlay();
        else if (PREV.equals(a)) e.previous();
        else if (LIKE.equals(a)) e.toggleLikeNow();
        else if (START.equals(a)) {
            String what = intent.getStringExtra("what");
            if (what == null) return;
            switch (what) {
                case "sakin": e.discoverAndPlay("sakin", null); break;
                case "enerjik": e.discoverAndPlay("enerjik", null); break;
                case "yol": e.discoverAndPlay("mutlu", "araba"); break;
                case "favoriler": e.favoritesAndPlay(); break;
                case "gunun": e.dailyAndPlay(); break;
                case "simdi":
                    e.notifier.cancelHeadphonesPrompt();
                    e.discoverForNow(true);
                    break;
                default: return;
            }
            android.widget.Toast.makeText(context, "Ahenk: liste hazırlanıyor, birazdan başlıyor…",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
