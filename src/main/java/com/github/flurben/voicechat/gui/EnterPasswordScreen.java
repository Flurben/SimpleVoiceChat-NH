package com.github.flurben.voicechat.gui;

import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.common.ClientGroup;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

public class EnterPasswordScreen extends VoiceChatScreenBase {

    private final ClientGroup group;
    private GuiTextField password;
    private VButton join;
    private boolean wrong;

    public EnterPasswordScreen(GuiScreen parent, ClientGroup group) {
        super(parent, I18n.format("gui.voicechat.enter_password.title"), 195, 74);
        this.group = group;
    }

    @Override
    public void initGui() {
        String old = password == null ? "" : password.getText();
        super.initGui();
        password = addField(guiLeft + 7, guiTop + 22, xSize - 14, 12, 32);
        password.setText(old);
        password.setFocused(true);
        join = add(new VButton(guiLeft + 6, guiTop + ySize - 26, xSize - 12, 20, I18n.format("message.voicechat.join_group"), this::joinGroup));
        onTextChanged(password);
        ClientManager.INSTANCE.consumeWrongPassword();
    }

    @Override
    protected void onTextChanged(GuiTextField field) {
        join.enabled = !password.getText().isEmpty();
    }

    @Override
    protected void onEnter() {
        joinGroup();
    }

    private void joinGroup() {
        if (password.getText().isEmpty()) {
            return;
        }
        wrong = false;
        ClientManager.INSTANCE.joinGroup(group.id, password.getText());
    }

    @Override
    public void onGroupChanged() {
        ClientManager m = ClientManager.INSTANCE;
        if (m.consumeWrongPassword()) {
            wrong = true;
            password.setText("");
            onTextChanged(password);
            return;
        }
        if (m.getGroup() != null) {
            mc.displayGuiScreen(new GroupScreen(m.getGroup()));
        }
    }

    @Override
    protected void drawBackgroundPanel(int mouseX, int mouseY) {
        GuiDraw.panel(guiLeft, guiTop, xSize, ySize);
    }

    @Override
    protected int fontColor() {
        return GuiDraw.FONT_COLOR;
    }

    @Override
    protected void drawForeground(int mouseX, int mouseY) {
        if (wrong) {
            String s = EnumChatFormatting.DARK_RED + I18n.format("message.voicechat.wrong_password");
            fontRendererObj.drawString(s, guiLeft + xSize / 2 - fontRendererObj.getStringWidth(s) / 2, guiTop + 37, 0);
        } else if (password.getText().isEmpty() && !password.isFocused()) {
            fontRendererObj.drawString(I18n.format("message.voicechat.password"), guiLeft + 10, guiTop + 24, 0x808080);
        }
    }
}
