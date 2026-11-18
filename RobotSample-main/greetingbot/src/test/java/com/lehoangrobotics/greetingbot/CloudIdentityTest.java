package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class CloudIdentityTest {
    @Test public void identitySurvivesRetryAndRestart() {
        CloudIdentity first = CloudIdentity.restore("", "");
        CloudIdentity restored = CloudIdentity.restore(first.id, first.token);
        assertEquals(first.id, restored.id); assertEquals(first.token, restored.token);
        assertTrue(first.id.matches("GREETING-[a-f0-9]{32}")); assertTrue(first.token.matches("[a-f0-9]{48}"));
    }
    @Test public void differentInstallationsDoNotShareCredentials() {
        CloudIdentity first = CloudIdentity.restore("", ""), second = CloudIdentity.restore("", "");
        assertNotEquals(first.id, second.id); assertNotEquals(first.token, second.token);
    }
    @Test public void damagedCredentialCreatesNewIdentityInsteadOfTakingOverOldId() {
        CloudIdentity first = CloudIdentity.restore("", "");
        assertNotEquals(first.id, CloudIdentity.restore(first.id, "damaged").id);
        assertNotEquals(first.token, CloudIdentity.restore("../invalid", first.token).token);
    }
}
