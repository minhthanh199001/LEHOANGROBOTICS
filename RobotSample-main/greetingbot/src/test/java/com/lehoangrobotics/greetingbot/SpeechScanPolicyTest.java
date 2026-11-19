package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechScanPolicyTest {
    @Test public void scansAtOnePointFourSecondsEvenWhenBackgroundNoiseContinues() {
        SpeechScanPolicy p = new SpeechScanPolicy(); p.begin();
        assertEquals(SpeechScanPolicy.Scan.NONE, p.next(1399, 0, 10, 40000));
        assertEquals(SpeechScanPolicy.Scan.PREVIEW, p.next(1400, 0, 10, 40000));
        assertEquals(SpeechScanPolicy.Scan.NONE, p.next(1600, 0, 10, 48000));
        assertEquals(SpeechScanPolicy.Scan.FINAL, p.next(3000, 0, 10, 90000));
    }
    @Test public void finishesAShortCallAfterSixHundredMillisecondsOfSilence() {
        SpeechScanPolicy p = new SpeechScanPolicy(); p.begin();
        assertEquals(SpeechScanPolicy.Scan.FINAL, p.next(1000, 600, 5, 32000));
        p.begin(); assertEquals(SpeechScanPolicy.Scan.PREVIEW, p.next(1400, 0, 10, 40000));
    }
    @Test public void aClickCannotTriggerAnUpload() {
        SpeechScanPolicy p = new SpeechScanPolicy(); p.begin();
        assertEquals(SpeechScanPolicy.Scan.NONE, p.next(1400, 0, 1, 32000));
        assertEquals(SpeechScanPolicy.Scan.DISCARD, p.next(1500, 600, 1, 32000));
    }
}
