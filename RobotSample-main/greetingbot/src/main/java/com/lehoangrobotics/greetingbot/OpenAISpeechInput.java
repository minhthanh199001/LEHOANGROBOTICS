package com.lehoangrobotics.greetingbot;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONObject;

/** Orion PCM capture -> authenticated LAN proxy -> OpenAI. No OpenAI key on the robot. */
final class OpenAISpeechInput {
    interface Listener {
        void listening();
        void speechStarted(String id, long startedAt);
        void sending(String id, long startedAt, long endedAt);
        void result(String id, String transcript, long endedAt, MicroArrayReading direction);
        void error(String message);
    }
    private volatile boolean cancelled, paused;
    private volatile AudioRecord recorder;
    private volatile HttpURLConnection connection;
    private volatile Thread worker;

    void start(String baseUrl, String token, Listener listener) {
        if (worker != null) throw new IllegalStateException("Microphone đang đóng phiên trước. Thử lại sau một giây.");
        cancelled = false; paused = false;
        worker = new Thread(() -> {
            AudioRecord input = null;
            try {
                URI base = new URI(baseUrl);
                if ((!"http".equals(base.getScheme()) && !"https".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || (base.getPath() != null && !base.getPath().isEmpty() && !base.getPath().equals("/")))
                    throw new IllegalArgumentException("Địa chỉ server giọng nói không hợp lệ.");
                String server = baseUrl.replaceAll("/+$", "");
                JSONObject ready = request(server + "/api/greeting/speech/status", token, null, "", 0, 0);
                if (!ready.optBoolean("ready")) throw new IllegalStateException("Server chưa cấu hình khóa OpenAI.");
                JSONObject directionStatus = ready.optJSONObject("direction");
                if (directionStatus == null || !directionStatus.optBoolean("ready"))
                    throw new IllegalStateException("Cầu nối MicroArray chưa sẵn sàng. Kiểm tra PC và ADB.");
                String bootId;
                try (FileInputStream identity = new FileInputStream("/proc/sys/kernel/random/boot_id")) {
                    byte[] bytes = new byte[80]; int count = identity.read(bytes);
                    bootId = count > 0 ? new String(bytes, 0, count, "US-ASCII").trim() : "";
                }
                if (!bootId.matches("[0-9a-fA-F-]{36}")) throw new IllegalStateException("Không đọc được định danh phiên robot.");
                int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) throw new IllegalStateException("Không hỗ trợ cấu hình microphone Orion.");
                input = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 8192));
                recorder = input;
                if (input.getState() != AudioRecord.STATE_INITIALIZED || cancelled) throw new InterruptedException();
                input.startRecording();
                if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Không mở được microphone.");
                listener.listening();
                short[] stereo = new short[1024];
                byte[] pre = new byte[9600]; int preCount = 0, prePosition = 0;
                ByteArrayOutputStream phrase = null;
                double noise = 100;
                int serial = 0, loudFrames = 0;
                long started = 0, lastLoud = 0;
                String id = "";
                int pendingLeft = -1;
                while (!cancelled) {
                    int read = input.read(stereo, 0, stereo.length);
                    if (read <= 0) { if (cancelled) break; throw new IllegalStateException("Không đọc được microphone: " + read); }
                    ByteArrayOutputStream frame = new ByteArrayOutputStream(read);
                    double square = 0; int samples = 0;
                    for (int i = 0; i < read; i++) {
                        if (pendingLeft < 0) pendingLeft = stereo[i] & 0xffff;
                        else {
                            short sample = (short) pendingLeft; square += (double) sample * sample; samples++;
                            frame.write(pendingLeft & 255); frame.write(pendingLeft >> 8); pendingLeft = -1;
                        }
                    }
                    byte[] bytes = frame.toByteArray();
                    long now = SystemClock.elapsedRealtime();
                    if (paused) { phrase = null; preCount = 0; prePosition = 0; loudFrames = 0; continue; }
                    double rms = samples == 0 ? 0 : Math.sqrt(square / samples);
                    boolean loud = rms > Math.max(280, noise * 2.8);
                    if (phrase == null) {
                        if (!loud) noise = Math.max(40, Math.min(2000, noise * .98 + rms * .02));
                        if (loud) {
                            phrase = new ByteArrayOutputStream(160000);
                            for (int i = 0; i < preCount; i++) phrase.write(pre[(prePosition - preCount + pre.length + i) % pre.length]);
                            started = now; lastLoud = now; loudFrames = 1; id = "audio-" + (++serial);
                            listener.speechStarted(id, started);
                        }
                    }
                    if (phrase != null) {
                        phrase.write(bytes);
                        if (loud) { lastLoud = now; loudFrames++; }
                        if (now - lastLoud >= 550 || now - started >= 4500) {
                            byte[] pcm = phrase.toByteArray(); phrase = null; preCount = 0; prePosition = 0;
                            if (loudFrames >= 6 && now - started >= 450 && pcm.length >= 14400) {
                                listener.sending(id, started, now);
                                // Pause capture while uploading so old buffered audio cannot become the next utterance.
                                input.stop();
                                long offset = System.currentTimeMillis() - SystemClock.elapsedRealtime();
                                long startUnix = started + offset, endUnix = now + offset;
                                JSONObject result = request(server + "/api/greeting/speech/transcribe", token, wave(pcm), bootId, startUnix, endUnix);
                                if (cancelled) break;
                                MicroArrayReading direction = MicroArrayReading.parse(result.optString("direction", "null"), startUnix, endUnix, offset);
                                android.util.Log.i("GreetingVoice", "Capture " + result.optString("captureStatus")
                                    + "; transcript chars=" + result.optString("text").length()
                                    + "; parsed angle=" + (direction == null ? "none" : direction.angle));
                                listener.result(id, result.optString("text"), now, direction);
                                input.startRecording(); listener.listening();
                            }
                            loudFrames = 0;
                        }
                    } else {
                        for (byte value : bytes) { pre[prePosition] = value; prePosition = (prePosition + 1) % pre.length; preCount = Math.min(pre.length, preCount + 1); }
                    }
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch (Exception error) { if (!cancelled) listener.error("Nhận giọng nói OpenAI: " + error.getMessage()); }
            finally {
                recorder = null;
                if (input != null) { try { input.stop(); } catch (Exception ignored) {} input.release(); }
                worker = null;
            }
        }, "Greeting-OpenAI-ASR");
        worker.start();
    }
    void pause(boolean value) { paused = value; }
    void stop() {
        cancelled = true;
        HttpURLConnection request = connection; if (request != null) request.disconnect();
        AudioRecord input = recorder; if (input != null) try { input.stop(); } catch (Exception ignored) {}
        Thread thread = worker; if (thread != null) thread.interrupt();
    }
    private JSONObject request(String url, String token, byte[] wave, String bootId, long startUnix, long endUnix) throws Exception {
        if (cancelled) throw new InterruptedException();
        HttpURLConnection request = (HttpURLConnection) new java.net.URL(url).openConnection();
        connection = request;
        try {
            request.setConnectTimeout(5000); request.setReadTimeout(14000); request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Authorization", "Bearer " + token);
            if (wave != null) {
                request.setRequestMethod("POST"); request.setDoOutput(true); request.setFixedLengthStreamingMode(wave.length);
                request.setRequestProperty("Content-Type", "audio/wav");
                request.setRequestProperty("X-Robot-Boot-Id", bootId);
                request.setRequestProperty("X-Audio-Start-Ms", Long.toString(startUnix));
                request.setRequestProperty("X-Audio-End-Ms", Long.toString(endUnix));
                try (java.io.OutputStream output = request.getOutputStream()) { output.write(wave); }
            }
            int code = request.getResponseCode();
            if (code != 200) {
                if (code == 401) throw new IllegalStateException("Mã kết nối server giọng nói không đúng.");
                if (code == 404 || code == 405) throw new IllegalStateException("Server chưa có chức năng OpenAI; cần cập nhật server.");
                if (code == 503) throw new IllegalStateException("Server chưa cấu hình OpenAI.");
                if (code == 429) throw new IllegalStateException("Server giọng nói đang bận; thử lại.");
                throw new IllegalStateException("Server giọng nói trả HTTP " + code + ". Kiểm tra kết nối/hạn mức OpenAI trên server.");
            }
            try (InputStream input = request.getInputStream(); ByteArrayOutputStream response = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int read;
                while ((read = input.read(buffer)) > 0) {
                    if (response.size() + read > 16384) throw new IllegalStateException("Phản hồi nhận dạng quá dài.");
                    response.write(buffer, 0, read);
                }
                return new JSONObject(response.toString("UTF-8"));
            }
        } finally { connection = null; request.disconnect(); }
    }
    static byte[] wave(byte[] pcm) {
        ByteBuffer result = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        result.put(new byte[]{'R','I','F','F'}).putInt(36 + pcm.length).put(new byte[]{'W','A','V','E','f','m','t',' '});
        result.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        result.put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm); return result.array();
    }
}
