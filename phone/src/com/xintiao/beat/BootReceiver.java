package com.xintiao.beat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            if (c.getSharedPreferences("beat", Context.MODE_PRIVATE).getBoolean("keep_alive", true)) BeatService.start(c);
        }
    }
}
