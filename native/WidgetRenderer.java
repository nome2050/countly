package com.countly.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Draws the Countly home-screen widgets as a bitmap (donut charts, rings, bars, category icons)
 * so they can look like the in-app dashboard. All coordinates are in dp; the canvas is pre-scaled.
 */
final class WidgetRenderer {
    static final int OK = 0xFF30E88D, WARN = 0xFFFFB524, BAD = 0xFFFF4D5E, EXP = 0xFFFF2E93;
    static final int MUTED = 0xFF9B99A6, WHITE = 0xFFF2F1F7, TRACK = 0x24FFFFFF;

    private WidgetRenderer() {}

    static class Item {
        String name;
        int y, m, d;      // expiry date
        int days;         // days left (negative = overdue)
        int warn = 90;
        Path icon;
        int cc = 0xFFB8B8CC;

        int state() { // 0 ok, 1 renew soon, 2 expiring, 3 expired
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
        Data data = new Data();
        try {
            String raw = c.getSharedPreferences(CountlyWidget.PREFS, Context.MODE_PRIVATE)
                    .getString(CountlyWidget.KEY, "{}");
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

    /** {number, long label, short label} for an item: "days left" or overdue in days/months/years. */
    static String[] amount(Data data, Item it) {
        if (it.days >= 0) {
            if (it.days == 0) return new String[]{"0", "today", "today"};
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

    // --------------------------------------------------------------- helpers

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint text(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    private static void txt(Canvas c, String s, float x, float y, Paint p, float maxW, Paint.Align a) {
        if (s == null) return;
        if (maxW > 0) s = TextUtils.ellipsize(s, new TextPaint(p), maxW, TextUtils.TruncateAt.END).toString();
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
        c.drawRoundRect(new RectF(x, y, x + size, y + size), size * 0.3f, size * 0.3f, fill(alpha(it.cc, 52)));
        float in = size * 0.58f;
        icon(c, it.icon, x + (size - in) / 2, y + (size - in) / 2, in, it.cc);
    }

    // ------------------------------------------------------------- rendering

    static Bitmap render(Context ctx, String kind, int wDp, int hDp, float density) {
        Data data = load(ctx);
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
        else if ("health".equals(kind)) health(c, data, 14, 12, w - 28, h - 24);
        else if ("full".equals(kind)) full(c, data, w, h);
        else list(c, data, w, h);
        return bmp;
    }

    private static void background(Canvas c, float w, float h, int primary) {
        float rad = 26;
        RectF r = new RectF(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, fill(0xFF000000));
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * 0.92f, 0, Math.max(w, h) * 0.95f,
                alpha(primary, 78), alpha(primary, 0), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, glow);
        Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow2.setShader(new LinearGradient(0, h, 0, h * 0.55f, alpha(primary, 26), alpha(primary, 0), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, glow2);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(1);
        st.setColor(0x1FFFFFFF);
        c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), rad, rad, st);
    }

    private static void emptyState(Canvas c, Data data, float w, float h) {
        float cx = w / 2, cy = h / 2 - 6;
        c.drawCircle(cx, cy, 18, fill(alpha(data.primary, 50)));
        Paint pl = fill(data.primary);
        c.drawRoundRect(new RectF(cx - 8, cy - 1.5f, cx + 8, cy + 1.5f), 1.5f, 1.5f, pl);
        c.drawRoundRect(new RectF(cx - 1.5f, cy - 8, cx + 1.5f, cy + 8), 1.5f, 1.5f, pl);
        txt(c, "Tap to add a document", cx, cy + 36, text(MUTED, 12, false), w - 24, Paint.Align.CENTER);
    }

    /** 2x2: closest deadline as a big progress ring. */
    private static void next(Canvas c, Data data, float w, float h) {
        if (data.items.isEmpty()) { emptyState(c, data, w, h); return; }
        Item it = data.items.get(0);
        float pad = 12;
        txt(c, it.name, pad, pad + 11, text(WHITE, 12.5f, true), w - 2 * pad - 2, Paint.Align.LEFT);
        float top = pad + 20, bottom = h - 10;
        float r = Math.min((w - 2 * pad) / 2, (bottom - top) / 2) - 5;
        float cx = w / 2, cy = (top + bottom) / 2;
        float stroke = Math.max(5, r * 0.17f);
        c.drawCircle(cx, cy, r, ringBg(it.color()));
        arc(c, cx, cy, r, stroke, 0, 360, TRACK);
        float frac = Math.max(0.04f, Math.min(1f, it.days / 365f));
        arc(c, cx, cy, r, stroke, -90, 360 * (it.days < 0 ? 1f : frac), it.color());
        String[] a = amount(data, it);
        txt(c, a[0], cx, cy + r * 0.2f, text(WHITE, r * 0.78f, true), r * 1.5f, Paint.Align.CENTER);
        txt(c, a[1], cx, cy + r * 0.2f + r * 0.38f, text(it.color(), Math.max(8, r * 0.27f), true), r * 1.6f, Paint.Align.CENTER);
    }

    private static Paint ringBg(int color) {
        return fill(alpha(color, 22));
    }

    /** Header with app name and a coloured status line. */
    private static void header(Canvas c, Data data, float x, float y, float w) {
        c.drawRoundRect(new RectF(x, y + 2, x + 8, y + 16), 3, 3, fill(data.primary));
        txt(c, "Countly", x + 14, y + 14, text(data.primary, 15, true), w * 0.5f, Paint.Align.LEFT);
        int need = data.cnt[2] + data.cnt[3];
        String s;
        int col;
        if (data.items.isEmpty()) { s = "Tap to add documents"; col = MUTED; }
        else if (need > 0) { s = need + (need == 1 ? " needs action" : " need action"); col = BAD; }
        else { s = "All good ✓"; col = OK; }
        txt(c, s, x + w, y + 14, text(col, 12, true), w * 0.55f, Paint.Align.RIGHT);
    }

    /** Donut with the health score + 2x2 status counters. */
    private static void health(Canvas c, Data data, float x, float y, float w, float h) {
        if (data.items.isEmpty()) { emptyState(c, data, x * 2 + w, y * 2 + h); return; }
        int total = data.items.size();
        float r = Math.min(h / 2f - 3, w * 0.30f);
        float cx = x + r + 3, cy = y + h / 2f;
        float stroke = r * 0.27f, rr = r - stroke / 2;
        arc(c, cx, cy, rr, stroke, 0, 360, TRACK);
        int[] cols = {OK, WARN, BAD, EXP};
        float start = -90;
        for (int i = 0; i < 4; i++) {
            if (data.cnt[i] == 0) continue;
            float sw = 360f * data.cnt[i] / total;
            float gap = total > 1 ? 7 : 0;
            arc(c, cx, cy, rr, stroke, start + gap / 2, Math.max(2, sw - gap), cols[i]);
            start += sw;
        }
        int score = Math.round(100f * data.cnt[0] / total);
        txt(c, score + "%", cx, cy + r * 0.14f, text(WHITE, r * 0.5f, true), r * 1.3f, Paint.Align.CENTER);
        txt(c, "healthy", cx, cy + r * 0.14f + r * 0.3f, text(MUTED, Math.max(8, r * 0.2f), false), r * 1.3f, Paint.Align.CENTER);

        float gx = x + 2 * r + 20, gw = x + w - gx;
        if (gw < 60) return;
        String[] lbl = {"Valid", "Renew", "≤ 30 days", "Expired"};
        float cell = gw / 2f, ch = Math.min(40, h / 2f);
        float gy = y + (h - 2 * ch) / 2f;
        for (int i = 0; i < 4; i++) {
            float px = gx + (i % 2) * cell, py = gy + (i / 2) * ch;
            c.drawRoundRect(new RectF(px, py + 4, px + 4, py + 4 + 24), 2, 2, fill(cols[i]));
            txt(c, String.valueOf(data.cnt[i]), px + 10, py + 20, text(WHITE, 19, true), cell - 12, Paint.Align.LEFT);
            txt(c, lbl[i], px + 10, py + 32, text(MUTED, 10, false), cell - 12, Paint.Align.LEFT);
        }
    }

    /** 12-month expiry timeline (first bar = already expired). */
    private static void timeline(Canvas c, Data data, float x, float y, float w, float h) {
        txt(c, "Expiry timeline", x, y + 10, text(MUTED, 10.5f, false), w, Paint.Align.LEFT);
        int[] counts = new int[13];
        Calendar now = Calendar.getInstance();
        for (Item it : data.items) {
            if (it.days < 0) { counts[0]++; continue; }
            int idx = (it.y - now.get(Calendar.YEAR)) * 12 + it.m - 1 - now.get(Calendar.MONTH);
            if (idx >= 0 && idx < 12) counts[idx + 1]++;
        }
        int max = 1;
        for (int v : counts) max = Math.max(max, v);
        float top = y + 16, labelH = 11, base = y + h - labelH;
        float gap = 4, bw = (w - gap * 12) / 13f;
        SimpleDateFormat f = new SimpleDateFormat("MMM", Locale.getDefault());
        for (int i = 0; i < 13; i++) {
            float bx = x + i * (bw + gap);
            float bh = counts[i] == 0 ? 3 : Math.max(8, (base - top - 10) * counts[i] / max);
            int col = counts[i] == 0 ? 0x1FFFFFFF : i == 0 ? EXP : data.primary;
            c.drawRoundRect(new RectF(bx, base - bh, bx + bw, base), 3, 3, fill(col));
            if (counts[i] > 0)
                txt(c, String.valueOf(counts[i]), bx + bw / 2, base - bh - 2, text(MUTED, 8, false), 0, Paint.Align.CENTER);
            String l = "!";
            if (i > 0) {
                Calendar m = Calendar.getInstance();
                m.set(Calendar.DAY_OF_MONTH, 1);
                m.add(Calendar.MONTH, i - 1);
                String s = f.format(new Date(m.getTimeInMillis()));
                l = s.length() > 0 ? s.substring(0, 1).toUpperCase() : "";
            }
            txt(c, l, bx + bw / 2, y + h - 1, text(i == 1 ? WHITE : MUTED, 8.5f, i == 1), 0, Paint.Align.CENTER);
        }
    }

    /** Rows of closest deadlines: category icon, name, status progress bar, amount. */
    private static void rows(Canvas c, Data data, float x, float y, float w, float h, int maxRows) {
        int n = (int) Math.min(Math.min(maxRows, data.items.size()), Math.floor(h / 30f));
        if (n < 1) return;
        float rh = Math.min(42, h / n);
        for (int i = 0; i < n; i++) {
            Item it = data.items.get(i);
            float top = y + i * rh;
            float tile = Math.min(28, rh - 6);
            float ty = top + (rh - tile) / 2;
            iconTile(c, it, x, ty, tile);
            float nx = x + tile + 10, rw = 62;
            float textW = w - tile - 10 - rw - 6;
            String[] a = amount(data, it);
            if (rh >= 32) {
                txt(c, it.name, nx, top + rh / 2 - 2, text(WHITE, 13, true), textW, Paint.Align.LEFT);
                float by = top + rh / 2 + 5;
                c.drawRoundRect(new RectF(nx, by, nx + textW, by + 3.5f), 2, 2, fill(TRACK));
                float frac = it.days < 0 ? 1f : Math.max(0.05f, Math.min(1f, it.days / 365f));
                c.drawRoundRect(new RectF(nx, by, nx + textW * frac, by + 3.5f), 2, 2, fill(it.color()));
                txt(c, a[0], x + w, top + rh / 2 + 1, text(it.color(), 16, true), rw, Paint.Align.RIGHT);
                txt(c, a[2], x + w, top + rh / 2 + 12, text(MUTED, 9, false), rw, Paint.Align.RIGHT);
            } else {
                txt(c, it.name, nx, top + rh / 2 + 4.5f, text(WHITE, 13, true), textW, Paint.Align.LEFT);
                txt(c, a[0] + " " + a[2], x + w, top + rh / 2 + 4.5f, text(it.color(), 13, true), rw, Paint.Align.RIGHT);
            }
        }
    }

    /** 4x3: header + closest deadlines. */
    private static void list(Canvas c, Data data, float w, float h) {
        float pad = 14;
        header(c, data, pad, 12, w - 2 * pad);
        if (data.items.isEmpty()) { emptyState(c, data, w, h + 30); return; }
        rows(c, data, pad, 38, w - 2 * pad, h - 38 - 10, 8);
    }

    /** 4x4: full dashboard. */
    private static void full(Canvas c, Data data, float w, float h) {
        float pad = 14;
        header(c, data, pad, 12, w - 2 * pad);
        if (data.items.isEmpty()) { emptyState(c, data, w, h + 30); return; }
        float y = 40;
        float hb = h >= 230 ? 88 : 74;
        health(c, data, pad, y, w - 2 * pad, hb);
        y += hb + 8;
        if (h - y >= 160) {
            timeline(c, data, pad, y, w - 2 * pad, 58);
            y += 66;
        }
        rows(c, data, pad, y, w - 2 * pad, h - y - 10, 6);
    }
}
