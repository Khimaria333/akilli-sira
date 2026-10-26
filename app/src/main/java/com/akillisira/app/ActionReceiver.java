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

    @Override
    public void onReceive(Context context, Intent intent) {
        Engine e = Engine.get(context);
        String a = intent.getAction();
        if (SKIP.equals(a)) e.skipNow();
        else if (DISLIKE_NEXT.equals(a)) e.dislikeNext();
        else if (DISLIKE_NOW.equals(a)) e.dislikeNow();
        else if (PLAY_PAUSE.equals(a)) e.togglePlay();
    }
}
