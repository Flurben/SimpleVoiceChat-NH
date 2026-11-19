package com.github.flurben.voicechat.voice.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Hooks for the in-game test (src/ingametest). They are unused in normal play: the test replaces the microphone with
 * a generated signal and silences the speaker, so it never records or plays real audio, and it reads what was mixed.
 */
public final class TestHooks {

    /** Replaces the microphone when set. Called every 20 ms for one frame. */
    public static volatile Supplier<short[]> microphone;
    /** Writes silence to the speaker instead of the mix. */
    public static volatile boolean muteOutput;
    /** Overrides the held state of the push to talk and whisper keys when set. */
    public static volatile Boolean pttKey;
    public static volatile Boolean whisperKey;

    /** Frames mixed per sender, and the last left/right gains. */
    public static final Map<UUID, AtomicInteger> framesPlayed = new ConcurrentHashMap<>();
    public static final Map<UUID, float[]> lastGains = new ConcurrentHashMap<>();

    private TestHooks() {}

    static void played(UUID sender, float left, float right) {
        framesPlayed.computeIfAbsent(sender, k -> new AtomicInteger()).incrementAndGet();
        lastGains.put(sender, new float[] {left, right});
    }

    public static int played(UUID sender) {
        AtomicInteger i = framesPlayed.get(sender);
        return i == null ? 0 : i.get();
    }

    public static void reset() {
        framesPlayed.clear();
        lastGains.clear();
    }
}
