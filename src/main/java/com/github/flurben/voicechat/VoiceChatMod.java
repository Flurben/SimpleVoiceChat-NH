package com.github.flurben.voicechat;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.flurben.voicechat.command.VoiceChatCommand;
import com.github.flurben.voicechat.config.ServerConfig;
import com.github.flurben.voicechat.net.VoiceNetwork;
import com.github.flurben.voicechat.voice.server.ServerVoiceChat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

@Mod(modid = VoiceChatMod.MODID, name = VoiceChatMod.NAME, version = Tags.VERSION, acceptableRemoteVersions = "*", guiFactory = "com.github.flurben.voicechat.gui.VoiceChatGuiFactory")
public class VoiceChatMod {

    public static final String MODID = "voicechat";
    public static final String NAME = "Simple Voice Chat NH";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @SidedProxy(clientSide = "com.github.flurben.voicechat.ClientProxy", serverSide = "com.github.flurben.voicechat.CommonProxy")
    public static CommonProxy proxy;

    public static File configDir;
    public static ServerConfig serverConfig;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configDir = new File(event.getModConfigurationDirectory(), "voicechat");
        configDir.mkdirs();
        serverConfig = new ServerConfig(new File(configDir, "voicechat-server.properties"));
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        VoiceNetwork.init(VoiceChatMod::onServerPacket);
        FMLCommonHandler.instance().bus().register(this);
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new VoiceChatCommand());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server.isDedicatedServer() || proxy.runLocalServer()) {
            ServerVoiceChat.start(server, serverConfig);
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerVoiceChat.stop();
    }

    /** Called on the network thread. Tunnelled audio is handled right away, everything else on the server thread. */
    private static void onServerPacket(EntityPlayerMP player, ByteBuf payload) {
        ServerVoiceChat server = ServerVoiceChat.get();
        if (server == null) {
            return;
        }
        ByteBuf copy = Unpooled.copiedBuffer(payload);
        if (copy.readableBytes() > 0 && copy.getByte(copy.readerIndex()) == VoiceNetwork.C_TUNNEL) {
            server.handlePacket(player, copy);
            return;
        }
        server.runOnMain(() -> {
            try {
                server.handlePacket(player, copy);
            } catch (RuntimeException e) {
                LOGGER.warn("Invalid voice chat packet from {}: {}", player.getCommandSenderName(), e.toString());
            }
        });
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerVoiceChat s = ServerVoiceChat.get();
            if (s != null) {
                s.tick();
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerVoiceChat s = ServerVoiceChat.get();
        if (s != null && event.player instanceof EntityPlayerMP) {
            s.onPlayerJoin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerVoiceChat s = ServerVoiceChat.get();
        if (s != null && event.player instanceof EntityPlayerMP) {
            s.onPlayerLeave((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerVoiceChat s = ServerVoiceChat.get();
        if (s != null && event.player instanceof EntityPlayerMP) {
            s.onDimensionChange((EntityPlayerMP) event.player);
        }
    }
}
