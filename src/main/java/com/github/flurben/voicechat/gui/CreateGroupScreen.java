package com.github.flurben.voicechat.gui;

import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.server.ServerVoiceChat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

public class CreateGroupScreen extends VoiceChatScreenBase {

    private GuiTextField name;
    private GuiTextField password;
    private VButton create;
    private GroupType type = GroupType.NORMAL;

    public CreateGroupScreen(GuiScreen parent) {
        super(parent, I18n.format("gui.voicechat.create_group.title"), 195, 124);
    }

    @Override
    public void initGui() {
        String oldName = name == null ? "" : name.getText();
        String oldPassword = password == null ? "" : password.getText();
        super.initGui();
        name = addField(guiLeft + 7, guiTop + 32, xSize - 14, 12, 24);
        name.setText(oldName);
        name.setFocused(true);
        password = addField(guiLeft + 7, guiTop + 58, xSize - 14, 12, 32);
        password.setText(oldPassword);
        add(new VButton(guiLeft + 6, guiTop + 74, xSize - 12, 20, "", () -> type = GroupType.values()[(type.ordinal() + 1) % GroupType.values().length]) {
            @Override
            public void drawButton(Minecraft mc, int mx, int my) {
                displayString = I18n.format("message.voicechat.group_type", I18n.format(type.translationKey()));
                super.drawButton(mc, mx, my);
            }
        }.tooltip(() -> JoinGroupScreen.describe(type)));
        create = add(new VButton(guiLeft + 6, guiTop + ySize - 26, xSize - 12, 20, I18n.format("message.voicechat.create"), this::createGroup));
        validate();
    }

    @Override
    protected void onTextChanged(GuiTextField field) {
        validate();
    }

    private void validate() {
        create.enabled = ServerVoiceChat.GROUP_NAME.matcher(name.getText()).matches();
    }

    @Override
    protected void onEnter() {
        createGroup();
    }

    private void createGroup() {
        validate();
        if (!create.enabled) {
            return;
        }
        ClientManager.INSTANCE.createGroup(name.getText(), password.getText(), type);
    }

    @Override
    public void onGroupChanged() {
        if (ClientManager.INSTANCE.getGroup() != null) {
            mc.displayGuiScreen(new GroupScreen(ClientManager.INSTANCE.getGroup()));
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
        fontRendererObj.drawString(I18n.format("message.voicechat.group_name"), guiLeft + 8, guiTop + 22, GuiDraw.FONT_COLOR);
        fontRendererObj.drawString(I18n.format("message.voicechat.optional_password"), guiLeft + 8, guiTop + 48, GuiDraw.FONT_COLOR);
    }
}
