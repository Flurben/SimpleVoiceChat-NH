package com.github.flurben.voicechat.voice.client;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.github.flurben.voicechat.gui.AdjustVolumesScreen;
import com.github.flurben.voicechat.gui.GroupScreen;
import com.github.flurben.voicechat.gui.JoinGroupScreen;
import com.github.flurben.voicechat.gui.VoiceChatScreen;
import com.github.flurben.voicechat.gui.VoiceChatSettingsScreen;
import com.github.flurben.voicechat.gui.onboarding.OnboardingScreens;
import com.github.flurben.voicechat.voice.common.ClientGroup;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;

public class KeyEvents {

    public static final String CATEGORY = "key.categories.voicechat";

    public static KeyBinding PTT;
    public static KeyBinding WHISPER;
    public static KeyBinding MUTE;
    public static KeyBinding DISABLE;
    public static KeyBinding HIDE_ICONS;
    public static KeyBinding VOICE_CHAT;
    public static KeyBinding VOICE_CHAT_SETTINGS;
    public static KeyBinding GROUP;
    public static KeyBinding TOGGLE_RECORDING;
    public static KeyBinding ADJUST_VOLUMES;

    public static void register() {
        PTT = reg("key.voicechat.push_to_talk", Keyboard.KEY_CAPITAL);
        WHISPER = reg("key.voicechat.whisper", Keyboard.KEY_NONE);
        MUTE = reg("key.voicechat.mute_microphone", Keyboard.KEY_M);
        DISABLE = reg("key.voicechat.disable_voice_chat", Keyboard.KEY_N);
        HIDE_ICONS = reg("key.voicechat.hide_icons", Keyboard.KEY_H);
        VOICE_CHAT = reg("key.voicechat.voice_chat", Keyboard.KEY_V);
        VOICE_CHAT_SETTINGS = reg("key.voicechat.voice_chat_settings", Keyboard.KEY_NONE);
        GROUP = reg("key.voicechat.voice_chat_group", Keyboard.KEY_NONE);
        TOGGLE_RECORDING = reg("key.voicechat.voice_chat_toggle_recording", Keyboard.KEY_NONE);
        ADJUST_VOLUMES = reg("key.voicechat.voice_chat_adjust_volumes", Keyboard.KEY_NONE);
    }

    private static KeyBinding reg(String name, int key) {
        KeyBinding k = new KeyBinding(name, key, CATEGORY);
        ClientRegistry.registerKeyBinding(k);
        return k;
    }

    /** Held state of a binding, read straight from the keyboard and mouse so it also works with inventories open. */
    public static boolean isHeld(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code == 0) {
            return false;
        }
        try {
            if (code < 0) {
                int button = code + 100;
                return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
            }
            return code < 256 && Keyboard.isKeyDown(code);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Screens where typing must not trigger push to talk. */
    private static boolean isTyping(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof GuiChat || screen instanceof GuiEditSign) {
            return true;
        }
        if (screen instanceof com.github.flurben.voicechat.gui.VoiceChatScreenBase) {
            return ((com.github.flurben.voicechat.gui.VoiceChatScreenBase) screen).isTyping();
        }
        String name = screen.getClass().getName();
        return name.contains("Search") || name.contains("Command") || name.contains("Book");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ClientManager m = ClientManager.INSTANCE;
        boolean allowed = mc.theWorld != null && !isTyping(mc.currentScreen);
        Boolean ptt = TestHooks.pttKey, whisper = TestHooks.whisperKey;
        m.setPttKeyDown(ptt != null ? ptt : allowed && isHeld(PTT));
        m.setWhisperKeyDown(whisper != null ? whisper : allowed && isHeld(WHISPER));
        ClientPositions.update(mc, event.renderTickTime);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        handlePresses();
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        handlePresses();
    }

    private void handlePresses() {
        Minecraft mc = Minecraft.getMinecraft();
        ClientManager m = ClientManager.INSTANCE;
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        if (VOICE_CHAT.isPressed()) {
            if (!m.config().onboardingFinished.get()) {
                mc.displayGuiScreen(OnboardingScreens.first(null));
            } else {
                mc.displayGuiScreen(new VoiceChatScreen());
            }
        }
        if (VOICE_CHAT_SETTINGS.isPressed()) {
            mc.displayGuiScreen(m.config().onboardingFinished.get() ? new VoiceChatSettingsScreen(null) : OnboardingScreens.first(null));
        }
        if (GROUP.isPressed()) {
            openGroupScreen(mc);
        }
        if (ADJUST_VOLUMES.isPressed()) {
            mc.displayGuiScreen(new AdjustVolumesScreen(null));
        }
        if (MUTE.isPressed()) {
            m.setMuted(!m.isMuted());
        }
        if (DISABLE.isPressed()) {
            m.setDisabled(!m.isDisabled());
        }
        if (HIDE_ICONS.isPressed()) {
            boolean hidden = !m.config().hideIcons.get();
            m.config().hideIcons.set(hidden).save();
            mc.ingameGUI.func_110326_a(I18n.format(hidden ? "message.voicechat.icons_hidden" : "message.voicechat.icons_visible"), false);
        }
        if (TOGGLE_RECORDING.isPressed()) {
            m.toggleRecording();
        }
    }

    public static void openGroupScreen(Minecraft mc) {
        ClientManager m = ClientManager.INSTANCE;
        if (m.getSession() == null) {
            mc.ingameGUI.func_110326_a(I18n.format("message.voicechat.voice_chat_unavailable"), false);
            return;
        }
        if (!m.getSession().getData().groupsEnabled) {
            mc.ingameGUI.func_110326_a(I18n.format("message.voicechat.groups_disabled"), false);
            return;
        }
        ClientGroup g = m.getGroup();
        mc.displayGuiScreen(g != null ? new GroupScreen(g) : new JoinGroupScreen());
    }

    public static String keyName(KeyBinding k) {
        int code = k.getKeyCode();
        if (code == 0) {
            return I18n.format("message.voicechat.not_bound");
        }
        return net.minecraft.client.settings.GameSettings.getKeyDisplayString(code);
    }
}
