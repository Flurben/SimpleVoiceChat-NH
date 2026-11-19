package com.github.flurben.voicechat.voice.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

public final class ByteBufUtil {

    private ByteBufUtil() {}

    public static void writeUUID(ByteBuf buf, UUID id) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }

    public static UUID readUUID(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeString(ByteBuf buf, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, b.length);
        buf.writeBytes(b);
    }

    public static String readString(ByteBuf buf, int maxChars) {
        int len = readVarInt(buf);
        if (len < 0 || len > maxChars * 4) {
            throw new IllegalArgumentException("String too long: " + len);
        }
        byte[] b = new byte[len];
        buf.readBytes(b);
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.length() > maxChars) {
            throw new IllegalArgumentException("String too long: " + s.length());
        }
        return s;
    }

    public static void writeNullableString(ByteBuf buf, String s) {
        buf.writeBoolean(s != null);
        if (s != null) {
            writeString(buf, s);
        }
    }

    public static String readNullableString(ByteBuf buf, int maxChars) {
        return buf.readBoolean() ? readString(buf, maxChars) : null;
    }

    public static void writeBytes(ByteBuf buf, byte[] data) {
        writeVarInt(buf, data.length);
        buf.writeBytes(data);
    }

    public static byte[] readBytes(ByteBuf buf, int max) {
        int len = readVarInt(buf);
        if (len < 0 || len > max) {
            throw new IllegalArgumentException("Byte array too long: " + len);
        }
        byte[] b = new byte[len];
        buf.readBytes(b);
        return b;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            result |= (b & 127) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IllegalArgumentException("VarInt too big");
            }
        } while ((b & 128) != 0);
        return result;
    }
}
