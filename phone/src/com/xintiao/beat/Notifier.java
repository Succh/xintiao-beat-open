package com.xintiao.beat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Android notifications. The "love" channel is what Mi Fitness mirrors to a Redmi Watch 3. */
final class Notifier {
    static final String CH_LOVE = "beat_love";
    static final String CH_QUIET = "beat_quiet";
    static final String CH_SERVICE = "beat_service";
    static final int ID_SERVICE = 1;
    private final Context ctx;
    private final NotificationManager nm;
    private int nextId = 100;

    Notifier(Context c) {
        ctx = c;
        nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel love = new NotificationChannel(CH_LOVE, "TA 的心跳", NotificationManager.IMPORTANCE_HIGH);
        love.setDescription("TA 想你、戳你、发短语时提醒（会同步到手表）");
        love.enableVibration(true);
        love.setVibrationPattern(new long[]{0, 400, 200, 400});
        NotificationChannel quiet = new NotificationChannel(CH_QUIET, "静默记录", NotificationManager.IMPORTANCE_LOW);
        quiet.setDescription("免打扰时段或手环已直接提醒时，只在手机上记录");
        NotificationChannel svc = new NotificationChannel(CH_SERVICE, "后台连接", NotificationManager.IMPORTANCE_MIN);
        svc.setDescription("保持和 TA 的连接");
        svc.setShowBadge(false);
        nm.createNotificationChannel(love);
        nm.createNotificationChannel(quiet);
        nm.createNotificationChannel(svc);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    void showIncoming(String title, String body, boolean silent) {
        Notification n = new Notification.Builder(ctx, silent ? CH_QUIET : CH_LOVE)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setColor(0xFFF06A9F)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build();
        try { nm.notify(nextId++, n); } catch (SecurityException ignored) { /* POST_NOTIFICATIONS denied */ }
        if (nextId > 130) nextId = 100;
    }

    Notification service(String text) {
        return new Notification.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("心跳 Beat")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp())
            .build();
    }
}
