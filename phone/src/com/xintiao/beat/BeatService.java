package com.xintiao.beat;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Foreground service that keeps the relay WebSocket + band listener alive in the background. */
public class BeatService extends Service {

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, BeatService.class));
        } catch (Exception ignored) {
            // background start not allowed right now; the activity will start it next time
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Hub hub = Hub.get(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.ID_SERVICE, hub.notifier.service("保持和 TA 的连接"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(Notifier.ID_SERVICE, hub.notifier.service("保持和 TA 的连接"));
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        hub.start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
