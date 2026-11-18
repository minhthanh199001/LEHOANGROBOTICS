package com.lehoangrobotics.greetingbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/** Keyword and freshness checks, independent of Android and the physical robot. */
final class VoiceDirectionPolicy {
    static final long ANGLE_MAX_AGE_MS = 5000;
    static final long COOLDOWN_MS = 500;
    private static final Pattern KEYWORD = Pattern.compile("(?:^| )(hello robot|robot oi)(?: |$)");
    private final LinkedHashSet<String> consumed = new LinkedHashSet<>();
    private boolean enabled, turning, consumedUtterance;
    private long angleAt = Long.MIN_VALUE, cooldownUntil;
    private double wakeAngle = Double.NaN;

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ")
            .replaceAll("\\bro bot\\b", "robot");
    }
    static String keyword(String text) {
        String normalized = normalize(text);
        java.util.regex.Matcher match = KEYWORD.matcher(normalized);
        if (!match.find()) return "";
        return match.group(1).equals("hello robot") ? "Hello robot" : "robot ơi";
    }
    static boolean validAngle(double angle) { return !Double.isNaN(angle) && !Double.isInfinite(angle) && angle >= 0 && angle <= 360; }

    static String transcript(String raw) {
        if (raw == null) return "";
        if (!raw.trim().startsWith("{")) return raw;
        try { return text(new JsonParser().parse(raw), 0); } catch (Exception e) { return ""; }
    }
    private static String text(JsonElement element, int depth) {
        if (depth > 4 || element == null || !element.isJsonObject()) return "";
        JsonObject object = element.getAsJsonObject();
        for (String key : new String[]{"userText", "asrResult", "asrText", "text", "query"}) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        }
        for (String key : new String[]{"data", "result", "asr_content", "asr"}) {
            String nested = text(object.get(key), depth + 1); if (!nested.isEmpty()) return nested;
        }
        return "";
    }
    static double soundAngle(String payload, boolean wakeEvent) {
        if (payload == null || payload.trim().isEmpty()) return Double.NaN;
        try {
            JsonElement root = new JsonParser().parse(payload);
            if (wakeEvent && root.isJsonPrimitive()) return checked(root.getAsDouble());
            return angle(root, wakeEvent, 0);
        } catch (Exception e) { return Double.NaN; }
    }
    private static double checked(double value) { return validAngle(value) ? value % 360 : Double.NaN; }
    private static double angle(JsonElement element, boolean wakeEvent, int depth) {
        if (depth > 4 || element == null || !element.isJsonObject()) return Double.NaN;
        JsonObject object = element.getAsJsonObject();
        String[] keys = wakeEvent ? new String[]{"soundAngle", "sound_angle", "audio_angle", "wakeupAngle", "angle"}
            : new String[]{"soundAngle", "sound_angle", "audio_angle", "wakeupAngle"};
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                try { double number = checked(value.getAsDouble()); if (validAngle(number)) return number; } catch (Exception ignored) {}
            }
        }
        for (String key : new String[]{"data", "params", "result", "slots"}) {
            double nested = angle(object.get(key), wakeEvent, depth + 1);
            if (validAngle(nested)) return nested;
        }
        return Double.NaN;
    }

    void start() { enabled = true; turning = false; consumedUtterance = false; cooldownUntil = 0; clearAngle(); consumed.clear(); }
    void stop() { enabled = false; clearAngle(); }
    void speechStarted(long now) {
        consumedUtterance = false;
        // A wake event can precede ASR start, but a previous utterance's angle must not carry over.
        if (angleAt == Long.MIN_VALUE || now - angleAt > 750) clearAngle();
    }
    void wakeAngle(double angle, long now) { if (enabled && !turning && validAngle(angle)) { wakeAngle = angle % 360; angleAt = now; } }
    void clearAngle() { wakeAngle = Double.NaN; angleAt = Long.MIN_VALUE; }
    boolean consumed(String eventId) { return eventId != null && consumed.contains(eventId); }
    double select(String heard, double explicitAngle, String eventId, long now) {
        if (!enabled || turning || consumedUtterance || now < cooldownUntil || keyword(heard).isEmpty()) return Double.NaN;
        if (eventId != null && consumed.contains(eventId)) return Double.NaN;
        double angle = checked(explicitAngle);
        if (!validAngle(angle) && angleAt != Long.MIN_VALUE && now >= angleAt && now - angleAt <= ANGLE_MAX_AGE_MS) angle = wakeAngle;
        if (!validAngle(angle)) return Double.NaN;
        consumedUtterance = true; turning = true; clearAngle();
        if (eventId != null && !eventId.isEmpty()) {
            consumed.add(eventId);
            if (consumed.size() > 64) consumed.remove(consumed.iterator().next());
        }
        return angle;
    }
    void finished(long now) { turning = false; cooldownUntil = now + COOLDOWN_MS; clearAngle(); }
}
