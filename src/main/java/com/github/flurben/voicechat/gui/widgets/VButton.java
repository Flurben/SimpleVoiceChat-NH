package com.github.flurben.voicechat.gui.widgets;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiButton;

/** A vanilla button that runs an action and can have a tooltip. */
public class VButton extends GuiButton {

    protected Runnable action;
    protected Supplier<List<String>> tooltip;

    public VButton(int x, int y, int w, int h, String text, Runnable action) {
        super(0, x, y, w, h, text);
        this.action = action;
    }

    public VButton tooltip(Supplier<List<String>> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public VButton tooltip(String text) {
        this.tooltip = () -> Collections.singletonList(text);
        return this;
    }

    public void onPress() {
        if (action != null) {
            action.run();
        }
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return visible && mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
    }

    public List<String> getTooltip() {
        return tooltip == null ? null : tooltip.get();
    }

    /** Called with the mouse button released anywhere, for sliders and key listeners. */
    public void release(int mouseX, int mouseY) {}

    /** Gives a widget the chance to take a key press, returns true if it was used. */
    public boolean keyTyped(char c, int key) {
        return false;
    }
}
