package com.github.flurben.voicechat.net;

import java.util.Collection;
import java.util.function.Consumer;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.voice.common.ByteBufUtil;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.PlayerState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

/**
 * Packets sent over the Minecraft connection. Packets are sent as plain custom payloads straight to the network
 * manager, which is safe from any thread (the voice threads use it for the tunnel fallback).
 */
public final class VoiceNetwork {

    public static final String CHANNEL = "voicechat";
    /** Bumped whenever the packets change in an incompatible way. */
    public static final int PROTOCOL = 1;

    // Client -> server
    public static final int C_REQUEST_SECRET = 0;
    public static final int C_UPDATE_STATE = 1;
    public static final int C_CREATE_GROUP = 2;
    public static final int C_JOIN_GROUP = 3;
    public static final int C_LEAVE_GROUP = 4;
    public static final int C_TUNNEL = 5;
    public static final int C_USE_TUNNEL = 6;

    // Server -> client
    public static final int S_SECRET = 0;
    public static final int S_PLAYER_STATES = 1;
    public static final int S_PLAYER_STATE = 2;
    public static final int S_REMOVE_PLAYER_STATE = 3;
    public static final int S_ADD_GROUP = 4;
    public static final int S_REMOVE_GROUP = 5;
    public static final int S_JOINED_GROUP = 6;
    public static final int S_TUNNEL = 7;
    public static final int S_INCOMPATIBLE = 8;

    public static FMLEventChannel channel;
    private static ServerPacketHandler serverHandler;

    private VoiceNetwork() {}

    public interface ServerPacketHandler {
        void handle(EntityPlayerMP player, ByteBuf buf);
    }

    public static void init(ServerPacketHandler server) {
        serverHandler = server;
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNEL);
        channel.register(new VoiceNetwork.Listener());
    }

    public static class Listener {
        @SubscribeEvent
        public void onServerPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
            EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
            try {
                serverHandler.handle(player, event.packet.payload());
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Invalid voice chat packet from {}: {}", player.getCommandSenderName(), e.toString());
            }
        }

        @SubscribeEvent
        public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
            try {
                VoiceChatMod.proxy.handleClientPacket(event.packet.payload());
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Invalid voice chat packet from the server", e);
            }
        }
    }

    public static ByteBuf buffer(int id) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(id);
        return buf;
    }

    public static void sendToPlayer(EntityPlayerMP player, ByteBuf buf) {
        if (player.playerNetServerHandler == null) {
            return;
        }
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        player.playerNetServerHandler.func_147362_b().scheduleOutboundPacket(new S3FPacketCustomPayload(CHANNEL, data));
    }

    public static void sendToPlayers(Collection<EntityPlayerMP> players, Consumer<ByteBuf> writer, int id) {
        for (EntityPlayerMP p : players) {
            ByteBuf buf = buffer(id);
            writer.accept(buf);
            sendToPlayer(p, buf);
        }
    }

    // Server -> client builders

    public static ByteBuf secret(SecretData data) {
        ByteBuf buf = buffer(S_SECRET);
        data.write(buf);
        return buf;
    }

    public static ByteBuf playerStates(Collection<PlayerState> states) {
        ByteBuf buf = buffer(S_PLAYER_STATES);
        buf.writeInt(states.size());
        for (PlayerState s : states) {
            s.write(buf);
        }
        return buf;
    }

    public static ByteBuf playerState(PlayerState s) {
        ByteBuf buf = buffer(S_PLAYER_STATE);
        s.write(buf);
        return buf;
    }

    public static ByteBuf removePlayerState(java.util.UUID id) {
        ByteBuf buf = buffer(S_REMOVE_PLAYER_STATE);
        ByteBufUtil.writeUUID(buf, id);
        return buf;
    }

    public static ByteBuf addGroup(ClientGroup g) {
        ByteBuf buf = buffer(S_ADD_GROUP);
        g.write(buf);
        return buf;
    }

    public static ByteBuf removeGroup(java.util.UUID id) {
        ByteBuf buf = buffer(S_REMOVE_GROUP);
        ByteBufUtil.writeUUID(buf, id);
        return buf;
    }

    public static ByteBuf joinedGroup(ClientGroup g, boolean wrongPassword) {
        ByteBuf buf = buffer(S_JOINED_GROUP);
        buf.writeBoolean(g != null);
        if (g != null) {
            g.write(buf);
        }
        buf.writeBoolean(wrongPassword);
        return buf;
    }

    public static ByteBuf tunnelToClient(byte[] data) {
        ByteBuf buf = buffer(S_TUNNEL);
        ByteBufUtil.writeBytes(buf, data);
        return buf;
    }

    public static ByteBuf incompatible(String serverVersion, int protocol) {
        ByteBuf buf = buffer(S_INCOMPATIBLE);
        ByteBufUtil.writeString(buf, serverVersion);
        buf.writeInt(protocol);
        return buf;
    }
}
