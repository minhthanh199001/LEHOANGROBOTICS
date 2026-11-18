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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Orion PCM capture -> authenticated LAN proxy -> OpenAI. No OpenAI key on the robot. */
final class OpenAISpeechInput {
    interface Listener {
        void calibrating();
        void meter(boolean signal);
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
    private volatile Thread captureReader;
    private final ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(80);
    private final AtomicInteger bufferGeneration = new AtomicInteger();
    private static final class Frame {
        final byte[] pcm; final double rms; final long at;
        Frame(byte[] pcm, double rms, long at) { this.pcm = pcm; this.rms = rms; this.at = at; }
    }

    void start(String baseUrl, String token, Listener listener) {
        if (worker != null || captureReader != null) throw new IllegalStateException("Microphone đang đóng phiên trước. Thử lại sau một giây.");
        cancelled = false; paused = false; frames.clear();
        worker = new Thread(() -> {
            AudioRecord input = null;
            try {
                URI base = new URI(baseUrl);
                if ((!"http".equals(base.getScheme()) && !"https".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || (base.getPath() != null && !base.getPath().isEmpty() && !base.getPath().equals("/")))
                    throw new IllegalArgumentException("Địa chỉ server giọng nói không hợp lệ.");
                String server = baseUrl.replaceAll("/+$", "");
                JSONObject ready = request(server + "/api/greeting/speech/status", token, null, "", 0, 0, 0, 0);
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
                listener.calibrating();
                final AudioRecord source = input;
                AtomicReference<Exception> readFailure = new AtomicReference<>();
                captureReader = new Thread(() -> {
                    short[] stereo = new short[1024]; int pendingLeft = -1;
                    try {
                        while (!cancelled) {
                            int read = source.read(stereo, 0, stereo.length);
                            if (read <= 0) { if (cancelled) break; throw new IllegalStateException("Không đọc được microphone: " + read); }
                            ByteArrayOutputStream pcm = new ByteArrayOutputStream(read);
                            double square = 0; int samples = 0;
                            for (int i = 0; i < read; i++) {
                                if (pendingLeft < 0) pendingLeft = stereo[i] & 0xffff;
                                else {
                                    short sample = (short) pendingLeft; square += (double) sample * sample; samples++;
                                    pcm.write(pendingLeft & 255); pcm.write(pendingLeft >> 8); pendingLeft = -1;
                                }
                            }
                            if (paused) { frames.clear(); continue; }
                            Frame frame = new Frame(pcm.toByteArray(), samples == 0 ? 0 : Math.sqrt(square / samples), SystemClock.elapsedRealtime());
                            if (!frames.offer(frame)) { frames.poll(); bufferGeneration.incrementAndGet(); frames.offer(frame); }
                        }
                    } catch (Exception error) { if (!cancelled) readFailure.set(error); }
                    finally { captureReader = null; }
                }, "Greeting-PCM-reader");
                captureReader.start();
                byte[] pre = new byte[16000]; int preCount = 0, prePosition = 0;
                ByteArrayOutputStream phrase = null;
                SpeechNoiseFloor noise = new SpeechNoiseFloor();
                SpeechScanPolicy scan = new SpeechScanPolicy();
                VoiceFocus focus = new VoiceFocus();
                int serial = 0, loudFrames = 0;
                long started = 0, lastLoud = 0, lastFrame = 0, lastMeter = 0;
                double peakRms = 0;
                long largestGap = 0; int meterFrames = 0, bufferVersion = bufferGeneration.get();
                String id = "";
                while (!cancelled) {
                    if (readFailure.get() != null) throw readFailure.get();
                    Frame frame = frames.poll(250, TimeUnit.MILLISECONDS);
                    if (frame == null) continue;
                    byte[] bytes = frame.pcm;
                    long now = frame.at;
                    if (lastFrame > 0) largestGap = Math.max(largestGap, now - lastFrame);
                    if (bufferVersion != bufferGeneration.get()) {
                        bufferVersion = bufferGeneration.get(); phrase = null; preCount = 0; prePosition = 0; loudFrames = 0;
                    }
                    lastFrame = now;
                    if (paused) { phrase = null; preCount = 0; prePosition = 0; loudFrames = 0; continue; }
                    double rms = frame.rms;
                    peakRms = Math.max(peakRms, rms);
                    meterFrames++;
                    if (now - lastMeter >= 2000) {
                        listener.meter(peakRms > noise.threshold());
                        android.util.Log.i("GreetingVoice", "Mic level: peak=" + (int) peakRms + "; threshold="
                            + (int) noise.threshold() + "; calibrated=" + noise.ready() + "; queued=" + frames.size()
                            + "; frames=" + meterFrames + "; maxGapMs=" + largestGap);
                        lastMeter = now; peakRms = 0; largestGap = 0; meterFrames = 0;
                    }
                    if (!noise.ready()) {
                        noise.calibrate(rms);
                        if (noise.ready()) listener.listening();
                        continue;
                    }
                    boolean loud = noise.loud(rms);
                    if (phrase == null) {
                        if (!loud) noise.quiet(rms);
                        if (loud) {
                            phrase = new ByteArrayOutputStream(160000);
                            for (int i = 0; i < preCount; i++) phrase.write(pre[(prePosition - preCount + pre.length + i) % pre.length]);
                            started = now; lastLoud = now; loudFrames = 1; id = "audio-" + (++serial);
                            scan.begin();
                            focus.begin();
                            listener.speechStarted(id, started);
                        }
                    }
                    if (phrase != null) {
                        phrase.write(bytes);
                        focus.observe(now, rms);
                        if (loud) { lastLoud = now; loudFrames++; }
                        SpeechScanPolicy.Scan action = scan.next(now - started, now - lastLoud, loudFrames, phrase.size());
                        if (action != SpeechScanPolicy.Scan.NONE) {
                            byte[] pcm = phrase.toByteArray();
                            if (action != SpeechScanPolicy.Scan.PREVIEW) { phrase = null; preCount = 0; prePosition = 0; }
                            if (action != SpeechScanPolicy.Scan.DISCARD) {
                                listener.sending(id, started, now);
                                // The reader continues during upload; every buffered frame keeps its actual capture time.
                                long offset = System.currentTimeMillis() - SystemClock.elapsedRealtime();
                                long startUnix = started + offset, endUnix = now + offset;
                                long requestAt = SystemClock.elapsedRealtime();
                                JSONObject result = request(server + "/api/greeting/speech/transcribe", token, wave(pcm), bootId, startUnix, endUnix,
                                    lastLoud + offset, focus.at() > 0 ? Math.min(focus.at(), lastLoud) + offset : 0);
                                if (cancelled) break;
                                MicroArrayReading direction = MicroArrayReading.parse(result.optString("direction", "null"), startUnix, endUnix, offset);
                                android.util.Log.i("GreetingVoice", "Capture " + result.optString("captureStatus")
                                    + "; transcript chars=" + result.optString("text").length()
                                    + "; parsed angle=" + (direction == null ? "none" : direction.angle)
                                    + "; stage=" + action + "; captureMs=" + (now - started)
                                    + "; asrMs=" + (SystemClock.elapsedRealtime() - requestAt));
                                listener.result(id, result.optString("text"), now, direction);
                                listener.listening();
                            } else android.util.Log.i("GreetingVoice", "Short audio ignored: loudFrames=" + loudFrames
                                + "; durationMs=" + (now - started) + "; pcmBytes=" + pcm.length);
                            if (phrase == null) loudFrames = 0;
                        }
                    } else {
                        for (byte value : bytes) { pre[prePosition] = value; prePosition = (prePosition + 1) % pre.length; preCount = Math.min(pre.length, preCount + 1); }
                    }
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch (Exception error) { if (!cancelled) listener.error("Nhận giọng nói OpenAI: " + error.getMessage()); }
            finally {
                cancelled = true;
                recorder = null;
                if (input != null) {
                    try { input.stop(); } catch (Exception ignored) {}
                    Thread reader = captureReader;
                    if (reader != null) try { reader.join(1000); } catch (InterruptedException ignored) {}
                    input.release();
                }
                frames.clear();
                worker = null;
            }
        }, "Greeting-OpenAI-ASR");
        worker.start();
    }
    void pause(boolean value) { paused = value; if (value) { bufferGeneration.incrementAndGet(); frames.clear(); } }
    void stop() {
        cancelled = true;
        HttpURLConnection request = connection; if (request != null) request.disconnect();
        AudioRecord input = recorder; if (input != null) try { input.stop(); } catch (Exception ignored) {}
        Thread thread = worker; if (thread != null) thread.interrupt();
    }
    private JSONObject request(String url, String token, byte[] wave, String bootId, long startUnix, long endUnix, long voiceEndUnix, long focusUnix) throws Exception {
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
                request.setRequestProperty("X-Voice-End-Ms", Long.toString(voiceEndUnix));
                if (focusUnix > 0) request.setRequestProperty("X-Voice-Focus-Ms", Long.toString(focusUnix));
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
