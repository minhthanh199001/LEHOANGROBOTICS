package com.ainirobot.robotos.bridge;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.robotos.maputils.GlobalData;
import com.ainirobot.robotos.maputils.MapppUtils;
import com.ainirobot.robotos.maputils.RoverMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RobotMapManager {
    private static final String TAG = "RobotMapManager";
    private static final String MAP_BASE_PATH = "/sdcard/robot/map";

    public interface OnMapPlacesChangedListener {
        void onPlacesChanged(String mapName);
    }

    private static final List<OnMapPlacesChangedListener> sPlacesListeners = new ArrayList<>();

    public static synchronized void addOnMapPlacesChangedListener(OnMapPlacesChangedListener listener) {
        if (listener != null && !sPlacesListeners.contains(listener)) {
            sPlacesListeners.add(listener);
        }
    }

    public static synchronized void removeOnMapPlacesChangedListener(OnMapPlacesChangedListener listener) {
        sPlacesListeners.remove(listener);
    }

    public static synchronized void notifyMapPlacesChanged(String mapName) {
        for (OnMapPlacesChangedListener l : new ArrayList<>(sPlacesListeners)) {
            try {
                l.onPlacesChanged(mapName);
            } catch (Throwable t) {
                Log.e(TAG, "Error in onPlacesChanged: " + t.getMessage());
            }
        }
    }

    public static String normalizeMapName(String s) {
        if (s == null) return "";
        return s.replace("ă", "a").replace("ầ", "a").replace("ấ", "a").replace("ả", "a").replace("ã", "a").replace("ạ", "a")
                .replace("à", "a").replace("á", "a").replace("â", "a")
                .replace("Ă", "A").replace("Ầ", "A").replace("Ấ", "A")
                .replace("ê", "e").replace("ế", "e").replace("ề", "e").replace("ệ", "e")
                .replace("ô", "o").replace("ồ", "o").replace("ố", "o").replace("ơ", "o")
                .replace("ư", "u").replace("ừ", "u").replace("ứ", "u")
                .replace("đ", "d").replace("Đ", "D")
                .trim().toLowerCase();
    }

    public static File resolveMapDir(String mapName) {
        if (TextUtils.isEmpty(mapName)) return new File(MAP_BASE_PATH);
        File direct = new File(MAP_BASE_PATH, mapName);
        if (direct.exists() && direct.isDirectory()) return direct;

        try {
            File base = new File(MAP_BASE_PATH);
            File[] dirs = base.listFiles();
            if (dirs != null) {
                // 1. Try diacritic-insensitive and case-insensitive match
                String normTarget = normalizeMapName(mapName);
                for (File d : dirs) {
                    if (d.isDirectory()) {
                        if (normalizeMapName(d.getName()).equalsIgnoreCase(normTarget)) {
                            return d;
                        }
                    }
                }
                // 2. Try suffix / numeric ID match (e.g. "1110154527" or "0721090621")
                String[] parts = mapName.split("-");
                if (parts.length > 1) {
                    String suffix = parts[parts.length - 1].trim();
                    if (!TextUtils.isEmpty(suffix)) {
                        for (File d : dirs) {
                            if (d.isDirectory() && d.getName().endsWith(suffix)) {
                                return d;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return direct;
    }

    public static List<String> getAvailableMapList() {
        List<String> list = new ArrayList<>();
        try {
            File dir = new File(MAP_BASE_PATH);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isDirectory() && !f.getName().startsWith(".")) {
                            list.add(f.getName());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getAvailableMapList error: " + t.getMessage());
        }
        return list;
    }

    public static String getCurrentMapName() {
        try {
            if (GlobalData.getInstance() != null) {
                String m = GlobalData.getInstance().getCurrentMapName();
                if (!TextUtils.isEmpty(m)) return m;
            }
        } catch (Throwable ignored) {}
        List<String> list = getAvailableMapList();
        if (!list.isEmpty()) return list.get(0);
        return "tăng 2-1110154527";
    }

    public static JsonObject buildMapDataJson(String currentMap) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "MAP_DATA");

        if (TextUtils.isEmpty(currentMap)) {
            try {
                if (GlobalData.getInstance() != null) {
                    currentMap = GlobalData.getInstance().getCurrentMapName();
                }
            } catch (Throwable ignored) {}
        }
        if (TextUtils.isEmpty(currentMap)) {
            currentMap = "tăng 2-1110154527";
        }
        File mapDir = resolveMapDir(currentMap);
        String actualMap = (mapDir != null && mapDir.exists()) ? mapDir.getName() : currentMap;
        root.addProperty("currentMap", actualMap);

        // Map list
        JsonArray mapArray = new JsonArray();
        List<String> allMaps = getAvailableMapList();
        for (String m : allMaps) {
            mapArray.add(m);
        }
        root.add("mapList", mapArray);

        // 1. Try to load High-Resolution continuous map: check pgm.zip, navi_data/map.pgm, and map.pgm
        String imageBase64 = "";
        try {
            RoverMap roverMap = null;
            File zipFile = new File(mapDir, "pgm.zip");
            if (zipFile.exists()) {
                Log.i(TAG, "Loading map from pgm.zip: " + zipFile.getAbsolutePath());
                roverMap = MapppUtils.loadMap(zipFile.getAbsolutePath());
            }

            if (roverMap == null || roverMap.bitmap == null) {
                File pgmFile = new File(mapDir, "navi_data/map.pgm");
                if (!pgmFile.exists()) {
                    pgmFile = new File(mapDir, "map.pgm");
                }
                if (pgmFile.exists()) {
                    Log.i(TAG, "Loading map from pgm file: " + pgmFile.getAbsolutePath());
                    roverMap = MapppUtils.loadMapNew(pgmFile.getAbsolutePath());
                }
            }

            if (roverMap != null && roverMap.bitmap != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                roverMap.bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                root.addProperty("originX", roverMap.x);
                root.addProperty("originY", roverMap.y);
                root.addProperty("resolution", roverMap.res);
                root.addProperty("pgmWidth", roverMap.width);
                root.addProperty("pgmHeight", roverMap.height);
                Log.i(TAG, "Loaded full-res continuous map: " + roverMap.width + "x" + roverMap.height + ", res=" + roverMap.res);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed loading full-res map: " + t.getMessage());
        }

        // 2. Fallback to thumbnail jpeg if full map was not available
        if (TextUtils.isEmpty(imageBase64)) {
            try {
                File imgFile = new File(MAP_BASE_PATH, actualMap + ".jpeg");
                if (!imgFile.exists()) {
                    imgFile = new File(mapDir, "map.jpeg");
                }
                if (imgFile.exists()) {
                    byte[] bytes = new byte[(int) imgFile.length()];
                    FileInputStream fis = new FileInputStream(imgFile);
                    fis.read(bytes);
                    fis.close();
                    imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to read map jpeg: " + t.getMessage());
            }
        }
        root.addProperty("imageBase64", imageBase64);

        // 3. Fetch Places: Read place.json from disk FIRST (Authoritative storage for custom & system places)
        Map<String, JsonObject> placesMap = new LinkedHashMap<>();
        try {
            File placeFile = new File(mapDir, "place.json");
            if (placeFile.exists()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(placeFile), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    double x = item.optDouble("x", 0.0);
                    double y = item.optDouble("y", 0.0);
                    double theta = item.optDouble("theta", 0.0);
                    String name = "";
                    if (item.has("name")) {
                        JSONObject nameObj = item.optJSONObject("name");
                        if (nameObj != null) {
                            if (nameObj.has("vi_VN")) {
                                name = nameObj.getString("vi_VN");
                            } else if (nameObj.has("en_US")) {
                                name = nameObj.getString("en_US");
                            } else if (nameObj.has("zh_CN")) {
                                name = nameObj.getString("zh_CN");
                            } else if (nameObj.keys().hasNext()) {
                                name = nameObj.getString(nameObj.keys().next());
                            }
                        } else {
                            name = item.optString("name", "");
                        }
                    }
                    if (TextUtils.isEmpty(name)) {
                        name = "Point " + (i + 1);
                    }

                    JsonObject p = new JsonObject();
                    p.addProperty("name", name);
                    p.addProperty("x", Math.round(x * 100.0) / 100.0);
                    p.addProperty("y", Math.round(y * 100.0) / 100.0);
                    p.addProperty("theta", Math.round(theta * 100.0) / 100.0);
                    placesMap.put(name.toLowerCase(), p);
                }
                Log.i(TAG, "Loaded " + placesMap.size() + " places from place.json for map: " + currentMap);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read place.json: " + t.getMessage());
        }

        // Only fallback to CoreService getPlaceList() if place.json on disk was empty or missing
        // AND strictly only if actualMap is the robot's currently active map in CoreService!
        String activeMap = getCurrentMapName();
        boolean isActiveMap = !TextUtils.isEmpty(activeMap) &&
                normalizeMapName(activeMap).equalsIgnoreCase(normalizeMapName(actualMap));
        if (placesMap.isEmpty() && isActiveMap) {
            try {
                if (RobotApi.getInstance().isApiConnectedService()) {
                    List<com.ainirobot.coreservice.client.actionbean.Pose> placeList = RobotApi.getInstance().getPlaceList();
                    if (placeList != null && !placeList.isEmpty()) {
                        for (com.ainirobot.coreservice.client.actionbean.Pose p : placeList) {
                            if (p == null || TextUtils.isEmpty(p.getName())) continue;
                            String key = p.getName().toLowerCase();
                            if (!placesMap.containsKey(key)) {
                                JsonObject item = new JsonObject();
                                item.addProperty("name", p.getName());
                                item.addProperty("x", Math.round(p.getX() * 100.0) / 100.0);
                                item.addProperty("y", Math.round(p.getY() * 100.0) / 100.0);
                                item.addProperty("theta", Math.round(p.getTheta() * 100.0) / 100.0);
                                placesMap.put(key, item);
                            }
                        }
                        Log.i(TAG, "Loaded " + placesMap.size() + " places from CoreService getPlaceList() fallback for active map: " + actualMap);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "getPlaceList from CoreService failed: " + t.getMessage());
            }
        }

        JsonArray placesArray = new JsonArray();
        for (JsonObject p : placesMap.values()) {
            placesArray.add(p);
        }
        root.add("places", placesArray);

        return root;
    }

    public static void switchMap(final String mapName, final CommandListener listener) {
        try {
            File mapDir = resolveMapDir(mapName);
            final String actualMap = (mapDir != null && mapDir.exists()) ? mapDir.getName() : mapName;
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().switchMap(0, actualMap, new CommandListener() {
                    @Override
                    public void onResult(int result, String message) {
                        boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                        if (ok) {
                            try {
                                if (GlobalData.getInstance() != null) {
                                    GlobalData.getInstance().setCurrentMapName(actualMap);
                                }
                            } catch (Throwable ignored) {}
                            notifyMapPlacesChanged(actualMap);
                        }
                        if (listener != null) {
                            listener.onResult(result, message);
                        }
                    }
                });
            } else if (listener != null) {
                listener.onResult(Definition.ACTION_RESPONSE_ALREADY_RUN, "CoreService not connected");
            }
        } catch (Throwable t) {
            Log.e(TAG, "switchMap error: " + t.getMessage());
            if (listener != null) {
                listener.onResult(-1, t.getMessage());
            }
        }
    }

    public static boolean saveOrUpdatePlaceToDisk(String currentMap, String placeName, float x, float y, float theta) {
        if (TextUtils.isEmpty(currentMap) || TextUtils.isEmpty(placeName)) return false;
        try {
            File mapDir = resolveMapDir(currentMap);
            if (!mapDir.exists()) {
                mapDir.mkdirs();
            }
            File placeFile = new File(mapDir, "place.json");
            JSONArray arr = new JSONArray();
            if (placeFile.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(placeFile), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    if (sb.length() > 0) {
                        arr = new JSONArray(sb.toString());
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed reading existing place.json: " + t.getMessage());
                }
            }

            boolean updated = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String existingName = "";
                if (item.has("name")) {
                    JSONObject nameObj = item.optJSONObject("name");
                    if (nameObj != null) {
                        if (nameObj.has("vi_VN")) existingName = nameObj.getString("vi_VN");
                        else if (nameObj.has("en_US")) existingName = nameObj.getString("en_US");
                        else if (nameObj.has("zh_CN")) existingName = nameObj.getString("zh_CN");
                    } else {
                        existingName = item.optString("name", "");
                    }
                }
                if (placeName.equalsIgnoreCase(existingName)) {
                    item.put("x", x);
                    item.put("y", y);
                    item.put("theta", theta);
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                JSONObject newItem = new JSONObject();
                newItem.put("x", x);
                newItem.put("y", y);
                newItem.put("theta", theta);
                JSONObject nameObj = new JSONObject();
                nameObj.put("vi_VN", placeName);
                nameObj.put("en_US", placeName);
                nameObj.put("zh_CN", placeName);
                newItem.put("name", nameObj);
                arr.put(newItem);
            }

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(placeFile), StandardCharsets.UTF_8);
            writer.write(arr.toString(2));
            writer.flush();
            writer.close();
            Log.i(TAG, "Successfully persisted place '" + placeName + "' to " + placeFile.getAbsolutePath());
            notifyMapPlacesChanged(currentMap);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "saveOrUpdatePlaceToDisk error: " + t.getMessage(), t);
            return false;
        }
    }

    public static boolean deletePlaceFromDisk(String currentMap, String placeName) {
        if (TextUtils.isEmpty(currentMap) || TextUtils.isEmpty(placeName)) return false;
        try {
            File mapDir = resolveMapDir(currentMap);
            File placeFile = new File(mapDir, "place.json");
            if (!placeFile.exists()) return true;

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(placeFile), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            JSONArray newArr = new JSONArray();
            boolean found = false;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String existingName = "";
                if (item.has("name")) {
                    JSONObject nameObj = item.optJSONObject("name");
                    if (nameObj != null) {
                        if (nameObj.has("vi_VN")) existingName = nameObj.getString("vi_VN");
                        else if (nameObj.has("en_US")) existingName = nameObj.getString("en_US");
                        else if (nameObj.has("zh_CN")) existingName = nameObj.getString("zh_CN");
                    } else {
                        existingName = item.optString("name", "");
                    }
                }
                if (placeName.trim().equalsIgnoreCase(existingName.trim()) ||
                    normalizeMapName(placeName).equalsIgnoreCase(normalizeMapName(existingName))) {
                    found = true;
                    continue; // Skip this one to delete
                }
                newArr.put(item);
            }

            if (found) {
                OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(placeFile), StandardCharsets.UTF_8);
                writer.write(newArr.toString(2));
                writer.flush();
                writer.close();
                Log.i(TAG, "Successfully deleted place '" + placeName + "' from " + placeFile.getAbsolutePath());
                notifyMapPlacesChanged(currentMap);
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "deletePlaceFromDisk error: " + t.getMessage(), t);
            return false;
        }
    }

    public static JsonObject getPlaceByName(String mapName, String placeName) {
        if (TextUtils.isEmpty(placeName)) return null;
        try {
            JsonObject mapData = buildMapDataJson(mapName);
            if (mapData != null && mapData.has("places")) {
                com.google.gson.JsonArray places = mapData.getAsJsonArray("places");
                for (com.google.gson.JsonElement el : places) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("name") && placeName.equalsIgnoreCase(p.get("name").getAsString())) {
                        return p;
                    }
                }
                // Try fuzzy/case-insensitive contains match for charging/standby
                for (com.google.gson.JsonElement el : places) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("name")) {
                        String name = p.get("name").getAsString().toLowerCase();
                        String target = placeName.toLowerCase();
                        if (name.contains(target) || target.contains(name)) {
                            return p;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getPlaceByName error: " + t.getMessage());
        }
        return null;
    }
}

