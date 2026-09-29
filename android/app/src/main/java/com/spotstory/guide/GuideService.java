package com.spotstory.guide;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class GuideService extends Service implements LocationListener, TextToSpeech.OnInitListener {
    static final String ACTION_STOP = "com.spotstory.guide.STOP";
    private static final String CHANNEL = "spotstory_location";
    private static final int NOTIFICATION_ID = 14;
    private final Set<String> spoken = new HashSet<>();
    private LocationManager locationManager;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        tts = new TextToSpeech(this, this);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel(CHANNEL, "到点讲解", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        Notification notification = notification("正在寻找附近的讲解点");
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION_ID, notification);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status("没有定位权限，请先在 App 中开启"); stopSelf(); return START_NOT_STICKY;
        }
        try {
            boolean registered = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 15, this);
                registered = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 15, this);
                registered = true;
            }
            if (!registered) { status("手机定位未开启，请在系统设置中开启"); stopSelf(); return START_NOT_STICKY; }
            status("到点讲解运行中；点击通知可返回应用");
        } catch (SecurityException | IllegalArgumentException e) {
            status("定位暂不可用，请检查手机定位设置"); stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location location) {
        if (!location.hasAccuracy() || location.getAccuracy() > 80) {
            status("定位误差较大，可手动选景点"); return;
        }
        Spots.Spot spot = Spots.nearest(location);
        if (spot != null && spoken.add(spot.id)) {
            status("已到达：" + spot.name);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification("已到达：" + spot.name));
            say(spot.intro);
        }
    }

    private Notification notification(String detail) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, GuideService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setContentTitle("SpotStory · 到点讲解")
            .setContentText(detail).setSmallIcon(R.drawable.ic_spotstory).setContentIntent(openIntent)
            .addAction(new Notification.Action.Builder(null, "停止", stopIntent).build()).setOngoing(true).build();
    }

    private void status(String value) { getSharedPreferences("guide", MODE_PRIVATE).edit().putString("status", value).apply(); }
    private void say(String value) {
        if (ttsReady) tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "arrival");
        else pendingSpeech = value;
    }
    @Override public void onInit(int result) {
        if (result == TextToSpeech.SUCCESS && tts.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE) {
            ttsReady = true;
            if (pendingSpeech != null) { say(pendingSpeech); pendingSpeech = null; }
        } else status("未找到中文语音引擎，仍可阅读讲解");
    }
    @Override public void onDestroy() {
        locationManager.removeUpdates(this);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        status("到点讲解已停止");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
