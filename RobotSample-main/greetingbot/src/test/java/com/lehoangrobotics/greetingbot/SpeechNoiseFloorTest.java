package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechNoiseFloorTest {
    @Test public void fanNoiseDoesNotKeepASpeechSegmentOpen() {
        SpeechNoiseFloor floor = new SpeechNoiseFloor();
        for (int i = 0; i < 20; i++) floor.calibrate(600);
        assertTrue(floor.ready());
        for (int i = 0; i < 100; i++) { assertFalse(floor.loud(650)); floor.quiet(650); }
        assertTrue(floor.loud(2400));
    }
    @Test public void aBriefClickDuringCalibrationDoesNotHideFollowingSpeech() {
        SpeechNoiseFloor floor = new SpeechNoiseFloor(); floor.calibrate(24000);
        for (int i = 1; i < 20; i++) floor.calibrate(100);
        assertTrue(floor.loud(500)); assertFalse(floor.loud(100));
    }
    @Test public void waitsForCalibrationAndMaintainsAMinimumThresholdInSilence() {
        SpeechNoiseFloor floor = new SpeechNoiseFloor(); assertFalse(floor.loud(2000));
        for (int i = 0; i < 20; i++) floor.calibrate(0);
        assertFalse(floor.loud(200)); assertTrue(floor.loud(500));
    }
}
