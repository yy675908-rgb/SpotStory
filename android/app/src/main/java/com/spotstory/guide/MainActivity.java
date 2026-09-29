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

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 10, VOICE_REQUEST = 11, MIC_REQUEST = 12, EXPLORE_LOCATION_REQUEST = 13;
    private static final int EXPLORE_FOOD = 1, EXPLORE_FUN = 2, EXPLORE_NIGHT = 3, EXPLORE_SHOW = 4;
    private final int green = Color.rgb(18, 76, 68), ink = Color.rgb(23, 53, 47), cream = Color.rgb(245, 245, 237);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); } };
    private TextToSpeech tts;
    private boolean ttsReady;
    private ScrollView scroll;
    private LinearLayout root, detail, artifacts, explorePanel;
    private TextView statusText, introText, storyText, lookForText, answerText;
    private final Button[] rateButtons = new Button[3];
    private final java.util.Map<String, Button> spotPlayButtons = new java.util.HashMap<>();
    private final java.util.Map<String, LinearLayout> spotCards = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> spotHeaders = new java.util.HashMap<>();
    private Spots.Spot current;
    private long lastArrivalHandled;
    private final List<String> speechChunks = new ArrayList<>();
    private int speechIndex;
    private long speechGeneration;
    private String activeSpeechId;
    private boolean speaking;
    private Button speechButton;
    private int pendingExploreAction;
    private final List<android.location.LocationListener> exploreListeners = new ArrayList<>();
    private Runnable exploreTimeout;
    private int exploreRequestToken;
    private SpeechRecognizer recognizer;
    private boolean handsFree, listening, waitingForQuestion, foreground;
    private final Runnable restartListening = this::listenAgain;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(cream);
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
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(17), dp(24), dp(32));
        scroll.addView(root); setContentView(scroll);

        TextView brand = text("沿途  /  YAN TU", 13, green, true); root.addView(brand);
        TextView headline = text("走到哪，讲到哪", 29, ink, true); headline.setPadding(0, dp(8), 0, dp(4)); root.addView(headline);
        root.addView(text("播放时说“沿途”提问，能打断讲解。史实附来源。", 12, green, false));

        LinearLayout location = card(); location.setBackground(round(green, 18));
        location.setPadding(dp(20), dp(20), dp(20), dp(22));
        TextView locTitle = text("到点讲解", 18, Color.WHITE, true); location.addView(locTitle);
        statusText = text("定位尚未开启", 13, Color.rgb(218, 237, 227), false);
        statusText.setPadding(0, dp(8), 0, dp(9)); location.addView(statusText);
        LinearLayout locationActions = new LinearLayout(this);
        Button start = button("开启到点讲解", Color.rgb(236, 200, 141), ink);
        start.setOnClickListener(v -> enableLocation());
        locationActions.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button stop = button("停止", Color.rgb(30, 96, 84), Color.WHITE);
        stop.setOnClickListener(v -> { stopService(new Intent(this, GuideService.class)); statusText.setText("到点讲解已停止"); });
        LinearLayout.LayoutParams stopSize = new LinearLayout.LayoutParams(0, dp(52), 1f);
        stopSize.setMargins(dp(6), 0, 0, 0); locationActions.addView(stop, stopSize);
        location.addView(locationActions); root.addView(location);

        LinearLayout exploreActions = new LinearLayout(this);
        String[] labels = {"吃什么", "玩什么", "找夜景", "找演出"};
        for (int i = 0; i < labels.length; i++) {
            final int action = i + 1;
            Button option = button(labels[i], Color.WHITE, green);
            option.setTextSize(14); option.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1f);
            size.setMargins(i == 0 ? 0 : dp(5), 0, 0, 0);
            exploreActions.addView(option, size);
            option.setOnClickListener(v -> explore(action));
        }
        LinearLayout.LayoutParams exploreMargin = new LinearLayout.LayoutParams(-1, -2);
        exploreMargin.setMargins(0, dp(12), 0, 0); root.addView(exploreActions, exploreMargin);
        explorePanel = card(); explorePanel.setVisibility(View.GONE); root.addView(explorePanel);

        LinearLayout section = new LinearLayout(this); section.setGravity(android.view.Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(15), 0, dp(3));
        TextView sectionTitle = text("选择眼前的地方", 19, ink, true);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout speed = new LinearLayout(this); speed.setOrientation(LinearLayout.VERTICAL);
        TextView speedTitle = text("语速", 11, green, false); speed.addView(speedTitle);
        LinearLayout rates = new LinearLayout(this);
        for (int i = 0; i < rateButtons.length; i++) {
            final int index = i;
            Button rate = button(VoiceSettings.RATE_LABELS[i], Color.WHITE, green);
            rate.setTextSize(12); rate.setPadding(0, 0, 0, 0); rate.setMinWidth(0); rate.setMinimumWidth(0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(43), dp(38));
            if (i > 0) size.setMargins(dp(3), 0, 0, 0);
            rates.addView(rate, size); rateButtons[i] = rate;
            rate.setOnClickListener(v -> {
                VoiceSettings.setRate(this, index); updateRateButtons();
                if (ttsReady) VoiceSettings.apply(this, tts);
                if (speaking) { speechGeneration++; playNextChunk(); }
            });
        }
        speed.addView(rates); section.addView(speed); root.addView(section); updateRateButtons();
        for (Spots.Spot spot : Spots.ALL) {
            LinearLayout spotCard = card(); spotCards.put(spot.id, spotCard);
            LinearLayout.LayoutParams cardSize = (LinearLayout.LayoutParams) spotCard.getLayoutParams();
            cardSize.setMargins(dp(4), dp(8), dp(4), 0); spotCard.setLayoutParams(cardSize);
            LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView item = text(spot.name + "  ⌄\n" + spot.area, 17, ink, false);
            spotHeaders.put(spot.id, item);
            item.setPadding(0, dp(6), 0, dp(6)); item.setContentDescription(spot.name + "，展开或收起讲解");
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
            Button play = button("▶", Color.rgb(239, 246, 238), green);
            play.setTextSize(20); play.setPadding(0, 0, 0, 0);
            play.setContentDescription("播放" + spot.name + "完整故事");
            play.setOnClickListener(v -> {
                if (current != spot || detail.getParent() != spotCard) showSpot(spot);
                toggleNarration(play, spot.story);
            });
            LinearLayout.LayoutParams playSize = new LinearLayout.LayoutParams(dp(44), dp(46));
            playSize.setMargins(dp(4), 0, 0, 0); header.addView(play, playSize);
            spotPlayButtons.put(spot.id, play);
            if (spot.radius > 0) {
                Button route = button("高德 ↗", Color.rgb(236, 200, 141), ink);
                route.setContentDescription("高德步行去" + spot.name);
                route.setOnClickListener(v -> navigateTo(spot));
                route.setTextSize(13); route.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(72), dp(46));
                size.setMargins(dp(4), 0, 0, 0); header.addView(route, size);
            }
            spotCard.addView(header); root.addView(spotCard);
        }

        detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(0, dp(6), 0, 0);
        LinearLayout sourceRow = new LinearLayout(this); sourceRow.setGravity(android.view.Gravity.END);
        Button source = button("来源 ↗", Color.rgb(239, 246, 238), green);
        source.setTextSize(12); source.setPadding(0, 0, 0, 0);
        source.setContentDescription("查看资料来源");
        source.setOnClickListener(v -> { if (current != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(current.source))); });
        sourceRow.addView(source, new LinearLayout.LayoutParams(dp(70), dp(36))); detail.addView(sourceRow);
        introText = text("", 16, ink, false); introText.setLineSpacing(dp(2), 1f); detail.addView(introText);
        TextView findHeading = text("到现场看哪里", 18, green, true);
        findHeading.setPadding(0, dp(13), 0, dp(4)); detail.addView(findHeading);
        lookForText = text("", 15, ink, false); lookForText.setLineSpacing(dp(2), 1f); detail.addView(lookForText);
        TextView storyHeading = text("再听一段 · 历史与典故", 18, green, true);
        storyHeading.setPadding(0, dp(13), 0, dp(4)); detail.addView(storyHeading);
        storyText = text("", 15, ink, false); storyText.setLineSpacing(dp(2), 1f); detail.addView(storyText);
        artifacts = new LinearLayout(this); artifacts.setOrientation(LinearLayout.VERTICAL); detail.addView(artifacts);
        answerText = text("", 15, ink, false); answerText.setPadding(0, dp(8), 0, dp(6)); detail.addView(answerText);
        Button collapse = button("⌃ 收起讲解", Color.rgb(239, 246, 238), green);
        collapse.setOnClickListener(v -> collapseSpot()); detail.addView(collapse);
        TextView note = text("长沙讲解点持续扩充。步行路线由高德地图规划；GPS 无法可靠判断具体岔路或馆内展柜。到点讲解会在通知栏运行，停止后不再定位。", 12, Color.rgb(97, 116, 106), false);
        note.setPadding(0, dp(10), 0, 0); root.addView(note);
    }

    private void showSpot(Spots.Spot spot) {
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
        stopNarration(); current = null;
        if (detail.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) detail.getParent()).removeView(detail);
        detail.setVisibility(View.GONE); stopListening();
    }

    private void updateRateButtons() {
        for (int i = 0; i < rateButtons.length; i++) if (rateButtons[i] != null) {
            boolean selected = i == VoiceSettings.rateIndex(this);
            rateButtons[i].setBackground(round(selected ? green : Color.rgb(239, 246, 238), 10));
            rateButtons[i].setTextColor(selected ? Color.WHITE : green);
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
            else answerText.setText("未获麦克风权限；文字和播放仍可用。可在系统设置中开启权限。");
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
        if (handsFree) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return;
        }
        enableHandsFree();
    }
    private void enableHandsFree() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            answerText.setText("这台手机没有可用的语音识别服务，暂不能免触屏提问。"); return;
        }
        handsFree = true;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }
            @Override public void onPartialResults(Bundle partial) {
                String phrase = recognizedText(partial);
                if (!waitingForQuestion && phrase.contains("沿途")) {
                    waitingForQuestion = true; GuideService.pauseSpeech(); stopNarration(); answerText.setText("我在听，接着说你的问题…");
                }
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                String phrase = recognizedText(results);
                if (phrase.contains("沿途")) {
                    if (!waitingForQuestion) { waitingForQuestion = true; GuideService.pauseSpeech(); stopNarration(); }
                    phrase = phrase.substring(phrase.indexOf("沿途") + 2).trim();
                } else if (!waitingForQuestion) { scheduleListening(); return; }
                if (!phrase.isEmpty()) {
                    waitingForQuestion = false;
                    handleVoiceQuestion(phrase);
                } else answerText.setText("我在听，接着说你的问题…");
                scheduleListening();
            }
            @Override public void onError(int error) {
                listening = false;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    handsFree = false; answerText.setText("麦克风权限已关闭，免触屏提问已停止。"); return;
                }
                if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER) {
                    handsFree = false; answerText.setText("语音识别服务暂不可用，稍后再点播放重试。"); return;
                }
                scheduleListening();
            }
        });
        answerText.setText("免触屏提问已开启。播讲时说“沿途”再提问。");
        scheduleListening();
    }
    private String recognizedText(Bundle result) {
        ArrayList<String> matches = result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return matches == null || matches.isEmpty() ? "" : matches.get(0);
    }
    private void scheduleListening() {
        handler.removeCallbacks(restartListening);
        if (handsFree && foreground && current != null) handler.postDelayed(restartListening, 600);
    }
    private void listenAgain() {
        if (!handsFree || !foreground || current == null || listening || recognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { recognizer.startListening(intent); listening = true; }
        catch (RuntimeException e) { listening = false; answerText.setText("语音监听暂不可用，请稍后重试播放。"); }
    }
    private void stopListening() {
        handler.removeCallbacks(restartListening);
        if (recognizer != null && listening) { recognizer.cancel(); listening = false; }
        waitingForQuestion = false;
    }

    private void say(String content) { startNarration(content, null); }
    private void toggleNarration(Button button, String content) {
        if (speechButton == button && !speechChunks.isEmpty()) {
            if (speaking) { speaking = false; speechGeneration++; activeSpeechId = null; tts.stop(); updateSpeechButtons(); }
            else { speaking = true; playNextChunk(); updateSpeechButtons(); }
        } else startNarration(content, button);
        ensureHandsFree();
    }
    private void startNarration(String content, Button button) {
        if (!ttsReady) { answerText.setText("系统中文语音尚未就绪，可阅读文字讲解"); return; }
        stopNarration();
        for (String part : content.split("(?<=[。！？；])|\\n+")) if (!part.trim().isEmpty()) speechChunks.add(part.trim());
        if (speechChunks.isEmpty()) return;
        speechButton = button; speaking = true; playNextChunk(); updateSpeechButtons();
    }
    private void playNextChunk() {
        if (!speaking || speechIndex >= speechChunks.size()) { stopNarration(); return; }
        activeSpeechId = "manual:" + speechGeneration + ":" + speechIndex;
        if (tts.speak(speechChunks.get(speechIndex), TextToSpeech.QUEUE_FLUSH, null, activeSpeechId) == TextToSpeech.ERROR)
            stopNarration();
    }
    private void stopNarration() {
        speaking = false; speechGeneration++; speechIndex = 0;
        speechChunks.clear(); activeSpeechId = null; speechButton = null;
        if (tts != null) tts.stop();
        updateSpeechButtons();
    }
    private void updateSpeechButtons() {
        for (Spots.Spot spot : Spots.ALL) {
            Button button = spotPlayButtons.get(spot.id);
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
    @Override protected void onDestroy() { cancelExploreLocation(); stopListening(); if (recognizer != null) recognizer.destroy(); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }

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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(9), 0, 0);
        button.setLayoutParams(params); return button;
    }
}
