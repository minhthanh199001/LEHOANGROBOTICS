package com.ainirobot.robotos.bridge;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class RobotBridgeManager {
    private static final String TAG = "RobotBridgeManager";
    private static final int DEFAULT_PORT = 8899;

    private static RobotBridgeManager sInstance;
    private RobotBridgeServer mServer;
    private RobotCommandHandler mCommandHandler;
    private RobotTelemetryReporter mTelemetryReporter;
    private boolean mIsRunning = false;

    public static synchronized RobotBridgeManager getInstance() {
        if (sInstance == null) {
            sInstance = new RobotBridgeManager();
        }
        return sInstance;
    }

    private RobotBridgeManager() {
    }

    public synchronized void startBridge(Context context) {
        if (mIsRunning) {
            Log.w(TAG, "RobotBridgeManager is already running");
            return;
        }

        try {
            mCommandHandler = new RobotCommandHandler();
            mServer = new RobotBridgeServer(DEFAULT_PORT, mCommandHandler);
            mServer.start();

            mTelemetryReporter = new RobotTelemetryReporter(mServer);
            mTelemetryReporter.start();

            mIsRunning = true;
            Log.i(TAG, "Lê Hoàng Robot Bridge started successfully on IP: " + getIpAddress(context) + ":" + DEFAULT_PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start RobotBridgeManager: " + e.getMessage(), e);
        }
    }

    public synchronized void stopBridge() {
        if (!mIsRunning) return;

        try {
            if (mTelemetryReporter != null) {
                mTelemetryReporter.stop();
                mTelemetryReporter = null;
            }

            if (mServer != null) {
                mServer.stop();
                mServer = null;
            }

            mCommandHandler = null;
            mIsRunning = false;
            Log.i(TAG, "Lê Hoàng Robot Bridge stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping RobotBridgeManager: " + e.getMessage(), e);
        }
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public RobotTelemetryReporter getTelemetryReporter() {
        return mTelemetryReporter;
    }

    public RobotBridgeServer getServer() {
        return mServer;
    }

    public String getIpAddress(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}

