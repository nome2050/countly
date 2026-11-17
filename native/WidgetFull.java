package com.countly.app;

/** Large (4x4): full dashboard */
public class WidgetFull extends BaseWidget {
    @Override String kind() { return "full"; }
    @Override int defW() { return 276; }
    @Override int defH() { return 276; }
}
