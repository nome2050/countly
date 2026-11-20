package com.countly.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Shared behaviour of all Countly widgets: size-aware bitmap rendering + daily refresh. */
public abstract class BaseWidget extends AppWidgetProvider {
    abstract String kind();

    abstract int defW();

    abstract int defH();

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id, kind(), defW(), defH());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        draw(c, m, id, kind(), defW(), defH());
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            refreshAll(c);
        }
    }

    /** Redraws every Countly widget instance of every size. */
    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        refresh(c, m, CountlyWidget.class, "list", 276, 203);
        refresh(c, m, WidgetNext.class, "next", 130, 130);
        refresh(c, m, WidgetHealth.class, "health", 276, 130);
        refresh(c, m, WidgetFull.class, "full", 276, 276);
    }

    private static void refresh(Context c, AppWidgetManager m, Class<?> cls, String kind, int dw, int dh) {
        int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
        for (int id : ids) draw(c, m, id, kind, dw, dh);
    }

    static void draw(Context c, AppWidgetManager m, int id, String kind, int dw, int dh) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_countly);
        try {
            Bundle o = m.getAppWidgetOptions(id);
            boolean land = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int w = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int h = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (w < 60) w = dw;
            if (h < 60) h = dh;
            float density = c.getResources().getDisplayMetrics().density;
            Bitmap bmp = WidgetRenderer.render(WidgetRenderer.load(c), kind, w, h, density);
            rv.setImageViewBitmap(R.id.img, bmp);
        } catch (Throwable ignored) {
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            PendingIntent pi = PendingIntent.getActivity(c, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.root, pi);
        }
        m.updateAppWidget(id, rv);
    }
}
