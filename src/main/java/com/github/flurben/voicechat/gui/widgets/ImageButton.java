package com.github.flurben.voicechat.gui.widgets;

import java.util.function.IntSupplier;

import org.lwjgl.opengl.GL11;

import com.github.flurben.voicechat.gui.GuiDraw;

import net.minecraft.client.Minecraft;

/** A 20x20 button showing an icon from the atlas, which may change with state. */
public class ImageButton extends VButton {

    private final IntSupplier icon;

    public ImageButton(int x, int y, IntSupplier icon, Runnable action) {
        super(x, y, 20, 20, "", action);
        this.icon = icon;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        super.drawButton(mc, mouseX, mouseY);
        if (!enabled) {
            GL11.glColor4f(0.6F, 0.6F, 0.6F, 1F);
        }
        GuiDraw.icon(icon.getAsInt(), xPosition + 2, yPosition + 2, 16);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
