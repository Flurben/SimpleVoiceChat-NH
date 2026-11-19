package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.flurben.voicechat.audio.AudioDevices;
import com.github.flurben.voicechat.config.PropertiesConfig;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.AudioOutput;
import com.github.flurben.voicechat.voice.client.ClientManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/** Lists the microphones or speakers to pick from. Speakers can play a test sound. */
public class SelectDeviceScreen extends VoiceChatScreenBase {

    private final boolean microphone;
    private ScrollList list;

    public SelectDeviceScreen(GuiScreen parent, boolean microphone) {
        super(parent, I18n.format(microphone ? "gui.voicechat.select_microphone.title" : "gui.voicechat.select_speaker.title"), 248, 219);
        this.microphone = microphone;
    }

    private PropertiesConfig.StringEntry entry() {
        return microphone ? ClientManager.INSTANCE.config().microphone : ClientManager.INSTANCE.config().speaker;
    }

    @Override
    public void initGui() {
        super.initGui();
        list = addList(new ScrollList(guiLeft + 10, guiTop + 20, xSize - 20, ySize - 20 - 32, 24));
        list.setEmptyText(I18n.format(microphone ? "message.voicechat.no_microphone" : "message.voicechat.no_speaker"));
        List<DeviceEntry> entries = new ArrayList<>();
        List<String> devices = microphone ? AudioDevices.getMicrophones() : AudioDevices.getSpeakers();
        if (!devices.isEmpty()) {
            entries.add(new DeviceEntry(""));
            for (String d : devices) {
                entries.add(new DeviceEntry(d));
            }
        }
        list.setEntries(entries);
        add(new VButton(guiLeft + 10, guiTop + ySize - 28, xSize - 20, 20, I18n.format("message.voicechat.back"), this::close));
    }

    private class DeviceEntry extends ScrollList.Entry {
        private final String device;

        DeviceEntry(String device) {
            this.device = device;
        }

        private String label() {
            return device.isEmpty() ? I18n.format(microphone ? "message.voicechat.default_microphone" : "message.voicechat.default_speaker") : device;
        }

        private boolean testHovered(int x, int y, int width, int mouseX, int mouseY) {
            return !microphone && mouseX >= x + width - 22 && mouseX < x + width - 4 && mouseY >= y + 3 && mouseY < y + 21;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            boolean selected = entry().get().equals(device);
            if (selected || hovered) {
                Gui.drawRect(x, y, x + width, y + height, selected ? 0x40FFFFFF : 0x20FFFFFF);
            }
            GuiDraw.icon(microphone ? GuiDraw.MICROPHONE : GuiDraw.SPEAKER, x + 4, y + 4, 16);
            String text = mc.fontRenderer.trimStringToWidth(label(), width - 30 - (microphone ? 0 : 22) - (selected ? 18 : 0));
            mc.fontRenderer.drawStringWithShadow(text, x + 26, y + 8, 0xFFFFFF);
            int right = x + width - 4;
            if (!microphone) {
                Gui.drawRect(right - 18, y + 3, right, y + 21, testHovered(x, y, width, mouseX, mouseY) ? 0x60FFFFFF : 0x30FFFFFF);
                GuiDraw.icon(GuiDraw.SPEAKER, right - 17, y + 4, 16);
                right -= 22;
            }
            if (selected) {
                GuiDraw.icon(GuiDraw.DEVICE_SELECTED, right - 16, y + 4, 16);
            }
        }

        @Override
        public List<String> tooltip(int x, int y, int width, int height, int mouseX, int mouseY) {
            if (testHovered(x, y, width, mouseX, mouseY)) {
                return Collections.singletonList(I18n.format("message.voicechat.test_speaker"));
            }
            return null;
        }

        @Override
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            Minecraft mc = Minecraft.getMinecraft();
            if (testHovered(x, y, width, mouseX, mouseY)) {
                if (!entry().get().equals(device)) {
                    select();
                }
                ClientManager.INSTANCE.startAudio();
                AudioOutput out = ClientManager.INSTANCE.getOutput();
                if (out != null) {
                    out.playTestSound();
                }
                return true;
            }
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
            select();
            return true;
        }

        private void select() {
            entry().set(device).save();
            ClientManager.INSTANCE.reloadAudio();
        }
    }
}
