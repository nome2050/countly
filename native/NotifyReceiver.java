package com.countly.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fired by the daily alarm. */
public class NotifyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Notifier.run(c, false);
    }
}
