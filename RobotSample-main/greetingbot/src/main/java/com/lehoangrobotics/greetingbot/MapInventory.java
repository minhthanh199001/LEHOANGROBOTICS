package com.lehoangrobotics.greetingbot;

import android.os.Environment;
import android.os.Handler;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads inactive maps without changing the robot's active map. */
final class MapInventory implements AutoCloseable {
    interface Host {
        boolean canRead();
        int nextId();
        void updated();
    }
    private interface Request { int send(CommandListener listener); }
    private interface Result { void accept(String value); }

    private final Handler main;
    private final RobotApi robot;
    private final Host host;
    private final ExecutorService diskReader = Executors.newSingleThreadExecutor();
    private Map<String, JSONArray> maps = new LinkedHashMap<>();
    private int generation;
    private boolean busy, storageFallback;
    private String message = "";

    MapInventory(Handler main, RobotApi robot, Host host) {
        this.main = main;
        this.robot = robot;
        this.host = host;
    }

    boolean needsStorage() { return storageFallback; }
    String message() { return message; }
    JSONArray places(String map) { return maps.get(map); }
    void recordActive(String map, JSONArray points) { if (!map.isEmpty()) maps.put(map, points); }

    JSONArray snapshot(String activeMap) throws Exception {
        JSONArray result = new JSONArray();
        for (Map.Entry<String, JSONArray> map : maps.entrySet())
            if (!map.getKey().equals(activeMap))
                result.put(new JSONObject().put("name", map.getKey()).put("places", map.getValue()));
        return result;
    }

    void refresh(String activeMap, boolean canReadStorage) {
        if (busy || !host.canRead()) return;
        busy = true;
        final int run = ++generation;
        request(listener -> robot.getLocalMapInfoList(host.nextId(), listener), value -> {
            List<String> names = mapNames(value);
            if (canReadStorage) {
                diskReader.execute(() -> {
                    Map<String, JSONArray> fromDisk = diskMaps();
                    main.post(() -> loadMaps(run, activeMap, names, fromDisk));
                });
            } else loadMaps(run, activeMap, names, new LinkedHashMap<>());
        });
    }

    private void loadMaps(int run, String activeMap, List<String> names, Map<String, JSONArray> fromDisk) {
        if (run != generation || !host.canRead()) return;
        for (String name : fromDisk.keySet()) if (!names.contains(name)) names.add(name);
        names.remove(activeMap);
        if (names.size() > 64) names.subList(64, names.size()).clear();
        readNext(run, activeMap, names, 0, fromDisk, new LinkedHashMap<>(), false);
    }

    private void readNext(int run, String activeMap, List<String> names, int index, Map<String, JSONArray> fromDisk,
                          Map<String, JSONArray> found, boolean incomplete) {
        if (run != generation || !host.canRead()) return;
        if (index >= names.size()) {
            JSONArray current = maps.get(activeMap);
            if (current != null) found.put(activeMap, current);
            maps = found;
            storageFallback = names.isEmpty() || incomplete;
            message = incomplete ? "Một số map chưa đọc được điểm."
                : names.isEmpty() ? "Chỉ đọc được map đang mở." : "";
            busy = false;
            host.updated();
            return;
        }
        String name = names.get(index);
        request(listener -> robot.getPlaceListByMapName(host.nextId(), name, listener), value -> {
            if (run != generation || !host.canRead()) return;
            JSONArray points = placeArray(value);
            if (points == null) points = fromDisk.get(name);
            found.put(name, points != null ? points : new JSONArray());
            readNext(run, activeMap, names, index + 1, fromDisk, found, incomplete || points == null);
        });
    }

    void cancel() { generation++; busy = false; }
    @Override public void close() { cancel(); diskReader.shutdownNow(); }

    private void request(Request request, Result result) {
        final int run = generation;
        class Reply extends CommandListener {
            boolean done;
            void finish(String value) {
                if (done || run != generation) return;
                done = true;
                result.accept(value);
            }
            @Override public void onResult(int code, String value) {
                main.post(() -> finish(code == Definition.RESULT_OK ? value : null));
            }
            @Override public void onResult(int code, String value, String extra) { onResult(code, value); }
            @Override public void onError(int code, String value) { main.post(() -> finish(null)); }
            @Override public void onError(int code, String value, String extra) { onError(code, value); }
        }
        Reply reply = new Reply();
        main.postDelayed(() -> reply.finish(null), 2500);
        try { if (request.send(reply) < 0) main.post(() -> reply.finish(null)); }
        catch (Exception e) { main.post(() -> reply.finish(null)); }
    }

    private static JSONArray array(String text, String... keys) {
        if (text == null) return null;
        try {
            if (text.trim().startsWith("[")) return new JSONArray(text);
            JSONObject value = new JSONObject(text);
            for (String key : keys) {
                JSONArray list = value.optJSONArray(key);
                if (list != null) return list;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<String> mapNames(String text) {
        List<String> names = new ArrayList<>();
        JSONArray list = array(text, "maps", "mapList", "mapInfoList", "mapNames");
        if (list == null) return names;
        for (int i = 0; i < list.length(); i++) {
            JSONObject map = list.optJSONObject(i);
            String name = map == null ? list.optString(i, "") :
                map.optString("mapName", map.optString("name", map.optString("map_name", "")));
            if (!name.trim().isEmpty() && name.length() <= 200 && !names.contains(name)) names.add(name);
        }
        return names;
    }

    private static JSONArray placeArray(String text) {
        JSONArray source = array(text, "places", "placeList", "positionList");
        if (source == null) return null;
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length() && result.length() < 1000; i++) {
            JSONObject point = source.optJSONObject(i);
            if (point == null) continue;
            JSONObject translated = point.optJSONObject("name");
            String name = translated == null ? point.optString("name", "") : translated.optString("vi_VN",
                translated.optString("en_US", translated.optString("zh_CN", "")));
            if (name.trim().isEmpty() || name.length() > 200) continue;
            try {
                result.put(new JSONObject().put("name", name).put("x", point.optDouble("x", 0))
                    .put("y", point.optDouble("y", 0)).put("theta", point.optDouble("theta", 0))
                    .put("status", point.optInt("status", 0)));
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static Map<String, JSONArray> diskMaps() {
        Map<String, JSONArray> result = new LinkedHashMap<>();
        File base = new File(Environment.getExternalStorageDirectory(), "robot/map");
        File[] dirs = base.listFiles(File::isDirectory);
        if (dirs == null) return result;
        for (File dir : dirs) {
            String name = dir.getName();
            if (name.startsWith(".") || name.length() > 200) continue;
            File points = new File(dir, "place.json");
            if (!points.isFile() || points.length() > 4_194_304) continue;
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(points), StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = input.read(buffer)) != -1 && json.length() < 4_194_304) json.append(buffer, 0, read);
                JSONArray parsed = placeArray(json.toString());
                if (parsed != null) result.put(name, parsed);
            } catch (Exception ignored) {}
        }
        return result;
    }
}
