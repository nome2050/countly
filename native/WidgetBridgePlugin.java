package com.countly.app;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Lets the web app push its data to the home-screen widget and ask Android to pin it. */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        Context c = getContext();
        String json = call.getString("json", "{}");
        c.getSharedPreferences(CountlyWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putString(CountlyWidget.KEY, json).apply();
        CountlyWidget.refreshAll(c);
        call.resolve();
    }

    @PluginMethod
    public void pin(PluginCall call) {
        Context c = getContext();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(c, CountlyWidget.class), null, null);
            call.resolve();
        } else {
            call.reject("Long-press the home screen, tap Widgets, then pick Countly.");
        }
    }
}
