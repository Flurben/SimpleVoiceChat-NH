package com.github.flurben.voicechat.gui.widgets;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;

/** A vanilla looking slider for a value between 0 and 1. */
public abstract class SliderButton extends VButton {

    protected double value;
    private boolean dragging;

    public SliderButton(int x, int y, int w, int h, double value) {
        super(x, y, w, h, "", null);
        this.value = value;
    }

    protected abstract String getText();

    protected abstract void applyValue();

    /** Called once when the slider is let go, for saving. */
    protected void onRelease() {}

    @Override
    public int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        displayString = getText();
        super.drawButton(mc, mouseX, mouseY);
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        if (dragging) {
            setFromMouse(mouseX);
        }
        drawBackground(mc);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        int knob = xPosition + (int) (value * (width - 8));
        drawTexturedModalRect(knob, yPosition, 0, 66, 4, 20);
        drawTexturedModalRect(knob + 4, yPosition, 196, 66, 4, 20);
    }

    /** Extra drawing between the track and the knob, e.g. a level meter. */
    protected void drawBackground(Minecraft mc) {}

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            setFromMouse(mouseX);
            dragging = true;
            return true;
        }
        return false;
    }

    private void setFromMouse(int mouseX) {
        double v = (mouseX - (xPosition + 4)) / (double) (width - 8);
        value = Math.max(0D, Math.min(1D, v));
        applyValue();
    }

    @Override
    public void onPress() {}

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        release(mouseX, mouseY);
    }

    @Override
    public void release(int mouseX, int mouseY) {
        if (dragging) {
            dragging = false;
            onRelease();
        }
    }
}
