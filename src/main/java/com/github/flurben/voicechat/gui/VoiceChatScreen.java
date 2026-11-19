package com.github.flurben.voicechat.gui;

import java.util.Arrays;
import java.util.Collections;

import com.github.flurben.voicechat.gui.widgets.ImageButton;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.AudioRecorder;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.ClientVoiceChat;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.MicrophoneActivationType;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

/** The voice chat menu, opened with V: mute, disable, volumes, recording, icons, settings and groups. */
public class VoiceChatScreen extends VoiceChatScreenBase {

    private ImageButton mute;
    private VButton group;
    private int recordX, recordW;

    public VoiceChatScreen() {
        super(null, I18n.format("gui.voicechat.voice_chat.title"), 195, 76);
    }

    @Override
    public void initGui() {
        super.initGui();
        ClientManager m = ClientManager.INSTANCE;
        int by = guiTop + ySize - 6 - 20;

        mute = add(new ImageButton(guiLeft + 6, by, () -> m.isMuted() ? GuiDraw.BUTTON_MIC_OFF : GuiDraw.BUTTON_MIC, () -> m.setMuted(!m.isMuted())));
        mute.tooltip(() -> {
            if (m.config().microphoneActivationType.get() == MicrophoneActivationType.PTT) {
                return Arrays.asList(I18n.format(m.isMuted() ? "message.voicechat.mute.enabled" : "message.voicechat.mute.disabled"),
                    EnumChatFormatting.GRAY + I18n.format("message.voicechat.mute.ptt"));
            }
            return Collections.singletonList(I18n.format(m.isMuted() ? "message.voicechat.mute.enabled" : "message.voicechat.mute.disabled"));
        });

        add(new ImageButton(guiLeft + 6 + 22, by, () -> m.isDisabled() ? GuiDraw.BUTTON_SPEAKER_OFF : GuiDraw.BUTTON_SPEAKER, () -> m.setDisabled(!m.isDisabled())))
            .tooltip(() -> Collections.singletonList(I18n.format(m.isDisabled() ? "message.voicechat.disable.enabled" : "message.voicechat.disable.disabled")));

        add(new ImageButton(guiLeft + 6 + 44, by, () -> GuiDraw.BUTTON_VOLUMES, () -> mc.displayGuiScreen(new AdjustVolumesScreen(this))))
            .tooltip(I18n.format("message.voicechat.adjust_volumes"));

        if (m.canRecord()) {
            add(new ImageButton(guiLeft + xSize - 6 - 20 - 2 - 20, by, () -> m.isRecording() ? GuiDraw.BUTTON_RECORD_ON : GuiDraw.BUTTON_RECORD, m::toggleRecording))
                .tooltip(() -> Collections.singletonList(I18n.format(m.isRecording() ? "message.voicechat.recording.enabled" : "message.voicechat.recording.disabled")));
        }

        add(new ImageButton(guiLeft + xSize - 6 - 20, by, () -> m.config().hideIcons.get() ? GuiDraw.BUTTON_HIDE_OFF : GuiDraw.BUTTON_HIDE,
            () -> m.config().hideIcons.set(!m.config().hideIcons.get()).save()))
            .tooltip(() -> Collections.singletonList(I18n.format(m.config().hideIcons.get() ? "message.voicechat.hide_icons.enabled" : "message.voicechat.hide_icons.disabled")));

        add(new VButton(guiLeft + 6, guiTop + 6 + 15, 75, 20, I18n.format("message.voicechat.settings"), () -> mc.displayGuiScreen(new VoiceChatSettingsScreen(this))));
        group = add(new VButton(guiLeft + xSize - 6 - 75 + 1, guiTop + 6 + 15, 75, 20, I18n.format("message.voicechat.group"), () -> KeyEvents.openGroupScreen(mc)));
        ClientVoiceChat s = m.getSession();
        group.enabled = s != null && s.getData().groupsEnabled;

        recordX = 6 + 66;
        recordW = xSize - 2 * recordX;
    }

    @Override
    protected int closeKey() {
        return KeyEvents.VOICE_CHAT.getKeyCode();
    }

    @Override
    protected void drawBackgroundPanel(int mouseX, int mouseY) {
        GuiDraw.panel(guiLeft, guiTop, xSize, ySize);
    }

    @Override
    protected int fontColor() {
        return GuiDraw.FONT_COLOR;
    }

    @Override
    protected void drawForeground(int mouseX, int mouseY) {
        ClientManager m = ClientManager.INSTANCE;
        ClientVoiceChat s = m.getSession();
        AudioRecorder rec = s == null ? null : s.getRecorder();
        int cy = guiTop + ySize - 6 - 10 - 4;
        if (rec != null) {
            String time = EnumChatFormatting.DARK_RED + rec.getDuration();
            fontRendererObj.drawString(time, guiLeft + recordX + recordW / 2 - fontRendererObj.getStringWidth(time) / 2 + 11, cy, 0);
            if (mouseX >= guiLeft + recordX && mouseX < guiLeft + recordX + recordW + 22 && mouseY >= cy - 6 && mouseY < cy + 14) {
                drawTooltip(Collections.singletonList(I18n.format("message.voicechat.storage_size", rec.getStorage())), mouseX, mouseY);
            }
        } else if (s == null || !s.isConnected()) {
            String text = I18n.format(s == null ? "message.voicechat.voice_chat_unavailable" : "message.voicechat.voice_chat_not_connected");
            int w = fontRendererObj.getStringWidth(text);
            fontRendererObj.drawStringWithShadow(text, guiLeft + xSize / 2 - w / 2, guiTop - 12, 0xFF5555);
        }
    }
}
