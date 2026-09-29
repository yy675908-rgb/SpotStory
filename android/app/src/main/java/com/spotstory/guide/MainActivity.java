package com.spotstory.guide;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
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
    private static final int LOCATION_REQUEST = 10, VOICE_REQUEST = 11, MIC_REQUEST = 12;
    private final int green = Color.rgb(18, 76, 68), ink = Color.rgb(23, 53, 47), cream = Color.rgb(245, 245, 237);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); } };
    private TextToSpeech tts;
    private boolean ttsReady;
    private ScrollView scroll;
    private LinearLayout root, detail, artifacts, foodPanel;
    private TextView statusText, introText, storyText, lookForText, answerText;
    private Button speakButton, briefButton, foodToggle;
    private final Button[] rateButtons = new Button[3];
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
    private float foodTouchY;
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
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root); setContentView(scroll);

        TextView brand = text("沿途  /  YAN TU", 13, green, true); root.addView(brand);
        TextView headline = text("走到哪，讲到哪。", 29, ink, true); headline.setPadding(0, dp(14), 0, dp(6)); root.addView(headline);
        root.addView(text("到点自动讲解，也能手动选景点。史实附来源。", 14, ink, false));

        LinearLayout location = card(); location.setBackground(round(green, 18));
        TextView locTitle = text("到点讲解", 20, Color.WHITE, true); location.addView(locTitle);
        statusText = text("定位尚未开启", 13, Color.rgb(218, 237, 227), false);
        statusText.setPadding(0, dp(8), 0, dp(12)); location.addView(statusText);
        Button start = button("开启到点播讲", Color.rgb(236, 200, 141), ink);
        start.setOnClickListener(v -> enableLocation()); location.addView(start);
        Button stop = button("停止到点播讲", green, Color.WHITE);
        stop.setOnClickListener(v -> { stopService(new Intent(this, GuideService.class)); statusText.setText("到点讲解已停止"); });
        location.addView(stop); root.addView(location);

        TextView section = text("选择眼前的地方", 22, ink, true); section.setPadding(0, dp(25), 0, dp(8)); root.addView(section);
        for (Spots.Spot spot : Spots.ALL) {
            LinearLayout spotCard = card(); spotCards.put(spot.id, spotCard);
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
            if (spot.radius > 0) {
                Button route = button("高德 ↗", Color.rgb(236, 200, 141), ink);
                route.setContentDescription("高德步行去" + spot.name);
                route.setOnClickListener(v -> navigateTo(spot));
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(88), dp(50));
                size.setMargins(dp(8), 0, 0, 0); header.addView(route, size);
            }
            spotCard.addView(header); root.addView(spotCard);
        }

        detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(0, dp(13), 0, 0);
        introText = text("", 16, ink, false); introText.setLineSpacing(dp(4), 1f); detail.addView(introText);
        TextView findHeading = text("到现场看哪里", 18, green, true);
        findHeading.setPadding(0, dp(18), 0, dp(5)); detail.addView(findHeading);
        lookForText = text("", 15, ink, false); lookForText.setLineSpacing(dp(4), 1f); detail.addView(lookForText);
        TextView storyHeading = text("再听一段 · 历史与典故", 18, green, true);
        storyHeading.setPadding(0, dp(18), 0, dp(5)); detail.addView(storyHeading);
        storyText = text("", 15, ink, false); storyText.setLineSpacing(dp(5), 1f); detail.addView(storyText);
        artifacts = new LinearLayout(this); artifacts.setOrientation(LinearLayout.VERTICAL); detail.addView(artifacts);
        speakButton = button("▶ 听完整故事", green, Color.WHITE);
        speakButton.setOnClickListener(v -> toggleNarration(speakButton, current == null ? "" : current.story)); detail.addView(speakButton);
        briefButton = button("▶ 听简短介绍", Color.WHITE, green);
        briefButton.setOnClickListener(v -> toggleNarration(briefButton, current == null ? "" : current.intro)); detail.addView(briefButton);
        Button nearby = button("附近有什么", Color.WHITE, green);
        nearby.setOnClickListener(v -> { String result = nearbyText(); answerText.setText(result); say(result); }); detail.addView(nearby);
        foodToggle = button("⌄ 周边吃什么", Color.WHITE, green);
        foodToggle.setOnClickListener(v -> setFoodExpanded(foodPanel.getVisibility() != View.VISIBLE));
        foodToggle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { foodTouchY = event.getY(); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - foodTouchY;
                if (Math.abs(dy) > dp(36)) setFoodExpanded(dy > 0);
                else v.performClick();
                return true;
            }
            return true;
        }); detail.addView(foodToggle);
        foodPanel = new LinearLayout(this); foodPanel.setOrientation(LinearLayout.VERTICAL);
        foodPanel.setVisibility(View.GONE); detail.addView(foodPanel);
        Button fun = button("附近找夜景", Color.WHITE, green);
        fun.setOnClickListener(v -> searchMap("夜景")); detail.addView(fun);
        Button show = button("附近找演出", Color.WHITE, green);
        show.setOnClickListener(v -> searchMap("演出")); detail.addView(show);
        detail.addView(text("语速", 14, green, true));
        LinearLayout rates = new LinearLayout(this); rates.setGravity(android.view.Gravity.CENTER_VERTICAL);
        for (int i = 0; i < rateButtons.length; i++) {
            final int index = i;
            Button rate = button(VoiceSettings.RATE_LABELS[i], Color.WHITE, green);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1f);
            size.setMargins(i == 0 ? 0 : dp(4), 0, 0, 0);
            rates.addView(rate, size); rateButtons[i] = rate;
            rate.setOnClickListener(v -> {
                VoiceSettings.setRate(this, index); updateRateButtons();
                if (ttsReady) VoiceSettings.apply(this, tts);
                if (speaking) { speechGeneration++; playNextChunk(); }
            });
        }
        detail.addView(rates); updateRateButtons();
        Button voiceChoice = button("选择手机中文音色", Color.WHITE, green);
        voiceChoice.setOnClickListener(v -> chooseVoice()); detail.addView(voiceChoice);
        detail.addView(text("播放时说“沿途”再提问，可打断讲解。需授权麦克风，并保持应用在前台。", 13, green, false));
        answerText = text("", 15, ink, false); answerText.setPadding(0, dp(8), 0, dp(6)); detail.addView(answerText);
        Button source = button("查看资料来源 ↗", Color.WHITE, green);
        source.setOnClickListener(v -> { if (current != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(current.source))); });
        detail.addView(source);
        TextView note = text("长沙讲解点持续扩充。步行路线由高德地图规划；GPS 无法可靠判断具体岔路或馆内展柜。到点讲解会在通知栏运行，停止后不再定位。", 12, Color.rgb(97, 116, 106), false);
        note.setPadding(0, dp(14), 0, 0); root.addView(note);
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
        foodPanel.setVisibility(View.GONE); foodToggle.setText("⌄ 周边吃什么");
        lookForText.setText(spot.lookFor.isEmpty() ? "现场细节尚未核实，请看标识。" : spot.lookFor);
        answerText.setText(""); artifacts.removeAllViews();
        if (!spot.artifacts.isEmpty()) {
            TextView label = text("馆内值得看 · 看到展品后选择", 14, green, true);
            label.setPadding(0, dp(16), 0, dp(3)); artifacts.addView(label);
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

    private void setFoodExpanded(boolean open) {
        foodPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        foodToggle.setText(open ? "⌃ 收起餐厅" : "⌄ 周边吃什么");
        if (open) renderFood();
    }

    private void renderFood() {
        foodPanel.removeAllViews();
        if (current == null) return;
        if (current.radius > 0) {
            double[] point = foodOrigin();
            List<Restaurants.Restaurant> nearby = Restaurants.near(point[0], point[1]);
            foodPanel.addView(text("按预计步行距离从近到远 · 以高德路线为准", 13, green, false));
            if (nearby.isEmpty()) foodPanel.addView(text("这处周边暂未收录餐厅，可在美团继续找。", 14, ink, false));
            for (Restaurants.Restaurant item : nearby) {
                LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(11), 0, dp(11));
                long meters = Math.round(Geo.distance(point[0], point[1], item.lat, item.lng) * 1.3 / 50) * 50;
                TextView info = text(item.name + "\n" + item.kind + " · 预计步行约" + meters + "米", 14, ink, false);
                info.setLineSpacing(dp(3), 1f);
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                Button map = button("⌖", Color.rgb(239, 246, 238), green);
                map.setContentDescription("美团查找" + item.name);
                map.setOnClickListener(v -> openMeituan(item.name));
                LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(52), dp(52));
                iconSize.setMargins(dp(8), 0, 0, 0); row.addView(map, iconSize);
                foodPanel.addView(row);
            }
        } else foodPanel.addView(text("请选一个室外景点查看其周边餐厅。", 14, ink, false));
        Button more = button("美团查看更多餐厅 ↗", Color.rgb(239, 246, 238), green);
        more.setOnClickListener(v -> openMeituan("长沙 不辣 美食")); foodPanel.addView(more);
        foodPanel.addView(text("步行距离按直线距离估算；口味和营业请在美团核对，点单说明不吃辣。", 12, green, false));
    }

    private double[] foodOrigin() {
        double[] spot = current.gcj ? new double[]{current.lat, current.lng} : Geo.wgsToGcj(current.lat, current.lng);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return spot;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location value = manager.getLastKnownLocation(provider);
                if (value != null && (best == null || value.getTime() > best.getTime())) best = value;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        if (best == null || System.currentTimeMillis() - best.getTime() > 120000 ||
            !best.hasAccuracy() || best.getAccuracy() > 100) return spot;
        return Geo.wgsToGcj(best.getLatitude(), best.getLongitude());
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
                    setFoodExpanded(true);
                    answerText.setText("已列出附近餐厅；点 ⌖ 在美团查找店铺。"); return;
                }
                if (question.contains("夜景") || question.contains("演出") || question.contains("花鼓戏")) {
                    String keyword = question.contains("演出") || question.contains("花鼓戏") ? "演出"
                        : "夜景";
                    answerText.setText("已打开高德搜索“" + keyword + "”；请核对当天营业或演出信息。");
                    searchMap(keyword); return;
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
        if (speakButton != null) speakButton.setText((speaking && speechButton == speakButton ? "Ⅱ" : "▶") + " 听完整故事");
        if (briefButton != null) briefButton.setText((speaking && speechButton == briefButton ? "Ⅱ" : "▶") + " 听简短介绍");
    }
    private void chooseVoice() {
        if (!ttsReady) { answerText.setText("系统中文语音尚未就绪"); return; }
        List<Voice> voices = VoiceSettings.chineseVoices(tts);
        if (voices.isEmpty()) { answerText.setText("手机当前没有可选的中文音色，可在系统语音设置安装"); return; }
        String[] labels = new String[voices.size()];
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            labels[i] = voice.getName() + (voice.isNetworkConnectionRequired() ? " · 需联网" : " · 本机");
        }
        new AlertDialog.Builder(this).setTitle("选择中文音色（取决于手机语音引擎）")
            .setItems(labels, (dialog, index) -> {
                VoiceSettings.setVoice(this, voices.get(index).getName());
                VoiceSettings.apply(this, tts); say("你好，这里是沿途。我们慢慢听一段故事。");
            }).setNegativeButton("取消", null).show();
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
    private void searchMap(String query) {
        Uri url = Uri.parse("androidamap://poi").buildUpon()
            .appendQueryParameter("sourceApplication", "沿途")
            .appendQueryParameter("keywords", "长沙 " + query).appendQueryParameter("dev", "0").build();
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
            showSpot(spot); if (ttsReady) say(spot.story); ensureHandsFree(); break;
        }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; handler.post(refresh); scheduleListening(); }
    @Override protected void onPause() { foreground = false; handler.removeCallbacks(refresh); stopListening(); super.onPause(); }
    @Override protected void onDestroy() { stopListening(); if (recognizer != null) recognizer.destroy(); if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }

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
        layout.setPadding(dp(18), dp(18), dp(18), dp(18)); layout.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(12), 0, 0);
        layout.setLayoutParams(params); return layout;
    }
    private Button button(String value, int background, int foreground) {
        Button button = new Button(this); button.setText(value); button.setTextColor(foreground); button.setTextSize(15);
        button.setAllCaps(false); button.setBackground(round(background, 12)); button.setPadding(dp(15), dp(11), dp(15), dp(11));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(9), 0, 0);
        button.setLayoutParams(params); return button;
    }
}
