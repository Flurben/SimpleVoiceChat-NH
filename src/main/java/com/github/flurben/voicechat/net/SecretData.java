package com.github.flurben.voicechat.net;

import com.github.flurben.voicechat.voice.common.ByteBufUtil;
import com.github.flurben.voicechat.voice.common.Codec;

import io.netty.buffer.ByteBuf;

/** Everything a client needs to connect to the voice server, sent over the Minecraft connection. */
public class SecretData {

    /** The uuid the server knows the player by, which differs from the session uuid on offline mode servers. */
    public final java.util.UUID playerId;
    public final byte[] key;
    public final int port;
    public final String host;
    public final Codec codec;
    public final int mtuSize;
    public final double voiceDistance;
    public final double whisperDistance;
    public final int keepAlive;
    public final boolean groupsEnabled;
    public final boolean allowRecording;
    public final boolean allowTunnel;

    public SecretData(java.util.UUID playerId, byte[] key, int port, String host, Codec codec, int mtuSize, double voiceDistance, double whisperDistance, int keepAlive, boolean groupsEnabled,
        boolean allowRecording, boolean allowTunnel) {
        this.playerId = playerId;
        this.key = key;
        this.port = port;
        this.host = host;
        this.codec = codec;
        this.mtuSize = mtuSize;
        this.voiceDistance = voiceDistance;
        this.whisperDistance = whisperDistance;
        this.keepAlive = keepAlive;
        this.groupsEnabled = groupsEnabled;
        this.allowRecording = allowRecording;
        this.allowTunnel = allowTunnel;
    }

    public void write(ByteBuf buf) {
        ByteBufUtil.writeUUID(buf, playerId);
        ByteBufUtil.writeBytes(buf, key);
        buf.writeInt(port);
        ByteBufUtil.writeString(buf, host);
        buf.writeByte(codec.ordinal());
        buf.writeInt(mtuSize);
        buf.writeDouble(voiceDistance);
        buf.writeDouble(whisperDistance);
        buf.writeInt(keepAlive);
        buf.writeBoolean(groupsEnabled);
        buf.writeBoolean(allowRecording);
        buf.writeBoolean(allowTunnel);
    }

    public static SecretData read(ByteBuf buf) {
        java.util.UUID playerId = ByteBufUtil.readUUID(buf);
        byte[] key = ByteBufUtil.readBytes(buf, 64);
        int port = buf.readInt();
        String host = ByteBufUtil.readString(buf, 512);
        int c = buf.readByte();
        Codec codec = c >= 0 && c < Codec.values().length ? Codec.values()[c] : Codec.VOIP;
        return new SecretData(playerId, key, port, host, codec, buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean());
    }
}
