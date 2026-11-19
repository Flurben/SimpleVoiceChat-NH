package com.github.flurben.voicechat.voice.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers who talked when, for the icons above heads and in the group HUD. */
public final class TalkCache {

    private static final long TIMEOUT = 250L;

    private static final Map<UUID, Long> talking = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> whispering = new ConcurrentHashMap<>();

    private TalkCache() {}

    public static void update(UUID player, boolean whisper) {
        long now = System.currentTimeMillis();
        talking.put(player, now);
        if (whisper) {
            whispering.put(player, now);
        } else {
            whispering.remove(player);
        }
    }

    public static boolean isTalking(UUID player) {
        Long t = talking.get(player);
        return t != null && System.currentTimeMillis() - t < TIMEOUT;
    }

    public static boolean isWhispering(UUID player) {
        Long t = whispering.get(player);
        return t != null && System.currentTimeMillis() - t < TIMEOUT;
    }

    public static void clear() {
        talking.clear();
        whispering.clear();
    }
}
