package com.countly.app;

/** Home-screen widget "List" (4x3, resizable). Keeps its original class name so existing widgets keep working. */
public class CountlyWidget extends BaseWidget {
    static final String PREFS = "countly";
    static final String KEY = "data";

    @Override String kind() { return "list"; }
    @Override int defW() { return 276; }
    @Override int defH() { return 203; }

    public static void refreshAll(android.content.Context c) { BaseWidget.refreshAll(c); }
}
