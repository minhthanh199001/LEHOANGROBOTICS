package com.lehoangrobotics.greetingbot;

/** Associates a native direction with captured audio, independent of cloud response latency. */
final class CapturedSoundAngle {
    static final long MAX_RESPONSE_AGE_MS = 8000;
    private String id = "";
    private long startedAt, endedAt;
    private double angle = Double.NaN;
    void begin(String id, long startedAt) { this.id = id; this.startedAt = startedAt; endedAt = 0; angle = Double.NaN; }
    boolean observe(double value, long at) {
        if (!id.isEmpty() && VoiceDirectionPolicy.validAngle(value) && at >= startedAt - 250
            && (endedAt == 0 || at <= endedAt + 250)) { angle = value % 360; return true; }
        return false;
    }
    void end(String id, long endedAt) { if (this.id.equals(id) && endedAt >= startedAt) this.endedAt = endedAt; }
    double forResult(String id, long now) {
        return this.id.equals(id) && endedAt > 0 && now >= endedAt && now - endedAt <= MAX_RESPONSE_AGE_MS ? angle : Double.NaN;
    }
}
