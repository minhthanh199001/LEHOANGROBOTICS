package com.lehoangrobotics.greetingbot;

import android.os.Handler;
import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;
import org.json.JSONArray;
import org.json.JSONObject;

final class MapPositioning {
    interface Host {
        boolean authorized();
        int nextId();
        void finished(JSONArray points, String error);
    }
    private interface Request { int send(CommandListener reply); }
    private interface Result { void receive(int code, String value) throws Exception; }

    private final Handler main;
    private final RobotApi robot;
    private Operation active;

    MapPositioning(Handler main, RobotApi robot) {
        this.main = main;
        this.robot = robot;
    }

    boolean busy() { return active != null; }

    void start(String map, String point, Host host) {
        if (busy()) throw new IllegalStateException("SDK đang đổi map hoặc định vị.");
        Operation operation = new Operation(map, point, host);
        active = operation;
        main.postDelayed(() -> operation.finish(null, "Hết thời gian xác nhận map và vị trí từ SDK."), 18000);
        operation.begin();
    }

    private final class Operation {
        final String map, point;
        final Host host;
        JSONArray points;
        double x, y, heading;

        Operation(String map, String point, Host host) {
            this.map = map;
            this.point = point;
            this.host = host;
        }

        void begin() {
            request(listener -> robot.getMapName(host.nextId(), listener), (code, value) -> {
                if (code != Definition.RESULT_OK || value == null || value.isEmpty()) {
                    finish(null, "SDK chưa đọc được map hiện tại.");
                } else if (map.equals(value)) {
                    readPoints();
                } else {
                    request(listener -> robot.switchMap(host.nextId(), map, listener), (status, response) -> {
                        if (!accepted(status, response)) finish(null, "SDK không chuyển được sang map " + map + ".");
                        else waitForMap();
                    });
                }
            });
        }

        void waitForMap() {
            request(listener -> robot.getMapName(host.nextId(), listener), (code, value) -> {
                if (code == Definition.RESULT_OK && map.equals(value)) readPoints();
                else main.postDelayed(this::waitForMap, 400);
            });
        }

        void readPoints() {
            request(listener -> robot.getPlaceList(host.nextId(), listener), (code, value) -> {
                if (code != Definition.RESULT_OK || value == null) {
                    main.postDelayed(this::waitForMap, 400);
                    return;
                }
                points = new JSONArray(value);
                JSONObject position = ElevatorConfig.point(points, point);
                if (position == null) {
                    finish(null, "Điểm định vị không còn khả dụng trên map " + map + ": " + point);
                    return;
                }
                x = position.getDouble("x");
                y = position.getDouble("y");
                heading = position.getDouble("theta");
                JSONObject pose = new JSONObject().put(Definition.JSON_NAVI_POSITION_X, x)
                    .put(Definition.JSON_NAVI_POSITION_Y, y).put(Definition.JSON_NAVI_POSITION_THETA, heading);
                request(listener -> robot.setPoseEstimate(host.nextId(), pose.toString(), listener), (status, response) -> {
                    if (!accepted(status, response)) finish(null, "SDK từ chối định vị tại " + point + ".");
                    else main.postDelayed(this::verifyPosition, 400);
                });
            });
        }

        void verifyPosition() {
            request(listener -> robot.isRobotEstimate(host.nextId(), listener), (code, value) -> {
                if (code != Definition.RESULT_OK || !"true".equals(value)) {
                    main.postDelayed(this::verifyPosition, 400);
                    return;
                }
                request(listener -> robot.getPosition(host.nextId(), listener), (status, response) -> {
                    if (status == Definition.RESULT_OK && response != null) {
                        JSONObject actual = new JSONObject(response);
                        double dx = actual.getDouble(Definition.JSON_NAVI_POSITION_X) - x;
                        double dy = actual.getDouble(Definition.JSON_NAVI_POSITION_Y) - y;
                        double angle = actual.getDouble(Definition.JSON_NAVI_POSITION_THETA) - heading;
                        if (Math.hypot(dx, dy) <= 0.5 && Math.abs(Math.atan2(Math.sin(angle), Math.cos(angle))) <= Math.PI / 4) {
                            request(listener -> robot.getMapName(host.nextId(), listener), (mapStatus, current) -> {
                                if (mapStatus == Definition.RESULT_OK && map.equals(current)) finish(points, null);
                                else finish(null, "Map đã thay đổi trong lúc xác nhận vị trí.");
                            });
                            return;
                        }
                    }
                    main.postDelayed(this::verifyPosition, 400);
                });
            });
        }

        void finish(JSONArray points, String error) {
            if (active != this) return;
            active = null;
            host.finished(points, error);
        }

        void request(Request request, Result result) {
            if (active != this) return;
            if (!host.authorized()) {
                finish(null, "Đã ngừng xác nhận vị trí.");
                return;
            }
            class Reply extends CommandListener {
                boolean done;
                void receive(int code, String value) {
                    main.post(() -> {
                        if (done || active != Operation.this) return;
                        done = true;
                        if (!host.authorized()) {
                            finish(null, "Đã ngừng xác nhận vị trí.");
                            return;
                        }
                        try { result.receive(code, value); }
                        catch (Exception e) { finish(null, "Dữ liệu vị trí từ SDK không hợp lệ: " + e.getMessage()); }
                    });
                }
                @Override public void onResult(int code, String value) { receive(code, value); }
                @Override public void onResult(int code, String value, String extra) { receive(code, value); }
                @Override public void onError(int code, String value) { receive(-1, value); }
                @Override public void onError(int code, String value, String extra) { receive(-1, value); }
            }
            Reply reply = new Reply();
            try { if (request.send(reply) < 0) reply.receive(-1, null); }
            catch (Exception e) { reply.receive(-1, e.getMessage()); }
        }
    }

    private static boolean accepted(int code, String value) {
        return code == Definition.RESULT_OK || "succeed".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }
}
