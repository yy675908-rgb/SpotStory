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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 10, VOICE_REQUEST = 11;
    private final int green = Color.rgb(18, 76, 68), ink = Color.rgb(23, 53, 47), cream = Color.rgb(245, 245, 237);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); } };
    private TextToSpeech tts;
    private boolean ttsReady;
    private ScrollView scroll;
    private LinearLayout root, detail, artifacts;
    private TextView statusText, nameText, introText, storyText, lookForText, answerText, areaText;
    private Button favoriteButton, rateButton;
    private Spots.Spot current;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(green); getWindow().setNavigationBarColor(cream);
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE) {
                VoiceSettings.apply(this, tts); ttsReady = true;
            }
            else if (statusText != null) statusText.setText("未找到中文语音引擎，文字讲解仍可阅读");
        });
        buildScreen();
        showSpot(Spots.ALL.get(0));
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
            Button item = button(spot.name + "  ↗\n" + spot.area, Color.WHITE, ink);
            item.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            item.setOnClickListener(v -> { showSpot(spot); scroll.post(() -> scroll.smoothScrollTo(0, detail.getTop())); }); root.addView(item);
        }

        detail = card(); root.addView(detail);
        areaText = text("", 12, green, false); detail.addView(areaText);
        nameText = text("", 24, ink, true); nameText.setPadding(0, dp(5), 0, dp(8)); detail.addView(nameText);
        introText = text("", 16, ink, false); introText.setLineSpacing(dp(4), 1f); detail.addView(introText);
        TextView findHeading = text("到现场看哪里", 18, green, true);
        findHeading.setPadding(0, dp(18), 0, dp(5)); detail.addView(findHeading);
        lookForText = text("", 15, ink, false); lookForText.setLineSpacing(dp(4), 1f); detail.addView(lookForText);
        TextView storyHeading = text("再听一段 · 历史与典故", 18, green, true);
        storyHeading.setPadding(0, dp(18), 0, dp(5)); detail.addView(storyHeading);
        storyText = text("", 15, ink, false); storyText.setLineSpacing(dp(5), 1f); detail.addView(storyText);
        artifacts = new LinearLayout(this); artifacts.setOrientation(LinearLayout.VERTICAL); detail.addView(artifacts);
        Button speak = button("▶ 听完整故事", green, Color.WHITE);
        speak.setOnClickListener(v -> say(current == null ? "" : current.story)); detail.addView(speak);
        Button brief = button("▶ 听简短介绍", Color.WHITE, green);
        brief.setOnClickListener(v -> say(current == null ? "" : current.intro)); detail.addView(brief);
        Button navigate = button("↗ 去这里 · 高德步行路线", Color.rgb(236, 200, 141), ink);
        navigate.setOnClickListener(v -> navigateTo(current)); detail.addView(navigate);
        Button nearby = button("附近有什么", Color.WHITE, green);
        nearby.setOnClickListener(v -> { String result = nearbyText(); answerText.setText(result); say(result); }); detail.addView(nearby);
        Button food = button("附近找湘菜正餐", Color.WHITE, green);
        food.setOnClickListener(v -> searchMap("湘菜 正餐")); detail.addView(food);
        Button fun = button("附近找夜景", Color.WHITE, green);
        fun.setOnClickListener(v -> searchMap("夜景")); detail.addView(fun);
        Button show = button("附近找演出", Color.WHITE, green);
        show.setOnClickListener(v -> searchMap("演出")); detail.addView(show);
        rateButton = button("语速：" + VoiceSettings.RATE_LABELS[VoiceSettings.rateIndex(this)], Color.WHITE, green);
        rateButton.setOnClickListener(v -> {
            int next = (VoiceSettings.rateIndex(this) + 1) % VoiceSettings.RATES.length;
            VoiceSettings.setRate(this, next); rateButton.setText("语速：" + VoiceSettings.RATE_LABELS[next]);
            if (ttsReady) VoiceSettings.apply(this, tts);
        }); detail.addView(rateButton);
        Button voiceChoice = button("选择手机中文音色", Color.WHITE, green);
        voiceChoice.setOnClickListener(v -> chooseVoice()); detail.addView(voiceChoice);
        Button stopSpeech = button("■ 停止朗读", Color.WHITE, green);
        stopSpeech.setOnClickListener(v -> { if (tts != null) tts.stop(); }); detail.addView(stopSpeech);
        favoriteButton = button("☆ 收藏", Color.WHITE, green); favoriteButton.setOnClickListener(v -> toggleFavorite()); detail.addView(favoriteButton);
        Button ask = button("🎙 说话提问", Color.WHITE, green); ask.setOnClickListener(v -> askByVoice()); detail.addView(ask);
        answerText = text("", 15, ink, false); answerText.setPadding(0, dp(8), 0, dp(6)); detail.addView(answerText);
        Button source = button("查看资料来源 ↗", Color.WHITE, green);
        source.setOnClickListener(v -> { if (current != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(current.source))); });
        detail.addView(source);
        TextView note = text("长沙讲解点持续扩充。步行路线由高德地图规划；GPS 无法可靠判断具体岔路或馆内展柜。到点讲解会在通知栏运行，停止后不再定位。", 12, Color.rgb(97, 116, 106), false);
        note.setPadding(0, dp(14), 0, 0); root.addView(note);
    }

    private void showSpot(Spots.Spot spot) {
        current = spot; areaText.setText(spot.area); nameText.setText(spot.name); introText.setText(spot.intro); storyText.setText(spot.story);
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
        updateFavorite();
    }

    private void toggleFavorite() {
        if (current == null) return;
        boolean saved = getPreferences(MODE_PRIVATE).getBoolean(current.id, false);
        getPreferences(MODE_PRIVATE).edit().putBoolean(current.id, !saved).apply(); updateFavorite();
    }
    private void updateFavorite() {
        favoriteButton.setText(getPreferences(MODE_PRIVATE).getBoolean(current.id, false) ? "★ 已收藏" : "☆ 收藏");
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
        }
    }
    private void startGuide() {
        try { startForegroundService(new Intent(this, GuideService.class)); statusText.setText("正在开启到点讲解…"); }
        catch (Exception e) { statusText.setText("无法启动定位，请检查手机定位设置"); }
    }

    private void askByVoice() {
        if (current == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "你想问这个景点什么？");
        try { startActivityForResult(intent, VOICE_REQUEST); }
        catch (ActivityNotFoundException e) { answerText.setText("手机未安装可用的语音识别服务"); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null && current != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard != null && !heard.isEmpty()) {
                String question = heard.get(0);
                if (question.contains("怎么走") || question.contains("我要去") || question.contains("带我去")) {
                    for (Spots.Spot destination : Spots.ALL) if (destination.radius > 0 &&
                        (question.contains(destination.name.split(" · ")[0]) ||
                         (destination.id.equals("juzizhou") && question.contains("橘子洲")) ||
                         (destination.id.equals("taiping") && question.contains("太平街")))) {
                        answerText.setText("已打开去“" + destination.name + "”的步行路线");
                        navigateTo(destination); return;
                    }
                }
                if (question.contains("吃") || question.contains("饭店") || question.contains("餐馆") || question.contains("夜景") || question.contains("演出") || question.contains("花鼓戏")) {
                    String keyword = question.contains("演出") || question.contains("花鼓戏") ? "演出"
                        : question.contains("夜景") ? "夜景" : "湘菜 正餐";
                    answerText.setText("已打开高德搜索“" + keyword + "”；请核对当天营业或演出信息。");
                    searchMap(keyword); return;
                }
                String answer = question.contains("附近") || question.contains("周围") || question.contains("前面有什么")
                    ? nearbyText() : Spots.answer(current, question);
                answerText.setText("你问：“" + heard.get(0) + "”\n" + answer);
                say(answer);
            }
        }
    }

    private void say(String content) {
        if (ttsReady) tts.speak(content, TextToSpeech.QUEUE_FLUSH, null, "manual");
        else answerText.setText("系统中文语音尚未就绪，可阅读文字讲解");
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
        Uri route = Uri.parse("https://uri.amap.com/navigation").buildUpon()
            .appendQueryParameter("from", "")
            .appendQueryParameter("to", point[1] + "," + point[0] + "," + spot.name)
            .appendQueryParameter("mode", "walk").appendQueryParameter("callnative", "1")
            .appendQueryParameter("src", "yantu").build();
        try { startActivity(new Intent(Intent.ACTION_VIEW, route)); }
        catch (ActivityNotFoundException e) { answerText.setText("没有可用的地图应用或浏览器"); }
    }
    private void searchMap(String query) {
        Uri.Builder builder = Uri.parse("https://uri.amap.com/search").buildUpon()
            .appendQueryParameter("keyword", query).appendQueryParameter("city", "长沙");
        if (current != null && current.radius > 0) {
            double[] point = current.gcj ? new double[]{current.lat, current.lng} : Geo.wgsToGcj(current.lat, current.lng);
            builder.appendQueryParameter("center", point[1] + "," + point[0]);
        }
        Uri url = builder.appendQueryParameter("view", "list").appendQueryParameter("callnative", "1")
            .appendQueryParameter("src", "yantu").build();
        try { startActivity(new Intent(Intent.ACTION_VIEW, url)); }
        catch (ActivityNotFoundException e) { answerText.setText("没有可用的地图应用或浏览器"); }
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
        if (statusText != null) statusText.setText(getSharedPreferences("guide", MODE_PRIVATE).getString("status", "定位尚未开启"));
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }

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
