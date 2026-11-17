package com.countly.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Home-screen widget: shows the closest document deadlines as a black, rounded card. */
public class CountlyWidget extends AppWidgetProvider {
    static final String PREFS = "countly";
    static final String KEY = "data";
    private static final int MAX_ROWS = 6;

    private static final int[] ROW = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6};
    private static final int[] DOT = {R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5, R.id.dot6};
    private static final int[] NAME = {R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6};
    private static final int[] DAYS = {R.id.days1, R.id.days2, R.id.days3, R.id.days4, R.id.days5, R.id.days6};

    private static class Item {
        String name;
        int days;
        int warn;
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        draw(c, m, id);
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

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CountlyWidget.class));
        for (int id : ids) draw(c, m, id);
    }

    private static int daysUntil(String ymd) {
        String[] p = ymd.split("-");
        Calendar t = Calendar.getInstance();
        t.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 12, 0, 0);
        Calendar n = Calendar.getInstance();
        n.set(Calendar.HOUR_OF_DAY, 12);
        n.set(Calendar.MINUTE, 0);
        n.set(Calendar.SECOND, 0);
        return (int) Math.round((t.getTimeInMillis() - n.getTimeInMillis()) / 86400000.0);
    }

    private static void draw(Context c, AppWidgetManager m, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_countly);

        int primary = Color.parseColor("#8ecbff");
        List<Item> items = new ArrayList<>();
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}");
            JSONObject root = new JSONObject(raw);
            if (root.has("primary")) primary = Color.parseColor(root.getString("primary"));
            JSONArray arr = root.optJSONArray("docs");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    JSONObject o = arr.getJSONObject(k);
                    Item it = new Item();
                    it.name = o.getString("name");
                    it.days = daysUntil(o.getString("date"));
                    it.warn = o.optInt("warn", 90);
                    items.add(it);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.days - b.days;
            }
        });

        int need = 0;
        for (Item it : items) if (it.days <= 30) need++;

        rv.setTextColor(R.id.title, primary);
        if (items.isEmpty()) {
            rv.setTextViewText(R.id.summary, "Tap to add documents");
            rv.setTextColor(R.id.summary, Color.parseColor("#9b99a6"));
        } else if (need > 0) {
            rv.setTextViewText(R.id.summary, need + (need == 1 ? " needs action" : " need action"));
            rv.setTextColor(R.id.summary, Color.parseColor("#ff7a7a"));
        } else {
            rv.setTextViewText(R.id.summary, "All good ✓");
            rv.setTextColor(R.id.summary, Color.parseColor("#7ddba3"));
        }

        Bundle o = m.getAppWidgetOptions(id);
        int minH = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) : 110;
        int rows = Math.max(1, Math.min(MAX_ROWS, (minH - 44) / 30));

        for (int k = 0; k < MAX_ROWS; k++) {
            if (k < rows && k < items.size()) {
                Item it = items.get(k);
                int col = it.days <= 30 ? Color.parseColor("#ff7a7a")
                        : it.days <= it.warn ? Color.parseColor("#ffc857")
                        : Color.parseColor("#7ddba3");
                String d = it.days < 0 ? (-it.days) + "d over" : it.days == 0 ? "Today" : it.days + "d";
                rv.setViewVisibility(ROW[k], View.VISIBLE);
                rv.setTextColor(DOT[k], col);
                rv.setTextViewText(NAME[k], it.name);
                rv.setTextViewText(DAYS[k], d);
                rv.setTextColor(DAYS[k], col);
            } else {
                rv.setViewVisibility(ROW[k], View.GONE);
            }
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
