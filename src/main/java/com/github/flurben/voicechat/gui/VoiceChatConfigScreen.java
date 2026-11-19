package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.List;

import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.config.PropertiesConfig;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.ClientManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/** All the finer options, opened from the mod list. Toggles and enums cycle, numbers and text open an edit box. */
public class VoiceChatConfigScreen extends VoiceChatScreenBase {

    private ScrollList list;

    public VoiceChatConfigScreen(GuiScreen parent) {
        super(parent, I18n.format("gui.voicechat.config.title"), 0, 0);
    }

    @Override
    public void initGui() {
        xSize = Math.min(360, width - 20);
        ySize = height - 20;
        super.initGui();
        ClientConfig c = ClientManager.INSTANCE.config();
        list = addList(new ScrollList(guiLeft, guiTop + 44, xSize, ySize - 44 - 26, 22));
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("show_hud_icons", c.showHudIcons));
        rows.add(new Row("hud_icon_scale", c.hudIconScale));
        rows.add(new Row("hud_icon_pos_x", c.hudIconPosX));
        rows.add(new Row("hud_icon_pos_y", c.hudIconPosY));
        rows.add(new Row("show_nametag_icons", c.showNametagIcons));
        rows.add(new Row("show_group_hud", c.showGroupHud));
        rows.add(new Row("show_own_group_icon", c.showOwnGroupIcon));
        rows.add(new Row("group_player_icon_orientation", c.groupPlayerIconOrientation));
        rows.add(new Row("group_hud_icon_scale", c.groupHudIconScale));
        rows.add(new Row("group_player_icon_pos_x", c.groupPlayerIconPosX));
        rows.add(new Row("group_player_icon_pos_y", c.groupPlayerIconPosY));
        rows.add(new Row("mute_on_join", c.muteOnJoin));
        rows.add(new Row("run_local_server", c.runLocalServer));
        rows.add(new Row("offline_player_volume_adjustment", c.offlinePlayerVolumeAdjustment));
        rows.add(new Row("output_buffer_size", c.outputBufferSize));
        rows.add(new Row("audio_packet_threshold", c.audioPacketThreshold));
        rows.add(new Row("voice_deactivation_delay", c.voiceDeactivationDelay));
        rows.add(new Row("ptt_deactivation_delay", c.pttDeactivationDelay));
        rows.add(new Row("recording_destination", c.recordingDestination));
        rows.add(new Row("force_tunnel", c.forceTunnel));
        list.setEntries(rows);
        add(new VButton(guiLeft, guiTop + 18, xSize, 20, I18n.format("gui.voicechat.voice_chat_settings.title") + "...", () -> mc.displayGuiScreen(new VoiceChatSettingsScreen(this))));
        add(new VButton(guiLeft + xSize / 2 - 100, guiTop + ySize - 22, 200, 20, I18n.format("message.voicechat.done"), this::close));
    }

    @Override
    protected int fontColor() {
        return 0xFFFFFF;
    }

    @Override
    protected void drawBackgroundPanel(int mouseX, int mouseY) {}

    private class Row extends ScrollList.Entry {
        final String key;
        final PropertiesConfig.Entry<?> entry;

        Row(String key, PropertiesConfig.Entry<?> entry) {
            this.key = key;
            this.entry = entry;
        }

        private String valueText() {
            Object v = entry.get();
            if (v instanceof Boolean) {
                return (Boolean) v ? EnumChatFormatting.GREEN + I18n.format("message.voicechat.on") : EnumChatFormatting.RED + I18n.format("message.voicechat.off");
            }
            if (v instanceof Enum) {
                String s = ((Enum<?>) v).name();
                return s.charAt(0) + s.substring(1).toLowerCase();
            }
            String s = String.valueOf(v);
            return s.isEmpty() ? EnumChatFormatting.GRAY + "-" : s;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                Gui.drawRect(x, y, x + width, y + height, 0x20FFFFFF);
            }
            mc.fontRenderer.drawStringWithShadow(I18n.format("gui.voicechat.config." + key), x + 6, y + 7, 0xFFFFFF);
            String v = mc.fontRenderer.trimStringToWidth(valueText(), 110);
            int bx = x + width - 120;
            boolean h = mouseX >= bx && mouseX < bx + 116 && mouseY >= y + 1 && mouseY < y + 21;
            Gui.drawRect(bx, y + 1, bx + 116, y + 21, h ? 0xFF606060 : 0xFF404040);
            mc.fontRenderer.drawStringWithShadow(v, bx + 58 - mc.fontRenderer.getStringWidth(v) / 2, y + 7, 0xFFFFFF);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            if (mouseX < x + width - 120) {
                return false;
            }
            Minecraft mc = Minecraft.getMinecraft();
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
            if (entry instanceof PropertiesConfig.BoolEntry) {
                PropertiesConfig.BoolEntry b = (PropertiesConfig.BoolEntry) entry;
                b.set(!b.get()).save();
            } else if (entry instanceof PropertiesConfig.EnumEntry) {
                PropertiesConfig.EnumEntry e = (PropertiesConfig.EnumEntry) entry;
                Enum[] values = e.values();
                e.set(values[(((Enum) e.get()).ordinal() + 1) % values.length]).save();
            } else {
                mc.displayGuiScreen(new EditValueScreen(VoiceChatConfigScreen.this, I18n.format("gui.voicechat.config." + key), entry));
            }
            return true;
        }
    }

    /** A text box for numbers and text values. */
    static class EditValueScreen extends VoiceChatScreenBase {
        private final PropertiesConfig.Entry<?> entry;
        private GuiTextField field;
        private String error;

        EditValueScreen(GuiScreen parent, String title, PropertiesConfig.Entry<?> entry) {
            super(parent, title, 240, 80);
            this.entry = entry;
        }

        @Override
        public void initGui() {
            super.initGui();
            field = addField(guiLeft + 10, guiTop + 24, xSize - 20, 16, 512);
            field.setText(String.valueOf(entry.get()));
            field.setFocused(true);
            add(new VButton(guiLeft + 10, guiTop + ySize - 26, xSize / 2 - 12, 20, I18n.format("gui.cancel"), this::close));
            add(new VButton(guiLeft + xSize / 2 + 2, guiTop + ySize - 26, xSize / 2 - 12, 20, I18n.format("gui.done"), this::onEnter));
        }

        @Override
        protected void onEnter() {
            String t = field.getText().trim();
            try {
                if (entry instanceof PropertiesConfig.IntEntry) {
                    ((PropertiesConfig.IntEntry) entry).set(Integer.parseInt(t)).save();
                } else if (entry instanceof PropertiesConfig.DoubleEntry) {
                    ((PropertiesConfig.DoubleEntry) entry).set(Double.parseDouble(t)).save();
                } else if (entry instanceof PropertiesConfig.StringEntry) {
                    ((PropertiesConfig.StringEntry) entry).set(t).save();
                }
                close();
            } catch (NumberFormatException e) {
                error = EnumChatFormatting.RED + "Not a number";
            }
        }

        @Override
        protected void drawForeground(int mouseX, int mouseY) {
            if (error != null) {
                fontRendererObj.drawString(error, guiLeft + 10, guiTop + 44, 0);
            }
        }
    }
}
