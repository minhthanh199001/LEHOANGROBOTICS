package com.lehoangrobotics.greetingbot;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Locate sustained voice energy; one isolated click cannot choose the focus window. */
final class VoiceFocus {
    private static final class Level {
        final long at; final double rms;
        Level(long at, double rms) { this.at = at; this.rms = rms; }
    }
    private final ArrayDeque<Level> recent = new ArrayDeque<>();
    private double best = -1;
    private long focus;
    void begin() { recent.clear(); best = -1; focus = 0; }
    void observe(long at, double rms) {
        recent.addLast(new Level(at, rms));
        while (!recent.isEmpty() && at - recent.peekFirst().at > 300) recent.removeFirst();
        if (recent.size() < 3) return;
        double[] levels = new double[recent.size()]; int i = 0;
        for (Level level : recent) levels[i++] = level.rms;
        Arrays.sort(levels);
        double sustained = levels[levels.length - 3];
        if (sustained > best) { best = sustained; focus = (recent.peekFirst().at + at) / 2; }
    }
    long at() { return focus; }
}
