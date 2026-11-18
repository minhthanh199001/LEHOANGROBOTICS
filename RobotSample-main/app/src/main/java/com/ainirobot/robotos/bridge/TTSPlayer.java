package com.ainirobot.robotos.bridge;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.robotos.application.RobotOSApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public class TTSPlayer {
    private static final String TAG = "TTSPlayer";
    private static MediaPlayer sMediaPlayer;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public interface PlayCallback {
        void onDone(boolean success, String message);
    }

    public static synchronized void playText(final String text, final String langCode, final PlayCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onDone(false, "Text is empty");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String lang = (langCode != null && !langCode.isEmpty()) ? langCode : "vi";
                File cacheFile = null;
                boolean fileReady = false;

                try {
                    File cacheDir = RobotOSApplication.getInstance().getCacheDir();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    int textHash = Math.abs((text.trim() + "_" + lang).hashCode());
                    cacheFile = new File(cacheDir, "tts_" + textHash + ".mp3");

                    // Check if file is already cached from a previous request
                    if (cacheFile.exists() && cacheFile.length() > 500) {
                        fileReady = true;
                        Log.i(TAG, "Playing TTS from existing cache: " + cacheFile.getAbsolutePath());
                    } else {
                        String encoded = URLEncoder.encode(text.trim(), "UTF-8");
                        String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&tl=" + lang + "&client=tw-ob&q=" + encoded;

                        URL url = new URL(ttsUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; OrionStar) AppleWebKit/537.36");
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);

                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            InputStream is = conn.getInputStream();
                            FileOutputStream fos = new FileOutputStream(cacheFile);
                            byte[] buffer = new byte[4096];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                            fos.flush();
                            fos.close();
                            is.close();
                            fileReady = true;
                            Log.i(TAG, "TTS downloaded successfully: " + cacheFile.length() + " bytes");
                        } else {
                            Log.w(TAG, "Google TTS HTTP error: " + responseCode);
                        }
                        conn.disconnect();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to download Google TTS mp3: " + t.getMessage());
                }

                if (fileReady && cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
                    playAudioFile(cacheFile.getAbsolutePath(), callback);
                } else {
                    // Fallback to offline Android native TextToSpeech or SkillApi
                    playOfflineFallback(text, lang, callback);
                }
            }
        }).start();
    }

    public static synchronized void playVietnameseText(final String text, final PlayCallback callback) {
        playText(text, "vi", callback);
    }

    public static synchronized void playEnglishText(final String text, final PlayCallback callback) {
        playText(text, "en", callback);
    }

    private static void playAudioFile(final String filePath, final PlayCallback callback) {
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    stopCurrentPlayback();

                    // Ensure system audio is unmuted and adequate volume is set (especially when robot is docked)
                    try {
                        Context ctx = RobotOSApplication.getInstance();
                        if (ctx != null) {
                            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                            if (am != null) {
                                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                                if (cur < (int)(max * 0.75f)) {
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(max * 0.85f), 0);
                                }
                                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "AudioManager volume adjustment error: " + t.getMessage());
                    }

                    sMediaPlayer = new MediaPlayer();
                    sMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    sMediaPlayer.setDataSource(filePath);
                    sMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            Log.i(TAG, "Audio playback completed");
                            stopCurrentPlayback();
                            if (callback != null) callback.onDone(true, "Playback completed");
                        }
                    });
                    sMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            Log.e(TAG, "MediaPlayer error: what=" + what + " extra=" + extra);
                            stopCurrentPlayback();
                            if (callback != null) callback.onDone(false, "MediaPlayer error " + what);
                            return true;
                        }
                    });
                    sMediaPlayer.prepare();
                    sMediaPlayer.start();
                    Log.i(TAG, "MediaPlayer started playing: " + filePath);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to play audio file: " + t.getMessage(), t);
                    stopCurrentPlayback();
                    if (callback != null) callback.onDone(false, t.getMessage());
                }
            }
        });
    }

    private static void playOfflineFallback(final String text, final String lang, final PlayCallback callback) {
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                // 1. Try Android native TextToSpeech engine
                try {
                    TextToSpeech tts = RobotOSApplication.getInstance().getTTS();
                    if (tts != null) {
                        if ("vi".equalsIgnoreCase(lang)) {
                            tts.setLanguage(new Locale("vi", "VN"));
                        } else {
                            tts.setLanguage(Locale.US);
                        }
                        int res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
                        if (res == TextToSpeech.SUCCESS) {
                            Log.i(TAG, "Spoken via native Android TextToSpeech");
                            if (callback != null) callback.onDone(true, "Spoken via Android TTS");
                            return;
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Android native TTS fallback error: " + t.getMessage());
                }

                // 2. Try OrionStar CoreService SkillApi
                try {
                    SkillApi skillApi = RobotOSApplication.getInstance().getSkillApi();
                    if (skillApi != null && skillApi.isApiConnectedService()) {
                        skillApi.playText(text, null);
                        Log.i(TAG, "Spoken via SkillApi playText fallback");
                        if (callback != null) callback.onDone(true, "Sent to SkillApi fallback");
                        return;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "SkillApi fallback error: " + t.getMessage());
                }

                if (callback != null) callback.onDone(false, "No speech engine available");
            }
        });
    }

    public static synchronized void stopCurrentPlayback() {
        if (sMediaPlayer != null) {
            try {
                if (sMediaPlayer.isPlaying()) {
                    sMediaPlayer.stop();
                }
            } catch (Throwable ignored) {}
            try {
                sMediaPlayer.release();
            } catch (Throwable ignored) {}
            sMediaPlayer = null;
        }
    }
}
