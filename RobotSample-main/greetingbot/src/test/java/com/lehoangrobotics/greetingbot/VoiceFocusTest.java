package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceFocusTest {
    @Test public void sustainedVoiceWinsOverOneLouderClick() {
        VoiceFocus f = new VoiceFocus(); f.begin();
        f.observe(1000, 30000); f.observe(1032, 20); f.observe(1064, 20);
        f.observe(2000, 2000); f.observe(2032, 1800); f.observe(2064, 1900);
        assertTrue(f.at() >= 2000 && f.at() <= 2064);
    }
    @Test public void aNewUtteranceCannotUseTheOldVoicePeak() {
        VoiceFocus f = new VoiceFocus(); f.begin();
        f.observe(1000, 5000); f.observe(1032, 5000); f.observe(1064, 5000);
        f.begin(); assertEquals(0, f.at());
        f.observe(2000, 1000); f.observe(2032, 1000); assertEquals(0, f.at());
        f.observe(2064, 1000); assertTrue(f.at() >= 2000);
    }
}
