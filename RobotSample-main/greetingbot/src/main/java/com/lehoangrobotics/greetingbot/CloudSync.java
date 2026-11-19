package com.lehoangrobotics.greetingbot;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class CloudSync implements AutoCloseable {
    interface Listener {
        void settings(JSONObject settings);
        void elevator(JSONObject config);
        void command(JSONObject command, long deadline);
        void connection(String message);
    }
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, JSONObject> results = new ConcurrentHashMap<>();
    private final Listener listener;
    private volatile String snapshot = "{}";
    private volatile String[] credentials = {"", "", "", ""};
    private long nextEnrollment;
    private String[] attemptedEnrollment;
    private volatile boolean closed;

    CloudSync(Listener listener) {
        this.listener = listener;
        executor.scheduleWithFixedDelay(this::sync, 0, 2, TimeUnit.SECONDS);
    }
    void configure(String url, String id, String token, boolean automatic) { credentials = new String[]{url, id, token, automatic ? "auto" : ""}; }
    void snapshot(JSONObject json) { snapshot = json.toString(); }
    void result(String id, String status, String message) {
        if (id == null || id.isEmpty()) return;
        try { results.put(id, new JSONObject().put("id", id).put("status", status).put("message", message)); }
        catch (Exception ignored) {}
    }
    private void sync() {
        String[] config = credentials;
        if (closed || config[0].isEmpty() || config[1].isEmpty() || config[2].isEmpty()) return;
        HttpURLConnection connection = null;
        try {
            ArrayList<JSONObject> sent = new ArrayList<>(results.values());
            JSONObject body = new JSONObject().put("snapshot", new JSONObject(snapshot)).put("results", new JSONArray(sent));
            URL url = new URL(config[0] + "/api/greeting/device/" + URLEncoder.encode(config[1], "UTF-8") + "/sync");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Greeting-Device-Token", config[2]);
            byte[] payload = body.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(payload); }
            int code = connection.getResponseCode();
            if (code == 401 && config[3].equals("auto") && !closed && config == credentials &&
                (attemptedEnrollment != config || SystemClock.elapsedRealtime() >= nextEnrollment)) {
                attemptedEnrollment = config; nextEnrollment = SystemClock.elapsedRealtime() + 30000;
                connection.disconnect(); connection = null;
                enroll(config);
                main.post(() -> { if (!closed && config == credentials) listener.connection("Đã tự đăng ký Web; đang đồng bộ…"); });
                return;
            }
            if (code != 200) throw new IllegalStateException(code == 401 ? "Sai ID hoặc mã ghép nối" : "Server HTTP " + code);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buf = new byte[4096]; int count;
                while ((count = input.read(buf)) > 0) {
                    bytes.write(buf, 0, count);
                    if (bytes.size() > 524288) throw new IllegalStateException("Phản hồi server quá lớn");
                }
            }
            JSONObject response = new JSONObject(bytes.toString("UTF-8"));
            if (config != credentials || closed) return;
            for (JSONObject ack : sent) results.remove(ack.optString("id"), ack);
            // Server clock determines TTL, avoiding Android clock skew.
            JSONObject command = response.optJSONObject("command");
            long ttl = 0;
            if (command != null) {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
                format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                ttl = format.parse(command.getString("expiresAt")).getTime() - format.parse(response.getString("serverTime")).getTime();
            }
            final long deadline = SystemClock.elapsedRealtime() + Math.min(ttl, 15000);
            main.post(() -> {
                if (closed || config != credentials) return;
                JSONObject settings = response.optJSONObject("settings");
                if (settings != null) listener.settings(settings);
                JSONObject elevator = response.optJSONObject("elevator");
                if (elevator != null) listener.elevator(elevator);
                listener.connection("Đã kết nối Web quản lý");
                if (command != null) listener.command(command, deadline);
            });
        } catch (Exception e) {
            String message = "Web: " + e.getMessage();
            main.post(() -> { if (!closed && config == credentials) listener.connection(message); });
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void enroll(String[] config) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(config[0] + "/api/greeting/device/enroll").openConnection();
        try {
            connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Greeting-Device-Token", config[2]);
            byte[] payload = new JSONObject().put("id", config[1]).put("name", "Greetingbot · " + config[1].substring(config[1].length() - 6)).toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int code = connection.getResponseCode();
            if (code != 200) throw new IllegalStateException(code == 403 ? "Server chưa bật tự kết nối LAN" : code == 409 ? "ID thuộc robot khác; giữ nguyên mã hiện có" : "Tự kết nối HTTP " + code);
            try (InputStream input = connection.getInputStream()) { /* No credential is returned. */ }
            nextEnrollment = 0;
        } finally { connection.disconnect(); }
    }
    @Override public void close() { closed = true; executor.shutdownNow(); }
}
