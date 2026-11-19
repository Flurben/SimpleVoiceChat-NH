package com.github.flurben.voicechat.gui.widgets;

import java.util.Collections;

import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.MicThread;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

/** The voice activation threshold, with the live microphone level shown behind the knob while testing. */
public class VoiceActivationSlider extends SliderButton {

    public static final double MIN_DB = -90D;

    public VoiceActivationSlider(int x, int y, int w, int h) {
        super(x, y, w, h, toValue(ClientManager.INSTANCE.config().voiceActivationThreshold.get()));
        tooltip(() -> Math.round(MIN_DB + value * -MIN_DB) >= 0 ? Collections.singletonList(I18n.format("message.voicechat.voice_activation.disabled")) : null);
    }

    private static double toValue(double db) {
        return Math.max(0D, Math.min(1D, (db - MIN_DB) / -MIN_DB));
    }

    @Override
    protected String getText() {
        return I18n.format("message.voicechat.voice_activation", Math.round(MIN_DB + value * -MIN_DB));
    }

    @Override
    protected void applyValue() {
        ClientManager.INSTANCE.config().voiceActivationThreshold.set((double) Math.round(MIN_DB + value * -MIN_DB));
    }

    @Override
    protected void onRelease() {
        ClientManager.INSTANCE.config().voiceActivationThreshold.save();
    }

    @Override
    protected void drawBackground(Minecraft mc) {
        ClientManager m = ClientManager.INSTANCE;
        MicThread mic = m.getMic();
        if (mic == null || !m.isMicTesting()) {
            return;
        }
        double level = toValue(mic.getLevel());
        int w = (int) ((width - 2) * level);
        boolean above = level >= value;
        Gui.drawRect(xPosition + 1, yPosition + 1, xPosition + 1 + w, yPosition + height - 1, above ? 0xA040C040 : 0xA0808080);
    }
}
