package com.lehoangrobotics.greetingbot;

/** Calibrate on ambient sound first so a running fan cannot keep every utterance open. */
final class SpeechNoiseFloor {
    private final double[] initial = new double[20];
    private int count;
    private double noise = 100;
    boolean ready() { return count == initial.length; }
    void calibrate(double rms) {
        if (ready()) return;
        initial[count++] = rms;
        if (ready()) {
            java.util.Arrays.sort(initial);
            noise = bounded((initial[9] + initial[10]) / 2);
        }
    }
    double threshold() { return Math.max(200, noise * 2.0); }
    boolean loud(double rms) { return ready() && rms > threshold(); }
    void quiet(double rms) { noise = bounded(noise * .98 + rms * .02); }
    private double bounded(double value) { return Math.max(40, Math.min(6000, value)); }
}
