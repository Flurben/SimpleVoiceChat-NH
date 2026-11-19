package com.github.flurben.voicechat.voice.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Where the listener and the other players are, captured on the client thread every frame so the audio threads never
 * read entities directly.
 */
public final class ClientPositions {

    public static final class Pos {
        public final double x, y, z;

        public Pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Listener {
        public final double x, y, z;
        public final float yaw;

        Listener(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    private static volatile Listener listener;
    private static volatile Map<UUID, Pos> players = new HashMap<>();

    private ClientPositions() {}

    public static void update(Minecraft mc, float partialTicks) {
        EntityLivingBase view = mc.renderViewEntity;
        if (view == null || mc.theWorld == null) {
            listener = null;
            players = new HashMap<>();
            return;
        }
        double x = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
        double y = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
        double z = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
        // The bounding box is at the feet for every entity, posY is not for the local player
        double feet = view.boundingBox.minY + (y - view.posY);
        double eye = feet + (view instanceof EntityPlayer ? 1.62D : view.getEyeHeight());
        listener = new Listener(x, eye, z, view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks);
        Map<UUID, Pos> map = new HashMap<>();
        for (Object o : mc.theWorld.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            double px = p.prevPosX + (p.posX - p.prevPosX) * partialTicks;
            double pz = p.prevPosZ + (p.posZ - p.prevPosZ) * partialTicks;
            double py = p.boundingBox.minY + ((p.prevPosY + (p.posY - p.prevPosY) * partialTicks) - p.posY) + 1.62D;
            map.put(p.getUniqueID(), new Pos(px, py, pz));
        }
        players = map;
    }

    public static Listener getListener() {
        return listener;
    }

    public static Pos getPlayer(UUID id) {
        return players.get(id);
    }

    public static void clear() {
        listener = null;
        players = new HashMap<>();
    }
}
