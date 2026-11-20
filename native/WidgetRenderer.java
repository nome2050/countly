package com.countly.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.graphics.PathParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the Countly home-screen widgets as one bitmap. Designed for a one-glance read:
 * big numbers, few elements, status colours. All coordinates are in dp (canvas is pre-scaled).
 */
final class WidgetRenderer {
    static final int OK = 0xFF30E88D, WARN = 0xFFFFB524, BAD = 0xFFFF4D5E, EXP = 0xFFFF2E93;
    static final int MUTED = 0xFF9B99A6, WHITE = 0xFFF2F1F7, TRACK = 0x26FFFFFF;

    private WidgetRenderer() {}

    static class Item {
        String name;
        int y, m, d;      // expiry date
        int days;         // days left (negative = overdue)
        int warn = 90;
        Path icon;
        int cc = 0xFFB8B8CC;

        int state() { // 0 valid, 1 renew soon, 2 expiring (<=30d), 3 expired
            return days < 0 ? 3 : days <= 30 ? 2 : days <= warn ? 1 : 0;
        }

        int color() {
            int s = state();
            return s == 3 ? EXP : s == 2 ? BAD : s == 1 ? WARN : OK;
        }
    }

    static class Data {
        int primary = 0xFF2EA8FF;
        String over = "days";
        List<Item> items = new ArrayList<>();
        int[] cnt = new int[4];
    }

    // ------------------------------------------------------------------ data

    static Data load(Context c) {
        String raw = "{}";
        try {
            raw = c.getSharedPreferences(CountlyWidget.PREFS, Context.MODE_PRIVATE).getString(CountlyWidget.KEY, "{}");
        } catch (Exception ignored) { }
        return parse(raw);
    }

    static Data parse(String raw) {
        Data data = new Data();
        try {
            JSONObject root = new JSONObject(raw);
            if (root.has("primary")) data.primary = Color.parseColor(root.getString("primary"));
            data.over = root.optString("overUnit", "days");
            JSONArray arr = root.optJSONArray("docs");
            if (arr != null) {
                for (int k = 0; k < arr.length(); k++) {
                    try {
                        JSONObject o = arr.getJSONObject(k);
                        Item it = new Item();
                        it.name = o.getString("name");
                        String[] p = o.getString("date").split("-");
                        it.y = Integer.parseInt(p[0]);
                        it.m = Integer.parseInt(p[1]);
                        it.d = Integer.parseInt(p[2]);
                        it.days = daysUntil(it.y, it.m, it.d);
                        it.warn = o.optInt("warn", 90);
                        if (o.has("cc")) {
                            try { it.cc = Color.parseColor(o.getString("cc")); } catch (Exception ignored) { }
                        }
                        if (o.has("ic")) {
                            try { it.icon = PathParser.createPathFromPathData(o.getString("ic")); } catch (Exception ignored) { }
                        }
                        data.items.add(it);
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
        Collections.sort(data.items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return a.days - b.days; }
        });
        for (Item it : data.items) data.cnt[it.state()]++;
        return data;
    }

    static int daysUntil(int y, int m, int d) {
        Calendar t = Calendar.getInstance();
        t.set(y, m - 1, d, 12, 0, 0);
        Calendar n = Calendar.getInstance();
        n.set(Calendar.HOUR_OF_DAY, 12);
        n.set(Calendar.MINUTE, 0);
        n.set(Calendar.SECOND, 0);
        return (int) Math.round((t.getTimeInMillis() - n.getTimeInMillis()) / 86400000.0);
    }

    /** {number, long label, short label}: "49 / days left" or overdue in days/months/years. */
    static String[] amount(Data data, Item it) {
        if (it.days >= 0) {
            if (it.days == 0) return new String[]{"0", "due today", "today"};
            return new String[]{String.valueOf(it.days), it.days == 1 ? "day left" : "days left", it.days == 1 ? "day" : "days"};
        }
        Calendar n = Calendar.getInstance();
        int mo = (n.get(Calendar.YEAR) - it.y) * 12 + (n.get(Calendar.MONTH) + 1) - it.m;
        if (n.get(Calendar.DAY_OF_MONTH) < it.d) mo--;
        if (mo < 0) mo = 0;
        int yr = mo / 12;
        int days = -it.days;
        String u = data.over;
        if ("auto".equals(u)) u = yr >= 1 ? "years" : mo >= 1 ? "months" : "days";
        if ("years".equals(u) && yr < 1) u = mo >= 1 ? "months" : "days";
        if ("months".equals(u) && mo < 1) u = "days";
        if ("years".equals(u)) return new String[]{String.valueOf(yr), yr == 1 ? "year over" : "years over", "yr over"};
        if ("months".equals(u)) return new String[]{String.valueOf(mo), mo == 1 ? "month over" : "months over", "mo over"};
        return new String[]{String.valueOf(days), days == 1 ? "day over" : "days over", "d over"};
    }

    /** Short human text for a row: "Today", "Tomorrow", "23 days", "5 months", "1y 8m", "3 months over". */
    static String chip(Data data, Item it) {
        if (it.days < 0) {
            String[] a = amount(data, it);
            return a[0] + a[2].replace("d over", "d over").replace("mo over", " mo over").replace("yr over", " yr over");
        }
        if (it.days == 0) return "Today";
        if (it.days == 1) return "Tomorrow";
        if (it.days <= 60) return it.days + " days";
        if (it.days < 730) return Math.round(it.days / 30.4f) + " months";
        int y = it.days / 365;
        int m = Math.round((it.days - y * 365) / 30.4f);
        if (m >= 12) { y++; m = 0; }
        return m == 0 ? y + " years" : y + "y " + m + "m";
    }

    /** Worst status present, as a headline. */
    static String statusText(Data d) {
        if (d.items.isEmpty()) return "Add a document";
        if (d.cnt[3] > 0) return d.cnt[3] + " expired";
        if (d.cnt[2] > 0) return d.cnt[2] + " expiring soon";
        if (d.cnt[1] > 0) return d.cnt[1] + " to renew";
        return "All valid";
    }

    static int statusColor(Data d) {
        if (d.items.isEmpty()) return MUTED;
        return d.cnt[3] > 0 ? EXP : d.cnt[2] > 0 ? BAD : d.cnt[1] > 0 ? WARN : OK;
    }

    // --------------------------------------------------------------- helpers

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint text(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    /** Text that shrinks until it fits maxW (never below min), then is cut with an ellipsis. */
    private static void fitText(Canvas c, String s, float x, float y, int color, float size, float min,
                                boolean bold, float maxW, Paint.Align a) {
        if (s == null) return;
        Paint p = text(color, size, bold);
        while (p.measureText(s) > maxW && p.getTextSize() > min) p.setTextSize(p.getTextSize() - 0.5f);
        if (p.measureText(s) > maxW) s = TextUtils.ellipsize(s, new TextPaint(p), maxW, TextUtils.TruncateAt.END).toString();
        p.setTextAlign(a);
        c.drawText(s, x, y, p);
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static void arc(Canvas c, float cx, float cy, float r, float stroke, float start, float sweep, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, p);
    }

    private static void icon(Canvas c, Path path, float x, float y, float size, int color) {
        if (path == null) return;
        Path q = new Path(path);
        Matrix m = new Matrix();
        m.setScale(size / 24f, size / 24f);
        m.postTranslate(x, y);
        q.transform(m);
        c.drawPath(q, fill(color));
    }

    private static void iconTile(Canvas c, Item it, float x, float y, float size) {
        c.drawRoundRect(new RectF(x, y, x + size, y + size), size * 0.32f, size * 0.32f, fill(alpha(it.cc, 56)));
        float in = size * 0.58f;
        icon(c, it.icon, x + (size - in) / 2, y + (size - in) / 2, in, it.cc);
    }

    /** Rounded coloured chip, right edge at rightX, vertically centred on cy. Returns its width. */
    private static float pill(Canvas c, String s, float rightX, float cy, int col, float size) {
        Paint t = text(col, size, true);
        float pw = t.measureText(s) + 16, ph = size + 10;
        c.drawRoundRect(new RectF(rightX - pw, cy - ph / 2, rightX, cy + ph / 2), ph / 2, ph / 2, fill(alpha(col, 48)));
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, rightX - pw / 2, cy + size * 0.36f, t);
        return pw;
    }

    // ------------------------------------------------------------- rendering

    static Bitmap render(Data data, String kind, int wDp, int hDp, float density) {
        float scale = density;
        long px = (long) (wDp * scale) * (long) (hDp * scale);
        if (px > 1400000L) scale = (float) (scale * Math.sqrt(1400000.0 / px));
        int bw = Math.max(1, Math.round(wDp * scale)), bh = Math.max(1, Math.round(hDp * scale));
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(bw / (float) wDp, bh / (float) hDp);
        float w = wDp, h = hDp;

        background(c, w, h, data.primary);
        if ("next".equals(kind)) next(c, data, w, h);
        else if ("health".equals(kind)) glance(c, data, w, h);
        else if ("full".equals(kind)) full(c, data, w, h);
        else list(c, data, w, h);
        return bmp;
    }

    private static void background(Canvas c, float w, float h, int primary) {
        float rad = 28;
        RectF r = new RectF(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, fill(0xFF000000));
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * 0.1f, 0, Math.max(w, h) * 0.85f,
                alpha(primary, 52), alpha(primary, 0), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, glow);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(1);
        st.setColor(0x26FFFFFF);
        c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), rad, rad, st);
    }

    private static void emptyState(Canvas c, Data data, float w, float h) {
        float cx = w / 2, cy = h / 2 - 8;
        c.drawCircle(cx, cy, 20, fill(alpha(data.primary, 56)));
        Paint pl = fill(data.primary);
        c.drawRoundRect(new RectF(cx - 9, cy - 1.75f, cx + 9, cy + 1.75f), 1.75f, 1.75f, pl);
        c.drawRoundRect(new RectF(cx - 1.75f, cy - 9, cx + 1.75f, cy + 9), 1.75f, 1.75f, pl);
        fitText(c, "Tap to add a document", cx, cy + 40, MUTED, 12.5f, 9, false, w - 24, Paint.Align.CENTER);
    }

    /** 2x2: the single most urgent document as a big ring. */
    private static void next(Canvas c, Data data, float w, float h) {
        if (data.items.isEmpty()) { emptyState(c, data, w, h); return; }
        Item it = data.items.get(0);
        float pad = 12;
        fitText(c, it.name, w / 2, pad + 12, WHITE, 13, 9, true, w - 2 * pad, Paint.Align.CENTER);
        float top = pad + 22, bottom = h - 12;
        float r = Math.min((w - 2 * pad) / 2, (bottom - top) / 2) - 3;
        float cx = w / 2, cy = (top + bottom) / 2;
        float stroke = Math.max(6, r * 0.18f);
        int col = it.color();
        arc(c, cx, cy, r, stroke, 0, 360, TRACK);
        float frac = it.days < 0 ? 1f : Math.max(0.05f, Math.min(1f, it.days / 365f));
        arc(c, cx, cy, r, stroke, -90, 360 * frac, col);
        String[] a = amount(data, it);
        float inner = (r - stroke) * 1.7f;
        fitText(c, a[0], cx, cy + r * 0.16f, WHITE, r * 0.8f, 12, true, inner, Paint.Align.CENTER);
        fitText(c, a[1], cx, cy + r * 0.16f + r * 0.36f, col, Math.max(9, r * 0.26f), 7, true, inner, Paint.Align.CENTER);
    }

    /** 4x2: the most urgent document on the left (big), the next three on the right. */
    private static void glance(Canvas c, Data data, float w, float h) {
        if (data.items.isEmpty()) { emptyState(c, data, w, h); return; }
        Item it = data.items.get(0);
        float pad = 16;
        float split = Math.max(108, w * 0.38f);
        int col = it.color();
        String[] a = amount(data, it);
        float lw = split - pad - 6;
        fitText(c, "NEXT", pad, pad + 9, MUTED, 10, 8, true, lw, Paint.Align.LEFT);
        fitText(c, a[0], pad, h / 2 + 9, col, Math.min(40, h * 0.36f), 16, true, lw, Paint.Align.LEFT);
        fitText(c, a[1], pad, h / 2 + 26, col, 12, 8, true, lw, Paint.Align.LEFT);
        fitText(c, it.name, pad, h - pad + 1, WHITE, 12.5f, 11f, true, lw, Paint.Align.LEFT);

        c.drawRoundRect(new RectF(split, pad, split + 1, h - pad), 0.5f, 0.5f, fill(0x1FFFFFFF));
        float rx = split + 14, rw = w - rx - pad + 2;
        int n = (int) Math.min(3, data.items.size() - 1);
        if (n < 1) {
            fitText(c, statusText(data), rx, h / 2 + 5, statusColor(data), 14, 10, true, rw, Paint.Align.LEFT);
            return;
        }
        float rh = Math.min(36, (h - 2 * pad + 4) / n);
        float top = (h - rh * n) / 2f;
        for (int i = 0; i < n; i++) {
            Item o = data.items.get(i + 1);
            float cy = top + rh * i + rh / 2;
            String s = chip(data, o);
            Paint vp = text(o.color(), 12, true);
            float vw = vp.measureText(s);
            vp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(s, rx + rw, cy + 4.3f, vp);
            fitText(c, o.name, rx, cy + 4.5f, WHITE, 13, 12, true, rw - vw - 8, Paint.Align.LEFT);
        }
    }

    /** Header: app name + worst-status chip. */
    private static void header(Canvas c, Data data, float x, float y, float w) {
        float cy = y + 11;
        c.drawRoundRect(new RectF(x, cy - 7, x + 5, cy + 7), 2.5f, 2.5f, fill(data.primary));
        fitText(c, "Countly", x + 12, cy + 5.5f, data.primary, 16, 12, true, w * 0.4f, Paint.Align.LEFT);
        pill(c, statusText(data), x + w, cy, statusColor(data), 11.5f);
    }

    /** Rows of closest deadlines: category icon, name, status chip. */
    private static void rows(Canvas c, Data data, float x, float y, float w, float h, int maxRows) {
        int n = (int) Math.min(Math.min(maxRows, data.items.size()), Math.floor(h / 34f));
        if (n < 1) return;
        float rh = Math.min(44, h / n);
        for (int i = 0; i < n; i++) {
            Item it = data.items.get(i);
            float top = y + i * rh, cy = top + rh / 2;
            float tile = Math.min(28, rh - 8);
            iconTile(c, it, x, cy - tile / 2, tile);
            String s = chip(data, it);
            float pw = pill(c, s, x + w, cy, it.color(), 11.5f);
            fitText(c, it.name, x + tile + 10, cy + 4.8f, WHITE, 13.5f, 13f, true, w - tile - 10 - pw - 10, Paint.Align.LEFT);
        }
    }

    /** 4x3: header + the closest deadlines. */
    private static void list(Canvas c, Data data, float w, float h) {
        float pad = 16;
        header(c, data, pad, 12, w - 2 * pad);
        if (data.items.isEmpty()) { emptyState(c, data, w, h + 36); return; }
        rows(c, data, pad, 44, w - 2 * pad, h - 44 - 10, 6);
    }

    /** 4x4: header, four status counters, then the closest deadlines. */
    private static void full(Canvas c, Data data, float w, float h) {
        float pad = 16;
        header(c, data, pad, 12, w - 2 * pad);
        if (data.items.isEmpty()) { emptyState(c, data, w, h + 36); return; }
        float y = 44, gap = 8, tw = (w - 2 * pad - 3 * gap) / 4f, th = h >= 240 ? 52 : 46;
        String[] lbl = {"Valid", "Renew", "Soon", "Expired"};
        int[] cols = {OK, WARN, BAD, EXP};
        for (int i = 0; i < 4; i++) {
            float x = pad + i * (tw + gap);
            c.drawRoundRect(new RectF(x, y, x + tw, y + th), 14, 14, fill(alpha(cols[i], data.cnt[i] > 0 ? 46 : 20)));
            fitText(c, String.valueOf(data.cnt[i]), x + tw / 2, y + th * 0.52f, data.cnt[i] > 0 ? cols[i] : MUTED, 22, 12, true, tw - 6, Paint.Align.CENTER);
            fitText(c, lbl[i], x + tw / 2, y + th - 9, data.cnt[i] > 0 ? WHITE : MUTED, 10.5f, 7.5f, false, tw - 4, Paint.Align.CENTER);
        }
        y += th + 10;
        rows(c, data, pad, y, w - 2 * pad, h - y - 10, 8);
    }
}
