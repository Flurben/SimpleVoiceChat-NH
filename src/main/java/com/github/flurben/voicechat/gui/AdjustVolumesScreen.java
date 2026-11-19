package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.flurben.voicechat.config.VolumeConfig;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.TalkCache;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

/** One volume slider per player, with a search field. Players in your group can be invited from here too. */
public class AdjustVolumesScreen extends VoiceChatScreenBase {

    private static final double MAX = 4D;
    private static final net.minecraft.util.ResourceLocation WIDGETS = new net.minecraft.util.ResourceLocation("textures/gui/widgets.png");

    private ScrollList list;
    private GuiTextField search;

    public AdjustVolumesScreen(GuiScreen parent) {
        super(parent, I18n.format("gui.voicechat.adjust_volume.title"), 236, 219);
    }

    @Override
    public void initGui() {
        super.initGui();
        search = addField(guiLeft + 11, guiTop + 20, xSize - 22, 14, 32);
        list = addList(new ScrollList(guiLeft + 10, guiTop + 38, xSize - 20, ySize - 48, 24));
        refresh();
    }

    @Override
    protected int closeKey() {
        return KeyEvents.ADJUST_VOLUMES.getKeyCode();
    }

    @Override
    public boolean isTyping() {
        return search != null && search.isFocused() && !search.getText().isEmpty() || super.isTyping();
    }

    @Override
    protected void onTextChanged(GuiTextField field) {
        refresh();
    }

    private void refresh() {
        ClientManager m = ClientManager.INSTANCE;
        UUID own = m.getOwnId();
        Map<UUID, String> players = new LinkedHashMap<>();
        for (PlayerState s : m.getStates()) {
            if (!s.uuid.equals(own)) {
                players.put(s.uuid, s.name);
            }
        }
        if (m.config().offlinePlayerVolumeAdjustment.get()) {
            for (UUID id : m.getVolumes().getVolumes().keySet()) {
                if (!players.containsKey(id) && !id.equals(own)) {
                    String n = m.getVolumes().getName(id);
                    players.put(id, n == null ? id.toString() : n);
                }
            }
        }
        String filter = search.getText().trim().toLowerCase();
        List<PlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> e : players.entrySet()) {
            if (filter.isEmpty() || e.getValue().toLowerCase().contains(filter)) {
                entries.add(new PlayerEntry(e.getKey(), e.getValue()));
            }
        }
        entries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        list.setEmptyText(I18n.format(filter.isEmpty() ? "message.voicechat.no_players" : "message.voicechat.search_empty"));
        list.setEntries(entries);
    }

    @Override
    protected void drawForeground(int mouseX, int mouseY) {
        if (search.getText().isEmpty() && !search.isFocused()) {
            fontRendererObj.drawString(EnumChatFormatting.ITALIC + I18n.format("message.voicechat.search_hint"), guiLeft + 15, guiTop + 23, 0x808080);
        }
    }

    private static class PlayerEntry extends ScrollList.Entry {
        final UUID id;
        final String name;
        boolean dragging;

        PlayerEntry(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        private static int sliderX(int x) {
            return x + 26 + 74;
        }

        private static int sliderW(int x, int width) {
            return width - (sliderX(x) - x) - 4 - (canInvite() ? 22 : 0);
        }

        private static boolean canInvite() {
            return ClientManager.INSTANCE.getGroup() != null;
        }

        private boolean inGroup() {
            ClientGroup g = ClientManager.INSTANCE.getGroup();
            PlayerState s = ClientManager.INSTANCE.getState(id);
            return g != null && s != null && g.id.equals(s.group);
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                Gui.drawRect(x, y, x + width, y + height, 0x20FFFFFF);
            }
            if (TalkCache.isTalking(id)) {
                Gui.drawRect(x + 3, y + 3, x + 21, y + 21, 0xFFFFFFFF);
            }
            GuiDraw.head(id, x + 4, y + 4, 16);
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(name, 70), x + 26, y + 8, 0xFFFFFF);
            VolumeConfig v = ClientManager.INSTANCE.getVolumes();
            double vol = v.getVolume(id);
            int sx = sliderX(x), sw = sliderW(x, width);
            mc.getTextureManager().bindTexture(WIDGETS);
            org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
            Gui g = new Gui();
            g.drawTexturedModalRect(sx, y + 2, 0, 46, sw / 2, 20);
            g.drawTexturedModalRect(sx + sw / 2, y + 2, 200 - sw / 2, 46, sw / 2, 20);
            int knob = sx + (int) (vol / MAX * (sw - 8));
            g.drawTexturedModalRect(knob, y + 2, 0, 66, 4, 20);
            g.drawTexturedModalRect(knob + 4, y + 2, 196, 66, 4, 20);
            String text = I18n.format("message.voicechat.volume_amplification", Math.round(vol * 100D) + "%");
            mc.fontRenderer.drawStringWithShadow(text, sx + sw / 2 - mc.fontRenderer.getStringWidth(text) / 2, y + 8, 0xE0E0E0);
            if (canInvite() && !inGroup() && ClientManager.INSTANCE.getState(id) != null) {
                int bx = x + width - 22;
                boolean h = mouseX >= bx && mouseX < bx + 20 && mouseY >= y + 2 && mouseY < y + 22;
                Gui.drawRect(bx, y + 2, bx + 20, y + 22, h ? 0x60FFFFFF : 0x30FFFFFF);
                GuiDraw.icon(GuiDraw.INVITE, bx + 2, y + 4, 16);
            }
        }

        private void setFromMouse(int x, int width, int mouseX) {
            int sx = sliderX(x), sw = sliderW(x, width);
            double t = Math.max(0D, Math.min(1D, (mouseX - sx - 4) / (double) (sw - 8)));
            double vol = Math.round(t * MAX * 20D) / 20D;
            ClientManager.INSTANCE.getVolumes().setVolume(id, name, vol);
        }

        @Override
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            int sx = sliderX(x), sw = sliderW(x, width);
            if (mouseX >= sx && mouseX < sx + sw) {
                dragging = true;
                setFromMouse(x, width, mouseX);
                return true;
            }
            if (canInvite() && !inGroup() && mouseX >= x + width - 22 && mouseX < x + width - 2) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.thePlayer.sendChatMessage("/voicechat invite " + name);
                return true;
            }
            return false;
        }

        @Override
        public void drag(int x, int width, int mouseX, int mouseY) {
            if (dragging) {
                setFromMouse(x, width, mouseX);
            }
        }

        @Override
        public void release(int mouseX, int mouseY) {
            if (dragging) {
                dragging = false;
                ClientManager.INSTANCE.getVolumes().save();
            }
        }

        @Override
        public List<String> tooltip(int x, int y, int width, int height, int mouseX, int mouseY) {
            if (canInvite() && !inGroup() && mouseX >= x + width - 22 && mouseX < x + width - 2) {
                return Collections.singletonList(I18n.format("message.voicechat.invite_player", name));
            }
            return null;
        }
    }
}
