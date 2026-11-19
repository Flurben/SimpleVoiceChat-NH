package com.github.flurben.voicechat.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.common.PlayerState;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/** Drawing helpers shared by the screens and the HUD: panels, icons from the atlas and player heads. */
public final class GuiDraw {

    public static final ResourceLocation ICONS = new ResourceLocation(VoiceChatMod.MODID, "textures/gui/icons.png");

    // Atlas indices, see tools/make_textures.py
    public static final int MICROPHONE = 0;
    public static final int MICROPHONE_OFF = 1;
    public static final int MICROPHONE_WHISPER = 2;
    public static final int MICROPHONE_FORCED_OFF = 3;
    public static final int SPEAKER = 4;
    public static final int SPEAKER_OFF = 5;
    public static final int SPEAKER_WHISPER = 6;
    public static final int SPEAKER_FORCED_OFF = 7;
    public static final int DISCONNECTED = 8;
    public static final int LOCK_LIGHT = 9;
    public static final int BUTTON_MIC = 16;
    public static final int BUTTON_MIC_OFF = 17;
    public static final int BUTTON_SPEAKER = 18;
    public static final int BUTTON_SPEAKER_OFF = 19;
    public static final int BUTTON_HIDE = 20;
    public static final int BUTTON_HIDE_OFF = 21;
    public static final int BUTTON_RECORD = 22;
    public static final int BUTTON_RECORD_ON = 23;
    public static final int BUTTON_VOLUMES = 24;
    public static final int LOCK = 25;
    public static final int DEVICE_SELECTED = 26;
    public static final int TEST_SPEAKER = 27;
    public static final int OTHER_VOLUME = 28;
    public static final int LEAVE = 32;
    public static final int GROUP = 33;
    public static final int BUTTON_GROUP_HUD = 34;
    public static final int BUTTON_GROUP_HUD_OFF = 35;
    public static final int INVITE = 36;

    /** Text color inside panels, the dark gray of vanilla container titles. */
    public static final int FONT_COLOR = 0x404040;

    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<>();
    private static final Map<UUID, Boolean> REQUESTED = new HashMap<>();

    private GuiDraw() {}

    /** A vanilla style window: light gray with a bevel and a dark rounded border. */
    public static void panel(int x, int y, int w, int h) {
        int black = 0xFF000000, white = 0xFFFFFFFF, gray = 0xFFC6C6C6, shade = 0xFF555555;
        Gui.drawRect(x + 2, y, x + w - 2, y + 1, black);
        Gui.drawRect(x + 2, y + h - 1, x + w - 2, y + h, black);
        Gui.drawRect(x, y + 2, x + 1, y + h - 2, black);
        Gui.drawRect(x + w - 1, y + 2, x + w, y + h - 2, black);
        Gui.drawRect(x + 1, y + 1, x + 2, y + 2, black);
        Gui.drawRect(x + w - 2, y + 1, x + w - 1, y + 2, black);
        Gui.drawRect(x + 1, y + h - 2, x + 2, y + h - 1, black);
        Gui.drawRect(x + w - 2, y + h - 2, x + w - 1, y + h - 1, black);
        Gui.drawRect(x + 1, y + 2, x + w - 1, y + h - 2, gray);
        Gui.drawRect(x + 2, y + 1, x + w - 2, y + h - 1, gray);
        // Bevel
        Gui.drawRect(x + 2, y + 1, x + w - 3, y + 3, white);
        Gui.drawRect(x + 1, y + 2, x + 3, y + h - 3, white);
        Gui.drawRect(x + 3, y + h - 3, x + w - 2, y + h - 1, shade);
        Gui.drawRect(x + w - 3, y + 3, x + w - 1, y + h - 2, shade);
        Gui.drawRect(x + 3, y + 3, x + w - 3, y + h - 3, gray);
    }

    /** A sunken area inside a panel, like a slot or the list backgrounds. */
    public static void inset(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF373737);
        Gui.drawRect(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
    }

    public static void darkInset(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF373737);
        Gui.drawRect(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2B2B2B);
    }

    public static void icon(int index, double x, double y, double size) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICONS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        double u = (index % 16) * 16D / 256D, v = (index / 16) * 16D / 256D, s = 16D / 256D;
        quad(x, y, size, size, u, v, u + s, v + s);
    }

    public static void quad(double x, double y, double w, double h, double u0, double v0, double u1, double v1) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0D, u0, v1);
        t.addVertexWithUV(x + w, y + h, 0D, u1, v1);
        t.addVertexWithUV(x + w, y, 0D, u1, v0);
        t.addVertexWithUV(x, y, 0D, u0, v0);
        t.draw();
    }

    /** Draws the face and hat layer of a player skin. */
    public static void head(UUID player, double x, double y, double size) {
        ResourceLocation skin = getSkin(player);
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        // 1.7.10 skins are 64x32, mods in GT New Horizons can load modern 64x64 skins
        double h = skinHeight(skin);
        quad(x, y, size, size, 8D / 64D, 8D / h, 16D / 64D, 16D / h);
        quad(x, y, size, size, 40D / 64D, 8D / h, 48D / 64D, 16D / h);
    }

    private static final Map<ResourceLocation, Double> HEIGHTS = new HashMap<>();

    private static double skinHeight(ResourceLocation skin) {
        Double cached = HEIGHTS.get(skin);
        if (cached != null) {
            return cached;
        }
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 1 || h <= 1) {
            // Not uploaded yet, ask again next frame
            return 32D;
        }
        double height = w == h ? 64D : 32D;
        HEIGHTS.put(skin, height);
        return height;
    }

    private static boolean isSteve(ResourceLocation loc) {
        return loc == null || loc.getResourcePath().equals(AbstractClientPlayer.locationStevePng.getResourcePath());
    }

    public static ResourceLocation getSkin(UUID player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            EntityPlayer p = mc.theWorld.func_152378_a(player);
            if (p instanceof AbstractClientPlayer) {
                ResourceLocation loc = ((AbstractClientPlayer) p).getLocationSkin();
                if (!isSteve(loc)) {
                    return loc;
                }
            }
        }
        ResourceLocation cached = SKINS.get(player);
        if (cached != null) {
            return cached;
        }
        if (!REQUESTED.containsKey(player)) {
            REQUESTED.put(player, Boolean.TRUE);
            PlayerState state = ClientManager.INSTANCE == null ? null : ClientManager.INSTANCE.getState(player);
            String name = state != null ? state.name : ClientManager.INSTANCE == null ? null : ClientManager.INSTANCE.getName(player);
            GameProfile profile;
            GameProfile session = mc.getSession().func_148256_e();
            if (state != null && state.textures != null) {
                profile = new GameProfile(player, name);
                profile.getProperties().put("textures", new Property("textures", state.textures, state.texturesSignature));
            } else if (session != null && session.getName() != null && session.getName().equals(name)) {
                // Our own head: the skin manager fetches the textures of the session profile itself
                profile = session;
            } else {
                profile = new GameProfile(player, name);
            }
            try {
                mc.func_152342_ad().func_152790_a(profile, (type, location) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SKINS.put(player, location);
                    }
                }, false);
            } catch (RuntimeException ignored) {
            }
        }
        return AbstractClientPlayer.locationStevePng;
    }

    public static void clearSkinCache() {
        SKINS.clear();
        REQUESTED.clear();
    }
}
