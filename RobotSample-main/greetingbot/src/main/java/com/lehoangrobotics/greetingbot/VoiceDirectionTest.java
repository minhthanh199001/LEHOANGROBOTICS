package com.lehoangrobotics.greetingbot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;

/** OpenAI recognizes the phrase; only real Orion MicroArray data can supply a turn angle. */
final class VoiceDirectionTest implements AutoCloseable {
    interface Host { boolean canControl(); int nextId(); double angleOffset(); void changed(); }
    interface StopResult { void complete(boolean success, String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RobotApi robot = RobotApi.getInstance();
    private final VoiceDirectionPolicy policy = new VoiceDirectionPolicy();
    private final CapturedSoundAngle capture = new CapturedSoundAngle();
    private final OpenAISpeechInput input = new OpenAISpeechInput();
    private final Context context;
    private final Host host;
    private volatile boolean enabled;
    private boolean closed, turning, sdkActionRunning;
    private int generation, motionSerial;
    private String status = "Chưa bật test hướng âm OpenAI.", heard = "", lastKeyword = "", captureId = "", micStatus = "";
    private double lastAngle = Double.NaN, sourceAngle = Double.NaN;
    private long sourceAt;
    private double captureHeading = Double.NaN;
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
        return status + (enabled && !micStatus.isEmpty() ? "\n" + (turning ? "Micro: chờ xoay xong để nhận câu tiếp theo." : micStatus) : "") + (heard.isEmpty() ? "" : "\nNghe được: “" + heard + "”")
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
        enabled = true; generation++; heard = ""; lastKeyword = ""; captureId = ""; micStatus = "";
        lastAngle = Double.NaN; sourceAngle = Double.NaN; policy.start();
        status = "Đang kiểm tra kết nối server giọng nói OpenAI…"; host.changed();
        final int run = generation;
        try {
            input.start(url, token, new OpenAISpeechInput.Listener() {
                private boolean current() { return enabled && !closed && run == generation; }
                @Override public void meter(boolean signal) { main.post(() -> {
                    if (!current()) return;
                    micStatus = signal ? "Micro: đang thu, có tín hiệu âm thanh." : "Micro: đang nghe, chưa có giọng đủ rõ.";
                    host.changed();
                }); }
                @Override public void calibrating() { main.post(() -> {
                    if (!current()) return;
                    status = "Đang đo tiếng ồn nền… Chờ dòng ‘OpenAI đang nghe’ rồi nói."; host.changed();
                }); }
                @Override public void listening() { main.post(() -> {
                    if (!current() || turning) return;
                    if (captureId.isEmpty()) status = "OpenAI đang nghe (60 giây): Hello robot / robot ơi.";
                    host.changed();
                }); }
                @Override public void speechStarted(String id, long startedAt) { main.post(() -> {
                    if (!current() || turning) return;
                    captureId = id; capture.begin(id, startedAt); capture.observe(sourceAngle, sourceAt); lastAngle = Double.NaN;
                    captureHeading = Double.NaN;
                    heading(value -> { if (current() && !turning && captureId.equals(id)) captureHeading = value; });
                    policy.speechStarted(startedAt); status = "Đang thu câu gọi…"; host.changed();
                }); }
                @Override public void sending(String id, long startedAt, long endedAt) { main.post(() -> {
                    if (!current() || turning || !captureId.equals(id)) return;
                    capture.end(id, endedAt); capture.observe(sourceAngle, sourceAt);
                    status = "OpenAI đang nhận dạng câu vừa nói…"; host.changed();
                }); }
                @Override public void result(String id, String text, long endedAt, MicroArrayReading direction) { main.post(() -> {
                    if (!current() || turning || !captureId.equals(id)) return;
                    heard = clipped(text);
                    long now = SystemClock.elapsedRealtime();
                    if (direction != null && !VoiceDirectionPolicy.validAngle(capture.forResult(id, now))) {
                        capture.observe(direction.angle, direction.atElapsed);
                        lastAngle = direction.angle;
                        Log.i("GreetingVoice", "Native MicroArray via ADB: angle=" + direction.angle + "; samples=" + direction.sampleCount);
                    }
                    double capturedAngle = capture.forResult(id, now);
                    Log.i("GreetingVoice", "OpenAI transcript keyword=" + VoiceDirectionPolicy.keyword(text)
                        + "; captured direction=" + capturedAngle);
                    if (now < endedAt || now - endedAt > CapturedSoundAngle.MAX_RESPONSE_AGE_MS) {
                        status = "Kết quả nhận dạng đến quá chậm; hãy gọi lại để lấy hướng mới."; host.changed(); return;
                    }
                    recognized(text, capturedAngle, id, endedAt);
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
    private void recognized(String text, double angle, String eventId, long endedAt) {
        if (!enabled || closed || turning) return;
        String keyword = VoiceDirectionPolicy.keyword(text);
        if (keyword.isEmpty()) { status = text == null || text.trim().isEmpty()
            ? "Chưa nhận được câu gọi. Góc âm thanh riêng chưa đủ để xoay; đang nghe tiếp."
            : "Chưa đúng câu gọi. Đang nghe tiếp: Hello robot hoặc robot ơi."; host.changed(); return; }
        if (!host.canControl()) { stop("RobotOS đã giữ quyền điều khiển; dừng test hướng âm.", null); return; }
        if (!VoiceDirectionPolicy.validAngle(angle)) {
            status = "Đã nghe đúng “" + keyword + "”. Chưa có góc MicroArray ổn định khớp câu này. Giữ nguyên vị trí và gọi lại.";
            host.changed(); return;
        }
        double offset = host.angleOffset();
        double target = policy.select(text, TurnMeasurement.withOffset(angle, offset), eventId, SystemClock.elapsedRealtime());
        if (!VoiceDirectionPolicy.validAngle(target)) {
            status = "Đã nhận câu gọi; chờ hết khoảng nghỉ 2 giây sau xoay rồi gọi tiếp."; host.changed(); return;
        }
        lastAngle = angle; lastKeyword = keyword; turning = true; input.pause(true);
        double signedSource = TurnMeasurement.signedAngle(angle), signedTarget = TurnMeasurement.signedAngle(target);
        status = String.format(java.util.Locale.US, "Đang xoay theo “%s” · góc %+.0f° · bù %+.0f° → %+.0f°",
            keyword, signedSource, signedTarget - signedSource, signedTarget);
        Log.i("GreetingVoice", "Directional compensation: source=" + signedSource + "; configured=" + offset
            + "; applied=" + (signedTarget - signedSource) + "; target=" + signedTarget);
        host.changed();
        final int run = generation, turn = ++motionSerial;
        sendTurn(target, target, captureHeading, 0, run, turn, endedAt + CapturedSoundAngle.MAX_RESPONSE_AGE_MS);
        main.postDelayed(() -> { if (!closed && run == generation && turn == motionSerial && turning) stop("Chưa có kết quả xoay; đang yêu cầu dừng.", null); }, 12000);
    }
    private void sendTurn(double angle, double originalTarget, double before, int corrections, int run, int turn, long deadline) {
        if (closed || !enabled || !turning || run != generation || turn != motionSerial) return;
        if (corrections > 0 && SystemClock.elapsedRealtime() > deadline) { finish(false, "Đã hết thời gian bù góc của câu gọi này."); return; }
        if (!host.canControl()) { controlLost("RobotOS đã giữ quyền điều khiển; dừng xoay."); return; }
        try {
            sdkActionRunning = true;
            int accepted = robot.wakeUp(host.nextId(), (float) angle, false, new ActionListener() {
                private boolean terminal;
                private void result(int code, String data) { main.post(() -> {
                    if (terminal || closed || run != generation || turn != motionSerial || !turning) return;
                    terminal = true; sdkActionRunning = false;
                    if (code == Definition.ACTION_RESPONSE_STOP_SUCCESS) finish(true, "SDK đã xác nhận dừng xoay.");
                    else if (code == Definition.RESULT_OK) {
                        if (!enabled) { finish(true, "Đã dừng test hướng âm."); return; }
                        status = "SDK kết thúc lượt xoay; đang kiểm tra góc thân…"; host.changed();
                        main.postDelayed(() -> heading(after -> {
                            if (closed || !enabled || run != generation || turn != motionSerial || !turning) return;
                            double actual = TurnMeasurement.magnitude(before, after), requested = Math.min(originalTarget, 360 - originalTarget);
                            double residual = TurnMeasurement.residual(before, after, originalTarget);
                            Log.i("GreetingVoice", "Turn measurement head=false; target=" + originalTarget + "; before=" + before
                                + "; after=" + after + "; requested=" + requested + "; chassis=" + actual + "; residual=" + residual + "; corrections=" + corrections);
                            if (TurnMeasurement.canCorrect(residual, corrections, deadline - SystemClock.elapsedRealtime())) {
                                status = String.format(java.util.Locale.US, "Đang bù %.0f° còn lại…", Math.abs(residual)); host.changed();
                                sendTurn((residual + 360) % 360, originalTarget, before, corrections + 1, run, turn, deadline);
                            } else if (Double.isNaN(actual)) finish(true, "SDK đã kết thúc xoay; chưa đọc được góc thân.");
                            else {
                                finish(Math.abs(residual) <= 5, String.format(java.util.Locale.US,
                                    "Thân đã xoay %.0f° / yêu cầu %.0f° · còn lệch %.0f°.", actual, requested, Math.abs(residual)));
                            }
                        }), 300);
                    }
                    else finish(false, "SDK xoay trả về " + code + ": " + clipped(data));
                }); }
                @Override public void onResult(int code, String data) { result(code, data); }
                @Override public void onResult(int code, String data, String extra) { result(code, data); }
                @Override public void onError(int code, String error) { main.post(() -> {
                    if (!terminal && !closed && run == generation && turn == motionSerial && turning) {
                        terminal = true; sdkActionRunning = false; finish(false, "Lỗi xoay " + code + ": " + clipped(error));
                    }
                }); }
                @Override public void onError(int code, String error, String extra) { onError(code, error); }
            });
            if (accepted < 0) finish(false, "SDK từ chối xoay: " + accepted);
        } catch (Exception error) { finish(false, "Không gửi được lệnh xoay: " + error.getMessage()); }
    }
    private interface HeadingResult { void complete(double radians); }
    private void heading(HeadingResult callback) {
        final boolean[] delivered = {false};
        main.postDelayed(() -> { if (!delivered[0]) { delivered[0] = true; callback.complete(Double.NaN); } }, 1500);
        try {
            robot.getPosition(host.nextId(), new CommandListener() {
                private void received(int code, String data) { main.post(() -> {
                    if (delivered[0]) return; delivered[0] = true;
                    double theta = Double.NaN;
                    if (code == Definition.RESULT_OK) try {
                        theta = new org.json.JSONObject(data).getDouble(Definition.JSON_NAVI_POSITION_THETA);
                        if (Double.isNaN(theta) || Double.isInfinite(theta)) theta = Double.NaN;
                    } catch (Exception ignored) {}
                    callback.complete(theta);
                }); }
                @Override public void onResult(int code, String data) { received(code, data); }
                @Override public void onResult(int code, String data, String extra) { received(code, data); }
            });
        } catch (Exception ignored) { if (!delivered[0]) { delivered[0] = true; callback.complete(Double.NaN); } }
    }
    private void finish(boolean ok, String message) {
        turning = false; sdkActionRunning = false; motionSerial++; input.pause(false); policy.finished(SystemClock.elapsedRealtime()); status = message;
        Log.i("GreetingVoice", (ok ? "Completed: " : "Failed: ") + message);
        StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(ok, message); host.changed();
    }
    void stop(String message, StopResult completion) {
        enabled = false; policy.stop(); input.stop(); status = message;
        if (!turning || !sdkActionRunning) { turning = false; motionSerial++; generation++; if (completion != null) completion.complete(true, message); host.changed(); return; }
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
        enabled = false; turning = false; sdkActionRunning = false; generation++; motionSerial++; policy.stop(); input.stop(); status = message;
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
