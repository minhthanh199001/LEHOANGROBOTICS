package com.lehoangrobotics.greetingbot;

final class TurnMeasurement {
    static double withOffset(double angle, double offset) {
        return VoiceDirectionPolicy.validAngle(angle) && !Double.isNaN(offset) && !Double.isInfinite(offset)
            && offset >= -90 && offset <= 90 ? (angle + offset + 360) % 360 : Double.NaN;
    }
    static double residual(double beforeRadians, double afterRadians, double angle) {
        if (Double.isNaN(magnitude(beforeRadians, afterRadians)) || !VoiceDirectionPolicy.validAngle(angle)) return Double.NaN;
        double signedAngle = angle > 180 ? angle - 360 : angle;
        double error = beforeRadians - Math.toRadians(signedAngle) - afterRadians;
        return -Math.toDegrees(Math.atan2(Math.sin(error), Math.cos(error)));
    }
    static boolean canCorrect(double residual, int corrections, long remainingMs) {
        return !Double.isNaN(residual) && !Double.isInfinite(residual) && Math.abs(residual) > 5
            && Math.abs(residual) <= 45 && corrections == 0 && remainingMs >= 0 && remainingMs <= CapturedSoundAngle.MAX_RESPONSE_AGE_MS;
    }
    static double magnitude(double beforeRadians, double afterRadians) {
        if (Double.isNaN(beforeRadians) || Double.isInfinite(beforeRadians)
            || Double.isNaN(afterRadians) || Double.isInfinite(afterRadians)) return Double.NaN;
        double delta = afterRadians - beforeRadians;
        return Math.abs(Math.toDegrees(Math.atan2(Math.sin(delta), Math.cos(delta))));
    }
}
