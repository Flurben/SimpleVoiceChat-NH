package com.github.flurben.voicechat.voice.client;

import java.util.List;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.gui.GuiDraw;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.PlayerState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;

public class RenderEvents {

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ClientManager m = ClientManager.INSTANCE;
        ClientConfig cfg = m.config();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        ScaledResolution res = event.resolution;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        if (!cfg.onboardingFinished.get()) {
            String text = I18n.format("message.voicechat.set_up", KeyEvents.keyName(KeyEvents.VOICE_CHAT));
            mc.fontRenderer.drawStringWithShadow(text, res.getScaledWidth() / 2 - mc.fontRenderer.getStringWidth(text) / 2, res.getScaledHeight() - 72, 0xFFFFFF);
        }
        if (!cfg.hideIcons.get()) {
            if (cfg.showHudIcons.get()) {
                renderHudIcon(mc, m, cfg, res);
            }
            if (cfg.showGroupHud.get()) {
                renderGroupHud(mc, m, cfg, res);
            }
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static int ownIcon(ClientManager m) {
        if (!m.isConnected()) {
            return GuiDraw.DISCONNECTED;
        }
        if (m.isDisabled()) {
            return GuiDraw.SPEAKER_OFF;
        }
        if (m.isTalking()) {
            return m.isWhispering() ? GuiDraw.MICROPHONE_WHISPER : GuiDraw.MICROPHONE;
        }
        if (m.config().microphoneActivationType.get() == MicrophoneActivationType.VOICE && m.isMuted()) {
            return GuiDraw.MICROPHONE_OFF;
        }
        MicThread mic = m.getMic();
        if (mic != null && mic.getError() != null && !mic.isOpened()) {
            return GuiDraw.MICROPHONE_FORCED_OFF;
        }
        return -1;
    }

    private void renderHudIcon(Minecraft mc, ClientManager m, ClientConfig cfg, ScaledResolution res) {
        int icon = ownIcon(m);
        boolean recording = m.isRecording();
        if (icon < 0 && !recording) {
            return;
        }
        double scale = cfg.hudIconScale.get();
        double size = 16D * scale;
        int px = cfg.hudIconPosX.get(), py = cfg.hudIconPosY.get();
        double x = px < 0 ? res.getScaledWidth() + px - size : px;
        double y = py < 0 ? res.getScaledHeight() + py - size : py;
        if (icon >= 0) {
            GuiDraw.icon(icon, x, y, size);
        }
        if (recording) {
            ClientVoiceChat s = m.getSession();
            AudioRecorder rec = s == null ? null : s.getRecorder();
            double rx = x + (icon >= 0 ? size + 2 : 0);
            GuiDraw.icon(GuiDraw.BUTTON_RECORD_ON, rx, y + size / 2D - 4, 8);
            if (rec != null) {
                mc.fontRenderer.drawStringWithShadow(rec.getDuration(), (int) rx + 10, (int) (y + size / 2D - 4), 0xFF5555);
            }
        }
    }

    private void renderGroupHud(Minecraft mc, ClientManager m, ClientConfig cfg, ScaledResolution res) {
        ClientGroup group = m.getGroup();
        if (group == null) {
            return;
        }
        List<PlayerState> members = m.getGroupMembers(group.id);
        UUID own = m.getOwnId();
        if (!cfg.showOwnGroupIcon.get()) {
            members.removeIf(s -> s.uuid.equals(own));
        }
        if (members.isEmpty()) {
            return;
        }
        double scale = cfg.groupHudIconScale.get();
        double size = 8D * scale;
        double gap = Math.max(1D, scale);
        boolean vertical = cfg.groupPlayerIconOrientation.get() == GroupPlayerIconOrientation.VERTICAL;
        double total = members.size() * (size + gap) - gap;
        int px = cfg.groupPlayerIconPosX.get(), py = cfg.groupPlayerIconPosY.get();
        double w = vertical ? size : total, h = vertical ? total : size;
        double x = px < 0 ? res.getScaledWidth() + px - w : px;
        double y = py < 0 ? res.getScaledHeight() + py - h : py;
        for (PlayerState s : members) {
            boolean talking = s.uuid.equals(own) ? m.isTalking() : TalkCache.isTalking(s.uuid);
            if (talking) {
                int b = Math.max(1, (int) Math.round(scale / 2D));
                Gui.drawRect((int) x - b, (int) y - b, (int) Math.ceil(x + size) + b, (int) Math.ceil(y + size) + b, 0xFFFFFFFF);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GuiDraw.head(s.uuid, x, y, size);
            double small = size / 2D;
            if (s.disconnected) {
                GuiDraw.icon(GuiDraw.DISCONNECTED, x + size - small, y + size - small, small);
            } else if (s.disabled) {
                GuiDraw.icon(GuiDraw.SPEAKER_OFF, x + size - small, y + size - small, small);
            }
            if (vertical) {
                y += size + gap;
            } else {
                x += size + gap;
            }
        }
    }

    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Specials.Post event) {
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ClientManager m = ClientManager.INSTANCE;
        ClientConfig cfg = m.config();
        EntityPlayer player = (EntityPlayer) event.entity;
        if (player == mc.thePlayer || cfg.hideIcons.get() || !cfg.showNametagIcons.get() || !m.hasSession() || !Minecraft.isGuiEnabled()) {
            return;
        }
        if (player.isInvisibleToPlayer(mc.thePlayer) || player.riddenByEntity != null) {
            return;
        }
        double distSq = player.getDistanceSqToEntity(RenderManager.instance.livingPlayer);
        float max = player.isSneaking() ? 32F : 64F;
        if (distSq >= max * max) {
            return;
        }
        int icon = nameTagIcon(m, player.getUniqueID());
        if (icon < 0) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        int nameWidth = font.getStringWidth(player.func_145748_c_().getFormattedText());
        float f1 = 0.016666668F * 1.6F;
        GL11.glPushMatrix();
        GL11.glTranslated(event.x, event.y + player.height + 0.5D, event.z);
        GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-f1, -f1, f1);
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        double x = -nameWidth / 2D - 2D - 10D;
        if (!player.isSneaking()) {
            // Faint copy that shows through walls, like the name itself
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1F, 1F, 1F, 0.25F);
            drawNameIcon(icon, x);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        drawNameIcon(icon, x);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    private static void drawNameIcon(int index, double x) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(GuiDraw.ICONS);
        double u = (index % 16) * 16D / 256D, v = (index / 16) * 16D / 256D, s = 16D / 256D;
        GuiDraw.quad(x, -1D, 10D, 10D, u, v, u + s, v + s);
    }

    private static int nameTagIcon(ClientManager m, UUID player) {
        PlayerState state = m.getState(player);
        if (state == null || state.disconnected) {
            return GuiDraw.DISCONNECTED;
        }
        if (state.disabled) {
            return GuiDraw.SPEAKER_OFF;
        }
        if (TalkCache.isTalking(player)) {
            return TalkCache.isWhispering(player) ? GuiDraw.SPEAKER_WHISPER : GuiDraw.SPEAKER;
        }
        ClientGroup own = m.getGroup();
        if (own != null && own.id.equals(state.group)) {
            return GuiDraw.GROUP;
        }
        return -1;
    }
}
