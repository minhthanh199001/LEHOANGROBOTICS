package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceDirectionPolicyTest {
    @Test public void recognizesBothPhrasesAndVietnameseTranscription() {
        assertEquals("Hello robot", VoiceDirectionPolicy.keyword("HELLO, ROBOT!"));
        assertEquals("robot ơi", VoiceDirectionPolicy.keyword("Robot ơi!"));
        assertEquals("robot ơi", VoiceDirectionPolicy.keyword("Rô-bốt ơi, nghe tôi nói."));
        assertEquals("robot ơi", VoiceDirectionPolicy.keyword("robot oi"));
        assertEquals("", VoiceDirectionPolicy.keyword("robotics oi"));
        assertEquals("", VoiceDirectionPolicy.keyword("hello robotic"));
        assertEquals("", VoiceDirectionPolicy.keyword("tiếng ồn bất kỳ"));
    }
    @Test public void acceptsOnlyAnExplicitSoundDirectionInSpeechParameters() {
        assertEquals(270, VoiceDirectionPolicy.soundAngle("{\"soundAngle\":270}", false), 0);
        assertEquals(90, VoiceDirectionPolicy.soundAngle("{\"data\":{\"soundAngle\":\"90\"}}", false), 0);
        assertTrue(Double.isNaN(VoiceDirectionPolicy.soundAngle("{\"angle\":90}", false)));
        assertEquals(90, VoiceDirectionPolicy.soundAngle("90", true), 0);
        assertEquals(0, VoiceDirectionPolicy.soundAngle("{\"angle\":360}", true), 0);
        for (String raw : new String[]{"", "-1", "361", "NaN", "null", "not json"})
            assertTrue(raw, Double.isNaN(VoiceDirectionPolicy.soundAngle(raw, true)));
    }
    @Test public void readsOnlyTheRecognizedTextFromAsrJson() {
        assertEquals("robot ơi", VoiceDirectionPolicy.transcript("{\"text\":\"robot ơi\",\"answer\":\"Hello robot\"}"));
        assertEquals("", VoiceDirectionPolicy.transcript("{\"answer\":\"Hello robot\"}"));
    }
    @Test public void requiresKeywordAndFreshAngleAndAllowsZeroDegrees() {
        VoiceDirectionPolicy p = new VoiceDirectionPolicy(); p.start();
        assertTrue(Double.isNaN(p.select("Hello robot", Double.NaN, "a", 10000)));
        p.wakeAngle(90, 10000);
        assertTrue(Double.isNaN(p.select("tiếng ồn", 90, "b", 10001)));
        assertTrue(Double.isNaN(p.select("robot ơi", Double.NaN, "c", 15001)));
        assertEquals(0, p.select("Hello robot", 0, "d", 15002), 0);
    }
    @Test public void avoidsTwoTurnsFromAsrAndNlpForTheSameUtterance() {
        VoiceDirectionPolicy p = new VoiceDirectionPolicy(); p.start(); p.speechStarted(10000);
        assertEquals(90, p.select("Hello robot", 90, "request-1", 10010), 0);
        assertTrue(Double.isNaN(p.select("Hello robot", 90, "request-2", 10020)));
        p.finished(11000);
        assertTrue(Double.isNaN(p.select("Hello robot", 90, "request-2", 15000)));
        p.speechStarted(16000);
        assertTrue(Double.isNaN(p.select("Hello robot", 90, "request-1", 16010)));
        assertEquals(270, p.select("robot ơi", 270, "request-3", 16020), 0);
    }
    @Test public void dropsPreviousUtteranceAngleAndStopsOnLifecycleLoss() {
        VoiceDirectionPolicy p = new VoiceDirectionPolicy(); p.start(); p.wakeAngle(120, 1000);
        p.speechStarted(2000);
        assertTrue(Double.isNaN(p.select("Hello robot", Double.NaN, "a", 2001)));
        p.wakeAngle(180, 3000); p.speechStarted(3100);
        assertEquals(180, p.select("robot ơi", Double.NaN, "b", 3200), 0);
        p.stop();
        assertTrue(Double.isNaN(p.select("Hello robot", 90, "c", 20000)));
    }
    @Test public void acceptsANewCallHalfASecondAfterFinishingWithoutReplayingTheOldCall() {
        VoiceDirectionPolicy p = new VoiceDirectionPolicy(); p.start(); p.speechStarted(10000);
        assertEquals(90, p.select("robot ơi", 90, "a", 10100), 0); p.finished(11000);
        p.speechStarted(11100);
        assertTrue(Double.isNaN(p.select("robot ơi", 270, "b", 11499)));
        assertEquals(270, p.select("robot ơi", 270, "b", 11500), 0);
        p.finished(12000); p.speechStarted(13000);
        assertTrue(Double.isNaN(p.select("robot ơi", 90, "a", 13001)));
    }
}
