package com.ainirobot.robotos.bridge;

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
import com.ainirobot.robotos.maputils.GlobalData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.WebSocket;

import java.io.File;
import java.util.List;

public class RobotCommandHandler {
    private static final String TAG = "RobotCommandHandler";
    private static int sHeadReqId = 1000;
    private static int sMoveReqId = 2000;

    private final Handler mWatchdogHandler = new Handler(Looper.getMainLooper());
    private static final long AUTO_STOP_DELAY_MS = 750; // Safety watchdog: stop after 750ms if no new pulse

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

        CommandListener listener = new CommandListener() {
            @Override
            public void onResult(int result, String message) {
                boolean ok = (result == Definition.RESULT_OK || "succeed".equalsIgnoreCase(message));
                sendResult(client, msgId, "CMD_MOVE", ok, result, message);
            }
        };

        int reqId = sMoveReqId++;
        switch (action) {
            case "FORWARD":
                armSafetyWatchdog();
                RobotApi.getInstance().goForward(reqId, linearSpeed, listener);
                break;
            case "BACKWARD":
                armSafetyWatchdog();
                RobotApi.getInstance().goBackward(reqId, linearSpeed, listener);
                break;
            case "TURN_LEFT":
                armSafetyWatchdog();
                RobotApi.getInstance().turnLeft(reqId, angularSpeed, listener);
                break;
            case "TURN_RIGHT":
                armSafetyWatchdog();
                RobotApi.getInstance().turnRight(reqId, angularSpeed, listener);
                break;
            case "STOP":
            default:
                disarmSafetyWatchdog();
                RobotApi.getInstance().stopMove(reqId, listener);
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

        // Ensure any previous running navigation or move is stopped first to avoid ACTION_RESPONSE_ALREADY_RUN
        try {
            RobotApi.getInstance().stopNavigation(0);
        } catch (Throwable ignored) {}

        // 1. Check if target is Charging Dock
        String lower = destination != null ? destination.toLowerCase() : "";
        if (lower.contains("charge") || lower.contains("sạc") || lower.contains("charging")) {
            Log.i(TAG, "Navigating to Auto Charge Station...");
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
                    sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
                }
            });
            return;
        }

        // 2. If destination is a Named Station on the Map, use native place navigation!
        boolean isCustomCoord = TextUtils.isEmpty(destination) 
                || destination.startsWith("Điểm (") 
                || destination.startsWith("Tọa độ (")
                || destination.startsWith("Point (");

        if (!isCustomCoord && !TextUtils.isEmpty(destination)) {
            Log.i(TAG, "Navigating to Named Station via native route: " + destination);
            RobotApi.getInstance().startNavigation(0, destination, 1.5, timeoutMs, new ActionListener() {
                @Override
                public void onResult(int status, String responseString) throws RemoteException {
                    Log.i(TAG, "Named Station onResult: status=" + status + ", resp=" + responseString);
                    boolean success = (status == Definition.RESULT_OK && !"false".equalsIgnoreCase(responseString));
                    if (!success && x != null && y != null) {
                        Log.w(TAG, "Named navigation returned false. Falling back to Pose navigation...");
                        navigateByPose(client, msgId, destination, x, y, theta, 1.2, timeoutMs);
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
                        navigateByPose(client, msgId, destination, x, y, theta, 1.2, timeoutMs);
                    } else {
                        sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
                    }
                }
            });
            return;
        }

        // 3. For arbitrary floor click / custom coordinate: Navigate via Pose with No Directional Parking!
        if (x != null && y != null) {
            navigateByPose(client, msgId, destination, x, y, theta, 1.2, timeoutMs);
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

    private void navigateByPose(final WebSocket client, final String msgId, String destination, float x, float y, Float theta, double maxDist, long timeoutMs) {
        try {
            RobotApi.getInstance().stopNavigation(0);
        } catch (Throwable ignored) {}
        Pose pose = new Pose(x, y, theta != null ? theta : 0f, destination != null ? destination : "");
        pose.setNoDirectionalParking(true); // Don't spin in place to align angle!
        pose.setIgnoreDistance(true);
        Log.i(TAG, "Navigating via Pose (no directional parking): " + pose.toString());
        RobotApi.getInstance().startNavigation(0, pose, maxDist, timeoutMs, new ActionListener() {
            @Override
            public void onResult(int status, String responseString) throws RemoteException {
                boolean success = (status == Definition.RESULT_OK);
                sendResult(client, msgId, "CMD_NAVIGATE", success, status, responseString);
            }

            @Override
            public void onError(int errorCode, String errorString) throws RemoteException {
                Log.e(TAG, "Pose Navigate onError: code=" + errorCode + ", err=" + errorString);
                sendResult(client, msgId, "CMD_NAVIGATE", false, errorCode, errorString);
            }
        });
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
                RobotApi.getInstance().startNaviToAutoChargeAction(0, 3 * Definition.MINUTE, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws RemoteException {
                        sendResult(client, msgId, "CMD_CHARGE", status == Definition.RESULT_OK, status, responseString);
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws RemoteException {
                        sendResult(client, msgId, "CMD_CHARGE", false, errorCode, errorString);
                    }
                });
                break;
            case "STOP_CHARGE":
                RobotApi.getInstance().stopAutoChargeAction(0, true);
                sendResult(client, msgId, "CMD_CHARGE", true, 0, "Stopped auto charge");
                break;
            case "LEAVE_PILE":
                RobotApi.getInstance().leaveChargingPile(0, 0.5f, 0.5f, new CommandListener() {
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
