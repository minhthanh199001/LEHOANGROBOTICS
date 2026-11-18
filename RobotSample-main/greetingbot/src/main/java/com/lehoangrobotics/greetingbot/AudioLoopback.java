package com.lehoangrobotics.greetingbot;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;

/** Orion's microphone array: MIC, 16 kHz, PCM16, stereo input; replay left channel. */
final class AudioLoopback {
    interface Listener { void state(String state, String message); void complete(boolean ok, String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile AudioRecord recorder;
    private volatile AudioTrack track;
    private Thread worker;

    boolean busy() { return worker != null; }

    void start(int seconds, int volume, Listener listener) {
        if (busy()) { listener.complete(false, "Microphone đang bận."); return; }
        cancelled = false;
        worker = new Thread(() -> {
            boolean ok = false;
            String result = "Đã hủy kiểm tra microphone.";
            AudioRecord input = null;
            AudioTrack output = null;
            try {
                int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) throw new IllegalStateException("Không hỗ trợ cấu hình microphone Orion.");
                input = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 8192));
                recorder = input;
                if (input.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Không mở được microphone.");
                if (cancelled) throw new InterruptedException();
                input.startRecording();
                if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Không bắt đầu được thu âm.");
                main.post(() -> listener.state("recording", "Đang ghi âm " + seconds + " giây. Hãy nói một câu."));
                ByteArrayOutputStream pcm = new ByteArrayOutputStream(seconds * 32000);
                // Read only complete stereo frames and retain the left PCM16 sample.
                short[] stereo = new short[2048];
                int pendingLeft = -1;
                long end = SystemClock.elapsedRealtime() + seconds * 1000L;
                while (!cancelled && SystemClock.elapsedRealtime() < end) {
                    int read = input.read(stereo, 0, stereo.length);
                    if (read <= 0) throw new IllegalStateException("Không đọc được microphone: " + read);
                    for (int i = 0; i < read; i++) {
                        if (pendingLeft < 0) pendingLeft = stereo[i] & 0xffff;
                        else { pcm.write(pendingLeft & 0xff); pcm.write(pendingLeft >> 8); pendingLeft = -1; }
                    }
                }
                input.stop();
                input.release(); input = null; recorder = null;
                if (cancelled) throw new InterruptedException();
                byte[] audio = pcm.toByteArray();
                if (audio.length < 3200) throw new IllegalStateException("Bản ghi âm quá ngắn.");
                output = new AudioTrack(AudioManager.STREAM_MUSIC, 16000, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, audio.length, AudioTrack.MODE_STATIC);
                track = output;
                // MODE_STATIC starts as STATE_NO_STATIC_DATA until the first write.
                if (output.getState() == AudioTrack.STATE_UNINITIALIZED) throw new IllegalStateException("Không mở được loa.");
                output.setVolume(volume / 100f);
                if (output.write(audio, 0, audio.length) != audio.length) throw new IllegalStateException("Không nạp được âm thanh vào loa.");
                if (output.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("Không khởi tạo được dữ liệu phát lại.");
                if (cancelled) throw new InterruptedException();
                main.post(() -> listener.state("playing", "Đang phát lại câu vừa ghi âm qua loa robot."));
                output.play();
                long playbackEnd = SystemClock.elapsedRealtime() + audio.length * 1000L / 32000 + 200;
                while (!cancelled && SystemClock.elapsedRealtime() < playbackEnd) Thread.sleep(50);
                if (!cancelled) { ok = true; result = "Đã ghi âm và phát lại xong."; }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                result = "Lỗi microphone/loa: " + e.getMessage();
            } finally {
                recorder = null; track = null;
                if (input != null) { try { input.stop(); } catch (Exception ignored) {} input.release(); }
                if (output != null) { try { output.stop(); } catch (Exception ignored) {} output.release(); }
                final boolean success = ok; final String message = result;
                main.post(() -> { worker = null; listener.complete(success, message); });
            }
        }, "Greeting-Microphone");
        worker.start();
    }

    void cancel() { cancelled = true; if (worker != null) worker.interrupt(); }
}
