package com.lehoangrobotics.greetingbot;

import org.json.JSONObject;

final class GreetingSettings {
    double linearSpeed = 0.3, angularSpeed = 0.7, arrivalTolerance = 0.2;
    int obstacleTimeoutMs = 30000, microphoneSeconds = 5, playbackVolume = 60;

    void update(JSONObject json) {
        linearSpeed = bounded(json.optDouble("linearSpeed", linearSpeed), 0.1, 0.7, linearSpeed);
        angularSpeed = bounded(json.optDouble("angularSpeed", angularSpeed), 0.2, 1.8, angularSpeed);
        arrivalTolerance = bounded(json.optDouble("arrivalTolerance", arrivalTolerance), 0.05, 0.5, arrivalTolerance);
        obstacleTimeoutMs = (int) bounded(json.optDouble("obstacleTimeoutMs", obstacleTimeoutMs), 5000, 120000, obstacleTimeoutMs);
        microphoneSeconds = (int) bounded(json.optDouble("microphoneSeconds", microphoneSeconds), 2, 15, microphoneSeconds);
        playbackVolume = (int) bounded(json.optDouble("playbackVolume", playbackVolume), 0, 100, playbackVolume);
    }

    private static double bounded(double n, double min, double max, double fallback) {
        return Double.isNaN(n) || Double.isInfinite(n) || n < min || n > max ? fallback : n;
    }
}
