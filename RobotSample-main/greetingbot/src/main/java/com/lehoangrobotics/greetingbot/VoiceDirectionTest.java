package com.lehoangrobotics.greetingbot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;

/** OpenAI recognizes the phrase; only real Orion MicroArray data can supply a turn angle. */
final class VoiceDirectionTest implements AutoCloseable {
    interface Host { boolean canControl(); int nextId(); void changed(); }
    interface StopResult { void complete(boolean success, String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RobotApi robot = RobotApi.getInstance();
    private final VoiceDirectionPolicy policy = new VoiceDirectionPolicy();
    private final CapturedSoundAngle capture = new CapturedSoundAngle();
    private final OpenAISpeechInput input = new OpenAISpeechInput();
    private final Context context;
    private final Host host;
    private volatile boolean enabled;
    private boolean closed, turning;
    private int generation, motionSerial;
    private String status = "Chưa bật test hướng âm OpenAI.", heard = "", lastKeyword = "", captureId = "";
    private double lastAngle = Double.NaN, sourceAngle = Double.NaN;
    private long sourceAt;
    private StopResult stopResult;
    private final android.content.BroadcastReceiver wakeReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context ignored, android.content.Intent intent) {
            if (!enabled || closed || !Definition.INTENT_SPEECH_WAKEUP.equals(intent.getAction())) return;
            Object value = intent.getExtras() == null ? null : intent.getExtras().get(Definition.EXTRA_UPLOAD_ANGLE);
            try { if (value instanceof Number || value instanceof String) direction(Double.parseDouble(value.toString())); }
            catch (NumberFormatException ignoredError) {}
        }
    };

    VoiceDirectionTest(Context context, Host host) {
        this.context = context.getApplicationContext(); this.host = host;
        // System UID on the tested ROM is permitted; other applications cannot forge this event.
        this.context.registerReceiver(wakeReceiver, new android.content.IntentFilter(Definition.INTENT_SPEECH_WAKEUP),
            "com.lehoangrobotics.greetingbot.permission.ROBOT_VOICE_EVENT", main);
    }
    boolean active() { return enabled || turning; }
    String display() {
        return status + (heard.isEmpty() ? "" : "\nNghe được: “" + heard + "”")
            + (VoiceDirectionPolicy.validAngle(lastAngle) ? String.format(java.util.Locale.US, " · MicroArray %.0f°", lastAngle) : "");
    }
    void start() {
        if (closed || active()) return;
        if (!host.canControl()) { status = "Dừng điều hướng/ghi âm trước khi test hướng âm."; host.changed(); return; }
        android.content.SharedPreferences settings = context.getSharedPreferences("greetingbot", Context.MODE_PRIVATE);
        String url = settings.getString("voiceServerUrl", ""), token = settings.getString("voiceToken", "");
        if (url.isEmpty() || token.length() < 32) {
            status = "Mở Cấu hình → Giọng nói OpenAI, nhập server và mã kết nối."; host.changed(); return;
        }
        enabled = true; generation++; heard = ""; lastKeyword = ""; captureId = "";
        lastAngle = Double.NaN; sourceAngle = Double.NaN; policy.start();
        status = "Đang kiểm tra kết nối server giọng nói OpenAI…"; host.changed();
        final int run = generation;
        try {
            input.start(url, token, new OpenAISpeechInput.Listener() {
                private boolean current() { return enabled && !closed && run == generation; }
                @Override public void listening() { main.post(() -> {
                    if (!current() || turning) return;
                    if (heard.isEmpty()) status = "OpenAI đang nghe (60 giây): Hello robot / robot ơi.";
                    host.changed();
                }); }
                @Override public void speechStarted(String id, long startedAt) { main.post(() -> {
                    if (!current() || turning) return;
                    captureId = id; capture.begin(id, startedAt); capture.observe(sourceAngle, sourceAt); lastAngle = Double.NaN;
                    policy.speechStarted(startedAt); status = "Đang thu câu gọi…"; host.changed();
                }); }
                @Override public void sending(String id, long startedAt, long endedAt) { main.post(() -> {
                    if (!current() || turning || !captureId.equals(id)) return;
                    capture.end(id, endedAt); capture.observe(sourceAngle, sourceAt);
                    status = "OpenAI đang nhận dạng câu vừa nói…"; host.changed();
                }); }
                @Override public void result(String id, String text, long endedAt) { main.post(() -> {
                    if (!current() || turning || !captureId.equals(id)) return;
                    heard = clipped(text);
                    long now = SystemClock.elapsedRealtime();
                    double capturedAngle = capture.forResult(id, now);
                    Log.i("GreetingVoice", "OpenAI transcript keyword=" + VoiceDirectionPolicy.keyword(text)
                        + "; captured direction=" + capturedAngle);
                    if (now < endedAt || now - endedAt > CapturedSoundAngle.MAX_RESPONSE_AGE_MS) {
                        status = "Kết quả nhận dạng đến quá chậm; hãy gọi lại để lấy hướng mới."; host.changed(); return;
                    }
                    recognized(text, capturedAngle, id);
                }); }
                @Override public void error(String message) { main.post(() -> { if (current()) stop(message, null); }); }
            });
        } catch (Exception error) { stop(error.getMessage(), null); return; }
        main.postDelayed(() -> { if (enabled && generation == run) stop("Đã hết 60 giây test hướng âm.", null); }, 60000);
    }
    private void direction(double angle) {
        if (!enabled || turning || !VoiceDirectionPolicy.validAngle(angle)) return;
        sourceAngle = angle % 360; sourceAt = SystemClock.elapsedRealtime();
        // Tie the direction to audio capture, not to the time a delayed cloud response arrives.
        capture.observe(sourceAngle, sourceAt);
        Log.i("GreetingVoice", "MicroArray direction=" + sourceAngle);
    }
    boolean onModuleRequest(int id, String type, String text, String params) {
        if (!enabled || closed) return false;
        boolean wake = Definition.REQ_SPEECH_WAKEUP.equals(type);
        if (!wake && !Definition.REQ_SPEECH.equals(type) && !Definition.REQ_SPEECH_TEXT.equals(type)) return false;
        final int run = generation;
        main.post(() -> {
            try { if (enabled && !closed && run == generation && wake) direction(VoiceDirectionPolicy.soundAngle(params, true)); }
            finally { try { robot.finishModuleParser(id, true); } catch (Exception ignored) {} }
        });
        return true;
    }
    private void recognized(String text, double angle, String eventId) {
        if (!enabled || closed || turning) return;
        String keyword = VoiceDirectionPolicy.keyword(text);
        if (keyword.isEmpty()) { status = "Chưa đúng câu gọi. Nói: Hello robot hoặc robot ơi."; host.changed(); return; }
        if (!host.canControl()) { stop("RobotOS đã giữ quyền điều khiển; dừng test hướng âm.", null); return; }
        if (!VoiceDirectionPolicy.validAngle(angle)) {
            status = "Đã nghe đúng “" + keyword + "”. SDK chưa trả góc MicroArray của câu này; robot chưa xoay.";
            host.changed(); return;
        }
        double target = policy.select(text, angle, eventId, SystemClock.elapsedRealtime());
        if (!VoiceDirectionPolicy.validAngle(target)) return;
        lastAngle = target; lastKeyword = keyword; turning = true; input.pause(true);
        status = String.format(java.util.Locale.US, "Đang xoay theo “%s” · MicroArray %.0f°", keyword, target);
        host.changed();
        final int run = generation, turn = ++motionSerial;
        try {
            int accepted = robot.wakeUp(host.nextId(), (float) target, new ActionListener() {
                private void result(int code, String data) { main.post(() -> {
                    if (closed || run != generation || turn != motionSerial || !turning) return;
                    if (code == Definition.ACTION_RESPONSE_STOP_SUCCESS) finish(true, "SDK đã xác nhận dừng xoay.");
                    else if (code == Definition.RESULT_OK) finish(true, "Đã xoay về hướng người gọi “" + lastKeyword + "”.");
                    else finish(false, "SDK xoay trả về " + code + ": " + clipped(data));
                }); }
                @Override public void onResult(int code, String data) { result(code, data); }
                @Override public void onResult(int code, String data, String extra) { result(code, data); }
                @Override public void onError(int code, String error) { main.post(() -> {
                    if (!closed && run == generation && turn == motionSerial && turning) finish(false, "Lỗi xoay " + code + ": " + clipped(error));
                }); }
                @Override public void onError(int code, String error, String extra) { onError(code, error); }
            });
            if (accepted < 0) finish(false, "SDK từ chối xoay: " + accepted);
        } catch (Exception error) { finish(false, "Không gửi được lệnh xoay: " + error.getMessage()); }
        main.postDelayed(() -> { if (!closed && run == generation && turn == motionSerial && turning) stop("Chưa có kết quả xoay; đang yêu cầu dừng.", null); }, 12000);
    }
    private void finish(boolean ok, String message) {
        turning = false; motionSerial++; input.pause(false); policy.finished(SystemClock.elapsedRealtime()); status = message;
        Log.i("GreetingVoice", (ok ? "Completed: " : "Failed: ") + message);
        StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(ok, message); host.changed();
    }
    void stop(String message, StopResult completion) {
        enabled = false; policy.stop(); input.stop(); status = message;
        if (!turning) { generation++; if (completion != null) completion.complete(true, message); host.changed(); return; }
        if (stopResult != null) stopResult.complete(false, "Đã thay thế bằng yêu cầu dừng mới.");
        stopResult = completion;
        try {
            if (!host.canControl()) { controlLost(message); return; }
            int result = robot.stopWakeUp(host.nextId());
            if (result < 0) stopNotConfirmed("SDK chưa nhận lệnh dừng xoay: " + result + ". Bấm dừng để thử lại.");
        } catch (Exception error) { stopNotConfirmed("Chưa dừng được xoay: " + error.getMessage()); }
        final int run = generation;
        main.postDelayed(() -> { if (!closed && run == generation && turning) stopNotConfirmed("Chưa có xác nhận dừng xoay. Bấm dừng lại hoặc dùng nút dừng vật lý."); }, 5000);
        host.changed();
    }
    private void stopNotConfirmed(String message) {
        status = message; StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(false, message); host.changed();
    }
    void controlLost(String message) {
        enabled = false; turning = false; generation++; motionSerial++; policy.stop(); input.stop(); status = message;
        StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(false, message); host.changed();
    }
    private static String clipped(String value) { return value == null ? "" : value.substring(0, Math.min(160, value.length())); }
    @Override public void close() {
        if (closed) return;
        stop("Đã đóng test hướng âm.", null); closed = true; generation++;
        context.unregisterReceiver(wakeReceiver);
    }
}
