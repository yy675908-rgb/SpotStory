package com.spotstory.guide;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VoiceSettings {
    private static final String PREFS = "narration_voice";
    static final float[] RATES = {0.78f, 1.0f, 1.25f};
    static final String[] RATE_LABELS = {"舒缓", "自然", "明快"};

    static int rateIndex(Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("rate", 1);
        return Math.max(0, Math.min(index, RATES.length - 1));
    }
    static void setRate(Context context, int index) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("rate", index).apply();
    }
    static void setVoice(Context context, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("voice", name).apply();
    }
    static List<Voice> chineseVoices(TextToSpeech tts) {
        List<Voice> result = new ArrayList<>();
        Set<Voice> available = tts.getVoices();
        if (available != null) for (Voice voice : available) {
            if (voice.getLocale() != null && "zh".equals(voice.getLocale().getLanguage())) result.add(voice);
        }
        result.sort(Comparator.comparingInt((Voice voice) ->
            (voice.isNetworkConnectionRequired() ? 0 : 100) +
            ("CN".equals(voice.getLocale().getCountry()) ? 50 : 0) + voice.getQuality()).reversed());
        return result;
    }
    static void apply(Context context, TextToSpeech tts) {
        tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        tts.setPitch(1.0f);
        List<Voice> voices = chineseVoices(tts);
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("voice", "");
        for (Voice voice : voices) if (voice.getName().equals(saved)) {
            tts.setVoice(voice); tts.setSpeechRate(RATES[rateIndex(context)]); return;
        }
        if (!voices.isEmpty()) tts.setVoice(voices.get(0));
        tts.setSpeechRate(RATES[rateIndex(context)]);
    }
    private VoiceSettings() {}
}
