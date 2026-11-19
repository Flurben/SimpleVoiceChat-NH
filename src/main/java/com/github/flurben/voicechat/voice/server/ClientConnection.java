package com.github.flurben.voicechat.voice.server;

import java.net.SocketAddress;
import java.util.UUID;

/** The voice connection of one player. Either a UDP address or the tunnel through the Minecraft connection. */
public class ClientConnection {

    public final UUID player;
    public final byte[] key;
    public volatile SocketAddress address;
    public volatile boolean tunnel;
    public volatile boolean authenticated;
    public volatile long lastKeepAlive;
    public volatile long created;

    public ClientConnection(UUID player, byte[] key) {
        this.player = player;
        this.key = key;
        this.created = System.currentTimeMillis();
        this.lastKeepAlive = created;
    }
}
