package com.lehoangrobotics.greetingbot;

/** Scan a short call without waiting for a noisy room to become silent. */
final class SpeechScanPolicy {
    enum Scan { NONE, PREVIEW, FINAL, DISCARD }
    private boolean previewSent;
    void begin() { previewSent = false; }
    Scan next(long elapsedMs, long quietMs, int loudFrames, int pcmBytes) {
        boolean complete = quietMs >= 600 || elapsedMs >= 3000;
        boolean enough = loudFrames >= 3 && elapsedMs >= 450 && pcmBytes >= 14400;
        if (complete) return enough ? Scan.FINAL : Scan.DISCARD;
        if (!previewSent && enough && elapsedMs >= 1400) { previewSent = true; return Scan.PREVIEW; }
        return Scan.NONE;
    }
}
