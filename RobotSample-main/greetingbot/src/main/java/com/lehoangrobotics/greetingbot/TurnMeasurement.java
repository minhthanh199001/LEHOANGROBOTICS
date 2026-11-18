package com.lehoangrobotics.greetingbot;

final class TurnMeasurement {
    static double signedAngle(double angle) {
        if (!VoiceDirectionPolicy.validAngle(angle)) return Double.NaN;
        double normalized = angle % 360;
        return normalized > 180 ? normalized - 360 : normalized;
    }
    static double withOffset(double angle, double offset) {
        double signed = signedAngle(angle);
        if (Double.isNaN(signed) || Double.isNaN(offset) || Double.isInfinite(offset) || offset < -90 || offset > 90)
            return Double.NaN;
        if (signed == 0) return 0;
        // Increase/decrease the turn magnitude while preserving its direction. Stay on the
        // same side of 180 degrees so wakeUp cannot choose the opposite shortest turn.
        double magnitude = Math.max(0, Math.min(179.999, Math.abs(signed) + offset));
        return (Math.copySign(magnitude, signed) + 360) % 360;
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
