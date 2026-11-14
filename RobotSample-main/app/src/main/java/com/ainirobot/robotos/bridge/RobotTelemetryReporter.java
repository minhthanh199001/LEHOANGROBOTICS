package com.ainirobot.robotos.bridge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.StatusListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.robotsetting.RobotSettingApi;
import com.ainirobot.robotos.application.RobotOSApplication;
import com.ainirobot.robotos.maputils.Constant;
import com.ainirobot.robotos.maputils.GlobalData;
import com.ainirobot.robotos.maputils.GsonUtil;
import com.ainirobot.robotos.maputils.Pose2d;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONObject;

public class RobotTelemetryReporter {
    private static final String TAG = "RobotTelemetryReporter";
    private final RobotBridgeServer mServer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsRunning = false;
    private static final long REPORT_INTERVAL_MS = 500; // 2Hz

    private Pose2d mCurrentPose = new Pose2d(0, 0, 0, 0);
    private boolean mIsCharging = false;
    private int mBatteryLevel = 60;
    private String mRobotState = "AVAILABLE";
    private boolean mEmergencyStop = false;
    private String mDoorStatus = "CLOSED";
    private String mCurrentMapName = "";
    private long mLastSlowQueryTime = 0;

    private final Runnable mReportRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsRunning) return;
            try {
                long now = System.currentTimeMillis();
                // Query battery, active pose and map periodically every 2 seconds
                if (now - mLastSlowQueryTime > 2000) {
                    mLastSlowQueryTime = now;
                    queryBattery();
                    queryActivePosition();
                    queryMapInfo();
                }

                sendTelemetry();
            } catch (Throwable t) {
                Log.e(TAG, "Error in telemetry loop: " + t.getMessage());
            }
            if (mIsRunning) {
                mHandler.postDelayed(this, REPORT_INTERVAL_MS);
            }
        }
    };

    private final StatusListener mPoseListener = new StatusListener() {
        @Override
        public void onStatusUpdate(String type, String value) {
            try {
                Pose2d pose = GsonUtil.fromJson(value, Pose2d.class);
                if (pose != null) {
                    mCurrentPose = pose;
                    if (GlobalData.getInstance() != null) {
                        GlobalData.getInstance().setNewestLocation(pose);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing pose telemetry: " + e.getMessage());
            }
        }
    };

    public RobotTelemetryReporter(RobotBridgeServer server) {
        this.mServer = server;
    }

    public void start() {
        if (mIsRunning) return;
        mIsRunning = true;

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().registerStatusListener(Constant.CoreDef.POSE_LISTEN, mPoseListener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register pose listener: " + e.getMessage());
        }

        // Trigger immediate query
        queryBattery();
        queryActivePosition();
        queryMapInfo();

        mHandler.post(mReportRunnable);
        Log.i(TAG, "RobotTelemetryReporter started reporting every " + REPORT_INTERVAL_MS + "ms");
    }

    public void stop() {
        mIsRunning = false;
        mHandler.removeCallbacks(mReportRunnable);
        try {
            RobotApi.getInstance().unregisterStatusListener(mPoseListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister pose listener: " + e.getMessage());
        }
        Log.i(TAG, "RobotTelemetryReporter stopped");
    }

    private void queryBattery() {
        // Method 1: Android BatteryManager (Direct OS sensor)
        try {
            Context ctx = RobotOSApplication.getInstance();
            if (ctx != null) {
                BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (cap > 0 && cap <= 100) {
                        mBatteryLevel = cap;
                    }
                }
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = ctx.registerReceiver(null, ifilter);
                if (batteryStatus != null) {
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    mIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "queryBattery BatteryManager error: " + t.getMessage());
        }

        // Method 2: OrionStar Setting Provider
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                String batteryStr = RobotSettingApi.getInstance().getRobotString(Definition.ROBOT_SETTINGS_BATTERY_INFO);
                if (!TextUtils.isEmpty(batteryStr)) {
                    String digits = batteryStr.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        mBatteryLevel = Integer.parseInt(digits);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void queryActivePosition() {
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                // Keep POSE_LISTEN alive
                try {
                    RobotApi.getInstance().registerStatusListener(Constant.CoreDef.POSE_LISTEN, mPoseListener);
                } catch (Throwable ignored) {}

                // Synchronous check if available
                try {
                    com.ainirobot.coreservice.client.actionbean.Pose p = RobotApi.getInstance().getCurrentPose();
                    if (p != null) {
                        mCurrentPose = new Pose2d(p.getX(), p.getY(), p.getTheta(), 0);
                        if (GlobalData.getInstance() != null) {
                            GlobalData.getInstance().setNewestLocation(mCurrentPose);
                        }
                    }
                } catch (Throwable ignored) {}

                RobotApi.getInstance().getPosition(0, new CommandListener() {
                    @Override
                    public void onResult(int result, String message) {
                        if (result == Definition.RESULT_OK && !TextUtils.isEmpty(message)) {
                            try {
                                JSONObject json = new JSONObject(message);
                                double x = json.optDouble(Definition.JSON_NAVI_POSITION_X, 0.0);
                                double y = json.optDouble(Definition.JSON_NAVI_POSITION_Y, 0.0);
                                double theta = json.optDouble(Definition.JSON_NAVI_POSITION_THETA, 0.0);
                                mCurrentPose = new Pose2d((float) x, (float) y, (float) theta, 0);
                                if (GlobalData.getInstance() != null) {
                                    GlobalData.getInstance().setNewestLocation(mCurrentPose);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing getPosition result: " + e.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "queryActivePosition error: " + t.getMessage());
        }
    }

    private void queryMapInfo() {
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().getMapName(0, new CommandListener() {
                    @Override
                    public void onResult(int result, String message, String extraData) {
                        if (result == Definition.RESULT_OK && !TextUtils.isEmpty(message)) {
                            boolean changed = !message.equals(mCurrentMapName);
                            mCurrentMapName = message;
                            if (GlobalData.getInstance() != null) {
                                GlobalData.getInstance().setCurrentMapName(message);
                            }
                            if (changed || System.currentTimeMillis() - mLastMapBroadcastTime > 10000) {
                                mLastMapBroadcastTime = System.currentTimeMillis();
                                broadcastMapData();
                            }
                        }
                    }
                });
            } else {
                if (System.currentTimeMillis() - mLastMapBroadcastTime > 10000) {
                    mLastMapBroadcastTime = System.currentTimeMillis();
                    broadcastMapData();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "queryMapInfo error: " + t.getMessage());
        }
    }

    private long mLastMapBroadcastTime = 0;

    private void broadcastMapData() {
        try {
            if (mServer != null) {
                JsonObject mapData = RobotMapManager.buildMapDataJson(mCurrentMapName);
                mServer.broadcastTelemetry(mapData.toString());
            }
        } catch (Throwable t) {
            Log.e(TAG, "broadcastMapData error: " + t.getMessage());
        }
    }

    public void setRobotState(String state) {
        this.mRobotState = state;
    }

    public void setEmergencyStop(boolean emergencyStop) {
        this.mEmergencyStop = emergencyStop;
    }

    public void setDoorStatus(String doorStatus) {
        this.mDoorStatus = doorStatus;
    }

    private void sendTelemetry() {
        if (mServer == null) return;

        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "TELEMETRY");
            root.addProperty("robotId", "LH-ORIONSTAR-01");
            root.addProperty("timestamp", System.currentTimeMillis());

            // Battery
            JsonObject battery = new JsonObject();
            battery.addProperty("level", mBatteryLevel);
            battery.addProperty("isCharging", mIsCharging);
            root.add("battery", battery);

            // Pose
            JsonObject pose = new JsonObject();
            pose.addProperty("x", mCurrentPose != null ? mCurrentPose.x : 0.0);
            pose.addProperty("y", mCurrentPose != null ? mCurrentPose.y : 0.0);
            pose.addProperty("theta", mCurrentPose != null ? mCurrentPose.t : 0.0);

            boolean isEstimated = false;
            try {
                if (RobotApi.getInstance().isApiConnectedService()) {
                    isEstimated = RobotApi.getInstance().isRobotEstimate();
                }
            } catch (Throwable ignored) {
            }
            pose.addProperty("isEstimated", isEstimated);
            root.add("pose", pose);

            // Status
            JsonObject status = new JsonObject();
            status.addProperty("state", mRobotState);
            String currentMap = !TextUtils.isEmpty(mCurrentMapName) ? mCurrentMapName : "Bản đồ Mặc định";
            try {
                if (GlobalData.getInstance() != null) {
                    String map = GlobalData.getInstance().getCurrentMapName();
                    if (!TextUtils.isEmpty(map)) currentMap = map;
                }
            } catch (Throwable ignored) {
            }
            status.addProperty("currentMap", currentMap);
            status.addProperty("emergencyStop", mEmergencyStop);
            status.addProperty("currentPlace", "");
            status.addProperty("doorStatus", mDoorStatus);
            status.addProperty("speed", 0.0);
            root.add("status", status);

            // Errors
            root.add("errors", new JsonArray());

            mServer.broadcastTelemetry(root.toString());
        } catch (Throwable t) {
            Log.e(TAG, "sendTelemetry exception: " + t.getMessage());
        }
    }
}
