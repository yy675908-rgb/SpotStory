package com.spotstory.guide;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps a visible notification while the Activity narrates a manually selected story. */
public final class NarrationNoticeService extends Service {
    private static final String CHANNEL = "yantu_narration";
    private static final int NOTIFICATION_ID = 202;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "沿途讲解",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("正在播放景点讲解");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = intent == null ? "沿途正在讲解" : intent.getStringExtra("title");
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_spotstory)
            .setContentTitle(title == null ? "沿途正在讲解" : title)
            .setContentText("点击返回讲解页面")
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION_ID, notification);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
