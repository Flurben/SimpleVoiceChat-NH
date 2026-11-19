package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.github.flurben.voicechat.gui.widgets.KeybindButton;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.gui.widgets.VButton;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/**
 * Base of all voice chat screens: a centered window of a fixed size with a title, buttons with tooltips, text fields
 * and scroll lists. In game the window is drawn as a vanilla style panel, in the menus over the dirt background.
 */
public abstract class VoiceChatScreenBase extends GuiScreen {

    protected final GuiScreen parent;
    protected String title;
    protected int xSize, ySize;
    protected int guiLeft, guiTop;
    protected final List<GuiTextField> textFields = new ArrayList<>();
    protected final List<ScrollList> lists = new ArrayList<>();

    protected VoiceChatScreenBase(GuiScreen parent, String title, int xSize, int ySize) {
        this.parent = parent;
        this.title = title;
        this.xSize = xSize;
        this.ySize = ySize;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        textFields.clear();
        lists.clear();
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    protected boolean isIngame() {
        return mc.theWorld != null;
    }

    protected int fontColor() {
        return isIngame() ? GuiDraw.FONT_COLOR : 0xFFFFFF;
    }

    /** If a text field has focus, so keys must not trigger push to talk or close the screen. */
    public boolean isTyping() {
        for (GuiTextField f : textFields) {
            if (f.isFocused() && f.getVisible()) {
                return true;
            }
        }
        return false;
    }

    public void onGroupChanged() {}

    protected <T extends GuiButton> T add(T button) {
        buttonList.add(button);
        return button;
    }

    protected GuiTextField addField(int x, int y, int w, int h, int maxLength) {
        GuiTextField f = new GuiTextField(fontRendererObj, x, y, w, h);
        f.setMaxStringLength(maxLength);
        textFields.add(f);
        return f;
    }

    protected ScrollList addList(ScrollList list) {
        lists.add(list);
        return list;
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : textFields) {
            f.updateCursorCounter();
        }
    }

    protected void drawBackgroundPanel(int mouseX, int mouseY) {
        if (isIngame()) {
            GuiDraw.panel(guiLeft, guiTop, xSize, ySize);
        }
    }

    protected void drawForeground(int mouseX, int mouseY) {}

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawBackgroundPanel(mouseX, mouseY);
        if (title != null) {
            fontRendererObj.drawString(title, guiLeft + (xSize - fontRendererObj.getStringWidth(title)) / 2, guiTop + 7, fontColor());
        }
        for (ScrollList l : lists) {
            l.draw(mc, mouseX, mouseY);
        }
        for (GuiTextField f : textFields) {
            if (f.getVisible()) {
                f.drawTextBox();
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        drawForeground(mouseX, mouseY);
        drawTooltips(mouseX, mouseY);
    }

    protected void drawTooltips(int mouseX, int mouseY) {
        for (Object o : buttonList) {
            if (o instanceof VButton) {
                VButton b = (VButton) o;
                if (b.isHovered(mouseX, mouseY)) {
                    List<String> tip = b.getTooltip();
                    if (tip != null && !tip.isEmpty()) {
                        drawTooltip(tip, mouseX, mouseY);
                        return;
                    }
                }
            }
        }
        for (ScrollList l : lists) {
            List<String> tip = l.getTooltip(mouseX, mouseY);
            if (tip != null && !tip.isEmpty()) {
                drawTooltip(tip, mouseX, mouseY);
                return;
            }
        }
    }

    protected void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        List<String> wrapped = new ArrayList<>();
        for (String s : lines) {
            wrapped.addAll(fontRendererObj.listFormattedStringToWidth(s, 200));
        }
        func_146283_a(wrapped, mouseX, mouseY);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof VButton) {
            ((VButton) button).onPress();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        for (Object o : buttonList) {
            if (o instanceof KeybindButton && ((KeybindButton) o).mouseClick(button)) {
                return;
            }
        }
        for (GuiTextField f : textFields) {
            if (f.getVisible()) {
                f.mouseClicked(mouseX, mouseY, button);
            }
        }
        for (ScrollList l : lists) {
            if (l.mouseClicked(mouseX, mouseY, button)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state >= 0) {
            for (ScrollList l : lists) {
                l.mouseReleased(mouseX, mouseY);
            }
            for (Object o : buttonList) {
                if (o instanceof VButton) {
                    ((VButton) o).release(mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        for (ScrollList l : lists) {
            l.mouseDragged(mouseX, mouseY);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            for (ScrollList l : lists) {
                l.scroll(mx, my, wheel);
            }
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        for (Object o : buttonList) {
            if (o instanceof VButton && ((VButton) o).keyTyped(c, key)) {
                return;
            }
        }
        for (GuiTextField f : textFields) {
            if (f.getVisible() && f.isFocused()) {
                if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                    onEnter();
                    return;
                }
                if (key == Keyboard.KEY_TAB) {
                    focusNext(f);
                    return;
                }
                if (key != Keyboard.KEY_ESCAPE) {
                    f.textboxKeyTyped(c, key);
                    onTextChanged(f);
                    return;
                }
            }
        }
        if (key == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            onEnter();
            return;
        }
        if (closeKey() != 0 && key == closeKey()) {
            mc.displayGuiScreen(null);
        }
    }

    private void focusNext(GuiTextField current) {
        int i = textFields.indexOf(current);
        current.setFocused(false);
        textFields.get((i + 1) % textFields.size()).setFocused(true);
    }

    /** A key besides escape that closes the screen, like the voice chat menu key. */
    protected int closeKey() {
        return 0;
    }

    protected void onEnter() {}

    protected void onTextChanged(GuiTextField field) {}

    protected void close() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
