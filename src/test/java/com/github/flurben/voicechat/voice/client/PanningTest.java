package com.github.flurben.voicechat.voice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.flurben.voicechat.voice.common.VoiceMessage;

class PanningTest {

    private static void setListener(double x, double y, double z, float yaw) throws Exception {
        Constructor<ClientPositions.Listener> c = ClientPositions.Listener.class.getDeclaredConstructor(double.class, double.class, double.class, float.class);
        c.setAccessible(true);
        Field f = ClientPositions.class.getDeclaredField("listener");
        f.setAccessible(true);
        f.set(null, c.newInstance(x, y, z, yaw));
    }

    private static VoiceMessage.Sound at(double x, double y, double z) {
        return new VoiceMessage.Sound(VoiceMessage.PLAYER_SOUND, UUID.randomUUID(), 0, false, 48F, x, y, z, new byte[1]);
    }

    @Test
    void directionAndDistance() throws Exception {
        // Facing south (+Z): east (+X) is on the left, west (-X) on the right
        setListener(0, 64, 0, 0F);
        float[] west = AudioOutput.computeGains(at(-10, 64, 0), AudioType.NORMAL);
        assertTrue(west[1] > west[0] * 3, "west should be on the right: " + west[0] + " " + west[1]);
        float[] east = AudioOutput.computeGains(at(10, 64, 0), AudioType.NORMAL);
        assertTrue(east[0] > east[1] * 3, "east should be on the left");
        float[] front = AudioOutput.computeGains(at(0, 64, 10), AudioType.NORMAL);
        assertEquals(front[0], front[1], 1e-4);

        // Linear distance attenuation, silent at the maximum distance
        float[] near = AudioOutput.computeGains(at(0, 64, 12), AudioType.OFF);
        assertEquals(0.75F, near[0], 0.01F);
        assertEquals(near[0], near[1], 1e-4);
        float[] far = AudioOutput.computeGains(at(0, 64, 60), AudioType.NORMAL);
        assertEquals(0F, far[0] + far[1], 1e-6);

        // Turned around (facing north) the sides swap
        setListener(0, 64, 0, 180F);
        float[] westTurned = AudioOutput.computeGains(at(-10, 64, 0), AudioType.NORMAL);
        assertTrue(westTurned[0] > westTurned[1] * 3);

        // Reduced 3D audio pans less
        setListener(0, 64, 0, 0F);
        float[] reduced = AudioOutput.computeGains(at(-10, 64, 0), AudioType.REDUCED);
        assertTrue(reduced[0] > west[0] && reduced[1] <= west[1] + 1e-4);

        // Group audio is not positional
        VoiceMessage.Sound group = new VoiceMessage.Sound(VoiceMessage.GROUP_SOUND, UUID.randomUUID(), 0, false, 0F, 1000, 0, 1000, new byte[1]);
        float[] g = AudioOutput.computeGains(group, AudioType.NORMAL);
        assertEquals(1F, g[0]);
        assertEquals(1F, g[1]);
    }
}
