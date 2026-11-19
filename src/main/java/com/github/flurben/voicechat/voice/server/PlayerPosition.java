package com.github.flurben.voicechat.voice.server;

/** A snapshot of where a player is, taken on the server thread every tick so the voice threads never touch the world. */
public class PlayerPosition {

    public final int dimension;
    public final double x, y, z;

    public PlayerPosition(int dimension, double x, double y, double z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double distanceSq(PlayerPosition o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
