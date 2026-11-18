package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class MicroArrayReadingTest {
    private String frame = "{\"source\":\"orion-microarray-native-debug\",\"angle\":0,\"atUnixMs\":11000,\"sampleCount\":4,\"spreadDegrees\":2,\"captureStartUnixMs\":10000,\"captureEndUnixMs\":12000}";
    @Test public void acceptsRealZeroAndConvertsToTheOriginalMonotonicClock() {
        MicroArrayReading reading = MicroArrayReading.parse(frame, 10000, 12000, 9000);
        assertNotNull(reading); assertEquals(0, reading.angle, 0); assertEquals(2000, reading.atElapsed);
    }
    @Test public void rejectsWrongSourceOrAnotherAudioWindow() {
        assertNull(MicroArrayReading.parse(frame.replace("native-debug", "guessed"), 10000, 12000, 9000));
        assertNull(MicroArrayReading.parse(frame, 10001, 12000, 9000));
        assertNull(MicroArrayReading.parse(frame.replace("11000", "13000"), 10000, 12000, 9000));
    }
    @Test public void rejectsUnstableMissingOrInvalidDirections() {
        assertNull(MicroArrayReading.parse(frame.replace("\"sampleCount\":4", "\"sampleCount\":1"), 10000, 12000, 9000));
        assertNull(MicroArrayReading.parse(frame.replace("\"spreadDegrees\":2", "\"spreadDegrees\":36"), 10000, 12000, 9000));
        assertNull(MicroArrayReading.parse(frame.replace("\"angle\":0", "\"angle\":-1"), 10000, 12000, 9000));
        assertNull(MicroArrayReading.parse("{}", 10000, 12000, 9000));
        assertNull(MicroArrayReading.parse("null", 10000, 12000, 9000));
    }
}
