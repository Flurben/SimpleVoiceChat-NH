package com.github.flurben.voicechat.voice.common;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

/** What clients know about a group. The password itself never leaves the server. */
public class ClientGroup {

    public final UUID id;
    public final String name;
    public final boolean hasPassword;
    public final GroupType type;

    public ClientGroup(UUID id, String name, boolean hasPassword, GroupType type) {
        this.id = id;
        this.name = name;
        this.hasPassword = hasPassword;
        this.type = type;
    }

    public void write(ByteBuf buf) {
        ByteBufUtil.writeUUID(buf, id);
        ByteBufUtil.writeString(buf, name);
        buf.writeBoolean(hasPassword);
        buf.writeByte(type.ordinal());
    }

    public static ClientGroup read(ByteBuf buf) {
        return new ClientGroup(ByteBufUtil.readUUID(buf), ByteBufUtil.readString(buf, 64), buf.readBoolean(), GroupType.byId(buf.readByte()));
    }
}
