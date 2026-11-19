package com.github.flurben.voicechat;

import com.github.flurben.voicechat.gui.VoiceChatScreenBase;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.RenderEvents;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        new ClientManager(VoiceChatMod.configDir);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        KeyEvents.register();
        KeyEvents keys = new KeyEvents();
        FMLCommonHandler.instance().bus().register(keys);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new RenderEvents());
    }

    @Override
    public boolean runLocalServer() {
        return ClientManager.INSTANCE.config().runLocalServer.get();
    }

    @Override
    public void handleClientPacket(ByteBuf payload) {
        ClientManager.INSTANCE.handlePacket(payload);
    }

    @Override
    public void onGroupChanged() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof VoiceChatScreenBase) {
            ((VoiceChatScreenBase) mc.currentScreen).onGroupChanged();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientManager.INSTANCE.tick(Minecraft.getMinecraft());
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().func_152344_a(() -> ClientManager.INSTANCE.disconnect());
    }
}
