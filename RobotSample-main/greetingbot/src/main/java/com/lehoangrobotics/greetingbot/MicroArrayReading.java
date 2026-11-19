package com.lehoangrobotics.greetingbot;

import com.google.gson.Gson;

/** Validates the authenticated proxy's native telemetry against this exact recording. */
final class MicroArrayReading {
    private static final String SOURCE = "orion-microarray-native-debug";
    String source;
    Double angle, spreadDegrees;
    Long atUnixMs, captureStartUnixMs, captureEndUnixMs, sampleSpanMs;
    Integer sampleCount;
    long atElapsed;

    static MicroArrayReading parse(String json, long startUnix, long endUnix, long clockOffset) {
        try {
            MicroArrayReading value = new Gson().fromJson(json, MicroArrayReading.class);
            if (value == null || !SOURCE.equals(value.source) || value.angle == null
                || !VoiceDirectionPolicy.validAngle(value.angle) || value.spreadDegrees == null
                || Double.isNaN(value.spreadDegrees) || value.spreadDegrees < 0 || value.spreadDegrees > 35
                || value.sampleCount == null || value.sampleCount < 2 || value.sampleCount > 100
                || (value.sampleCount == 2 && (value.sampleSpanMs == null || value.sampleSpanMs < 80
                    || value.sampleSpanMs > 180 || value.spreadDegrees > 5.01))
                || value.atUnixMs == null || value.captureStartUnixMs == null || value.captureEndUnixMs == null
                || startUnix <= 0 || endUnix < startUnix || endUnix - startUnix > 6500
                || value.captureStartUnixMs != startUnix || value.captureEndUnixMs != endUnix
                || value.atUnixMs < startUnix - 250 || value.atUnixMs > endUnix + 200) return null;
            value.atElapsed = value.atUnixMs - clockOffset;
            return value;
        } catch (RuntimeException ignored) { return null; }
    }
}
