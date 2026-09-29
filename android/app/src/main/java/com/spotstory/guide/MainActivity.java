package com.spotstory.guide;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.lang.ref.WeakReference;

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 10, VOICE_REQUEST = 11, MIC_REQUEST = 12, EXPLORE_LOCATION_REQUEST = 13, NOTICE_REQUEST = 14;
    private static final int EXPLORE_FOOD = 1, EXPLORE_FUN = 2, EXPLORE_NIGHT = 3, EXPLORE_SHOW = 4;
    private final int green = Color.rgb(18, 76, 68), ink = Color.rgb(23, 53, 47), cream = Color.rgb(250, 250, 248);
    private final int sage = Color.rgb(220, 237, 229), accent = Color.rgb(204, 231, 220);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); } };
    private TextToSpeech tts;
    private boolean ttsReady;
    private ScrollView scroll;
    private LinearLayout root, detail, artifacts, explorePanel;
    private TextView statusText, introText, storyText, lookForText, answerText, voiceStatusText;
    private final TextView[] rateButtons = new TextView[3];
    private final java.util.Map<String, TextView> spotPlayButtons = new java.util.HashMap<>();
    private final java.util.Map<String, LinearLayout> spotCards = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> spotHeaders = new java.util.HashMap<>();
    private Spots.Spot current;
    private long lastArrivalHandled;
    private final List<String> speechChunks = new ArrayList<>();
    private int speechIndex;
    private long speechGeneration;
    private String activeSpeechId;
    private boolean speaking;
    private TextView speechButton;
    private int pendingExploreAction;
    private final List<android.location.LocationListener> exploreListeners = new ArrayList<>();
    private Runnable exploreTimeout;
    private int exploreRequestToken;
    private SpeechRecognizer recognizer;
    private boolean handsFree, listening, waitingForQuestion, foreground;
    private int recognitionFailures;
    private boolean useOnDeviceRecognizer, triedOnDeviceFallback;
    private boolean askedNoticePermission;
    private String voiceProblem = "";
    private String lastNonWake = "";
    private final Runnable restartListening = this::listenAgain;
    private static WeakReference<MainActivity> noticeActivity = new WeakReference<>(null);

    static boolean toggleFromNotice() {
        MainActivity activity = noticeActivity.get();
        if (activity == null || activity.speechChunks.isEmpty()) return false;
        activity.toggleNarration(activity.speechButton, "");
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        noticeActivity = new WeakReference<>(this);
        getWindow().setStatusBarColor(cream); getWindow().setNavigationBarColor(cream);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE) {
                VoiceSettings.apply(this, tts); ttsReady = true;
            }
            else if (statusText != null) statusText.setText("未找到中文语音引擎，文字讲解仍可阅读");
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) {
                runOnUiThread(() -> {
                    if (!speaking || !id.equals(activeSpeechId)) return;
                    speechIndex++;
                    playNextChunk();
                });
            }
            @Override public void onError(String id) {
                runOnUiThread(() -> { if (id.equals(activeSpeechId)) stopNarration(); });
            }
        });
        buildScreen();
        detail.setVisibility(View.GONE);
    }

    private void buildScreen() {
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(cream);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root); setContentView(scroll);

        LinearLayout intro = new LinearLayout(this); intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(dp(22), dp(4), dp(22), dp(13)); root.addView(intro);
        LinearLayout signature = new LinearLayout(this); signature.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView brand = text("沿途", 18, green, true); signature.addView(brand);
        TextView latin = text(" /  YAN TU", 12, green, false);
        latin.setLetterSpacing(.11f); signature.addView(latin); intro.addView(signature);
        TextView headline = text("走到哪，讲到哪", 25, ink, true);
        headline.setPadding(0, dp(8), 0, dp(3)); intro.addView(headline);
        voiceStatusText = text("播讲时说“沿途”提问 · 史实附来源", 12, green, false);
        voiceStatusText.setPadding(dp(8), 0, 0, 0); intro.addView(voiceStatusText);

        LinearLayout location = new LinearLayout(this); location.setOrientation(LinearLayout.VERTICAL);
        location.setBackground(round(sage, 17));
        location.setPadding(dp(18), dp(14), dp(18), dp(16));
        TextView locTitle = text("到点讲解", 17, ink, true); location.addView(locTitle);
        statusText = text("定位尚未开启", 13, Color.rgb(97, 116, 106), false);
        statusText.setPadding(0, dp(4), 0, dp(8)); location.addView(statusText);
        LinearLayout locationActions = new LinearLayout(this); locationActions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button start = button("开启到点讲解", green, Color.WHITE);
        start.setOnClickListener(v -> enableLocation());
        locationActions.addView(start, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView stop = text("停止", 14, green, false); stop.setGravity(android.view.Gravity.CENTER);
        stop.setOnClickListener(v -> { stopService(new Intent(this, GuideService.class)); statusText.setText("到点讲解已停止"); });
        LinearLayout.LayoutParams stopSize = new LinearLayout.LayoutParams(dp(64), dp(46));
        stopSize.setMargins(dp(8), 0, 0, 0); locationActions.addView(stop, stopSize);
        location.addView(locationActions);
        LinearLayout.LayoutParams locationMargin = new LinearLayout.LayoutParams(-1, -2);
        locationMargin.setMargins(dp(18), 0, dp(18), 0); root.addView(location, locationMargin);

        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), 0, dp(18), dp(24)); root.addView(body);

        LinearLayout exploreActions = new LinearLayout(this);
        exploreActions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        exploreActions.setBackground(round(Color.WHITE, 12));
        exploreActions.setPadding(dp(2), dp(2), dp(2), dp(2));
        String[] labels = {"吃什么", "玩什么", "找夜景", "找演出"};
        for (int i = 0; i < labels.length; i++) {
            final int action = i + 1;
            Button option = button(labels[i], Color.WHITE, green);
            option.setBackgroundColor(Color.TRANSPARENT);
            option.setTextSize(14); option.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(44), 1f);
            exploreActions.addView(option, size);
            option.setOnClickListener(v -> explore(action));
            if (i < labels.length - 1) {
                View divider = new View(this); divider.setBackgroundColor(Color.rgb(230, 235, 226));
                exploreActions.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(23)));
            }
        }
        LinearLayout.LayoutParams exploreMargin = new LinearLayout.LayoutParams(-1, -2);
        exploreMargin.setMargins(0, dp(12), 0, 0); body.addView(exploreActions, exploreMargin);
        explorePanel = card(); explorePanel.setVisibility(View.GONE); body.addView(explorePanel);

        LinearLayout section = new LinearLayout(this); section.setGravity(android.view.Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(11), 0, dp(2));
        TextView sectionTitle = text("选择眼前的地方", 19, ink, true);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout speed = new LinearLayout(this); speed.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView speedTitle = text("语速", 11, green, false); speed.addView(speedTitle);
        for (int i = 0; i < rateButtons.length; i++) {
            final int index = i;
            TextView rate = text(VoiceSettings.RATE_LABELS[i], 12, green, false);
            rate.setGravity(android.view.Gravity.CENTER); rate.setPadding(dp(3), dp(11), dp(3), dp(11));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMargins(dp(3), 0, 0, 0);
            speed.addView(rate, size); rateButtons[i] = rate;
            rate.setOnClickListener(v -> {
                VoiceSettings.setRate(this, index); updateRateButtons();
                if (ttsReady) VoiceSettings.apply(this, tts);
                if (speaking) { speechGeneration++; playNextChunk(); }
            });
        }
        section.addView(speed); body.addView(section); updateRateButtons();
        LinearLayout spotList = new LinearLayout(this); spotList.setOrientation(LinearLayout.VERTICAL);
        spotList.setPadding(dp(2), 0, dp(2), 0); body.addView(spotList);
        for (Spots.Spot spot : Spots.ALL) {
            LinearLayout spotCard = new LinearLayout(this); spotCard.setOrientation(LinearLayout.VERTICAL);
            spotCard.setPadding(dp(2), dp(7), dp(2), dp(7)); spotCards.put(spot.id, spotCard);
            LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView item = text(spot.name + "  ⌄\n" + spot.area, 14, ink, false);
            spotHeaders.put(spot.id, item);
            item.setPadding(0, dp(5), 0, dp(5)); item.setContentDescription(spot.name + "，展开或收起讲解");
            item.setOnClickListener(v -> { if (current == spot && detail.getParent() == spotCard) collapseSpot(); else showSpot(spot); });
            final float[] touchY = new float[1];
            item.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { touchY[0] = event.getY(); return true; }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float delta = event.getY() - touchY[0];
                    if (delta > dp(36)) showSpot(spot);
                    else if (delta < -dp(36) && current == spot) collapseSpot();
                    else v.performClick();
                    return true;
                }
                return true;
            });
            header.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView play = text("▶", 20, green, false);
            play.setGravity(android.view.Gravity.CENTER);
            play.setContentDescription("播放" + spot.name + "完整故事");
            play.setOnClickListener(v -> {
                if (current != spot) showSpot(spot);
                toggleNarration(play, spot.story);
            });
            LinearLayout.LayoutParams playSize = new LinearLayout.LayoutParams(dp(40), dp(46));
            playSize.setMargins(dp(2), 0, 0, 0); header.addView(play, playSize);
            spotPlayButtons.put(spot.id, play);
            if (spot.radius > 0) {
                Button route = button("高德 ↗", accent, ink);
                route.setContentDescription("高德步行去" + spot.name);
                route.setOnClickListener(v -> navigateTo(spot));
                route.setTextSize(13); route.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(68), dp(42));
                size.setMargins(dp(16), 0, dp(2), 0); header.addView(route, size);
            }
            spotCard.addView(header); spotList.addView(spotCard);
            if (spot != Spots.ALL.get(Spots.ALL.size() - 1)) {
                View divider = new View(this); divider.setBackgroundColor(Color.rgb(224, 232, 228));
                spotList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }

        detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(0, dp(6), 0, 0);
        LinearLayout sourceRow = new LinearLayout(this); sourceRow.setGravity(android.view.Gravity.END);
        TextView source = text("资料来源 ↗", 12, green, false);
        source.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT);
        source.setContentDescription("查看资料来源");
        source.setOnClickListener(v -> { if (current != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(current.source))); });
        sourceRow.addView(source, new LinearLayout.LayoutParams(dp(86), dp(44))); detail.addView(sourceRow);
        introText = text("", 16, ink, false); introText.setLineSpacing(dp(2), 1f); detail.addView(introText);
        TextView findHeading = text("到现场看哪里", 18, green, true);
        findHeading.setPadding(0, dp(13), 0, dp(4)); detail.addView(findHeading);
        lookForText = text("", 15, ink, false); lookForText.setLineSpacing(dp(2), 1f); detail.addView(lookForText);
        TextView storyHeading = text("再听一段 · 历史与典故", 18, green, true);
        storyHeading.setPadding(0, dp(13), 0, dp(4)); detail.addView(storyHeading);
        storyText = text("", 15, ink, false); storyText.setLineSpacing(dp(2), 1f); detail.addView(storyText);
        artifacts = new LinearLayout(this); artifacts.setOrientation(LinearLayout.VERTICAL); detail.addView(artifacts);
        answerText = text("", 15, ink, false); answerText.setPadding(0, dp(8), 0, dp(6)); detail.addView(answerText);
        Button collapse = button("⌃ 收起讲解", Color.rgb(235, 244, 239), green);
        collapse.setOnClickListener(v -> collapseSpot()); detail.addView(collapse);
    }

    private void showSpot(Spots.Spot spot) {
        if (current == spot && detail.getParent() != spotCards.get(spot.id)) {
            spotCards.get(spot.id).addView(detail); detail.setVisibility(View.VISIBLE);
            spotHeaders.get(spot.id).setText(spot.name + "  ⌃\n" + spot.area);
            return;
        }
        if (current == spot) return;
        if (ttsReady) GuideService.pauseSpeech();
        stopNarration();
        if (current != null) spotHeaders.get(current.id).setText(current.name + "  ⌄\n" + current.area);
        if (detail.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) detail.getParent()).removeView(detail);
        spotCards.get(spot.id).addView(detail); detail.setVisibility(View.VISIBLE);
        spotHeaders.get(spot.id).setText(spot.name + "  ⌃\n" + spot.area);
        current = spot; introText.setText(spot.intro); storyText.setText(spot.story);
        lookForText.setText(spot.lookFor.isEmpty() ? "现场细节尚未核实，请看标识。" : spot.lookFor);
        answerText.setText(""); artifacts.removeAllViews();
        if (!spot.artifacts.isEmpty()) {
            TextView label = text("馆内值得看 · 看到展品后选择", 14, green, true);
            label.setPadding(0, dp(12), 0, dp(3)); artifacts.addView(label);
            for (Spots.Artifact artifact : spot.artifacts) {
                Button item = button(artifact.name, Color.rgb(239, 246, 238), green);
                item.setOnClickListener(v -> { answerText.setText(artifact.intro); say(artifact.intro); }); artifacts.addView(item);
            }
        }
    }

    private void collapseSpot() {
        if (current != null) spotHeaders.get(current.id).setText(current.name + "  ⌄\n" + current.area);
        if (detail.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) detail.getParent()).removeView(detail);
        detail.setVisibility(View.GONE);
    }

    private void updateRateButtons() {
        for (int i = 0; i < rateButtons.length; i++) if (rateButtons[i] != null) {
            boolean selected = i == VoiceSettings.rateIndex(this);
            rateButtons[i].setBackground(null);
            rateButtons[i].setTextColor(selected ? green : Color.rgb(108, 134, 122));
            rateButtons[i].setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            rateButtons[i].setPaintFlags(selected
                ? rateButtons[i].getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG
                : rateButtons[i].getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
    }

    private void explore(int action) {
        pendingExploreAction = action;
        explorePanel.removeAllViews(); explorePanel.setVisibility(View.VISIBLE);
        explorePanel.addView(text("正在获取当前位置…", 14, ink, false));
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, EXPLORE_LOCATION_REQUEST);
            return;
        }
        requestExploreLocation(action);
    }

    private void cancelExploreLocation() {
        exploreRequestToken++;
        if (exploreTimeout != null) handler.removeCallbacks(exploreTimeout);
        exploreTimeout = null;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        for (LocationListener listener : exploreListeners) manager.removeUpdates(listener);
        exploreListeners.clear();
    }

    private void requestExploreLocation(int action) {
        cancelExploreLocation();
        final int token = exploreRequestToken;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (!manager.isProviderEnabled(provider)) continue;
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (token != exploreRequestToken || location == null || !location.hasAccuracy() ||
                        location.getAccuracy() > 150 || Math.abs(System.currentTimeMillis() - location.getTime()) > 20000) return;
                    double[] point = Geo.wgsToGcj(location.getLatitude(), location.getLongitude());
                    cancelExploreLocation();
                    showExploreAt(action, point);
                }
                @Override public void onStatusChanged(String name, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String name) { }
                @Override public void onProviderDisabled(String name) { }
            };
            try {
                manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
                exploreListeners.add(listener);
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (exploreListeners.isEmpty()) { showExploreError("请开启手机定位后再试。"); return; }
        exploreTimeout = () -> {
            if (token != exploreRequestToken) return;
            cancelExploreLocation(); showExploreError("暂时无法获取准确的实时位置，请到开阔处重试。");
        };
        handler.postDelayed(exploreTimeout, 15000);
    }

    private void showExploreError(String message) {
        explorePanel.removeAllViews(); explorePanel.setVisibility(View.VISIBLE);
        explorePanel.addView(text(message, 14, ink, false));
    }

    private void showExploreAt(int action, double[] point) {
        if (action == EXPLORE_NIGHT || action == EXPLORE_SHOW) {
            explorePanel.setVisibility(View.GONE);
            searchAroundAt(action == EXPLORE_NIGHT ? "夜景" : "演出", point);
            return;
        }
        explorePanel.removeAllViews(); explorePanel.setVisibility(View.VISIBLE);
        Button close = button("⌃ 收起", Color.rgb(239, 246, 238), green);
        close.setOnClickListener(v -> explorePanel.setVisibility(View.GONE)); explorePanel.addView(close);
        explorePanel.addView(text("按当前位置排序 · 步行距离为估算", 13, green, false));
        if (action == EXPLORE_FOOD) renderFoodAt(point);
        else renderFunAt(point);
    }

    private void renderFoodAt(double[] point) {
        List<Restaurants.Restaurant> nearby = Restaurants.near(point[0], point[1]);
        if (nearby.isEmpty()) explorePanel.addView(text("当前位置附近暂未收录餐厅，可在美团继续找。", 14, ink, false));
        for (Restaurants.Restaurant item : nearby) {
            LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));
            long meters = Math.round(Geo.distance(point[0], point[1], item.lat, item.lng) * 1.3 / 50) * 50;
            TextView info = text(item.name + "\n" + item.kind + " · 预计步行约" + meters + "米", 14, ink, false);
            info.setLineSpacing(dp(3), 1f); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Button map = button("⌖", Color.rgb(239, 246, 238), green);
            map.setContentDescription("美团查找" + item.name);
            map.setOnClickListener(v -> openMeituan(item.name));
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(52), dp(52));
            iconSize.setMargins(dp(8), 0, 0, 0); row.addView(map, iconSize);
            explorePanel.addView(row);
        }
        Button more = button("美团查看更多餐厅 ↗", Color.rgb(239, 246, 238), green);
        more.setOnClickListener(v -> openMeituan("不辣 美食")); explorePanel.addView(more);
        explorePanel.addView(text("步行距离按直线距离估算；口味和营业请在美团核对，点单说明不吃辣。", 12, green, false));
    }

    private void renderFunAt(double[] point) {
        List<Spots.Spot> nearby = new ArrayList<>();
        for (Spots.Spot spot : Spots.ALL) if (spot.radius > 0 &&
            Geo.distance(point[0], point[1], spot.gcj ? spot.lat : Geo.wgsToGcj(spot.lat, spot.lng)[0],
                spot.gcj ? spot.lng : Geo.wgsToGcj(spot.lat, spot.lng)[1]) <= 3500) nearby.add(spot);
        nearby.sort((a, b) -> Double.compare(exploreDistance(a, point), exploreDistance(b, point)));
        if (nearby.isEmpty()) explorePanel.addView(text("当前位置附近暂未收录讲解点，可在高德继续找。", 14, ink, false));
        for (Spots.Spot spot : nearby) {
            LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            long meters = Math.round(exploreDistance(spot, point) * 1.3 / 50) * 50;
            TextView info = text(spot.name + "\n" + spot.area + " · 预计步行约" + meters + "米", 14, ink, false);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Button route = button("高德 ↗", Color.rgb(239, 246, 238), green);
            route.setOnClickListener(v -> navigateTo(spot));
            row.addView(route, new LinearLayout.LayoutParams(dp(84), dp(48)));
            explorePanel.addView(row);
        }
        Button more = button("高德找更多玩法 ↗", Color.rgb(239, 246, 238), green);
        more.setOnClickListener(v -> searchAroundAt("景点", point)); explorePanel.addView(more);
    }

    private double exploreDistance(Spots.Spot spot, double[] point) {
        double[] target = spot.gcj ? new double[]{spot.lat, spot.lng} : Geo.wgsToGcj(spot.lat, spot.lng);
        return Geo.distance(point[0], point[1], target[0], target[1]);
    }

    private void openMeituan(String keyword) {
        Uri uri = Uri.parse("imeituan://www.meituan.com/search").buildUpon()
            .appendQueryParameter("q", keyword).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri); intent.setPackage("com.sankuai.meituan");
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.sankuai.meituan");
            if (launch != null) startActivity(launch);
            else android.widget.Toast.makeText(this, "请先安装美团 App", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void enableLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        } else startGuide();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST) {
            boolean granted = false;
            for (int value : results) if (value == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) startGuide(); else statusText.setText("未获得定位权限，可手动选景点");
        } else if (requestCode == EXPLORE_LOCATION_REQUEST) {
            boolean granted = false;
            for (int value : results) if (value == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) explore(pendingExploreAction);
            else showExploreError("请授权定位后再查当前位置周边。");
        } else if (requestCode == MIC_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) enableHandsFree();
            else voiceStatusText.setText("未授权麦克风，无法语音打断 · 史实附来源");
        }
    }
    private void startGuide() {
        try { startForegroundService(new Intent(this, GuideService.class)); statusText.setText("正在开启到点讲解…"); }
        catch (Exception e) { statusText.setText("无法启动定位，请检查手机定位设置"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null && current != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) handleVoiceQuestion(heard.get(0));
        }
    }
    private void handleVoiceQuestion(String question) {
        if (current == null || question.trim().isEmpty()) return;
                if (question.contains("怎么走") || question.contains("我要去") || question.contains("带我去")) {
                    int marker = Math.max(question.lastIndexOf("去"), question.lastIndexOf("到"));
                    String targetQuestion = marker >= 0 ? question.substring(marker + 1) : question;
                    for (Spots.Spot destination : Spots.ALL) if (destination.radius > 0 &&
                        (targetQuestion.contains(destination.name.split(" · ")[0]) ||
                         (destination.id.equals("juzizhou") && targetQuestion.contains("橘子洲")) ||
                         (destination.id.equals("taiping") && targetQuestion.contains("太平街")) ||
                         (destination.id.equals("jiayi") && targetQuestion.contains("长怀井")) ||
                         (destination.id.equals("huogong") && targetQuestion.contains("坡子街")))) {
                        answerText.setText("已打开去“" + destination.name + "”的步行路线");
                        navigateTo(destination); return;
                    }
                }
                if (question.contains("吃") || question.contains("饭店") || question.contains("餐馆") || question.contains("美食")) {
                    explore(EXPLORE_FOOD);
                    answerText.setText("正在定位，附近餐厅会显示在上方。"); return;
                }
                if (question.contains("玩什么") || question.contains("哪里好玩")) {
                    explore(EXPLORE_FUN); answerText.setText("正在定位，附近玩法会显示在上方。"); return;
                }
                if (question.contains("夜景") || question.contains("演出") || question.contains("花鼓戏")) {
                    explore(question.contains("演出") || question.contains("花鼓戏") ? EXPLORE_SHOW : EXPLORE_NIGHT);
                    answerText.setText("正在定位，随后会打开高德搜索；请核对当天信息。"); return;
                }
                String answer = question.contains("附近") || question.contains("周围") || question.contains("前面有什么")
                    ? nearbyText() : Spots.answer(current, question);
                answerText.setText("你问：“" + question + "”\n" + answer);
                say(answer);
    }

    private void ensureHandsFree() {
        if (handsFree) { scheduleListening(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceStatusText.setText("请授权麦克风，才能说“沿途”打断 · 史实附来源");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return;
        }
        enableHandsFree();
    }
    private void enableHandsFree() {
        boolean onDeviceAvailable = android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (!onDeviceAvailable && !SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatusText.setText("手机缺少语音识别服务，暂无法语音打断 · 史实附来源"); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this) && onDeviceAvailable) useOnDeviceRecognizer = true;
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        handsFree = true;
        recognitionFailures = 0;
        lastNonWake = "";
        try {
            recognizer = useOnDeviceRecognizer && onDeviceAvailable
                ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                : SpeechRecognizer.createSpeechRecognizer(this);
        } catch (RuntimeException e) {
            handsFree = false;
            voiceStatusText.setText("手机语音服务无法启动 · 史实附来源"); return;
        }
        final SpeechRecognizer activeRecognizer = recognizer;
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (recognizer != activeRecognizer) return;
                voiceProblem = "";
                voiceStatusText.setText(lastNonWake.isEmpty() ? "正在听“沿途” · 史实附来源"
                    : "正在听 · 上次听到“" + lastNonWake + "”");
            }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }
            @Override public void onPartialResults(Bundle partial) {
                if (recognizer != activeRecognizer) return;
                String phrase = recognizedText(partial);
                if (!waitingForQuestion && wakeIndex(phrase) >= 0) interruptForQuestion();
            }
            @Override public void onResults(Bundle results) {
                if (recognizer != activeRecognizer) return;
                listening = false;
                recognitionFailures = 0;
                String phrase = recognizedText(results);
                int wake = wakeIndex(phrase);
                if (wake >= 0) {
                    lastNonWake = "";
                    if (!waitingForQuestion) interruptForQuestion();
                    phrase = phrase.substring(wake + 2).trim();
                } else if (!waitingForQuestion) {
                    if (!phrase.isEmpty()) lastNonWake = phrase.substring(0, Math.min(phrase.length(), 8));
                    scheduleListening(); return;
                }
                if (!phrase.isEmpty()) {
                    waitingForQuestion = false;
                    handleVoiceQuestion(phrase);
                } else answerText.setText("我在听，接着说你的问题…");
                scheduleListening();
            }
            @Override public void onError(int error) {
                if (recognizer != activeRecognizer) return;
                listening = false;
                if (!foreground || (!speaking && !waitingForQuestion)) return;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    handsFree = false;
                    voiceProblem = "麦克风权限已关闭，无法语音打断";
                    voiceStatusText.setText(voiceProblem + " · 史实附来源"); return;
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    recognitionFailures++;
                    if (error == SpeechRecognizer.ERROR_CLIENT || recognitionFailures >= 3) {
                        if (!triedOnDeviceFallback && !useOnDeviceRecognizer && onDeviceAvailable) {
                            triedOnDeviceFallback = true;
                            useOnDeviceRecognizer = true;
                            voiceStatusText.setText("正在切换设备端语音识别 · 史实附来源");
                            handler.postDelayed(() -> { if (foreground && (speaking || waitingForQuestion)) enableHandsFree(); }, 450);
                            return;
                        }
                        handsFree = false;
                        voiceProblem = (useOnDeviceRecognizer ? "设备端" : "系统") + "语音识别不可用（错误 " + error + "）";
                        voiceStatusText.setText(voiceProblem + " · 史实附来源");
                        return;
                    }
                    voiceStatusText.setText("语音识别重试中（错误 " + error + "） · 史实附来源");
                }
                scheduleListening();
            }
        });
        voiceStatusText.setText((useOnDeviceRecognizer ? "设备端" : "系统") + "语音识别准备中 · 史实附来源");
        scheduleListening();
    }
    private int wakeIndex(String phrase) {
        int index = phrase.indexOf("沿途");
        return index >= 0 ? index : phrase.indexOf("沿图");
    }
    private void interruptForQuestion() {
        waitingForQuestion = true;
        GuideService.pauseSpeech(); stopNarration();
        voiceStatusText.setText("我在听你的问题 · 史实附来源");
        answerText.setText("我在听，接着说你的问题…");
    }
    private String recognizedText(Bundle result) {
        ArrayList<String> matches = result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return "";
        for (String match : matches) if (wakeIndex(match) >= 0) return match;
        return matches.get(0);
    }
    private void scheduleListening() {
        handler.removeCallbacks(restartListening);
        if (handsFree && foreground && current != null && (speaking || waitingForQuestion))
            handler.postDelayed(restartListening, recognitionFailures > 0 ? 1000 : 250);
    }
    private void listenAgain() {
        if (!handsFree || !foreground || current == null || (!speaking && !waitingForQuestion) ||
            listening || recognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> hints = new ArrayList<>(); hints.add("沿途"); hints.add("沿途我要问");
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints);
        }
        try { recognizer.startListening(intent); listening = true; }
        catch (RuntimeException e) {
            listening = false;
            if (!triedOnDeviceFallback && !useOnDeviceRecognizer && android.os.Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                triedOnDeviceFallback = true; useOnDeviceRecognizer = true;
                handler.postDelayed(this::enableHandsFree, 450);
            } else {
                handsFree = false;
                voiceProblem = "手机语音监听无法启动";
                voiceStatusText.setText(voiceProblem + " · 史实附来源");
            }
        }
    }
    private void stopListening() {
        handler.removeCallbacks(restartListening);
        if (recognizer != null && listening) { recognizer.cancel(); listening = false; }
        waitingForQuestion = false;
    }

    private void say(String content) { startNarration(content, null); }
    private void toggleNarration(TextView button, String content) {
        if (speechButton == button && !speechChunks.isEmpty()) {
            if (speaking) {
                speaking = false; speechGeneration++; activeSpeechId = null; tts.stop();
                showNarrationNotice();
                stopListening(); voiceStatusText.setText("播讲时说“沿途”提问 · 史实附来源");
                updateSpeechButtons();
            }
            else { speaking = true; showNarrationNotice(); playNextChunk(); updateSpeechButtons(); }
        } else startNarration(content, button);
        if (speaking) ensureHandsFree();
    }
    private void startNarration(String content, TextView button) {
        if (!ttsReady) { answerText.setText("系统中文语音尚未就绪，可阅读文字讲解"); return; }
        stopNarration();
        lastNonWake = "";
        for (String part : content.split("(?<=[。！？；])|\\n+")) if (!part.trim().isEmpty()) speechChunks.add(part.trim());
        if (speechChunks.isEmpty()) return;
        speechButton = button; speaking = true; showNarrationNotice(); playNextChunk(); updateSpeechButtons();
    }
    private void playNextChunk() {
        if (!speaking || speechIndex >= speechChunks.size()) { stopNarration(); return; }
        activeSpeechId = "manual:" + speechGeneration + ":" + speechIndex;
        if (tts.speak(speechChunks.get(speechIndex), TextToSpeech.QUEUE_FLUSH, null, activeSpeechId) == TextToSpeech.ERROR)
            stopNarration();
        else showNarrationNotice();
    }
    private void stopNarration() {
        speaking = false; speechGeneration++; speechIndex = 0;
        stopService(new Intent(this, NarrationNoticeService.class));
        speechChunks.clear(); activeSpeechId = null; speechButton = null;
        if (tts != null) tts.stop();
        if (!waitingForQuestion) {
            stopListening();
            if (voiceStatusText != null) voiceStatusText.setText(
                (voiceProblem.isEmpty() ? "播讲时说“沿途”提问" : voiceProblem) + " · 史实附来源");
        }
        updateSpeechButtons();
    }
    private void showNarrationNotice() {
        String excerpt = speechIndex < speechChunks.size() ? speechChunks.get(speechIndex) : "";
        if (excerpt.length() > 60) excerpt = excerpt.substring(0, 60) + "…";
        try { startForegroundService(new Intent(this, NarrationNoticeService.class)
            .putExtra("spot", current == null ? "沿途讲解" : current.name)
            .putExtra("excerpt", excerpt)
            .putExtra("playing", speaking)); }
        catch (RuntimeException e) { voiceStatusText.setText("通知栏无法启动 · 史实附来源"); }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !askedNoticePermission && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNoticePermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTICE_REQUEST);
        }
    }
    private void updateSpeechButtons() {
        for (Spots.Spot spot : Spots.ALL) {
            TextView button = spotPlayButtons.get(spot.id);
            if (button == null) continue;
            boolean playing = speaking && speechButton == button;
            button.setText(playing ? "Ⅱ" : "▶");
            button.setContentDescription((playing ? "暂停" : "播放") + spot.name + "完整故事");
        }
    }
    private void navigateTo(Spots.Spot spot) {
        if (spot == null) return;
        if (spot.radius <= 0) { answerText.setText("馆内位置请按现场导览手动寻找"); return; }
        double[] point = spot.gcj ? new double[]{spot.lat, spot.lng} : Geo.wgsToGcj(spot.lat, spot.lng);
        openAmapRoute(point[0], point[1], spot.name, null);
    }
    private void openAmapRoute(double lat, double lng, String name, String poi) {
        Uri.Builder builder = Uri.parse("amapuri://route/plan/").buildUpon()
            .appendQueryParameter("sourceApplication", "沿途")
            .appendQueryParameter("dlat", String.valueOf(lat)).appendQueryParameter("dlon", String.valueOf(lng))
            .appendQueryParameter("dname", name).appendQueryParameter("dev", "0").appendQueryParameter("t", "2");
        if (poi != null) builder.appendQueryParameter("did", poi);
        Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
        intent.setPackage("com.autonavi.minimap");
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, "无法打开高德地图，请检查安装", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void searchAroundAt(String query, double[] point) {
        Uri url = Uri.parse("androidamap://arroundpoi").buildUpon()
            .appendQueryParameter("sourceApplication", "沿途")
            .appendQueryParameter("keywords", query)
            .appendQueryParameter("lat", String.valueOf(point[0]))
            .appendQueryParameter("lon", String.valueOf(point[1]))
            .appendQueryParameter("dev", "0").build();
        Intent intent = new Intent(Intent.ACTION_VIEW, url); intent.setPackage("com.autonavi.minimap");
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, "无法打开高德地图，请检查安装", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private String nearbyText() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return "先开启定位权限，再查附近讲解点。";
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location value = manager.getLastKnownLocation(provider);
                if (value != null && (best == null || value.getTime() > best.getTime())) best = value;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (best == null || System.currentTimeMillis() - best.getTime() > 120000 || !best.hasAccuracy() || best.getAccuracy() > 100)
            return "位置还不够新或不够准。请开启到点讲解，等定位更新后再试。";
        final Location position = best;
        double[] gcj = Geo.wgsToGcj(position.getLatitude(), position.getLongitude());
        java.util.List<Spots.Spot> candidates = new java.util.ArrayList<>();
        for (Spots.Spot spot : Spots.ALL) if (spot.radius > 0) candidates.add(spot);
        candidates.sort((a, b) -> Double.compare(nearbyDistance(a, position, gcj), nearbyDistance(b, position, gcj)));
        StringBuilder result = new StringBuilder("附近已收录：");
        int count = 0;
        for (Spots.Spot spot : candidates) {
            double meters = nearbyDistance(spot, position, gcj);
            if (meters > 2000 || count >= 3) break;
            result.append(count == 0 ? "" : "、").append(spot.name).append("约")
                .append(Math.round(meters)).append("米");
            count++;
        }
        if (count == 0) return "附近两公里内暂无收录的长沙讲解点。";
        return result.append("。这是直线距离；步行路线请点选目的地后打开地图。").toString();
    }
    private double nearbyDistance(Spots.Spot spot, Location original, double[] gcj) {
        return Geo.distance(spot.gcj ? gcj[0] : original.getLatitude(),
            spot.gcj ? gcj[1] : original.getLongitude(), spot.lat, spot.lng);
    }
    private void updateStatus() {
        if (statusText == null) return;
        android.content.SharedPreferences guide = getSharedPreferences("guide", MODE_PRIVATE);
        statusText.setText(guide.getString("status", "定位尚未开启"));
        long arrival = guide.getLong("arrival_time", 0);
        if (arrival <= lastArrivalHandled || System.currentTimeMillis() - arrival > 300000) return;
        lastArrivalHandled = arrival;
        String id = guide.getString("arrival_spot", "");
        for (Spots.Spot spot : Spots.ALL) if (spot.id.equals(id)) {
            showSpot(spot); if (ttsReady) startNarration(spot.story, spotPlayButtons.get(spot.id)); ensureHandsFree(); break;
        }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; handler.post(refresh); scheduleListening(); }
    @Override protected void onPause() { foreground = false; handler.removeCallbacks(refresh); stopListening(); cancelExploreLocation(); super.onPause(); }
    @Override protected void onDestroy() { cancelExploreLocation(); stopListening(); stopService(new Intent(this, NarrationNoticeService.class)); if (noticeActivity.get() == this) noticeActivity.clear(); if (recognizer != null) recognizer.destroy(); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14)); layout.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(8), 0, 0);
        layout.setLayoutParams(params); return layout;
    }
    private Button button(String value, int background, int foreground) {
        Button button = new Button(this); button.setText(value); button.setTextColor(foreground); button.setTextSize(15);
        button.setAllCaps(false); button.setBackground(round(background, 12)); button.setPadding(dp(15), dp(11), dp(15), dp(11));
        button.setStateListAnimator(null); button.setElevation(0); button.setTranslationZ(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(9), 0, 0);
        button.setLayoutParams(params); return button;
    }
}
