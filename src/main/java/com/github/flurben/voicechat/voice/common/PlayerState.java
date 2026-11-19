package com.github.flurben.voicechat.voice.common;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

/**
 * The voice chat state of a player as everyone else sees it. The textures are the signed skin property of the player
 * profile, so other clients can draw the head in the group screens even when the player is far away.
 */
public class PlayerState {

    public final UUID uuid;
    public final String name;
    public volatile boolean disabled;
    public volatile boolean disconnected;
    public volatile UUID group;
    public volatile String textures;
    public volatile String texturesSignature;

    public PlayerState(UUID uuid, String name, boolean disabled, boolean disconnected) {
        this.uuid = uuid;
        this.name = name;
        this.disabled = disabled;
        this.disconnected = disconnected;
    }

    public boolean hasGroup() {
        return group != null;
    }

    public void write(ByteBuf buf) {
        ByteBufUtil.writeUUID(buf, uuid);
        ByteBufUtil.writeString(buf, name);
        buf.writeBoolean(disabled);
        buf.writeBoolean(disconnected);
        buf.writeBoolean(group != null);
        if (group != null) {
            ByteBufUtil.writeUUID(buf, group);
        }
        ByteBufUtil.writeNullableString(buf, textures);
        ByteBufUtil.writeNullableString(buf, texturesSignature);
    }

    public static PlayerState read(ByteBuf buf) {
        PlayerState s = new PlayerState(ByteBufUtil.readUUID(buf), ByteBufUtil.readString(buf, 64), buf.readBoolean(), buf.readBoolean());
        if (buf.readBoolean()) {
            s.group = ByteBufUtil.readUUID(buf);
        }
        s.textures = ByteBufUtil.readNullableString(buf, 32767);
        s.texturesSignature = ByteBufUtil.readNullableString(buf, 32767);
        return s;
    }
}
