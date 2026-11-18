package com.lehoangrobotics.greetingbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.module.ModuleCallbackApi;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;

/** A foreground SDK client. No background chassis control or automatic pose restoration. */
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RobotApi robot = RobotApi.getInstance();
    private final GreetingSettings settings = new GreetingSettings();
    private final AudioLoopback audio = new AudioLoopback();
    private SharedPreferences preferences;
    private CloudSync cloud;
    private VoiceDirectionTest voiceDirection;
    private boolean connected, foreground, suspended, localized, destroyed, refreshing, pendingMicrophone, pendingVoiceDirection;
    private int requestId = 100, navigationGeneration, refreshGeneration;
    private String mapName = "", target = "", navigation = "idle", microphone = "idle";
    private String message = "Đang kết nối RobotOS…", webMessage = "Chưa cấu hình Web quản lý";
    private String navigationCommand = "", stopCommand = "";
    private String refreshCommand = "";
    private JSONArray places = new JSONArray();
    private TextView sdkText, mapText, messageText, webText, emptyText, voiceText;
    private LinearLayout placeList;
    private Button refreshButton, stopButton, micButton, voiceButton;
    private final ArrayList<Button> placeButtons = new ArrayList<>();
    private long lastRefresh;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (pendingMicrophone && ready()) { pendingMicrophone = false; microphoneTest(""); }
            if (pendingVoiceDirection && ready() && voiceDirection != null) { pendingVoiceDirection = false; voiceDirection.start(); }
            if (ready() && !refreshing && !navigation.equals("checking") && !navigation.equals("stopping") &&
                SystemClock.elapsedRealtime() - lastRefresh > 5000) refresh(null);
            render();
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("greetingbot", MODE_PRIVATE);
        try { settings.update(new JSONObject(preferences.getString("motionSettings", "{}"))); } catch (Exception ignored) {}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        createUi();
        cloud = new CloudSync(new CloudSync.Listener() {
            @Override public void settings(JSONObject json) {
                settings.update(json);
                preferences.edit().putString("motionSettings", json.toString()).apply();
            }
            @Override public void command(JSONObject json, long deadline) { executeRemote(json, deadline); }
            @Override public void connection(String status) { webMessage = status; render(); }
        });
        configureCloud();
        robot.connectServer(this, new ApiListener() {
            @Override public void handleApiConnected() { main.post(() -> {
                if (destroyed) return;
                connected = true; suspended = false;
                if (voiceDirection == null) voiceDirection = new VoiceDirectionTest(MainActivity.this, new VoiceDirectionTest.Host() {
                    @Override public boolean canControl() { return ready() && navigation.equals("idle") && !audio.busy(); }
                    @Override public int nextId() { return MainActivity.this.nextId(); }
                    @Override public double angleOffset() { return settings.voiceAngleOffset; }
                    @Override public void changed() { render(); }
                });
                android.util.Log.i("Greetingbot", "SDK connected; ACL=" + com.ainirobot.coreservice.utils.SettingDataHelper.getInstance().getRobotInt("current_pkg_acl") + "; RobotOS=" + robot.getVersion());
                robot.setCallback(new ModuleCallbackApi() {
                    @Override public void onSuspend() { main.post(() -> loseControl("RobotOS đang tiếp quản (dừng khẩn, sạc hoặc ứng dụng khác).")); }
                    @Override public void onRecovery() { main.post(() -> {
                        if (destroyed) return;
                        suspended = false; message = "RobotOS đã trả quyền điều khiển.";
                        if (ready()) refresh(null); render();
                    }); }
                    @Override public boolean onSendRequest(int id, String type, String text, String params) {
                        return voiceDirection != null && voiceDirection.onModuleRequest(id, type, text, params);
                    }
                });
                message = "Đã kết nối SDK. Mở app từ Home của RobotOS để nhận quyền điều khiển.";
                if (ready()) refresh(null); render();
            }); }
            @Override public void handleApiDisabled() { main.post(() -> { connected = false; loseControl("RobotOS SDK chưa khả dụng."); }); }
            @Override public void handleApiDisconnected() { main.post(() -> { connected = false; loseControl("Mất kết nối RobotOS SDK."); }); }
        });
        main.post(tick);
    }

    @Override protected void onResume() {
        super.onResume(); foreground = true; lastRefresh = 0; render();
    }
    @Override protected void onPause() {
        // Stop while the Activity still has control. Never resume a previous movement automatically.
        pendingVoiceDirection = false;
        if (voiceDirection != null && voiceDirection.active()) voiceDirection.stop("Đã dừng test hướng âm khi app rời màn hình.", null);
        if (ready() && (navigation.equals("moving") || navigation.equals("checking"))) stopNavigation("");
        foreground = false; refreshing = false; refreshGeneration++; navigationGeneration++;
        if (!navigationCommand.isEmpty()) cloud.result(navigationCommand, "failed", "Ứng dụng đã rời màn hình.");
        if (!stopCommand.isEmpty()) cloud.result(stopCommand, "failed", "App rời màn hình trước khi nhận xác nhận dừng từ SDK.");
        if (!refreshCommand.isEmpty()) cloud.result(refreshCommand, "failed", "Ứng dụng đã rời màn hình.");
        navigationCommand = ""; stopCommand = ""; refreshCommand = ""; navigation = "idle"; audio.cancel(); render();
        super.onPause();
    }
    @Override protected void onDestroy() {
        destroyed = true; main.removeCallbacks(tick); audio.cancel();
        if (voiceDirection != null) voiceDirection.close();
        if (cloud != null) cloud.close();
        if (connected) robot.disconnectApi();
        super.onDestroy();
    }

    private boolean authorized() {
        if (!connected) return false;
        // Some ROMs (including the tested V11.9 Mini) omit current_pkg_acl.
        // In that case use Orion's documented connect/suspend/recovery contract.
        // CoreService continues to enforce authorization on every SDK request.
        try (android.database.Cursor cursor = getContentResolver().query(
            android.net.Uri.parse("content://com.ainirobot.coreservice.robotsettingprovider/setting"),
            new String[]{"value"}, "id=?", new String[]{"current_pkg_acl"}, null)) {
            if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0) || cursor.getString(0).isEmpty())
                return robot.isApiConnectedService();
            return "1".equals(cursor.getString(0));
        } catch (Exception e) { return false; }
    }
    private boolean ready() { return connected && authorized() && foreground && !suspended && !destroyed; }
    private int nextId() { return ++requestId; }
    private void loseControl(String why) {
        if (destroyed) return;
        suspended = true; localized = false; refreshing = false; refreshGeneration++; navigationGeneration++;
        if (voiceDirection != null) voiceDirection.controlLost("RobotOS đang tiếp quản; test hướng âm đã dừng.");
        if (!navigationCommand.isEmpty()) cloud.result(navigationCommand, "failed", why);
        if (!stopCommand.isEmpty()) cloud.result(stopCommand, "failed", why);
        if (!refreshCommand.isEmpty()) cloud.result(refreshCommand, "failed", why);
        navigationCommand = ""; stopCommand = ""; refreshCommand = ""; navigation = "idle"; target = "";
        audio.cancel(); message = why; render();
    }

    private abstract class Reply extends CommandListener {
        abstract void success(int code, String value);
        void failure(int code, String value) { message = "SDK " + code + ": " + value; render(); }
        @Override public void onResult(int code, String value) { main.post(() -> {
            if (destroyed) return;
            try { success(code, value); } catch (Exception e) { failure(-1, e.getMessage()); }
        }); }
        @Override public void onResult(int code, String value, String extra) { onResult(code, value); }
        @Override public void onError(int code, String value) { main.post(() -> { if (!destroyed) failure(code, value); }); }
        @Override public void onError(int code, String value, String extra) { onError(code, value); }
    }

    private void refresh(Runnable complete) {
        refresh(complete, "");
    }
    private void refresh(Runnable complete, String commandId) {
        if (!ready()) return;
        if (!refreshCommand.isEmpty()) cloud.result(refreshCommand, "failed", "Đã thay thế bằng yêu cầu đọc bản đồ mới.");
        refreshCommand = commandId;
        refreshing = true;
        lastRefresh = SystemClock.elapsedRealtime();
        final int generation = ++refreshGeneration;
        try {
            robot.getMapName(nextId(), new Reply() {
                @Override void success(int code, String value) {
                    if (generation != refreshGeneration || !ready()) return;
                    if (code != Definition.RESULT_OK || value == null || value.trim().isEmpty()) { failure(code, "Không đọc được tên bản đồ."); return; }
                    if (!mapName.equals(value)) {
                        if (navigation.equals("moving")) stopNavigation("");
                        mapName = value; places = new JSONArray(); localized = false; rebuildPlaces();
                    }
                    robot.getPlaceList(nextId(), new Reply() {
                        @Override void success(int status, String data) {
                            if (generation != refreshGeneration || !ready()) return;
                            try {
                                if (status != Definition.RESULT_OK) throw new IllegalStateException("SDK " + status);
                                places = new JSONArray(data); rebuildPlaces();
                                robot.isRobotEstimate(nextId(), new Reply() {
                                    @Override void success(int result, String estimate) {
                                        if (generation != refreshGeneration || !ready()) return;
                                        localized = result == Definition.RESULT_OK && "true".equals(estimate);
                                        if (navigation.equals("moving") && !localized) stopNavigation("");
                                        cloud.result(refreshCommand, "completed", "Đã đọc điểm từ bản đồ robot."); refreshCommand = "";
                                        refreshing = false; refreshGeneration++;
                                        if (message.startsWith("Đã kết nối SDK") || message.startsWith("Đang đọc bản đồ"))
                                            message = "Đã đọc " + places.length() + " điểm. " + (localized ? "Chọn một điểm để di chuyển." : "Chưa định vị; mở ứng dụng bản đồ để định vị robot.");
                                        render(); if (complete != null) complete.run();
                                    }
                                    @Override void failure(int c, String text) { failRefresh(generation, c, text); }
                                });
                            } catch (Exception e) { failRefresh(generation, status, "Không đọc được điểm: " + e.getMessage()); }
                        }
                        @Override void failure(int c, String text) { failRefresh(generation, c, text); }
                    });
                }
                @Override void failure(int c, String text) { failRefresh(generation, c, text); }
            });
        } catch (Exception e) { failRefresh(generation, -1, e.getMessage()); }
        main.postDelayed(() -> {
            if (generation == refreshGeneration) failRefresh(generation, -1, "SDK không trả lời kiểm tra bản đồ.");
        }, 6000);
    }

    private void failRefresh(int generation, int code, String why) {
        if (generation != refreshGeneration) return;
        cloud.result(refreshCommand, "failed", "Không đọc được bản đồ: " + why); refreshCommand = "";
        refreshing = false; refreshGeneration++; localized = false;
        if (navigation.equals("checking")) finishNavigation(false, "Không đọc được bản đồ: " + why);
        else { message = "SDK " + code + ": " + why; render(); }
    }

    private boolean reachable(String name) {
        for (int i = 0; i < places.length(); i++) {
            JSONObject point = places.optJSONObject(i);
            if (point != null && name.equals(point.optString("name")) && point.optInt("status", -1) == 0) return true;
        }
        return false;
    }

    private void navigate(String name, String expectedMap, String commandId) {
        navigate(name, expectedMap, commandId, Long.MAX_VALUE);
    }
    private void navigate(String name, String expectedMap, String commandId, long deadline) {
        if (voiceDirection != null && voiceDirection.active()) {
            message = "Dừng test hướng âm trước khi điều hướng."; cloud.result(commandId, "failed", message); render(); return;
        }
        if (!ready() || !navigation.equals("idle")) { cloud.result(commandId, "failed", "Robot chưa sẵn sàng hoặc đang điều hướng."); return; }
        navigationCommand = commandId; target = name; navigation = "checking";
        message = "Đang kiểm tra bản đồ và định vị…"; render();
        final int generation = ++navigationGeneration;
        refresh(() -> {
            if (generation != navigationGeneration || !ready()) return;
            if (SystemClock.elapsedRealtime() >= deadline || !mapName.equals(expectedMap) || !reachable(name) || !localized) {
                finishNavigation(false, "Bản đồ/điểm đã thay đổi hoặc robot chưa định vị. Kiểm tra trong Map Tool."); return;
            }
            navigation = "moving"; message = "Đang đi tới: " + name;
            cloud.result(commandId, "running", message); render();
            try {
                int accepted = robot.startNavigation(nextId(), name, settings.arrivalTolerance, settings.obstacleTimeoutMs,
                    settings.linearSpeed, settings.angularSpeed, new ActionListener() {
                        private void result(int code, String response) { main.post(() -> {
                            if (generation != navigationGeneration || destroyed) return;
                            if (code == Definition.ACTION_RESPONSE_STOP_SUCCESS) { stopped(); return; }
                            finishNavigation(code == Definition.RESULT_OK && "true".equals(response),
                                code == Definition.RESULT_OK && "true".equals(response) ? "Đã tới: " + name : "Điều hướng không thành công (" + code + ").");
                        }); }
                        @Override public void onResult(int code, String response) { result(code, response); }
                        @Override public void onResult(int code, String response, String extra) { result(code, response); }
                        @Override public void onError(int code, String text) { main.post(() -> {
                            if (generation != navigationGeneration || destroyed) return;
                            finishNavigation(code == Definition.ERROR_IN_DESTINATION,
                                code == Definition.ERROR_IN_DESTINATION ? "Robot đã ở điểm: " + name : "Lỗi điều hướng " + code + ": " + text);
                        }); }
                        @Override public void onError(int code, String text, String extra) { onError(code, text); }
                        @Override public void onStatusUpdate(int code, String data, String extra) { main.post(() -> {
                            if (generation != navigationGeneration || destroyed) return;
                            if (code == 1020 || code == 1025) { stopNavigation(""); return; }
                            message = code == 1018 ? "Có vật cản; robot đang chờ đường trống." : "Đang đi tới: " + name + " (" + code + ")";
                            render();
                        }); }
                        @Override public void onStatusUpdate(int code, String data) { onStatusUpdate(code, data, ""); }
                    });
                if (accepted < 0) finishNavigation(false, "SDK từ chối điều hướng: " + accepted);
            } catch (Exception e) { finishNavigation(false, "Lỗi SDK: " + e.getMessage()); }
        });
    }

    private void finishNavigation(boolean ok, String why) {
        navigationGeneration++;
        cloud.result(navigationCommand, ok ? "completed" : "failed", why);
        if (!stopCommand.isEmpty()) cloud.result(stopCommand, ok ? "completed" : "failed", "Điều hướng đã kết thúc: " + why);
        stopCommand = "";
        navigationCommand = ""; navigation = "idle"; target = ""; message = why; render();
    }
    private void stopped() {
        navigationGeneration++; refreshGeneration++; refreshing = false;
        cloud.result(refreshCommand, "failed", "Đã hủy yêu cầu đọc bản đồ."); refreshCommand = "";
        cloud.result(navigationCommand, "failed", "Điều hướng đã được dừng.");
        cloud.result(stopCommand, "completed", "SDK đã xác nhận dừng điều hướng.");
        navigationCommand = ""; stopCommand = ""; navigation = "idle"; target = "";
        message = "Đã dừng điều hướng."; render();
    }
    private void stopNavigation(String commandId) {
        if (voiceDirection != null && voiceDirection.active()) {
            cloud.result(commandId, "running", "Đang yêu cầu dừng test hướng âm.");
            voiceDirection.stop("Đã tắt nghe từ khóa; đang dừng xoay nếu có.",
                (ok, text) -> cloud.result(commandId, ok ? "completed" : "failed", text));
            return;
        }
        if (!ready()) { cloud.result(commandId, "failed", "RobotOS đang giữ quyền điều khiển."); return; }
        if (navigation.equals("idle") || navigation.equals("checking")) { stopCommand = commandId; stopped(); return; }
        if (navigation.equals("stopping")) { cloud.result(commandId, "failed", "Đang chờ SDK xác nhận dừng."); return; }
        stopCommand = commandId; navigation = "stopping"; message = "Đang yêu cầu SDK dừng điều hướng…";
        cloud.result(commandId, "running", message); render();
        try {
            int accepted = robot.stopNavigation(nextId());
            if (accepted < 0) stopUnconfirmed("SDK chưa nhận lệnh dừng: " + accepted + ". Có thể bấm dừng để thử lại.");
        } catch (Exception e) { stopUnconfirmed("Không gửi được lệnh dừng: " + e.getMessage()); }
        // Do not claim the robot stopped without ACTION_RESPONSE_STOP_SUCCESS.
        final int generation = navigationGeneration;
        main.postDelayed(() -> {
            if (!destroyed && generation == navigationGeneration && navigation.equals("stopping"))
                stopUnconfirmed("Chưa nhận xác nhận dừng từ SDK. Bấm dừng để thử lại hoặc dùng nút dừng vật lý.");
        }, 5000);
    }

    private void stopUnconfirmed(String why) {
        cloud.result(stopCommand, "failed", why); stopCommand = "";
        navigation = "moving"; message = why; render();
    }

    private void microphoneTest(String commandId) {
        if (voiceDirection != null && voiceDirection.active()) {
            message = "Dừng test hướng âm trước khi ghi âm/phát loa."; cloud.result(commandId, "failed", message); render(); return;
        }
        if (!ready()) { cloud.result(commandId, "failed", "Ứng dụng chưa có quyền điều khiển RobotOS."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            cloud.result(commandId, "failed", "Chưa cấp quyền microphone trên màn hình robot.");
            if (commandId.isEmpty()) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        if (audio.busy()) { cloud.result(commandId, "failed", "Microphone đang bận."); return; }
        microphone = "recording"; cloud.result(commandId, "running", "Đang ghi âm."); render();
        audio.start(settings.microphoneSeconds, settings.playbackVolume, new AudioLoopback.Listener() {
            @Override public void state(String state, String text) { if (!destroyed && foreground) { microphone = state; message = text; render(); } }
            @Override public void complete(boolean ok, String text) {
                cloud.result(commandId, ok ? "completed" : "failed", text);
                microphone = "idle"; if (!destroyed) { message = text; render(); }
            }
        });
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(code, permissions, grantResults);
        if (code == 7 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) pendingMicrophone = true;
        else if (code == 8 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) pendingVoiceDirection = true;
        else { message = "Cần cấp quyền microphone để ghi âm."; render(); }
    }

    private void toggleVoiceDirection() {
        if (voiceDirection == null) return;
        if (voiceDirection.active()) { voiceDirection.stop("Đã dừng test hướng âm.", null); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 8); return;
        }
        voiceDirection.start();
    }

    private void executeRemote(JSONObject command, long deadline) {
        String id = command.optString("id");
        if (!id.matches("[a-fA-F0-9]{32}")) return;
        String seen = preferences.getString("consumedCommands", "");
        if (seen.contains(id)) return;
        // Persist consumption before any physical action, including across process restarts.
        String updated = id + "," + seen;
        if (updated.length() > 64 * 33) updated = updated.substring(0, 64 * 33);
        if (!preferences.edit().putString("consumedCommands", updated).commit()) {
            cloud.result(id, "failed", "Không ghi được nhật ký lệnh."); return;
        }
        if (SystemClock.elapsedRealtime() >= deadline || !ready()) {
            cloud.result(id, "failed", "Lệnh hết hạn hoặc app chưa sẵn sàng ở màn hình chính."); return;
        }
        switch (command.optString("type")) {
            case "navigate": navigate(command.optString("placeName"), command.optString("mapName"), id, deadline); break;
            case "stop_navigation": stopNavigation(id); break;
            case "microphone_test": microphoneTest(id); break;
            case "refresh_places":
                cloud.result(id, "running", "Đang đọc bản đồ.");
                refresh(null, id);
                break;
            default: cloud.result(id, "failed", "Lệnh không được hỗ trợ.");
        }
    }

    private void configureCloud() {
        cloud.configure(preferences.getString("serverUrl", "http://192.168.0.123:5050"), preferences.getString("robotId", "GREETING-01"), preferences.getString("deviceToken", ""));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(21, 40, 60));
        view.setPadding(0, dp(5), 0, dp(5)); return view;
    }
    private Button button(String text, LinearLayout parent, View.OnClickListener click) {
        Button view = new Button(this); view.setText(text); view.setTextSize(19); view.setAllCaps(false);
        view.setMinHeight(dp(56)); view.setOnClickListener(click);
        LinearLayout.LayoutParams params = parent.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        parent.addView(view, params); return view;
    }
    private void createUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12)); root.setBackgroundColor(Color.rgb(241, 246, 250));
        TextView title = label("LÊ HOÀNG · GREETINGBOT", 29); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        sdkText = label("", 17); root.addView(sdkText);
        mapText = label("", 20); root.addView(mapText);
        messageText = label(message, 18); messageText.setMaxLines(3); root.addView(messageText);
        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.HORIZONTAL); root.addView(tools);
        refreshButton = button("↻  Đọc lại điểm", tools, v -> { message = "Đang đọc bản đồ…"; refresh(null); });
        button("Mở ứng dụng bản đồ", tools, v -> openMapTool());
        button("Cấu hình", tools, v -> showConfiguration());
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); root.addView(actions);
        stopButton = button("■  DỪNG CHUYỂN ĐỘNG", actions, v -> stopNavigation("")); stopButton.setTextColor(Color.rgb(183, 28, 28));
        micButton = button("●  Test microphone → phát lại qua loa", actions, v -> {
            if (audio.busy()) audio.cancel(); else microphoneTest("");
        });
        voiceButton = button("◎  Test hướng âm: Hello robot / robot ơi", root, v -> toggleVoiceDirection());
        voiceText = label("Bấm test rồi gọi robot từ một hướng. Test tự tắt sau 60 giây.", 15); voiceText.setMaxLines(5); root.addView(voiceText);
        TextView heading = label("CHỌN ĐIỂM ĐẾN", 18); heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        emptyText = label("Chưa có điểm. Tạo bản đồ và điểm trong Map Tool của robot, sau đó bấm Đọc lại điểm.", 18); root.addView(emptyText);
        ScrollView pointsScroll = new ScrollView(this); root.addView(pointsScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        placeList = new LinearLayout(this); placeList.setOrientation(LinearLayout.VERTICAL); pointsScroll.addView(placeList);
        webText = label(webMessage, 15); root.addView(webText);
        setContentView(root);
    }

    private void rebuildPlaces() {
        placeList.removeAllViews(); placeButtons.clear(); emptyText.setVisibility(places.length() == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < places.length(); i++) {
            JSONObject point = places.optJSONObject(i); if (point == null) continue;
            String name = point.optString("name"); if (name.isEmpty()) continue;
            String pointMap = mapName;
            Button view = button(name + (point.optInt("status", -1) == 0 ? "   →" : " · Không thể tới"), placeList, v -> navigate(name, pointMap, ""));
            view.setTag(point.optInt("status", -1) == 0); placeButtons.add(view);
        }
        render();
    }
    private void render() {
        if (sdkText == null || destroyed) return;
        boolean authorized = authorized(); boolean ready = connected && authorized && foreground && !suspended;
        setText(sdkText, !connected ? "SDK: chưa kết nối" : !authorized ? "SDK: cần mở app từ Home RobotOS" : suspended ? "SDK: hệ thống đang tiếp quản" : "SDK: đã kết nối · " + (localized ? "Đã định vị" : "Chưa định vị"));
        setText(mapText, "Bản đồ: " + (mapName.isEmpty() ? "Chưa đọc được" : mapName));
        setText(messageText, message); setText(webText, webMessage);
        refreshButton.setEnabled(ready && !refreshing && !navigation.equals("checking") && !navigation.equals("stopping"));
        boolean voiceActive = voiceDirection != null && voiceDirection.active();
        for (Button button : placeButtons) button.setEnabled(ready && !voiceActive && localized && navigation.equals("idle") && Boolean.TRUE.equals(button.getTag()));
        stopButton.setEnabled(ready && (voiceActive || (!navigation.equals("stopping") && !navigation.equals("idle"))));
        micButton.setEnabled((ready && !voiceActive) || audio.busy());
        voiceButton.setEnabled(voiceActive || (ready && voiceDirection != null && navigation.equals("idle") && !audio.busy()));
        setText(voiceButton, voiceActive ? "■  Dừng test hướng âm / dừng xoay" : "◎  Test hướng âm: Hello robot / robot ơi");
        if (voiceDirection != null) setText(voiceText, voiceDirection.display());
        setText(micButton, audio.busy() ? "■  Hủy kiểm tra microphone" : "●  Test microphone → phát lại qua loa");
        if (cloud != null) {
            try { cloud.snapshot(new JSONObject().put("sdkConnected", connected).put("authorized", authorized)
                .put("foreground", foreground).put("suspended", suspended).put("localized", localized)
                .put("mapName", mapName).put("places", places).put("navigationState", navigation)
                .put("target", target).put("microphoneState", microphone).put("message", message)
                .put("systemVersion", connected ? robot.getVersion() : "")); } catch (Exception ignored) {}
        }
    }

    private void setText(TextView view, String text) { if (!view.getText().toString().equals(text)) view.setText(text); }

    private void openMapTool() {
        String packageName = preferences.getString("mapToolPackage", "com.ainirobot.maptool");
        Intent launch = packageName.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            message = "Mở Map Tool từ Home của RobotOS, hoặc nhập tên gói Map Tool trong Cấu hình kết nối Web."; render(); return;
        }
        startActivity(launch);
    }
    private EditText field(LinearLayout root, String title, String value, boolean secret) {
        root.addView(label(title, 16)); EditText input = new EditText(this); input.setSingleLine(true); input.setText(value);
        input.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        root.addView(input); return input;
    }
    private void showConfiguration() {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(20), dp(8), dp(20), dp(8));
        Button speech = button("Giọng nói OpenAI", fields, v -> {});
        EditText url = field(fields, "Địa chỉ Web/server (không thêm /fleetHub)", preferences.getString("serverUrl", "http://192.168.0.123:5050"), false);
        EditText id = field(fields, "ID robot", preferences.getString("robotId", "GREETING-01"), false);
        EditText token = field(fields, "Mã ghép nối từ Web quản lý", preferences.getString("deviceToken", ""), true);
        EditText mapPackage = field(fields, "Tên gói ứng dụng Map Tool (tùy chọn)", preferences.getString("mapToolPackage", "com.ainirobot.maptool"), false);
        ScrollView scroll = new ScrollView(this); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Kết nối Web quản lý").setView(scroll)
            .setPositiveButton("Lưu", null).setNegativeButton("Đóng", null)
            .setNeutralButton("Ngắt kết nối", (d, which) -> {
                preferences.edit().remove("deviceToken").remove("serverUrl").apply();
                configureCloud(); webMessage = "Chưa ghép nối Web quản lý"; render();
            }).create();
        speech.setOnClickListener(v -> { dialog.dismiss(); showVoiceConfiguration(); });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String server = url.getText().toString().trim().replaceAll("/+$", "");
            try {
                if (!server.isEmpty()) {
                    URI uri = new URI(server);
                    if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null ||
                        uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                        throw new IllegalArgumentException("Nhập URL gốc, ví dụ http://192.168.0.179:5050");
                    if (!id.getText().toString().matches("[A-Za-z0-9_-]{1,64}") || token.getText().toString().trim().isEmpty())
                        throw new IllegalArgumentException("Cần ID robot và mã ghép nối từ Web.");
                }
                preferences.edit().putString("serverUrl", server).putString("robotId", id.getText().toString().trim())
                    .putString("deviceToken", token.getText().toString().trim()).putString("mapToolPackage", mapPackage.getText().toString().trim()).apply();
                configureCloud(); webMessage = server.isEmpty() ? "Chưa cấu hình Web quản lý" : "Đang kết nối Web…"; render(); dialog.dismiss();
            } catch (Exception e) { url.setError(e.getMessage()); }
        }));
        dialog.show();
    }
    private void showVoiceConfiguration() {
        if (voiceDirection != null && voiceDirection.active()) voiceDirection.stop("Đã dừng test để thay cấu hình giọng nói.", null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText url = field(fields, "Server giọng nói OpenAI", preferences.getString("voiceServerUrl", "http://192.168.0.123:5050"), false);
        EditText token = field(fields, "Mã kết nối giọng nói", preferences.getString("voiceToken", ""), true);
        EditText offset = field(fields, "Bù theo chiều quay (độ, mặc định 25: dương +25 / âm -25)", Double.toString(settings.voiceAngleOffset), false);
        offset.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Giọng nói OpenAI").setView(fields)
            .setPositiveButton("Lưu", null).setNegativeButton("Đóng", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String server = url.getText().toString().trim().replaceAll("/+$", ""); URI uri = new URI(server);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty())
                    throw new IllegalArgumentException("Nhập địa chỉ gốc của server giọng nói.");
                String credential = token.getText().toString().trim();
                if (!credential.matches("[A-Za-z0-9_-]{32,128}")) { token.setError("Mã kết nối phải có 32–128 ký tự."); return; }
                double angleOffset = Double.parseDouble(offset.getText().toString().trim());
                if (Double.isNaN(angleOffset) || Double.isInfinite(angleOffset) || angleOffset < -90 || angleOffset > 90) {
                    offset.setError("Offset từ -90 đến 90 độ."); return;
                }
                JSONObject savedSettings = new JSONObject(preferences.getString("motionSettings", "{}"));
                savedSettings.put("voiceAngleOffset", angleOffset); settings.update(savedSettings);
                preferences.edit().putString("voiceServerUrl", server).putString("voiceToken", credential)
                    .putString("motionSettings", savedSettings.toString()).apply();
                message = "Đã lưu kết nối giọng nói OpenAI. Bấm Test hướng âm để thử."; render(); dialog.dismiss();
            } catch (Exception error) { url.setError(error.getMessage()); }
        }));
        dialog.show();
    }
}
