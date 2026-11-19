package com.lehoangrobotics.greetingbot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenAISpeechInputTest {
    @Test public void waveIsCompatibleWithTheProxyPcmFormatWithoutChangingSamples() {
        byte[] pcm = new byte[]{1, 2, 3, 4, (byte) 255, (byte) 128};
        byte[] wav = OpenAISpeechInput.wave(pcm);
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(wav.length - 8, b.getInt(4)); assertEquals(1, b.getShort(20)); assertEquals(1, b.getShort(22));
        assertEquals(16000, b.getInt(24)); assertEquals(32000, b.getInt(28)); assertEquals(16, b.getShort(34));
        assertEquals(pcm.length, b.getInt(40)); assertArrayEquals(pcm, java.util.Arrays.copyOfRange(wav, 44, wav.length));
    }
    @Test public void cannotUseAnAngleFromThePreviousSpeakerOrNextUtterance() {
        CapturedSoundAngle window = new CapturedSoundAngle(); window.begin("a", 10000);
        window.observe(90, 9700); window.end("a", 12000);
        assertTrue(Double.isNaN(window.forResult("a", 13000)));
        window.observe(270, 12300);
        assertTrue(Double.isNaN(window.forResult("a", 13000)));
        window.observe(180, 11500);
        assertEquals(180, window.forResult("a", 13000), 0);
        window.begin("b", 14000); window.end("b", 15000);
        assertTrue(Double.isNaN(window.forResult("b", 16000)));
        assertTrue(Double.isNaN(window.forResult("a", 16000)));
    }
    @Test public void keepsTheCapturedAngleDuringUploadButRejectsLateRepliesAndClockReset() {
        CapturedSoundAngle window = new CapturedSoundAngle(); window.begin("a", 10000);
        window.observe(0, 11000); window.end("a", 12000);
        assertEquals(0, window.forResult("a", 20000), 0);
        assertTrue(Double.isNaN(window.forResult("a", 20001)));
        assertTrue(Double.isNaN(window.forResult("a", 1000)));
    }
    @Test public void ignoresInvalidDirectionsAndMismatchedAudioCompletion() {
        CapturedSoundAngle window = new CapturedSoundAngle(); window.begin("a", 10000);
        window.observe(-1, 11000); window.observe(Double.NaN, 11000); window.end("b", 12000);
        assertTrue(Double.isNaN(window.forResult("a", 13000)));
        window.observe(360, 11000); window.end("a", 12000);
        assertEquals(0, window.forResult("a", 13000), 0);
    }
    @Test public void aFinalScanCanRefineTheDirectionFromItsPreview() {
        CapturedSoundAngle window = new CapturedSoundAngle(); window.begin("a", 10000);
        window.end("a", 11400); assertTrue(window.observe(30, 11000));
        assertFalse(window.observe(90, 12000));
        window.end("a", 13000); assertTrue(window.observe(90, 12000));
        assertEquals(90, window.forResult("a", 14000), 0);
    }
}
