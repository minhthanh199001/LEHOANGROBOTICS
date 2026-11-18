package com.lehoangrobotics.greetingbot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.speech.SkillApi;
import com.ainirobot.coreservice.client.speech.SkillCallback;
import com.ainirobot.agent.AppAgent;
import com.ainirobot.agent.base.Transcription;

/** Uses RobotOS ASR and microphone-array direction, then the SDK's matching wakeUp/stopWakeUp pair. */
final class VoiceDirectionTest implements AutoCloseable {
    interface Host { boolean canControl(); int nextId(); void changed(); }
    interface StopResult { void complete(boolean success, String message); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RobotApi robot = RobotApi.getInstance();
    private final SkillApi speech = new SkillApi();
    private final VoiceDirectionPolicy policy = new VoiceDirectionPolicy();
    private final Host host;
    private final Context context;
    private final GreetingApplication application;
    private final AppAgent agent;
    private boolean savedAgentMuted, savedAgentWakeFree;
    private String agentSid = "";
    private String pendingPhrase = "", pendingEvent = "";
    private long pendingAt;
    private boolean wakeReceiverRegistered;
    private final android.content.BroadcastReceiver wakeReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context ignored, android.content.Intent intent) {
            if (!enabled || closed || !Definition.INTENT_SPEECH_WAKEUP.equals(intent.getAction())) return;
            Object value = intent.getExtras() == null ? null : intent.getExtras().get(Definition.EXTRA_UPLOAD_ANGLE);
            double angle = Double.NaN;
            try { if (value instanceof Number || value instanceof String) angle = Double.parseDouble(value.toString()); }
            catch (NumberFormatException ignoredError) {}
            Log.i("GreetingVoice", "System wake event; direction=" + angle);
            wakeAngle(angle);
        }
    };
    private boolean speechConnected, closed, turning, restoring, savedRecognition, savedContinuous, saved;
    private volatile boolean enabled;
    private int generation, utterance, motionSerial;
    private String status = "Chưa bật test hướng âm.", heard = "", lastKeyword = "";
    private double lastAngle = Double.NaN;
    private StopResult stopResult;

    VoiceDirectionTest(Context context, Host host) {
        this.context = context.getApplicationContext();
        this.application = (GreetingApplication) this.context;
        this.agent = application.agent();
        this.host = host;
        // Orion's services run as Android system UID on the tested ROM. Requiring our
        // signature permission lets system broadcasts through and rejects other apps.
        this.context.registerReceiver(wakeReceiver, new android.content.IntentFilter(Definition.INTENT_SPEECH_WAKEUP),
            "com.lehoangrobotics.greetingbot.permission.ROBOT_VOICE_EVENT", main);
        wakeReceiverRegistered = true;
        if (agent != null) {
            speechConnected = true;
            status = "Sẵn sàng test hướng âm qua AgentOS.";
            return;
        }
        if (!application.agentError().isEmpty()) {
            status = application.agentError(); return;
        }
        speech.connectApi(this.context, new ApiListener() {
            @Override public void handleApiConnected() { main.post(() -> {
                if (closed) return;
                speechConnected = true; speech.registerCallBack(callback);
                status = "Sẵn sàng test hướng âm. Nói: Hello robot hoặc robot ơi.";
                if (enabled) beginListening(); else host.changed();
            }); }
            @Override public void handleApiDisabled() { main.post(() -> speechLost("Dịch vụ nhận dạng giọng nói chưa khả dụng.")); }
            @Override public void handleApiDisconnected() { main.post(() -> speechLost("Mất kết nối dịch vụ giọng nói.")); }
        });
    }

    private final SkillCallback callback = new SkillCallback() {
        @Override public void onSpeechParResult(String text) { main.post(() -> {
            if (!enabled || closed || turning) return;
            heard = clipped(VoiceDirectionPolicy.transcript(text)); host.changed();
        }); }
        @Override public void onStart() { main.post(() -> {
            if (!enabled || closed || turning) return;
            utterance++; policy.speechStarted(SystemClock.elapsedRealtime());
            pendingPhrase = "";
            Log.i("GreetingVoice", "ASR utterance started");
        }); }
        @Override public void onStop() { Log.i("GreetingVoice", "ASR utterance stopped"); }
        @Override public void onVolumeChange(int volume) {}
        @Override public void onQueryEnded(int reason) { main.post(() -> {
            if (enabled) Log.i("GreetingVoice", "ASR query ended reason=" + reason);
            if (enabled && !turning && (reason == 3 || reason == 4)) {
                policy.clearAngle(); status = "Đang chờ: Hello robot hoặc robot ơi."; host.changed();
            }
        }); }
        @Override public void onQueryAsrResult(String text) { main.post(() -> {
            if (!enabled || closed || turning) return;
            Log.i("GreetingVoice", "ASR final keyword=" + VoiceDirectionPolicy.keyword(VoiceDirectionPolicy.transcript(text)));
            recognized(VoiceDirectionPolicy.transcript(text), VoiceDirectionPolicy.soundAngle(text, false), "asr-" + utterance);
        }); }
        @Override public void onError(String sid, int code, String error) { main.post(() -> {
            if (!enabled || closed) return;
            Log.w("GreetingVoice", "ASR error code=" + code);
            status = "Nhận dạng giọng nói lỗi " + code + ": " + clipped(error); host.changed();
        }); }
    };

    boolean active() { return enabled || turning; }
    boolean turning() { return turning; }
    String state() { return turning ? "turning" : enabled ? "listening" : "idle"; }
    String display() {
        return status + (heard.isEmpty() ? "" : "\nNghe được: “" + heard + "”")
            + (VoiceDirectionPolicy.validAngle(lastAngle) ? String.format(java.util.Locale.US, " · MicroArray %.0f°", lastAngle) : "");
    }

    void start() {
        if (closed || active()) return;
        if (!host.canControl()) { status = "Dừng điều hướng/ghi âm trước khi test hướng âm."; host.changed(); return; }
        enabled = true; generation++; heard = ""; lastKeyword = ""; pendingPhrase = ""; lastAngle = Double.NaN; policy.start();
        if (speechConnected) beginListening();
        else {
            status = "Đang kết nối dịch vụ giọng nói…"; host.changed();
            final int run = generation;
            main.postDelayed(() -> {
                if (enabled && generation == run && !speechConnected) stop("Không kết nối được dịch vụ giọng nói. Kiểm tra Speech Service của RobotOS.", null);
            }, 8000);
        }
    }
    private void beginListening() {
        if (!enabled || !host.canControl()) { stop("Ứng dụng chưa có quyền điều khiển RobotOS.", null); return; }
        try {
            if (agent != null) {
                savedAgentMuted = agent.isMicrophoneMuted(); savedAgentWakeFree = agent.isEnableWakeFree(); saved = true;
                agentSid = "";
                application.voiceListener(this::agentTranscription);
                agent.setDisablePlan(true);
                agent.setEnableWakeFree(false);
                agent.setMicrophoneMuted(false);
                status = "AgentOS đang nghe (60 giây): Hello robot / robot ơi. Đang thu âm cả hai bên robot.";
                Log.i("GreetingVoice", "AgentOS listening; wake-free filter disabled; language=" + setting(Definition.ROBOT_LANGUAGE));
                host.changed();
                final int run = generation;
                main.postDelayed(() -> { if (enabled && generation == run) stop("Đã hết 60 giây test hướng âm.", null); }, 60000);
                return;
            }
            savedRecognition = speech.isRecognizable(); savedContinuous = speech.isRecognizeContinue(); saved = true;
            speech.setRecognizable(true);
            speech.setRecognizeModeForce(true);
            status = "Đang bật nhận dạng giọng nói…"; host.changed();
            final int run = generation;
            // SDK setters are one-way Binder calls. A synchronous getter can overtake them.
            main.postDelayed(() -> checkListening(run, 0), 500);
        } catch (Exception e) { stop("Không bật được nhận dạng: " + e.getMessage(), null); }
    }
    private void agentTranscription(Transcription transcript) {
        final int run = generation;
        main.post(() -> {
            if (!enabled || closed || turning || run != generation) return;
            if (!transcript.getSid().equals(agentSid)) {
                agentSid = transcript.getSid(); policy.speechStarted(SystemClock.elapsedRealtime());
                pendingPhrase = "";
            }
            heard = clipped(transcript.getText());
            if (!transcript.getError().isEmpty()) {
                status = "AgentOS nhận dạng lỗi: " + clipped(transcript.getError()); host.changed(); return;
            }
            android.os.Bundle extra = transcript.getExtra();
            org.json.JSONObject metadata = new org.json.JSONObject();
            if (extra != null) for (String key : new String[]{"soundAngle", "sound_angle", "audio_angle", "wakeupAngle"}) {
                Object value = extra.get(key);
                if (value instanceof Number || value instanceof String) try { metadata.put(key, value); } catch (Exception ignored) {}
            }
            double angle = VoiceDirectionPolicy.soundAngle(metadata.toString(), false);
            Log.i("GreetingVoice", "AgentOS ASR final=" + transcript.getFinal() + "; keyword="
                + VoiceDirectionPolicy.keyword(heard) + "; direction=" + angle
                + "; extraKeys=" + (extra == null ? "none" : extra.keySet()));
            if (transcript.getFinal()) recognized(transcript.getText(), angle, "agent-" + transcript.getSid());
            else { status = "Đang nhận dạng câu gọi…"; host.changed(); }
        });
    }
    private void checkListening(int run, int attempt) {
        if (!enabled || closed || generation != run) return;
        try {
            boolean recognizable = speech.isRecognizable(), continuous = speech.isRecognizeContinue();
            String asrSwitch = setting(Definition.ROBOT_SETTING_ENABLE_ASR_SWITCH);
            Log.i("GreetingVoice", "Speech state recognizable=" + recognizable + "; continuous=" + continuous
                + "; ASR switch=" + asrSwitch + "; language=" + setting(Definition.ROBOT_LANGUAGE));
            if ((!recognizable || !continuous) && attempt < 3) {
                main.postDelayed(() -> checkListening(run, attempt + 1), 500); return;
            }
            if (!recognizable || !continuous) {
                stop("0".equals(asrSwitch)
                    ? "Microphone hệ thống đang tắt. Bật microphone trên RobotOS rồi bấm test lại."
                    : "Dịch vụ giọng nói chưa cho app bật nghe liên tục. Mở lại app từ Home của RobotOS rồi thử lại.", null);
                return;
            }
            status = "Đang nghe (60 giây): Hello robot / robot ơi. Chờ kết quả nhận dạng cuối và góc MicroArray.";
            Log.i("GreetingVoice", "Voice-direction test listening"); host.changed();
            main.postDelayed(() -> { if (enabled && generation == run) stop("Đã hết 60 giây test hướng âm.", null); }, 60000);
        } catch (Exception e) { stop("Không kiểm tra được nhận dạng: " + e.getMessage(), null); }
    }

    /** Called only from the trusted CoreService ModuleCallback, never from an exported broadcast. */
    boolean onModuleRequest(int id, String type, String text, String params) {
        if (!enabled || closed) return false;
        boolean wake = Definition.REQ_SPEECH_WAKEUP.equals(type);
        boolean voice = wake || Definition.REQ_SPEECH.equals(type) || Definition.REQ_SPEECH_TEXT.equals(type)
            || !VoiceDirectionPolicy.keyword(text).isEmpty();
        if (!voice) return false;
        final int run = generation;
        main.post(() -> {
            try {
                if (enabled && !closed && generation == run) {
                    double angle = VoiceDirectionPolicy.soundAngle(params, wake);
                    Log.i("GreetingVoice", "Core voice event=" + type + "; direction=" + angle + "; keyword=" + VoiceDirectionPolicy.keyword(text));
                    if (wake) {
                        wakeAngle(angle);
                    }
                    String transcript = text == null || text.isEmpty() ? VoiceDirectionPolicy.transcript(params) : text;
                    if (!transcript.isEmpty() && (!wake || !VoiceDirectionPolicy.keyword(transcript).isEmpty()))
                        recognized(transcript, angle, "core-" + id);
                }
            } finally {
                try { robot.finishModuleParser(id, true); } catch (Exception ignored) {}
            }
        });
        return true;
    }

    private void wakeAngle(double angle) {
        if (!enabled || turning || !VoiceDirectionPolicy.validAngle(angle)) return;
        long now = SystemClock.elapsedRealtime();
        policy.wakeAngle(angle, now); lastAngle = angle;
        if (!pendingPhrase.isEmpty() && now >= pendingAt && now - pendingAt <= 750) {
            String phrase = pendingPhrase, event = pendingEvent; pendingPhrase = "";
            recognized(phrase, angle, event); return;
        }
        status = "MicroArray nhận hướng âm; đang chờ đúng từ khóa."; host.changed();
    }

    private void recognized(String text, double angle, String eventId) {
        if (!enabled || closed || turning) return;
        heard = clipped(text);
        String keyword = VoiceDirectionPolicy.keyword(text);
        if (keyword.isEmpty()) { pendingPhrase = ""; status = "Chưa đúng từ khóa. Nói: Hello robot hoặc robot ơi."; policy.clearAngle(); host.changed(); return; }
        if (!host.canControl()) { stop("RobotOS đã giữ quyền điều khiển; dừng test hướng âm.", null); return; }
        double target = policy.select(text, angle, eventId, SystemClock.elapsedRealtime());
        if (!VoiceDirectionPolicy.validAngle(target)) {
            pendingPhrase = text; pendingEvent = eventId; pendingAt = SystemClock.elapsedRealtime();
            status = "Đã nghe “" + keyword + "”. Đang chờ góc MicroArray của câu gọi này.";
            host.changed(); return;
        }
        lastAngle = target; lastKeyword = keyword; turning = true;
        status = String.format(java.util.Locale.US, "Đang xoay theo “%s” · góc MicroArray %.0f°", keyword, target);
        Log.i("GreetingVoice", "Keyword matched; SDK wakeUp angle=" + target); host.changed();
        final int run = generation;
        final int turn = ++motionSerial;
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
                    if (!closed && run == generation && turn == motionSerial && turning) finish(false, "Lỗi xoay theo âm " + code + ": " + clipped(error));
                }); }
                @Override public void onError(int code, String error, String extra) { onError(code, error); }
            });
            if (accepted < 0) finish(false, "SDK từ chối xoay theo âm: " + accepted);
        } catch (Exception e) { finish(false, "Không gửi được lệnh xoay: " + e.getMessage()); }
        main.postDelayed(() -> {
            if (!closed && run == generation && turn == motionSerial && turning) stop("Chưa nhận kết quả xoay sau 12 giây; đang yêu cầu dừng.", null);
        }, 12000);
    }

    private void finish(boolean ok, String message) {
        turning = false; motionSerial++; policy.finished(SystemClock.elapsedRealtime()); status = message;
        Log.i("GreetingVoice", (ok ? "Completed: " : "Failed: ") + message);
        StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(ok, message);
        host.changed();
    }
    void stop(String message, StopResult completion) {
        enabled = false; pendingPhrase = ""; policy.stop(); restoreSpeech(); status = message;
        if (!turning) { generation++; if (completion != null) completion.complete(true, message); host.changed(); return; }
        if (stopResult != null) stopResult.complete(false, "Đã thay thế bằng yêu cầu dừng mới.");
        stopResult = completion;
        try {
            if (!host.canControl()) { controlLost(message); return; }
            int result = robot.stopWakeUp(host.nextId());
            if (result < 0) stopNotConfirmed("SDK chưa nhận lệnh dừng xoay: " + result + ". Bấm dừng để thử lại.");
        } catch (Exception e) { stopNotConfirmed("Chưa dừng được xoay: " + e.getMessage()); }
        final int run = generation;
        main.postDelayed(() -> {
            if (!closed && run == generation && turning) stopNotConfirmed("Chưa có xác nhận dừng xoay. Bấm dừng lại hoặc dùng nút dừng vật lý.");
        }, 5000);
        host.changed();
    }
    private void stopNotConfirmed(String message) {
        status = message; StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(false, message); host.changed();
    }
    void controlLost(String message) {
        enabled = false; turning = false; pendingPhrase = ""; generation++; motionSerial++; policy.stop(); restoreSpeech(); status = message;
        StopResult completion = stopResult; stopResult = null;
        if (completion != null) completion.complete(false, message); host.changed();
    }
    private void speechLost(String message) {
        if (closed) return;
        if (active()) stop(message, null);
        speechConnected = false; status = message; host.changed();
    }
    private void restoreSpeech() {
        if (!saved || !speechConnected || restoring) return;
        restoring = true;
        try {
            if (agent != null) {
                application.voiceListener(null);
                agent.setMicrophoneMuted(savedAgentMuted); agent.setEnableWakeFree(savedAgentWakeFree);
            } else { speech.setRecognizeModeForce(savedContinuous); speech.setRecognizable(savedRecognition); }
        }
        catch (Exception ignored) {}
        finally { saved = false; restoring = false; }
    }
    private static String clipped(String value) { return value == null ? "" : value.substring(0, Math.min(160, value.length())); }
    private String setting(String key) {
        try (android.database.Cursor cursor = context.getContentResolver().query(
                android.net.Uri.parse("content://com.ainirobot.coreservice.robotsettingprovider/setting"),
                new String[]{"value"}, "id=?", new String[]{key}, null)) {
            return cursor != null && cursor.moveToFirst() && !cursor.isNull(0) ? clipped(cursor.getString(0)) : "unknown";
        } catch (Exception ignored) { return "unknown"; }
    }
    @Override public void close() {
        if (closed) return;
        stop("Đã đóng test hướng âm.", null); closed = true; generation++;
        if (wakeReceiverRegistered) { context.unregisterReceiver(wakeReceiver); wakeReceiverRegistered = false; }
        try { if (agent == null) { if (speechConnected) speech.unregisterCallBack(callback); speech.disconnectApi(); } } catch (Exception ignored) {}
    }
}
