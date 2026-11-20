package com.countly.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Daily reminder: schedules an alarm, then posts one notification that lists what is pending. */
final class Notifier {
    static final String CH_REMIND = "countly_remind", CH_URGENT = "countly_urgent";
    static final int NOTIF_ID = 7001, ALARM_ID = 4242;

    private Notifier() {}

    static class Cfg {
        boolean on = true;
        int hour = 9, min = 0;
        Set<Integer> offsets = new HashSet<>();
        String overdue = "weekly";
        boolean summary = false;
    }

    static Cfg cfg(Context c) {
        Cfg k = new Cfg();
        k.offsets.add(7);
        k.offsets.add(2);
        k.offsets.add(0);
        try {
            String raw = c.getSharedPreferences(CountlyWidget.PREFS, Context.MODE_PRIVATE).getString(CountlyWidget.KEY, "{}");
            JSONObject n = new JSONObject(raw).optJSONObject("notif");
            if (n != null) {
                k.on = n.optBoolean("on", true);
                k.hour = n.optInt("hour", 9);
                k.min = n.optInt("min", 0);
                k.overdue = n.optString("overdue", "weekly");
                k.summary = n.optBoolean("summary", false);
                JSONArray o = n.optJSONArray("offsets");
                if (o != null) {
                    k.offsets.clear();
                    for (int i = 0; i < o.length(); i++) k.offsets.add(o.getInt(i));
                }
            }
        } catch (Exception ignored) { }
        return k;
    }

    private static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getBroadcast(c, ALARM_ID, new Intent(c, NotifyReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** (Re)schedules the next daily check, or cancels it when reminders are off. */
    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarmIntent(c);
            am.cancel(pi);
            Cfg k = cfg(c);
            if (!k.on) return;
            Calendar t = Calendar.getInstance();
            t.set(Calendar.HOUR_OF_DAY, k.hour);
            t.set(Calendar.MINUTE, k.min);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= System.currentTimeMillis() + 30000) t.add(Calendar.DAY_OF_YEAR, 1);
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            else am.set(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
        } catch (Exception ignored) { }
    }

    /** Text for one line: "in 7 days", "tomorrow", "today", "647 days over". */
    private static String when(WidgetRenderer.Data d, WidgetRenderer.Item it) {
        if (it.days < 0) return WidgetRenderer.chip(d, it);
        if (it.days == 0) return "today";
        if (it.days == 1) return "tomorrow";
        return "in " + WidgetRenderer.chip(d, it);
    }

    private static boolean triggered(Cfg k, WidgetRenderer.Item it) {
        if (it.days >= 0) return k.offsets.contains(it.days);
        if ("daily".equals(k.overdue)) return true;
        if ("weekly".equals(k.overdue)) return (-it.days) % 7 == 0;
        return false;
    }

    /** Posts the reminder if something is due (or always when force is true, for the test button). */
    static void run(Context c, boolean force) {
        try {
            Cfg k = cfg(c);
            if (!k.on && !force) return;
            WidgetRenderer.Data d = WidgetRenderer.load(c);
            List<WidgetRenderer.Item> pending = new ArrayList<>(), due = new ArrayList<>();
            int valid = 0;
            for (WidgetRenderer.Item it : d.items) {
                if (it.state() == 0) valid++;
                else pending.add(it);
                if (triggered(k, it)) due.add(it);
            }
            if (!force) {
                if (due.isEmpty() && !(k.summary && !pending.isEmpty())) return;
            }
            if (d.items.isEmpty() && !force) return;

            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            if (!nm.areNotificationsEnabled()) return;

            boolean urgent = false;
            for (WidgetRenderer.Item it : due) if (it.days <= 2) urgent = true;
            if (force) urgent = false;
            ensureChannels(c);

            String title;
            if (force && pending.isEmpty()) title = "Countly is set up ✓";
            else if (due.size() == 1) {
                WidgetRenderer.Item it = due.get(0);
                title = it.name + (it.days < 0 ? " expired" : it.days == 0 ? " expires today" : " expires " + when(d, it));
            } else if (due.size() > 1) title = due.size() + " documents need attention";
            else title = pending.size() + (pending.size() == 1 ? " document is pending" : " documents are pending");
            if (force) title = "Test · " + title;

            String summary = pending.size() + " pending · " + valid + " valid";
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            int shown = 0;
            for (WidgetRenderer.Item it : (pending.isEmpty() ? d.items : pending)) {
                if (shown++ >= 7) break;
                String mark = it.state() == 3 ? "⛔ " : it.state() == 2 ? "⚠ " : it.state() == 1 ? "• " : "✓ ";
                style.addLine(mark + it.name + " — " + when(d, it));
            }
            style.setSummaryText(summary);

            Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            PendingIntent open = launch == null ? null : PendingIntent.getActivity(c, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder b = new NotificationCompat.Builder(c, urgent ? CH_URGENT : CH_REMIND)
                    .setSmallIcon(R.drawable.ic_stat_countly)
                    .setColor(d.primary)
                    .setContentTitle(title)
                    .setContentText(summary)
                    .setStyle(style)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(urgent ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                    .setOnlyAlertOnce(false);
            if (open != null) b.setContentIntent(open);
            nm.notify(NOTIF_ID, b.build());
        } catch (Exception ignored) {
        } finally {
            if (!force) schedule(c);
        }
    }

    private static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m == null) return;
        NotificationChannel r = new NotificationChannel(CH_REMIND, "Renewal reminders", NotificationManager.IMPORTANCE_DEFAULT);
        r.setDescription("Heads-up before a document expires");
        NotificationChannel u = new NotificationChannel(CH_URGENT, "Urgent alerts", NotificationManager.IMPORTANCE_HIGH);
        u.setDescription("Documents expiring within 2 days, today, or overdue");
        u.enableVibration(true);
        m.createNotificationChannel(r);
        m.createNotificationChannel(u);
    }
}
