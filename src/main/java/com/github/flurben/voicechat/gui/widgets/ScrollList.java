package com.github.flurben.voicechat.gui.widgets;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.github.flurben.voicechat.gui.GuiDraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

/** A scrolling list of entries drawn inside a dark inset, with a scroll bar. */
public class ScrollList {

    public abstract static class Entry {
        /** Draws the entry, x/y is the top left inside the list. */
        public abstract void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered);

        /** @return true if the click was used */
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            return false;
        }

        public void release(int mouseX, int mouseY) {}

        public void drag(int x, int width, int mouseX, int mouseY) {}

        public List<String> tooltip(int x, int y, int width, int height, int mouseX, int mouseY) {
            return null;
        }
    }

    public int x, y, width, height;
    public final int entryHeight;
    private final List<Entry> entries = new ArrayList<>();
    private double scroll;
    private boolean draggingBar;
    private Entry pressed;
    private String emptyText;

    public ScrollList(int x, int y, int width, int height, int entryHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.entryHeight = entryHeight;
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
    }

    public void setEntries(List<? extends Entry> list) {
        entries.clear();
        entries.addAll(list);
        clampScroll();
    }

    public List<Entry> getEntries() {
        return entries;
    }

    private int contentHeight() {
        return entries.size() * entryHeight;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (height - 2));
    }

    private void clampScroll() {
        scroll = Math.max(0D, Math.min(maxScroll(), scroll));
    }

    public boolean isInside(int mx, int my) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    private int innerWidth() {
        return width - 2 - (maxScroll() > 0 ? 6 : 0);
    }

    public void draw(Minecraft mc, int mouseX, int mouseY) {
        GuiDraw.darkInset(x, y, width, height);
        if (entries.isEmpty() && emptyText != null) {
            mc.fontRenderer.drawString(emptyText, x + width / 2 - mc.fontRenderer.getStringWidth(emptyText) / 2, y + height / 2 - 4, 0xAAAAAA);
            return;
        }
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int f = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((x + 1) * f, mc.displayHeight - (y + height - 1) * f, (width - 2) * f, (height - 2) * f);
        int iw = innerWidth();
        boolean inside = isInside(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 1 + i * entryHeight - (int) scroll;
            if (ey + entryHeight < y || ey > y + height) {
                continue;
            }
            boolean hovered = inside && mouseX < x + 1 + iw && mouseY >= ey && mouseY < ey + entryHeight;
            entries.get(i).draw(mc, x + 1, ey, iw, entryHeight, mouseX, mouseY, hovered);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (maxScroll() > 0) {
            int barX = x + width - 7;
            int track = height - 2;
            int barH = Math.max(10, track * track / contentHeight());
            int barY = y + 1 + (int) ((track - barH) * (scroll / maxScroll()));
            Gui.drawRect(barX, y + 1, barX + 6, y + height - 1, 0xFF000000);
            Gui.drawRect(barX, barY, barX + 6, barY + barH, 0xFF808080);
            Gui.drawRect(barX, barY, barX + 5, barY + barH - 1, 0xFFC0C0C0);
        }
    }

    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY)) {
            return null;
        }
        int iw = innerWidth();
        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 1 + i * entryHeight - (int) scroll;
            if (mouseY >= ey && mouseY < ey + entryHeight && mouseY >= y && mouseY < y + height) {
                return entries.get(i).tooltip(x + 1, ey, iw, entryHeight, mouseX, mouseY);
            }
        }
        return null;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isInside(mouseX, mouseY)) {
            return false;
        }
        if (maxScroll() > 0 && mouseX >= x + width - 7) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        int iw = innerWidth();
        for (int i = 0; i < entries.size(); i++) {
            int ey = y + 1 + i * entryHeight - (int) scroll;
            if (mouseY >= ey && mouseY < ey + entryHeight) {
                Entry e = entries.get(i);
                pressed = e;
                return e.click(x + 1, ey, iw, entryHeight, mouseX, mouseY, button);
            }
        }
        return false;
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (draggingBar) {
            dragBar(mouseY);
        } else if (pressed != null) {
            pressed.drag(x + 1, innerWidth(), mouseX, mouseY);
        }
    }

    private void dragBar(int mouseY) {
        double t = (mouseY - y) / (double) height;
        scroll = t * maxScroll();
        clampScroll();
    }

    public void mouseReleased(int mouseX, int mouseY) {
        draggingBar = false;
        if (pressed != null) {
            pressed.release(mouseX, mouseY);
            pressed = null;
        }
    }

    public void scroll(int mouseX, int mouseY, int wheel) {
        if (wheel != 0 && isInside(mouseX, mouseY)) {
            scroll -= Math.signum(wheel) * entryHeight / 2D;
            clampScroll();
        }
    }
}
