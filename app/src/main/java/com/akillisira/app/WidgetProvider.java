package com.akillisira.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Ana ekran widget'ı (düzenler ve içerik: Widgets). */
public class WidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Engine.get(context).forceWidgets();
    }

    /** Boyut değişince (Android 11 ve öncesinde) uygun düzene geç. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        Engine.get(context).forceWidgets();
    }
}
