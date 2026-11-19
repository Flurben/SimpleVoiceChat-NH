package com.github.flurben.voicechat.gui.onboarding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.flurben.voicechat.audio.AudioDevices;
import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.config.PropertiesConfig;
import com.github.flurben.voicechat.gui.GuiDraw;
import com.github.flurben.voicechat.gui.VoiceChatScreen;
import com.github.flurben.voicechat.gui.VoiceChatScreenBase;
import com.github.flurben.voicechat.gui.widgets.ImageButton;
import com.github.flurben.voicechat.gui.widgets.KeybindButton;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.gui.widgets.VoiceActivationSlider;
import com.github.flurben.voicechat.voice.client.AudioOutput;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.MicrophoneActivationType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/** The first time setup: microphone, speaker and how to talk. */
public final class OnboardingScreens {

    private OnboardingScreens() {}

    public static GuiScreen first(GuiScreen parent) {
        return new Page(parent, Step.INTRODUCTION);
    }

    enum Step {
        INTRODUCTION,
        MICROPHONE,
        SPEAKER,
        ACTIVATION,
        PTT,
        VOICE,
        FINAL
    }

    static class Page extends VoiceChatScreenBase {

        private final GuiScreen root;
        private final Step step;
        private ScrollList list;

        Page(GuiScreen root, Step step) {
            super(root, I18n.format("message.voicechat.onboarding.title"), 280, 200);
            this.root = root;
            this.step = step;
        }

        private ClientConfig cfg() {
            return ClientManager.INSTANCE.config();
        }

        private void go(Step s) {
            mc.displayGuiScreen(new Page(root, s));
        }

        private void finish() {
            cfg().onboardingFinished.set(true).save();
            ClientManager.INSTANCE.setMicTesting(false);
            mc.displayGuiScreen(mc.theWorld != null ? new VoiceChatScreen() : root);
        }

        @Override
        public void initGui() {
            super.initGui();
            int bw = (xSize - 30) / 2;
            int left = guiLeft + 10, right = guiLeft + xSize / 2 + 5, by = guiTop + ySize - 28;
            switch (step) {
                case INTRODUCTION:
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.skip"), this::finish));
                    add(new VButton(right, by, bw, 20, I18n.format("message.voicechat.onboarding.next"), () -> go(Step.MICROPHONE)));
                    break;
                case MICROPHONE:
                case SPEAKER:
                    boolean mic = step == Step.MICROPHONE;
                    list = addList(new ScrollList(guiLeft + 10, guiTop + 40, xSize - 20, ySize - 40 - 34, 20));
                    List<DeviceEntry> entries = new ArrayList<>();
                    PropertiesConfig.StringEntry entry = mic ? cfg().microphone : cfg().speaker;
                    entries.add(new DeviceEntry(entry, "", mic));
                    for (String d : mic ? AudioDevices.getMicrophones() : AudioDevices.getSpeakers()) {
                        entries.add(new DeviceEntry(entry, d, mic));
                    }
                    list.setEntries(entries);
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.back"), () -> go(mic ? Step.INTRODUCTION : Step.MICROPHONE)));
                    add(new VButton(right, by, bw, 20, I18n.format("message.voicechat.onboarding.next"), () -> go(mic ? Step.SPEAKER : Step.ACTIVATION)));
                    if (!mic) {
                        add(new ImageButton(guiLeft + xSize - 10 - 20, guiTop + 16, () -> GuiDraw.TEST_SPEAKER, () -> {
                            ClientManager.INSTANCE.startAudio();
                            AudioOutput out = ClientManager.INSTANCE.getOutput();
                            if (out != null) {
                                out.playTestSound();
                            }
                        })).tooltip(I18n.format("message.voicechat.test_speaker"));
                    }
                    break;
                case ACTIVATION:
                    add(new VButton(left, guiTop + 110, bw, 20, I18n.format("message.voicechat.onboarding.ptt.title"), () -> {
                        cfg().microphoneActivationType.set(MicrophoneActivationType.PTT).save();
                        go(Step.PTT);
                    }));
                    add(new VButton(right, guiTop + 110, bw, 20, I18n.format("message.voicechat.onboarding.voice.title"), () -> {
                        cfg().microphoneActivationType.set(MicrophoneActivationType.VOICE).save();
                        go(Step.VOICE);
                    }));
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.back"), () -> go(Step.SPEAKER)));
                    break;
                case PTT:
                    add(new KeybindButton(KeyEvents.PTT, guiLeft + 40, guiTop + 110, xSize - 80, 20, I18n.format("message.voicechat.activation_type.ptt")));
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.back"), () -> go(Step.ACTIVATION)));
                    add(new VButton(right, by, bw, 20, I18n.format("message.voicechat.onboarding.next"), () -> go(Step.FINAL)));
                    break;
                case VOICE: {
                    ClientManager m = ClientManager.INSTANCE;
                    cfg().voiceActivityDetection.set(false).save();
                    add(new ImageButton(guiLeft + 20, guiTop + 110, () -> m.isMicTesting() ? GuiDraw.BUTTON_MIC : GuiDraw.BUTTON_MIC_OFF, () -> m.setMicTesting(!m.isMicTesting())))
                        .tooltip(() -> Collections.singletonList(I18n.format(m.isMicTesting() ? "message.voicechat.mic_test.enabled" : "message.voicechat.mic_test.disabled")));
                    add(new VoiceActivationSlider(guiLeft + 42, guiTop + 110, xSize - 62, 20));
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.back"), () -> go(Step.ACTIVATION)));
                    add(new VButton(right, by, bw, 20, I18n.format("message.voicechat.onboarding.next"), () -> go(Step.FINAL)));
                    break;
                }
                case FINAL:
                    add(new VButton(left, by, bw, 20, I18n.format("message.voicechat.onboarding.back"),
                        () -> go(cfg().microphoneActivationType.get() == MicrophoneActivationType.PTT ? Step.PTT : Step.VOICE)));
                    add(new VButton(right, by, bw, 20, I18n.format("message.voicechat.onboarding.finish"), this::finish));
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            if (step == Step.VOICE) {
                ClientManager.INSTANCE.setMicTesting(false);
            }
        }

        @Override
        protected void drawForeground(int mouseX, int mouseY) {
            String heading;
            String body = null;
            switch (step) {
                case INTRODUCTION:
                    heading = I18n.format("message.voicechat.onboarding.introduction.title");
                    body = I18n.format("message.voicechat.onboarding.introduction.description") + "\n\n" + I18n.format("message.voicechat.onboarding.introduction.skip");
                    break;
                case MICROPHONE:
                    heading = I18n.format("message.voicechat.onboarding.microphone.title");
                    break;
                case SPEAKER:
                    heading = I18n.format("message.voicechat.onboarding.speaker.title");
                    break;
                case ACTIVATION:
                    heading = I18n.format("message.voicechat.onboarding.activation.title");
                    body = I18n.format("message.voicechat.onboarding.activation.description");
                    break;
                case PTT:
                    heading = I18n.format("message.voicechat.onboarding.ptt.title");
                    body = I18n.format("message.voicechat.onboarding.ptt.description");
                    break;
                case VOICE:
                    heading = I18n.format("message.voicechat.onboarding.voice.title");
                    body = I18n.format("message.voicechat.onboarding.voice.description");
                    break;
                default:
                    heading = I18n.format("message.voicechat.onboarding.final.title");
                    body = I18n.format("message.voicechat.onboarding.final.description", KeyEvents.keyName(KeyEvents.VOICE_CHAT), KeyEvents.keyName(KeyEvents.MUTE));
            }
            int color = fontColor();
            fontRendererObj.drawString(heading, guiLeft + (xSize - fontRendererObj.getStringWidth(heading)) / 2, guiTop + 24, color);
            if (body != null) {
                int y = guiTop + 42;
                for (String paragraph : body.split("\n")) {
                    for (Object line : fontRendererObj.listFormattedStringToWidth(paragraph, xSize - 30)) {
                        fontRendererObj.drawString((String) line, guiLeft + 15, y, color);
                        y += 10;
                    }
                    if (paragraph.isEmpty()) {
                        y += 0;
                    }
                }
            }
        }
    }

    static class DeviceEntry extends ScrollList.Entry {
        final PropertiesConfig.StringEntry entry;
        final String device;
        final boolean mic;

        DeviceEntry(PropertiesConfig.StringEntry entry, String device, boolean mic) {
            this.entry = entry;
            this.device = device;
            this.mic = mic;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            boolean selected = entry.get().equals(device);
            if (selected || hovered) {
                Gui.drawRect(x, y, x + width, y + height, selected ? 0x40FFFFFF : 0x20FFFFFF);
            }
            String label = device.isEmpty() ? I18n.format(mic ? "message.voicechat.default_microphone" : "message.voicechat.default_speaker") : device;
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(label, width - 30), x + 6, y + 6, 0xFFFFFF);
            if (selected) {
                GuiDraw.icon(GuiDraw.DEVICE_SELECTED, x + width - 18, y + 2, 16);
            }
        }

        @Override
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            entry.set(device).save();
            ClientManager.INSTANCE.reloadAudio();
            return true;
        }
    }
}
