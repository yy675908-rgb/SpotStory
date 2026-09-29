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
    static final String ACTION_TOGGLE = "com.spotstory.guide.NARRATION_TOGGLE";
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
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            if (!MainActivity.toggleFromNotice()) stopSelf();
            return START_NOT_STICKY;
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String spot = intent == null ? null : intent.getStringExtra("spot");
        String excerpt = intent == null ? null : intent.getStringExtra("excerpt");
        boolean playing = intent == null || intent.getBooleanExtra("playing", true);
        Intent toggle = new Intent(this, NarrationNoticeService.class).setAction(ACTION_TOGGLE);
        PendingIntent control = PendingIntent.getService(this, 1, toggle,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_spotstory)
            .setContentTitle((playing ? "正在讲解 · " : "已暂停 · ") + (spot == null ? "沿途" : spot))
            .setContentText(excerpt == null || excerpt.isEmpty() ? "点击返回讲解页面" : excerpt)
            .setContentIntent(pending)
            .addAction(new Notification.Action.Builder(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "暂停" : "播放", control).build())
            .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0))
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION_ID, notification);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
