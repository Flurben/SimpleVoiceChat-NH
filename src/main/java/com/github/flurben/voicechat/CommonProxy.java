package com.github.flurben.voicechat;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import io.netty.buffer.ByteBuf;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    /** If the integrated server should host the voice chat (singleplayer and LAN). */
    public boolean runLocalServer() {
        return true;
    }

    public void handleClientPacket(ByteBuf payload) {}

    public void onGroupChanged() {}
}
