package com.github.flurben.voicechat.gui;

import java.util.Arrays;
import java.util.Collections;

import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.gui.widgets.ImageButton;
import com.github.flurben.voicechat.gui.widgets.KeybindButton;
import com.github.flurben.voicechat.gui.widgets.SliderButton;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.gui.widgets.VoiceActivationSlider;
import com.github.flurben.voicechat.voice.client.AudioType;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.MicrophoneActivationType;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

public class VoiceChatSettingsScreen extends VoiceChatScreenBase {

    private VButton vadButton;
    private KeybindButton keybind;
    private VoiceActivationSlider activationSlider;
    private SliderButton gainSlider;

    public VoiceChatSettingsScreen(GuiScreen parent) {
        super(parent, I18n.format("gui.voicechat.voice_chat_settings.title"), 248, 219);
    }

    @Override
    public void initGui() {
        super.initGui();
        ClientManager m = ClientManager.INSTANCE;
        ClientConfig cfg = m.config();
        int x = guiLeft + 10, w = xSize - 20;
        int y = guiTop + 20;

        add(new SliderButton(x, y, w, 20, cfg.voiceChatVolume.get() / 2D) {
            @Override
            protected String getText() {
                return I18n.format("message.voicechat.voice_chat_volume", Math.round(value * 200D) + "%");
            }

            @Override
            protected void applyValue() {
                cfg.voiceChatVolume.set(value * 2D);
            }

            @Override
            protected void onRelease() {
                cfg.voiceChatVolume.save();
            }
        });
        y += 21;

        add(new VButton(x, y, 80, 20, "", () -> {
            cfg.automaticGainControl.set(!cfg.automaticGainControl.get()).save();
            updateVisibility();
        }) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
                displayString = I18n.format(cfg.automaticGainControl.get() ? "message.voicechat.gain.auto" : "message.voicechat.gain.manual");
                super.drawButton(mc, mx, my);
            }
        }.tooltip(() -> cfg.automaticGainControl.get() ? null : Collections.singletonList(I18n.format("message.voicechat.gain.manual.warning"))));
        double range = ClientConfig.MAX_GAIN - ClientConfig.MIN_GAIN;
        gainSlider = add(new SliderButton(x + 81, y, w - 81, 20, (cfg.microphoneGain.get() - ClientConfig.MIN_GAIN) / range) {
            @Override
            protected String getText() {
                if (!enabled) {
                    return I18n.format("message.voicechat.microphone_gain.auto");
                }
                return I18n.format("message.voicechat.microphone_gain", Math.round(ClientConfig.MIN_GAIN + value * range));
            }

            @Override
            protected void applyValue() {
                cfg.microphoneGain.set((double) Math.round(ClientConfig.MIN_GAIN + value * range));
            }

            @Override
            protected void onRelease() {
                cfg.microphoneGain.save();
            }
        });
        y += 21;

        add(new VButton(x, y, w, 20, "", () -> cfg.denoiser.set(!cfg.denoiser.get()).save()) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
                displayString = I18n.format("message.voicechat.denoiser", I18n.format(cfg.denoiser.get() ? "message.voicechat.on" : "message.voicechat.off"));
                super.drawButton(mc, mx, my);
            }
        });
        y += 21;

        add(new ImageButton(x, y, () -> m.isMicTesting() ? GuiDraw.BUTTON_MIC : GuiDraw.BUTTON_MIC_OFF, () -> m.setMicTesting(!m.isMicTesting())))
            .tooltip(() -> Collections.singletonList(I18n.format(m.isMicTesting() ? "message.voicechat.mic_test.enabled" : "message.voicechat.mic_test.disabled")));
        add(new VButton(x + 21, y, w - 21, 20, "", () -> {
            MicrophoneActivationType t = cfg.microphoneActivationType.get() == MicrophoneActivationType.PTT ? MicrophoneActivationType.VOICE : MicrophoneActivationType.PTT;
            cfg.microphoneActivationType.set(t).save();
            updateVisibility();
        }) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
                displayString = I18n.format("message.voicechat.activation_type", I18n.format(cfg.microphoneActivationType.get().translationKey));
                super.drawButton(mc, mx, my);
            }
        });
        y += 21;

        vadButton = add(new VButton(x, y, w, 20, "", () -> {
            cfg.voiceActivityDetection.set(!cfg.voiceActivityDetection.get()).save();
            updateVisibility();
        }) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
                displayString = I18n.format("message.voicechat.voice_activation_detection",
                    I18n.format(cfg.voiceActivityDetection.get() ? "message.voicechat.voice_activation_detection.auto" : "message.voicechat.voice_activation_detection.manual"));
                super.drawButton(mc, mx, my);
            }
        });
        keybind = add(new KeybindButton(KeyEvents.PTT, x, y, w, 20, I18n.format("message.voicechat.activation_type.ptt")));
        y += 21;
        activationSlider = add(new VoiceActivationSlider(x, y, w, 20));
        y += 21;

        add(new VButton(x, y, w, 20, "", () -> {
            AudioType[] types = AudioType.values();
            cfg.audioType.set(types[(cfg.audioType.get().ordinal() + 1) % types.length]).save();
        }) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
                displayString = I18n.format("message.voicechat.audio_type", I18n.format(cfg.audioType.get().translationKey));
                super.drawButton(mc, mx, my);
            }
        });
        y += 21;

        add(new VButton(x, y, w / 2 - 1, 20, I18n.format("message.voicechat.select_microphone"), () -> mc.displayGuiScreen(new SelectDeviceScreen(this, true))));
        add(new VButton(guiLeft + xSize / 2 + 1, y, w / 2 - 1, 20, I18n.format("message.voicechat.select_speaker"), () -> mc.displayGuiScreen(new SelectDeviceScreen(this, false))));
        y += 21;
        if (!isIngame() && parent != null) {
            add(new VButton(x, y, w, 20, I18n.format("message.voicechat.back"), this::close));
        }
        updateVisibility();
    }

    private void updateVisibility() {
        ClientConfig cfg = ClientManager.INSTANCE.config();
        boolean voice = cfg.microphoneActivationType.get() == MicrophoneActivationType.VOICE;
        vadButton.visible = voice;
        keybind.visible = !voice;
        keybind.stopListening();
        activationSlider.visible = voice && !cfg.voiceActivityDetection.get();
        gainSlider.enabled = !cfg.automaticGainControl.get();
    }

    @Override
    protected void close() {
        if (keybind.isListening()) {
            keybind.stopListening();
            return;
        }
        super.close();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ClientManager.INSTANCE.setMicTesting(false);
    }

    @Override
    protected void drawForeground(int mouseX, int mouseY) {
        if (!ClientManager.INSTANCE.isMicTesting()) {
            return;
        }
        String err = ClientManager.INSTANCE.getMic() == null ? null : ClientManager.INSTANCE.getMic().getError();
        if (err != null) {
            String text = EnumChatFormatting.RED + I18n.format("message.voicechat.microphone_unavailable");
            drawTooltip(Arrays.asList(text, EnumChatFormatting.GRAY + err), guiLeft + 4, guiTop + ySize + 12);
        }
    }
}
