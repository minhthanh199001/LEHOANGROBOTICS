package com.lehoangrobotics.greetingbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ElevatorConfig {
    static final class Floor {
        final int number;
        final String map, waiting, cabin, charging;

        Floor(JSONObject json) {
            number = json.optInt("number");
            map = json.optString("mapName");
            waiting = json.optString("waitingPoint");
            cabin = json.optString("cabinPoint");
            charging = json.optString("chargingPoint");
        }

        String label() { return "Tầng " + number; }
    }

    final List<Floor> floors;
    final Floor dock;

    ElevatorConfig(JSONObject json) {
        List<Floor> parsed = new ArrayList<>();
        Floor main = null;
        JSONArray list = json.optJSONArray("floors");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject value = list.optJSONObject(i);
                if (value == null) continue;
                Floor floor = new Floor(value);
                parsed.add(floor);
                if (!json.isNull("mainChargingFloor") && json.optInt("mainChargingFloor") == floor.number)
                    main = floor;
            }
        }
        floors = Collections.unmodifiableList(parsed);
        dock = main;
    }

    Floor forMap(String name) {
        for (Floor floor : floors) if (floor.map.equals(name)) return floor;
        return null;
    }

    static JSONObject point(JSONArray points, String name) {
        if (points == null) return null;
        for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point != null && name.equals(point.optString("name")) && point.optInt("status", -1) == 0)
                return point;
        }
        return null;
    }
}
