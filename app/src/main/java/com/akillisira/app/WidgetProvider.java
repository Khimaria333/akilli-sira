package com.akillisira.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Ana ekran widget'ı: çalan, sıradaki, Geç ve 👎 düğmeleri. */
public class WidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Engine.get(context).forceWidgets();
    }
}
