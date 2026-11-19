package com.github.flurben.voicechat.gui.widgets;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumChatFormatting;

/** Shows a key binding and changes it: click, then press a key or mouse button. Escape unbinds. */
public class KeybindButton extends VButton {

    private final KeyBinding binding;
    private final String label;
    private boolean listening;

    public KeybindButton(KeyBinding binding, int x, int y, int w, int h, String label) {
        super(x, y, w, h, "", null);
        this.binding = binding;
        this.label = label;
        tooltip(I18n.format("message.voicechat.press_to_reassign_key"));
    }

    public boolean isListening() {
        return listening;
    }

    public void stopListening() {
        listening = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        String key = binding.getKeyCode() == 0 ? I18n.format("message.voicechat.not_bound") : GameSettings.getKeyDisplayString(binding.getKeyCode());
        if (listening) {
            displayString = label + ": " + EnumChatFormatting.YELLOW + "> " + key + " <";
        } else {
            displayString = label + ": " + key;
        }
        super.drawButton(mc, mouseX, mouseY);
    }

    @Override
    public void onPress() {
        listening = !listening;
    }

    /** A mouse click while listening binds the mouse button. */
    public boolean mouseClick(int button) {
        if (!listening) {
            return false;
        }
        set(button - 100);
        return true;
    }

    @Override
    public boolean keyTyped(char c, int key) {
        if (!listening) {
            return false;
        }
        set(key == Keyboard.KEY_ESCAPE ? 0 : key);
        return true;
    }

    private void set(int code) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.setOptionKeyBinding(binding, code);
        KeyBinding.resetKeyBindingArrayAndHash();
        mc.gameSettings.saveOptions();
        listening = false;
    }
}
