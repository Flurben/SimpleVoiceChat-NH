package com.github.flurben.voicechat.voice.common;

import java.security.GeneralSecurityException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Messages sent over the voice connection (UDP, or tunnelled through the Minecraft connection).
 * <p>
 * Client to server datagrams: {@code [MAGIC][player uuid][IV + encrypted body]}<br>
 * Server to client datagrams: {@code [MAGIC][IV + encrypted body]}<br>
 * Body: {@code [type][fields...]}
 */
public abstract class VoiceMessage {

    public static final byte MAGIC = (byte) 0xF7;
    public static final int MAX_DATAGRAM = 4096;

    public static final byte MIC = 1;
    public static final byte PLAYER_SOUND = 2;
    public static final byte GROUP_SOUND = 3;
    public static final byte LOCATION_SOUND = 4;
    public static final byte AUTHENTICATE = 5;
    public static final byte AUTHENTICATE_ACK = 6;
    public static final byte KEEP_ALIVE = 7;
    public static final byte PING = 8;

    public abstract byte type();

    protected abstract void writeBody(ByteBuf buf);

    public byte[] encodeBody() {
        ByteBuf buf = Unpooled.buffer(256);
        buf.writeByte(type());
        writeBody(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    public static VoiceMessage decodeBody(byte[] body) {
        ByteBuf buf = Unpooled.wrappedBuffer(body);
        byte type = buf.readByte();
        switch (type) {
            case MIC:
                return new Mic(buf.readLong(), buf.readBoolean(), ByteBufUtil.readBytes(buf, MAX_DATAGRAM));
            case PLAYER_SOUND:
            case GROUP_SOUND:
            case LOCATION_SOUND:
                return Sound.read(type, buf);
            case AUTHENTICATE:
                return new Authenticate();
            case AUTHENTICATE_ACK:
                return new AuthenticateAck();
            case KEEP_ALIVE:
                return new KeepAlive();
            case PING:
                return new Ping(buf.readLong(), buf.readLong());
            default:
                throw new IllegalArgumentException("Unknown voice message " + type);
        }
    }

    /** Encodes a message sent by a client, the server finds the key by the player uuid. */
    public static byte[] encodeFromClient(UUID player, byte[] key, VoiceMessage msg) throws GeneralSecurityException {
        byte[] body = msg.encodeBody();
        byte[] enc = VoiceCrypto.encrypt(key, body, 0, body.length);
        ByteBuf buf = Unpooled.buffer(1 + 16 + enc.length);
        buf.writeByte(MAGIC);
        ByteBufUtil.writeUUID(buf, player);
        buf.writeBytes(enc);
        return buf.array();
    }

    public static byte[] encodeFromServer(byte[] key, VoiceMessage msg) throws GeneralSecurityException {
        byte[] body = msg.encodeBody();
        byte[] enc = VoiceCrypto.encrypt(key, body, 0, body.length);
        byte[] out = new byte[1 + enc.length];
        out[0] = MAGIC;
        System.arraycopy(enc, 0, out, 1, enc.length);
        return out;
    }

    public static VoiceMessage decodeFromServer(byte[] key, byte[] data, int len) throws GeneralSecurityException {
        if (len < 1 || data[0] != MAGIC) {
            throw new GeneralSecurityException("Invalid packet");
        }
        return decodeBody(VoiceCrypto.decrypt(key, data, 1, len - 1));
    }

    /** Reads the sender of a client datagram, or null if it is not a voice packet. */
    public static UUID readClientSender(byte[] data, int len) {
        if (len < 17 || data[0] != MAGIC) {
            return null;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(data, 1, 16);
        return ByteBufUtil.readUUID(buf);
    }

    public static VoiceMessage decodeFromClient(byte[] key, byte[] data, int len) throws GeneralSecurityException {
        return decodeBody(VoiceCrypto.decrypt(key, data, 17, len - 17));
    }

    /** Opus audio from the microphone. Empty data marks the end of a sentence. */
    public static class Mic extends VoiceMessage {
        public final long sequence;
        public final boolean whispering;
        public final byte[] data;

        public Mic(long sequence, boolean whispering, byte[] data) {
            this.sequence = sequence;
            this.whispering = whispering;
            this.data = data;
        }

        @Override
        public byte type() {
            return MIC;
        }

        @Override
        protected void writeBody(ByteBuf buf) {
            buf.writeLong(sequence);
            buf.writeBoolean(whispering);
            ByteBufUtil.writeBytes(buf, data);
        }
    }

    /** Audio from another player, either positional (proximity), at a location, or from the group. */
    public static class Sound extends VoiceMessage {
        public final byte kind;
        public final UUID sender;
        public final long sequence;
        public final boolean whispering;
        public final float distance;
        public final double x, y, z;
        public final byte[] data;

        public Sound(byte kind, UUID sender, long sequence, boolean whispering, float distance, double x, double y, double z, byte[] data) {
            this.kind = kind;
            this.sender = sender;
            this.sequence = sequence;
            this.whispering = whispering;
            this.distance = distance;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        @Override
        public byte type() {
            return kind;
        }

        @Override
        protected void writeBody(ByteBuf buf) {
            ByteBufUtil.writeUUID(buf, sender);
            buf.writeLong(sequence);
            buf.writeBoolean(whispering);
            buf.writeFloat(distance);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            ByteBufUtil.writeBytes(buf, data);
        }

        static Sound read(byte kind, ByteBuf buf) {
            return new Sound(kind, ByteBufUtil.readUUID(buf), buf.readLong(), buf.readBoolean(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                ByteBufUtil.readBytes(buf, MAX_DATAGRAM));
        }

        public boolean isGroup() {
            return kind == GROUP_SOUND;
        }
    }

    public static class Authenticate extends VoiceMessage {
        @Override
        public byte type() {
            return AUTHENTICATE;
        }

        @Override
        protected void writeBody(ByteBuf buf) {}
    }

    public static class AuthenticateAck extends VoiceMessage {
        @Override
        public byte type() {
            return AUTHENTICATE_ACK;
        }

        @Override
        protected void writeBody(ByteBuf buf) {}
    }

    public static class KeepAlive extends VoiceMessage {
        @Override
        public byte type() {
            return KEEP_ALIVE;
        }

        @Override
        protected void writeBody(ByteBuf buf) {}
    }

    /** Round trip measurement, sent by the server and echoed by the client. */
    public static class Ping extends VoiceMessage {
        public final long id;
        public final long timestamp;

        public Ping(long id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        @Override
        public byte type() {
            return PING;
        }

        @Override
        protected void writeBody(ByteBuf buf) {
            buf.writeLong(id);
            buf.writeLong(timestamp);
        }
    }
}
