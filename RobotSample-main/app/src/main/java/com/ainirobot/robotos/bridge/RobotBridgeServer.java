package com.ainirobot.robotos.bridge;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class RobotBridgeServer extends WebSocketServer {
    private static final String TAG = "RobotBridgeServer";
    private final RobotCommandHandler mCommandHandler;

    public RobotBridgeServer(int port, RobotCommandHandler commandHandler) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
        this.mCommandHandler = commandHandler;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.i(TAG, "Client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.i(TAG, "Client disconnected: " + conn.getRemoteSocketAddress() + " Reason: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "Received message from PC: " + message);
        if (mCommandHandler != null) {
            mCommandHandler.handleCommand(conn, message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "WebSocket error: " + (ex != null ? ex.getMessage() : "unknown"));
    }

    @Override
    public void onStart() {
        Log.i(TAG, "RobotBridgeServer started on port: " + getPort());
    }

    public void broadcastTelemetry(String jsonTelemetry) {
        broadcast(jsonTelemetry);
    }
}

