package com.ainirobot.robotos.bridge;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.robotos.application.RobotOSApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TTSPlayer {
    private static final String TAG = "TTSPlayer";
    private static MediaPlayer sMediaPlayer;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public interface PlayCallback {
        void onDone(boolean success, String message);
    }

    public static synchronized void playVietnameseText(final String text, final PlayCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File cacheFile = null;
                boolean downloaded = false;
                try {
                    String encoded = URLEncoder.encode(text, "UTF-8");
                    String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&tl=vi&client=tw-ob&q=" + encoded;

                    File cacheDir = RobotOSApplication.getInstance().getCacheDir();
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    cacheFile = new File(cacheDir, "tts_speech.mp3");

                    URL url = new URL(ttsUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; OrionStar) AppleWebKit/537.36");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);

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
                        downloaded = true;
                        Log.i(TAG, "TTS downloaded successfully: " + cacheFile.length() + " bytes");
                    }
                    conn.disconnect();
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to download Google TTS mp3: " + t.getMessage());
                }

                if (downloaded && cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
                    playAudioFile(cacheFile.getAbsolutePath(), callback);
                } else {
                    // Fallback to SkillApi or system tone
                    playSkillApiFallback(text, callback);
                }
            }
        }).start();
    }

    private static void playAudioFile(final String filePath, final PlayCallback callback) {
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    stopCurrentPlayback();

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

    private static void playSkillApiFallback(String text, PlayCallback callback) {
        try {
            SkillApi skillApi = RobotOSApplication.getInstance().getSkillApi();
            if (skillApi != null && skillApi.isApiConnectedService()) {
                skillApi.playText(text, null);
                if (callback != null) callback.onDone(true, "Sent to SkillApi fallback");
            } else {
                if (callback != null) callback.onDone(false, "No speech engine available");
            }
        } catch (Throwable t) {
            if (callback != null) callback.onDone(false, t.getMessage());
        }
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

