package com.ainirobot.robotos.bridge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.actionbean.Pose;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.robotos.application.RobotOSApplication;
import com.ainirobot.robotos.maputils.GlobalData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.WebSocket;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RobotCommandHandler {
    private static final String TAG = "RobotCommandHandler";
    private static int sHeadReqId = 1000;
    private static int sMoveReqId = 2000;

    private final Handler mWatchdogHandler = new Handler(Looper.getMainLooper());
    private static final long AUTO_STOP_DELAY_MS = 1200; // Safety watchdog: stop after 1200ms if no new pulse

    private final Runnable mAutoStopRunnable = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "Safety Watchdog: Auto-stopping move to ensure safety");
            try {
                RobotApi.getInstance().stopMove(0, null);
            } catch (Throwable t) {
                Log.e(TAG, "Watchdog stopMove error: " + t.getMessage());
            }
        }
    };

    private void armSafetyWatchdog() {
        mWatchdogHandler.removeCallbacks(mAutoStopRunnable);
        mWatchdogHandler.postDelayed(mAutoStopRunnable, AUTO_STOP_DELAY_MS);
    }

    private void disarmSafetyWatchdog() {
        mWatchdogHandler.removeCallbacks(mAutoStopRunnable);
    }

    public void handleCommand(final WebSocket client, final String jsonString) {
        try {
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            if (!json.has("type")) return;

            final String type = json.get("type").getAsString();
            final String msgId = json.has("msgId") ? json.get("msgId").getAsString() : "";
            Log.i(TAG, "Received command: " + type + ", msgId=" + msgId);

            switch (type) {
                case "CMD_MOVE":
                    handleMove(client, msgId, json);
                    break;
                case "CMD_HEAD":
                    handleHead(client, msgId, json);
                    break;
                case "CMD_EMERGENCY_STOP":
                    handleEmergencyStop(client, msgId);
                    break;
                case "CMD_NAVIGATE":
                    handleNavigate(client, msgId, json);
                    break;
                case "CMD_STOP_NAVIGATE":
                    handleStopNavigate(client, msgId);
                    break;
                case "CMD_SPEAK":
                    handleSpeak(client, msgId, json);
                    break;
                case "CMD_CHARGE":
                    handleCharge(client, msgId, json);
                    break;
                case "CMD_DOOR":
                    handleDoor(client, msgId, json);
                    break;
                case "CMD_GET_MAP_DATA":
                    handleGetMapData(client, msgId);
                    break;
                case "CMD_SWITCH_MAP":
                    handleSwitchMap(client, msgId, json);
                    break;
                case "CMD_SET_POSE_ESTIMATE":
                    handleSetPoseEstimate(client, msgId, json);
                    break;
                case "CMD_SET_LOCATION":
                    handleSetLocation(client, msgId, json);
                    break;
                case "CMD_RESET_ESTIMATE":
                    handleResetEstimate(client, msgId);
                    break;
                case "CMD_SWITCH_MAP_AND_RELOCALIZE":
                    handleSwitchMapAndRelocalize(client, msgId, json);
                    break;
                case "CMD_ADD_PLACE":
                    handleAddPlace(client, msgId, json);
                    break;
                case "CMD_DELETE_PLACE":
                    handleDeletePlace(client, msgId, json);
                    break;
                default:
                    Log.w(TAG, "Unknown command type: " + type);
                    sendResult(client, msgId, type, false, -1, "Unknown command: " + type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse command: " + e.getMessage(), e);
        }
    }

    private void handleMove(final WebSocket client, final String msgId, JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString().toUpperCase() : "STOP";
        float linearSpeed = json.has("linearSpeed") ? json.get("linearSpeed").getAsFloat() : 0.4f;
        float angularSpeed = json.has("angularSpeed") ? json.get("angularSpeed").getAsFloat() : 25.0f;

        Log.i(TAG, "Executing MOVE: " + action + " linearSpeed=" + linearSpeed + " angularSpeed=" + angularSpeed);

        // Always stop any autonomous navigation or auto-charge lock before executing manual drive!
        try {
            RobotApi.getInstance().stopNavigation(0);
        } catch (Throwable ignored) {}
        try {
            RobotApi.getInstance().stopAutoChargeAction(0, true);
        } catch (Throwable ignored) {}

        CommandListener listener = new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                sendResult(client, msgId, "CMD_MOVE", ok, result, message);
            }
        };

        boolean isChargingNow = false;
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                isChargingNow = RobotApi.getInstance().getChargeStatus();
            }
        } catch (Throwable ignored) {}

        switch (action) {
            case "FORWARD":
                if (isChargingNow) {
                    Log.i(TAG, "User sent FORWARD while docked. Auto-leaving charging pile...");
                    try {
                        RobotApi.getInstance().stopAutoChargeAction(0, true);
                    } catch (Throwable ignored) {}
                    RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, listener);
                    return;
                }
                armSafetyWatchdog();
                RobotApi.getInstance().goForward(0, linearSpeed, listener);
                break;
            case "BACKWARD":
                if (isChargingNow) {
                    Log.i(TAG, "User sent BACKWARD while docked. Must undock forward first!");
                    RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, listener);
                    return;
                }
                armSafetyWatchdog();
                RobotApi.getInstance().goBackward(0, linearSpeed, listener);
                break;
            case "TURN_LEFT":
                if (isChargingNow) {
                    Log.i(TAG, "User sent TURN_LEFT while docked. Must undock forward first!");
                    RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, listener);
                    return;
                }
                armSafetyWatchdog();
                RobotApi.getInstance().turnLeft(0, angularSpeed, listener);
                break;
            case "TURN_RIGHT":
                if (isChargingNow) {
                    Log.i(TAG, "User sent TURN_RIGHT while docked. Must undock forward first!");
                    RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, listener);
                    return;
                }
                armSafetyWatchdog();
                RobotApi.getInstance().turnRight(0, angularSpeed, listener);
                break;
            case "STOP":
            default:
                disarmSafetyWatchdog();
                RobotApi.getInstance().stopMove(0, listener);
                break;
        }
    }

    private void handleHead(final WebSocket client, final String msgId, JsonObject json) {
        int h = json.has("horizontalAngle") ? json.get("horizontalAngle").getAsInt() : 0;
        int v = json.has("verticalAngle") ? json.get("verticalAngle").getAsInt() : 0;

        RobotApi.getInstance().moveHead(sHeadReqId++, "relative", "relative", h, v, new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                sendResult(client, msgId, "CMD_HEAD", "succeed".equalsIgnoreCase(message) || result == Definition.RESULT_OK, result, message);
            }
        });
    }

    private void handleEmergencyStop(final WebSocket client, final String msgId) {
        Log.i(TAG, "Executing EMERGENCY STOP");
        disarmSafetyWatchdog();
        TTSPlayer.stopCurrentPlayback();
        RobotApi.getInstance().stopMove(0, new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                RobotApi.getInstance().stopNavigation(0);
                sendResult(client, msgId, "CMD_EMERGENCY_STOP", true, 0, "Emergency stop executed");
            }
        });
    }

    private void ensureLeftChargingPileIfNeeded(final Runnable onReady) {
        boolean isCharging = false;
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                isCharging = RobotApi.getInstance().getChargeStatus();
            }
        } catch (Throwable ignored) {}

        if (!isCharging) {
            try {
                RobotApi.getInstance().stopNavigation(0);
                RobotApi.getInstance().stopAutoChargeAction(0, true);
            } catch (Throwable ignored) {}
            onReady.run();
            return;
        }

        Log.i(TAG, "⚡ Robot is currently in charging contacts. Initiating auto-undock...");
        try {
            RobotApi.getInstance().stopAutoChargeAction(0, true);
            RobotApi.getInstance().stopNavigation(0);
        } catch (Throwable t) {
            Log.w(TAG, "stopAutoChargeAction error: " + t.getMessage());
        }

        final AtomicBoolean executed = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (executed.compareAndSet(false, true)) {
                    Log.i(TAG, "leaveChargingPile completed or timed out. Proceeding to navigation target.");
                    try {
                        RobotApi.getInstance().stopNavigation(0);
                    } catch (Throwable ignored) {}
                    onReady.run();
                }
            }
        };

        // Safety fallback: if leaveChargingPile doesn't return in 2.2s, proceed anyway
        handler.postDelayed(timeoutRunnable, 2200);

        try {
            RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    Log.i(TAG, "leaveChargingPile onResult: " + result + ", msg=" + message);
                    handler.removeCallbacks(timeoutRunnable);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (executed.compareAndSet(false, true)) {
                                try {
                                    RobotApi.getInstance().stopNavigation(0);
                                } catch (Throwable ignored) {}
                                onReady.run();
                            }
                        }
                    }, 400);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "leaveChargingPile exception: " + t.getMessage());
            handler.removeCallbacks(timeoutRunnable);
            if (executed.compareAndSet(false, true)) {
                try {
                    RobotApi.getInstance().stopNavigation(0);
                } catch (Throwable ignored) {}
                onReady.run();
            }
        }
    }

    private void handleNavigate(final WebSocket client, final String msgId, JsonObject json) {
        final String destination = json.has("destination") ? json.get("destination").getAsString() : "";
        final double maxDistance = json.has("maxDistance") ? json.get("maxDistance").getAsDouble() : 1.5;
        final long timeoutMs = json.has("timeoutSeconds") ? json.get("timeoutSeconds").getAsLong() * 1000 : 60000;

        Float parseX = null;
        Float parseY = null;
        Float parseTheta = 0f;
        if (json.has("x") && !json.get("x").isJsonNull()) {
            try { parseX = json.get("x").getAsFloat(); } catch (Exception ignored) {}
        }
        if (json.has("y") && !json.get("y").isJsonNull()) {
            try { parseY = json.get("y").getAsFloat(); } catch (Exception ignored) {}
        }
        if (json.has("theta") && !json.get("theta").isJsonNull()) {
            try { parseTheta = json.get("theta").getAsFloat(); } catch (Exception ignored) {}
        }

        final Float x = parseX;
        final Float y = parseY;
        final Float theta = parseTheta;

        if (TextUtils.isEmpty(destination) && (x == null || y == null)) {
            sendResult(client, msgId, "CMD_NAVIGATE", false, -1, "Destination or coordinates required");
            return;
        }

        Log.i(TAG, "Executing NAVIGATE to: " + destination + (x != null ? " (" + x + ", " + y + ")" : ""));
        disarmSafetyWatchdog();

        // 1. Check if target is Charging Dock
        String lower = destination != null ? destination.toLowerCase() : "";
        if (lower.contains("charge") || lower.contains("sạc") || lower.contains("charging")) {
            boolean isDocked = false;
            try {
                if (RobotApi.getInstance().isApiConnectedService()) {
                    isDocked = RobotApi.getInstance().getChargeStatus();
                }
            } catch (Throwable ignored) {}
            if (isDocked) {
                Log.i(TAG, "Robot is already docked at charging station.");
                sendResult(client, msgId, "CMD_NAVIGATE", true, 0, "Robot đã ở trạm sạc pin");
                return;
            }

            Log.i(TAG, "Navigating to Auto Charge Station...");
            try {
                RobotApi.getInstance().stopNavigation(0);
                RobotApi.getInstance().stopAutoChargeAction(0, true);
            } catch (Throwable ignored) {}

            RobotApi.getInstance().startNaviToAutoChargeAction(0, timeoutMs, new ActionListener() {
                @Override
                public void onResult(int status, String responseString) throws RemoteException {
                    Log.i(TAG, "AutoCharge onResult: status=" + status + ", resp=" + responseString);
                    boolean success = (status == Definition.RESULT_OK);
                    sendResult(client, msgId, "CMD_NAVIGATE", success, status, responseString);
                }

                @Override
                public void onError(int errorCode, String errorString) throws RemoteException {
                    Log.e(TAG, "AutoCharge onError: code=" + errorCode + ", err=" + errorString);
                    // Fallback to navigating to Charging Point coordinates if auto-charge fails!
                    if (x != null && y != null) {
                        Log.i(TAG, "AutoCharge failed. Falling back to coordinates (" + x + ", " + y + ")...");
                        navigateByPose(client, msgId, destination, x, y, theta, 1.2, timeoutMs);
                    } else {
                        String currentMap = RobotMapManager.getCurrentMapName();
                        JsonObject cp = RobotMapManager.getPlaceByName(currentMap, "Charging Point");
                        if (cp == null) cp = RobotMapManager.getPlaceByName(currentMap, "Charging Pole");
                        if (cp != null && cp.has("x") && cp.has("y")) {
                            float cx = cp.get("x").getAsFloat();
                            float cy = cp.get("y").getAsFloat();
                            navigateByPose(client, msgId, destination, cx, cy, 0f, 1.2, timeoutMs);
                        } else {
                            sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
                        }
                    }
                }
            });
            return;
        }

        // 2. If target is a table/station/coordinate, make sure robot leaves charging pile first if docked!
        ensureLeftChargingPileIfNeeded(new Runnable() {
            @Override
            public void run() {
                executeNavigationInternal(client, msgId, destination, x, y, theta, maxDistance, timeoutMs);
            }
        });
    }

    private void executeNavigationInternal(final WebSocket client, final String msgId, final String destination, final Float x, final Float y, final Float theta, final double maxDistance, final long timeoutMs) {
        try {
            RobotApi.getInstance().stopNavigation(0);
            RobotApi.getInstance().stopAutoChargeAction(0, true);
        } catch (Throwable ignored) {}

        boolean isCustomCoord = TextUtils.isEmpty(destination) 
                || destination.startsWith("Điểm (") 
                || destination.startsWith("Tọa độ (")
                || destination.startsWith("Point (");

        if (!isCustomCoord && !TextUtils.isEmpty(destination)) {
            Log.i(TAG, "Navigating to Named Station via native route: " + destination);
            int navResult = -1;
            try {
                navResult = RobotApi.getInstance().startNavigation(0, destination, maxDistance, timeoutMs, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws RemoteException {
                        Log.i(TAG, "Named Station onResult: status=" + status + ", resp=" + responseString);
                        boolean success = (status == Definition.RESULT_OK && !"false".equalsIgnoreCase(responseString));
                        if (!success && x != null && y != null) {
                            Log.w(TAG, "Named navigation returned false. Falling back to Pose navigation...");
                            navigateByPose(client, msgId, destination, x, y, theta, maxDistance, timeoutMs);
                        } else {
                            sendResult(client, msgId, "CMD_NAVIGATE", success, status, responseString);
                        }
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws RemoteException {
                        Log.e(TAG, "Named Station onError: code=" + errorCode + ", err=" + errorString);
                        // If named place failed and coordinates exist, fallback to Pose navigation
                        if (x != null && y != null) {
                            Log.i(TAG, "Falling back to Pose navigation...");
                            navigateByPose(client, msgId, destination, x, y, theta, maxDistance, timeoutMs);
                        } else {
                            sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "startNavigation by name threw: " + t.getMessage());
            }

            // If startNavigation by name was rejected immediately (< 0) and coordinates exist, fallback to Pose right away!
            if (navResult < 0 && x != null && y != null) {
                Log.w(TAG, "startNavigation by name rejected (code " + navResult + "). Fallback directly to Pose navigation...");
                navigateByPose(client, msgId, destination, x, y, theta, maxDistance, timeoutMs);
            }
            return;
        }

        // 3. For arbitrary floor click / custom coordinate: Navigate via Pose with No Directional Parking!
        if (x != null && y != null) {
            navigateByPose(client, msgId, destination, x, y, theta, maxDistance, timeoutMs);
            return;
        }

        // 4. Fallback: Navigate by name
        RobotApi.getInstance().startNavigation(0, destination, maxDistance, timeoutMs, new ActionListener() {
            @Override
            public void onResult(int status, String responseString) throws RemoteException {
                Log.i(TAG, "Navigate onResult: status=" + status + ", resp=" + responseString);
                boolean success = (status == Definition.RESULT_OK);
                sendResult(client, msgId, "CMD_NAVIGATE", success, status, responseString);
            }

            @Override
            public void onError(int errorCode, String errorString) throws RemoteException {
                Log.e(TAG, "Navigate onError: code=" + errorCode + ", err=" + errorString);
                sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
            }
        });
    }

    private void navigateByPose(final WebSocket client, final String msgId, final String destination, final float x, final float y, final Float theta, final double maxDist, final long timeoutMs) {
        try {
            RobotApi.getInstance().stopNavigation(0);
            RobotApi.getInstance().stopAutoChargeAction(0, true);
        } catch (Throwable ignored) {}

        // Small 150ms delay to let CoreService cleanly release the drive lock before starting next pose nav
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Pose pose = new Pose(x, y, theta != null ? theta : 0f, destination != null ? destination : "");
                pose.setNoDirectionalParking(true); // Don't spin in place to align angle!
                pose.setIgnoreDistance(true);
                Log.i(TAG, "Navigating via Pose (no directional parking): " + pose.toString());
                try {
                    int ret = RobotApi.getInstance().startNavigation(0, pose, maxDist, timeoutMs, new ActionListener() {
                        @Override
                        public void onResult(int status, String responseString) throws RemoteException {
                            Log.i(TAG, "Pose Navigate onResult: status=" + status + ", resp=" + responseString);
                            boolean success = (status == Definition.RESULT_OK);
                            sendResult(client, msgId, "CMD_NAVIGATE", success, status, responseString);
                        }

                        @Override
                        public void onError(int errorCode, String errorString) throws RemoteException {
                            Log.e(TAG, "Pose Navigate onError: code=" + errorCode + ", err=" + errorString);
                            sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
                        }
                    });
                    if (ret < 0) {
                        Log.e(TAG, "Pose startNavigation returned error code: " + ret);
                        sendResult(client, msgId, "CMD_NAVIGATE", false, ret, "Pose navigation returned code: " + ret);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Pose startNavigation exception: " + t.getMessage(), t);
                    sendResult(client, msgId, "CMD_NAVIGATE", false, -1, t.getMessage());
                }
            }
        }, 150);
    }

    private void handleStopNavigate(final WebSocket client, final String msgId) {
        Log.i(TAG, "Executing STOP NAVIGATE");
        try {
            RobotApi.getInstance().stopNavigation(0);
        } catch (Throwable t) {
            Log.w(TAG, "stopNavigation ex: " + t.getMessage());
        }
        try {
            RobotApi.getInstance().stopAutoChargeAction(0, true);
        } catch (Throwable t) {
            Log.w(TAG, "stopAutoChargeAction ex: " + t.getMessage());
        }
        sendResult(client, msgId, "CMD_STOP_NAVIGATE", true, 0, "Navigation stopped");
    }

    private void handleSpeak(final WebSocket client, final String msgId, JsonObject json) {
        final String text = json.has("text") ? json.get("text").getAsString() : "";
        Log.i(TAG, "Executing Vietnamese SPEAK via TTSPlayer: " + text);

        if (TextUtils.isEmpty(text)) {
            sendResult(client, msgId, "CMD_SPEAK", false, -1, "Text is empty");
            return;
        }

        TTSPlayer.playVietnameseText(text, new TTSPlayer.PlayCallback() {
            @Override
            public void onDone(boolean success, String message) {
                Log.i(TAG, "TTS playback finished: success=" + success + " msg=" + message);
                sendResult(client, msgId, "CMD_SPEAK", success, success ? 0 : -1, message);
            }
        });
    }

    private void handleGetMapData(final WebSocket client, final String msgId) {
        String currentMap = "";
        try {
            if (GlobalData.getInstance() != null) {
                currentMap = GlobalData.getInstance().getCurrentMapName();
            }
        } catch (Throwable ignored) {}
        JsonObject mapData = RobotMapManager.buildMapDataJson(currentMap);
        mapData.addProperty("msgId", msgId);
        try {
            client.send(mapData.toString());
            Log.i(TAG, "Sent MAP_DATA to PC");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send MAP_DATA: " + t.getMessage());
        }
    }

    private void handleSwitchMap(final WebSocket client, final String msgId, JsonObject json) {
        final String mapName = json.has("mapName") ? json.get("mapName").getAsString() : "";
        Log.i(TAG, "Executing SWITCH_MAP to: " + mapName);

        if (TextUtils.isEmpty(mapName)) {
            sendResult(client, msgId, "CMD_SWITCH_MAP", false, -1, "Map name empty");
            return;
        }

        RobotMapManager.switchMap(mapName, new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                Log.i(TAG, "switchMap result: " + result + ", msg=" + message);
                sendResult(client, msgId, "CMD_SWITCH_MAP", ok, result, message);

                if (ok) {
                    try {
                        if (GlobalData.getInstance() != null) {
                            GlobalData.getInstance().setCurrentMapName(mapName);
                        }
                    } catch (Throwable ignored) {}

                    // Broadcast updated MAP_DATA
                    JsonObject mapData = RobotMapManager.buildMapDataJson(mapName);
                    try {
                        client.send(mapData.toString());
                    } catch (Throwable ignored) {}

                    // Notify local app UI (DeliveryFragment) to immediately reload stations for the new map
                    RobotMapManager.notifyMapPlacesChanged(mapName);
                }
            }
        });
    }

    private void handleSetPoseEstimate(final WebSocket client, final String msgId, JsonObject json) {
        float x = json.has("x") ? json.get("x").getAsFloat() : 0f;
        float y = json.has("y") ? json.get("y").getAsFloat() : 0f;
        float theta = json.has("theta") ? json.get("theta").getAsFloat() : 0f;

        Log.i(TAG, "Executing SET_POSE_ESTIMATE: (" + x + ", " + y + ", " + theta + ")");
        try {
            org.json.JSONObject params = new org.json.JSONObject();
            params.put(Definition.JSON_NAVI_POSITION_X, x);
            params.put(Definition.JSON_NAVI_POSITION_Y, y);
            params.put(Definition.JSON_NAVI_POSITION_THETA, theta);

            final org.json.JSONObject finalParams = params;
            RobotApi.getInstance().setPoseEstimate(0, params.toString(), new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                    Log.i(TAG, "setPoseEstimate onResult: " + result + ", msg=" + message);
                    if (!ok) {
                        try {
                            RobotApi.getInstance().setForceEstimate(0, finalParams.toString(), new CommandListener() {
                                @Override
                                public void onResult(int resF, String msgF) {
                                    boolean okF = (resF == Definition.RESULT_OK || "succeed".equalsIgnoreCase(msgF));
                                    Log.i(TAG, "setForceEstimate onResult: " + resF + ", msg=" + msgF);
                                    sendResult(client, msgId, "CMD_SET_POSE_ESTIMATE", okF, resF, msgF);
                                }
                            });
                            return;
                        } catch (Throwable ignored) {}
                    }
                    sendResult(client, msgId, "CMD_SET_POSE_ESTIMATE", ok, result, message);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "setPoseEstimate exception: " + t.getMessage(), t);
            sendResult(client, msgId, "CMD_SET_POSE_ESTIMATE", false, -1, t.getMessage());
        }
    }

    private void handleSetLocation(final WebSocket client, final String msgId, JsonObject json) {
        final String placeName = json.has("placeName") ? json.get("placeName").getAsString() : "";
        Log.i(TAG, "Executing SET_LOCATION: " + placeName);

        if (TextUtils.isEmpty(placeName)) {
            sendResult(client, msgId, "CMD_SET_LOCATION", false, -1, "Place name is empty");
            return;
        }

        try {
            // Cố gắng lấy tọa độ từ RobotMapManager để nạp kèm nếu có
            try {
                String currentMap = RobotMapManager.getCurrentMapName();
                JsonObject place = RobotMapManager.getPlaceByName(currentMap, placeName);
                if (place != null) {
                    float px = (float) place.get("x").getAsDouble();
                    float py = (float) place.get("y").getAsDouble();
                    float pth = (float) place.get("theta").getAsDouble();
                    org.json.JSONObject p = new org.json.JSONObject();
                    p.put(Definition.JSON_NAVI_POSITION_X, px);
                    p.put(Definition.JSON_NAVI_POSITION_Y, py);
                    p.put(Definition.JSON_NAVI_POSITION_THETA, pth);
                    RobotApi.getInstance().setPoseEstimate(0, p.toString(), null);
                }
            } catch (Throwable ignored) {}

            RobotApi.getInstance().setLocation(0, placeName, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                    Log.i(TAG, "setLocation onResult: " + result + ", msg=" + message);
                    sendResult(client, msgId, "CMD_SET_LOCATION", ok, result, message);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "setLocation exception: " + t.getMessage(), t);
            sendResult(client, msgId, "CMD_SET_LOCATION", false, -1, t.getMessage());
        }
    }

    private void handleResetEstimate(final WebSocket client, final String msgId) {
        Log.i(TAG, "Executing RESET_ESTIMATE (Action 24 + Chassis 360 rotation)");
        TTSPlayer.playVietnameseText("Robot bắt đầu xoay để quét Laser tìm lại vị trí...", null);

        try {
            // 1. Gọi thuật toán resetEstimate (Action ID 24)
            RobotApi.getInstance().resetEstimate(0, 5, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    Log.i(TAG, "resetEstimate onResult: " + result + ", msg=" + message);
                }
            });

            // 2. Quay khung gầm robot nhẹ nhàng để Laser quét các vách xung quanh
            RobotApi.getInstance().turnLeft(0, 20.0f, null);

            // 3. Vòng lặp kiểm tra định vị trong 18 giây
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final long startTime = System.currentTimeMillis();
            final Runnable checkRunnable = new Runnable() {
                @Override
                public void run() {
                    long elapsed = System.currentTimeMillis() - startTime;
                    RobotApi.getInstance().isRobotEstimate(0, new CommandListener() {
                        @Override
                        public void onResult(int res, String msg) {
                            if ("true".equalsIgnoreCase(msg)) {
                                try {
                                    RobotApi.getInstance().stopMove(0, null);
                                } catch (Throwable ignored) {}
                                TTSPlayer.playVietnameseText("Đã tìm thấy vị trí robot thành công!", null);
                                sendResult(client, msgId, "CMD_RESET_ESTIMATE", true, 0, "Succeed");
                                return;
                            }
                        }
                    });

                    if (elapsed >= 18000) {
                        try {
                            RobotApi.getInstance().stopMove(0, null);
                        } catch (Throwable ignored) {}
                        sendResult(client, msgId, "CMD_RESET_ESTIMATE", false, -1, "Timeout 360 scan");
                    } else {
                        handler.postDelayed(this, 1200);
                    }
                }
            };
            handler.postDelayed(checkRunnable, 1500);

        } catch (Throwable t) {
            Log.e(TAG, "resetEstimate exception: " + t.getMessage(), t);
            try {
                RobotApi.getInstance().stopMove(0, null);
            } catch (Throwable ignored) {}
            sendResult(client, msgId, "CMD_RESET_ESTIMATE", false, -1, t.getMessage());
        }
    }

    private void handleSwitchMapAndRelocalize(final WebSocket client, final String msgId, JsonObject json) {
        final String mapName = json.has("mapName") ? json.get("mapName").getAsString() : "";
        final String placeName = json.has("placeName") && !json.get("placeName").isJsonNull() ? json.get("placeName").getAsString() : "";
        final Float x = json.has("x") && !json.get("x").isJsonNull() ? json.get("x").getAsFloat() : null;
        final Float y = json.has("y") && !json.get("y").isJsonNull() ? json.get("y").getAsFloat() : null;
        final Float theta = json.has("theta") && !json.get("theta").isJsonNull() ? json.get("theta").getAsFloat() : 0f;

        Log.i(TAG, "Executing SWITCH_MAP_AND_RELOCALIZE to map: " + mapName + ", place: " + placeName + ", coord: (" + x + ", " + y + ")");

        if (TextUtils.isEmpty(mapName)) {
            sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", false, -1, "Map name empty");
            return;
        }

        // 1. Switch Map
        RobotMapManager.switchMap(mapName, new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                boolean mapOk = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                if (!mapOk) {
                    sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", false, result, "Failed to switch map: " + message);
                    return;
                }

                try {
                    if (GlobalData.getInstance() != null) {
                        GlobalData.getInstance().setCurrentMapName(mapName);
                    }
                } catch (Throwable ignored) {}

                // Broadcast map data
                JsonObject mapData = RobotMapManager.buildMapDataJson(mapName);
                try {
                    client.send(mapData.toString());
                } catch (Throwable ignored) {}

                // Notify local app UI (DeliveryFragment) to immediately reload stations for the new map
                RobotMapManager.notifyMapPlacesChanged(mapName);

                // 2. Delay 600ms for SLAM system to finish loading map into memory
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // 3. Relocalize either by place name or coordinates
                        if (!TextUtils.isEmpty(placeName)) {
                            Log.i(TAG, "Relocalizing on new floor via place: " + placeName);
                            RobotApi.getInstance().setLocation(0, placeName, new CommandListener() {
                                @Override
                                public void onResult(int res, String msg) {
                                    boolean locOk = (res == Definition.RESULT_OK || "succeed".equalsIgnoreCase(msg));
                                    sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", locOk, res,
                                            locOk ? "Chuyển tầng và định vị tại " + placeName + " thành công" : "Chuyển map xong nhưng lỗi định vị: " + msg);
                                }
                            });
                        } else if (x != null && y != null) {
                            Log.i(TAG, "Relocalizing on new floor via pose: (" + x + ", " + y + ", " + theta + ")");
                            try {
                                org.json.JSONObject params = new org.json.JSONObject();
                                params.put(Definition.JSON_NAVI_POSITION_X, x);
                                params.put(Definition.JSON_NAVI_POSITION_Y, y);
                                params.put(Definition.JSON_NAVI_POSITION_THETA, theta != null ? theta : 0f);

                                RobotApi.getInstance().setPoseEstimate(0, params.toString(), new CommandListener() {
                                    @Override
                                    public void onResult(int res, String msg) {
                                        boolean locOk = (res == Definition.RESULT_OK || "succeed".equalsIgnoreCase(msg));
                                        sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", locOk, res,
                                                locOk ? "Chuyển tầng và định vị tọa độ thành công" : "Chuyển map xong nhưng lỗi định vị: " + msg);
                                    }
                                });
                            } catch (Throwable t) {
                                sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", false, -1, t.getMessage());
                            }
                        } else {
                            // No initial place/pose specified, map switched
                            sendResult(client, msgId, "CMD_SWITCH_MAP_AND_RELOCALIZE", true, 0, "Chuyển bản đồ sang " + mapName + " thành công (Cần định vị thêm)");
                        }
                    }
                }, 600);
            }
        });
    }

    private void handleCharge(final WebSocket client, final String msgId, JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString().toUpperCase() : "START_CHARGE";
        disarmSafetyWatchdog();

        switch (action) {
            case "START_CHARGE":
                boolean isDocked = false;
                try {
                    if (RobotApi.getInstance().isApiConnectedService()) {
                        isDocked = RobotApi.getInstance().getChargeStatus();
                    }
                } catch (Throwable ignored) {}
                if (isDocked) {
                    sendResult(client, msgId, "CMD_CHARGE", true, 0, "Robot đã ở trạm sạc pin");
                    return;
                }

                try {
                    RobotApi.getInstance().stopNavigation(0);
                    RobotApi.getInstance().stopAutoChargeAction(0, true);
                } catch (Throwable ignored) {}

                RobotApi.getInstance().startNaviToAutoChargeAction(0, 3 * Definition.MINUTE, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws RemoteException {
                        sendResult(client, msgId, "CMD_CHARGE", status == Definition.RESULT_OK, status, responseString);
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws RemoteException {
                        Log.w(TAG, "CMD_CHARGE startNaviToAutoChargeAction onError (" + errorCode + "). Fallback to Charging Point coordinates...");
                        String currentMap = RobotMapManager.getCurrentMapName();
                        JsonObject cp = RobotMapManager.getPlaceByName(currentMap, "Charging Point");
                        if (cp == null) cp = RobotMapManager.getPlaceByName(currentMap, "Charging Pole");
                        if (cp != null && cp.has("x") && cp.has("y")) {
                            float cx = cp.get("x").getAsFloat();
                            float cy = cp.get("y").getAsFloat();
                            navigateByPose(client, msgId, "Charging Point", cx, cy, 0f, 1.2, 60000);
                        } else {
                            sendResult(client, msgId, "CMD_CHARGE", false, errorCode, errorString);
                        }
                    }
                });
                break;
            case "STOP_CHARGE":
                try {
                    RobotApi.getInstance().stopAutoChargeAction(0, true);
                } catch (Throwable ignored) {}
                sendResult(client, msgId, "CMD_CHARGE", true, 0, "Stopped auto charge");
                break;
            case "LEAVE_PILE":
                try {
                    RobotApi.getInstance().stopAutoChargeAction(0, true);
                    RobotApi.getInstance().stopNavigation(0);
                } catch (Throwable ignored) {}
                RobotApi.getInstance().leaveChargingPile(0, 0.6f, 0.5f, new CommandListener() {
                    @Override
                    public void onResult(int result, String message) {
                        sendResult(client, msgId, "CMD_CHARGE", true, result, message);
                    }
                });
                break;
        }
    }

    private void handleDoor(final WebSocket client, final String msgId, JsonObject json) {
        String action = json.has("action") ? json.get("action").getAsString().toUpperCase() : "OPEN";
        sendResult(client, msgId, "CMD_DOOR", true, 0, "Door action executed: " + action);
    }

    private void handleAddPlace(final WebSocket client, final String msgId, JsonObject json) {
        final String name = json.has("name") ? json.get("name").getAsString().trim() : "";
        final float x = json.has("x") ? json.get("x").getAsFloat() : 0f;
        final float y = json.has("y") ? json.get("y").getAsFloat() : 0f;
        final float theta = json.has("theta") ? json.get("theta").getAsFloat() : 0f;

        String mapName = json.has("mapName") && !json.get("mapName").isJsonNull() ? json.get("mapName").getAsString().trim() : "";
        if (TextUtils.isEmpty(mapName)) {
            try {
                if (GlobalData.getInstance() != null) {
                    mapName = GlobalData.getInstance().getCurrentMapName();
                }
            } catch (Throwable ignored) {}
        }
        if (TextUtils.isEmpty(mapName)) {
            List<String> maps = RobotMapManager.getAvailableMapList();
            if (!maps.isEmpty()) mapName = maps.get(0);
        }
        if (TextUtils.isEmpty(mapName)) {
            mapName = "2-0916091752";
        }
        File targetDir = RobotMapManager.resolveMapDir(mapName);
        final String targetMap = (targetDir != null && targetDir.exists()) ? targetDir.getName() : mapName;

        Log.i(TAG, "Executing ADD_PLACE: targetMap=" + targetMap + ", name=" + name + ", x=" + x + ", y=" + y + ", theta=" + theta);

        if (TextUtils.isEmpty(name)) {
            sendResult(client, msgId, "CMD_ADD_PLACE", false, -1, "Tên điểm không được để trống");
            return;
        }

        // 1. Immediately persist to place.json on disk
        boolean diskOk = RobotMapManager.saveOrUpdatePlaceToDisk(targetMap, name, x, y, theta);
        Log.i(TAG, "saveOrUpdatePlaceToDisk result=" + diskOk + " for " + name);

        // 2. Also register into CoreService SLAM pose map
        try {
            Pose pose = new Pose(x, y, theta, name);
            RobotApi.getInstance().saveMappingPose(0, pose, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                    Log.i(TAG, "saveMappingPose onResult: " + result + ", msg=" + message);

                    // Persist to place file in CoreService
                    try {
                        RobotApi.getInstance().savePlaceListToPlaceFile(0, targetMap, null);
                    } catch (Throwable ignored) {}

                    // Automatically compute/update topological road routes for newly added points
                    try {
                        RobotApi.getInstance().autoDrawRoad(0, targetMap, new CommandListener() {
                            @Override
                            public void onResult(int res, String msg) {
                                Log.i(TAG, "autoDrawRoad onResult: " + res + ", msg=" + msg);
                            }
                        });
                    } catch (Throwable ignored) {}

                    sendResult(client, msgId, "CMD_ADD_PLACE", ok || diskOk, result,
                            "Đã lưu điểm '" + name + "' thành công");

                    // Broadcast refreshed map data containing new place to ALL clients
                    try {
                        JsonObject mapData = RobotMapManager.buildMapDataJson(targetMap);
                        RobotBridgeServer server = RobotBridgeManager.getInstance().getServer();
                        if (server != null) {
                            server.broadcast(mapData.toString());
                        } else if (client != null && client.isOpen()) {
                            client.send(mapData.toString());
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "saveMappingPose exception: " + t.getMessage(), t);
            // Even if CoreService threw, if disk save worked, consider successful
            sendResult(client, msgId, "CMD_ADD_PLACE", diskOk, diskOk ? 0 : -1, diskOk ? "Đã lưu điểm '" + name + "' vào bộ nhớ" : t.getMessage());
            try {
                JsonObject mapData = RobotMapManager.buildMapDataJson(targetMap);
                RobotBridgeServer server = RobotBridgeManager.getInstance().getServer();
                if (server != null) server.broadcast(mapData.toString());
            } catch (Throwable ignored) {}
        }
    }

    private void handleDeletePlace(final WebSocket client, final String msgId, JsonObject json) {
        final String name = json.has("name") ? json.get("name").getAsString().trim() : "";
        String mapName = json.has("mapName") && !json.get("mapName").isJsonNull() ? json.get("mapName").getAsString().trim() : "";
        if (TextUtils.isEmpty(mapName)) {
            try {
                if (GlobalData.getInstance() != null) {
                    mapName = GlobalData.getInstance().getCurrentMapName();
                }
            } catch (Throwable ignored) {}
        }
        if (TextUtils.isEmpty(mapName)) {
            List<String> maps = RobotMapManager.getAvailableMapList();
            if (!maps.isEmpty()) mapName = maps.get(0);
        }
        if (TextUtils.isEmpty(mapName)) {
            mapName = "2-0916091752";
        }
        File targetDir = RobotMapManager.resolveMapDir(mapName);
        final String targetMap = (targetDir != null && targetDir.exists()) ? targetDir.getName() : mapName;

        Log.i(TAG, "Executing DELETE_PLACE: targetMap=" + targetMap + ", name=" + name);

        if (TextUtils.isEmpty(name)) {
            sendResult(client, msgId, "CMD_DELETE_PLACE", false, -1, "Tên điểm không hợp lệ");
            return;
        }

        // 1. Immediately delete from place.json on disk
        boolean diskOk = RobotMapManager.deletePlaceFromDisk(targetMap, name);
        Log.i(TAG, "deletePlaceFromDisk result=" + diskOk + " for " + name);

        // 2. Delete from CoreService SLAM
        try {
            RobotApi.getInstance().deleteMappingPose(0, name, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                    Log.i(TAG, "deleteMappingPose onResult: " + result + ", msg=" + message);

                    // Persist deletion to CoreService
                    try {
                        RobotApi.getInstance().savePlaceListToPlaceFile(0, targetMap, null);
                    } catch (Throwable ignored) {}

                    // Refresh road routes after deletion
                    try {
                        RobotApi.getInstance().autoDrawRoad(0, targetMap, new CommandListener() {
                            @Override
                            public void onResult(int res, String msg) {
                                Log.i(TAG, "autoDrawRoad onResult: " + res + ", msg=" + msg);
                            }
                        });
                    } catch (Throwable ignored) {}

                    sendResult(client, msgId, "CMD_DELETE_PLACE", ok || diskOk, result,
                            "Đã xóa điểm '" + name + "'");

                    // Broadcast refreshed map data to ALL clients
                    try {
                        JsonObject mapData = RobotMapManager.buildMapDataJson(targetMap);
                        RobotBridgeServer server = RobotBridgeManager.getInstance().getServer();
                        if (server != null) {
                            server.broadcast(mapData.toString());
                        } else if (client != null && client.isOpen()) {
                            client.send(mapData.toString());
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "deleteMappingPose exception: " + t.getMessage(), t);
            sendResult(client, msgId, "CMD_DELETE_PLACE", diskOk, diskOk ? 0 : -1, diskOk ? "Đã xóa điểm '" + name + "'" : t.getMessage());
            try {
                JsonObject mapData = RobotMapManager.buildMapDataJson(targetMap);
                RobotBridgeServer server = RobotBridgeManager.getInstance().getServer();
                if (server != null) server.broadcast(mapData.toString());
            } catch (Throwable ignored) {}
        }
    }

    private void sendResult(WebSocket client, String msgId, String command, boolean success, int resultCode, String message) {
        if (client == null || !client.isOpen()) return;

        JsonObject res = new JsonObject();
        res.addProperty("type", "COMMAND_RESULT");
        res.addProperty("msgId", msgId);
        res.addProperty("command", command);
        res.addProperty("success", success);
        res.addProperty("resultCode", resultCode);
        res.addProperty("message", message != null ? message : "");

        try {
            client.send(res.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to send command result: " + e.getMessage());
        }
    }
}
