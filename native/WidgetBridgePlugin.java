package com.countly.app;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.Calendar;
import java.util.TimeZone;

/** Bridge between the web app and Android: widgets, reminders and the phone calendar. */
@CapacitorPlugin(
        name = "WidgetBridge",
        permissions = {
                @Permission(strings = {"android.permission.POST_NOTIFICATIONS"}, alias = "notifications"),
                @Permission(strings = {Manifest.permission.READ_CALENDAR}, alias = "calendar")
        })
public class WidgetBridgePlugin extends Plugin {

    // ---------------------------------------------------------------- widgets + data
    @PluginMethod
    public void update(PluginCall call) {
        Context c = getContext();
        String json = call.getString("json", "{}");
        c.getSharedPreferences(CountlyWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putString(CountlyWidget.KEY, json).apply();
        BaseWidget.refreshAll(c);
        Notifier.schedule(c);
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

    // ---------------------------------------------------------------- notifications
    private JSObject notifState() {
        JSObject r = new JSObject();
        r.put("granted", NotificationManagerCompat.from(getContext()).areNotificationsEnabled());
        return r;
    }

    @PluginMethod
    public void notifStatus(PluginCall call) {
        call.resolve(notifState());
    }

    @PluginMethod
    public void notifRequest(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notifCallback");
        } else {
            Notifier.schedule(getContext());
            call.resolve(notifState());
        }
    }

    @PermissionCallback
    private void notifCallback(PluginCall call) {
        Notifier.schedule(getContext());
        call.resolve(notifState());
    }

    @PluginMethod
    public void notifTest(PluginCall call) {
        Notifier.run(getContext(), true);
        call.resolve(notifState());
    }

    // ---------------------------------------------------------------- phone calendar
    @PluginMethod
    public void calendarEvents(PluginCall call) {
        if (getPermissionState("calendar") != PermissionState.GRANTED) {
            requestPermissionForAlias("calendar", call, "calendarCallback");
            return;
        }
        readCalendar(call);
    }

    @PermissionCallback
    private void calendarCallback(PluginCall call) {
        if (getPermissionState("calendar") == PermissionState.GRANTED) readCalendar(call);
        else call.reject("Calendar permission was not allowed.");
    }

    private void readCalendar(PluginCall call) {
        Cursor cur = null;
        try {
            long now = System.currentTimeMillis();
            long from = now - 30L * 86400000L, to = now + 1100L * 86400000L;
            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, from);
            ContentUris.appendId(b, to);
            String[] full = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY, "calendar_displayName", "account_name"};
            String[] basic = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY};
            try {
                cur = getContext().getContentResolver().query(b.build(), full, null, null, CalendarContract.Instances.BEGIN + " ASC");
            } catch (Exception e) {
                cur = getContext().getContentResolver().query(b.build(), basic, null, null, CalendarContract.Instances.BEGIN + " ASC");
            }
            JSArray out = new JSArray();
            while (cur != null && cur.moveToNext() && out.length() < 600) {
                String title = cur.getString(0);
                if (title == null || title.trim().isEmpty()) continue;
                long begin = cur.getLong(1);
                boolean allDay = cur.getInt(2) == 1;
                Calendar t = Calendar.getInstance(allDay ? TimeZone.getTimeZone("UTC") : TimeZone.getDefault());
                t.setTimeInMillis(begin);
                String date = String.format(java.util.Locale.US, "%04d-%02d-%02d",
                        t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH));
                JSObject o = new JSObject();
                o.put("title", title.trim());
                o.put("date", date);
                String cal = "";
                if (cur.getColumnCount() >= 5) {
                    String n1 = cur.getString(3), n2 = cur.getString(4);
                    cal = n1 != null ? n1 : (n2 != null ? n2 : "");
                    if (n2 != null && n1 != null && !n1.equals(n2)) cal = n1 + " · " + n2;
                }
                o.put("calendar", cal);
                out.put(o);
            }
            JSObject r = new JSObject();
            r.put("events", out);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Could not read the calendar: " + e.getMessage());
        } finally {
            if (cur != null) cur.close();
        }
    }
}
