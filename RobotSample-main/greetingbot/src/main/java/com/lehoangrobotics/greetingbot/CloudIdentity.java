package com.lehoangrobotics.greetingbot;

import java.security.SecureRandom;
import java.util.UUID;

/** Installation identity is saved before enrollment, so retries never rotate credentials. */
final class CloudIdentity {
    final String id, token;
    private CloudIdentity(String id, String token) { this.id = id; this.token = token; }
    static CloudIdentity restore(String id, String token) {
        if (id != null && id.matches("GREETING-[a-f0-9]{32}") && token != null && token.matches("[a-f0-9]{48}"))
            return new CloudIdentity(id, token);
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        StringBuilder secret = new StringBuilder(48);
        for (byte value : bytes) secret.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return new CloudIdentity("GREETING-" + UUID.randomUUID().toString().replace("-", ""), secret.toString());
    }
}
