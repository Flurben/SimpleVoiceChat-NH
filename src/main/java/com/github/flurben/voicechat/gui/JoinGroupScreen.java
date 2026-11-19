package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

/** All groups on the server, click one to join it, or create a new one. */
public class JoinGroupScreen extends VoiceChatScreenBase {

    private ScrollList list;
    private int lastCount = -1;

    public JoinGroupScreen() {
        super(null, I18n.format("gui.voicechat.join_create_group.title"), 236, 219);
    }

    @Override
    public void initGui() {
        super.initGui();
        list = addList(new ScrollList(guiLeft + 10, guiTop + 20, xSize - 20, ySize - 20 - 32, 32));
        list.setEmptyText(I18n.format("message.voicechat.no_groups"));
        add(new VButton(guiLeft + 10, guiTop + ySize - 28, xSize - 20, 20, I18n.format("message.voicechat.create_group"), () -> mc.displayGuiScreen(new CreateGroupScreen(this))));
        refresh();
    }

    @Override
    protected int closeKey() {
        return KeyEvents.GROUP.getKeyCode();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        int count = ClientManager.INSTANCE.getGroups().size() * 1000 + ClientManager.INSTANCE.getStates().size();
        if (count != lastCount) {
            refresh();
        }
    }

    private void refresh() {
        ClientManager m = ClientManager.INSTANCE;
        lastCount = m.getGroups().size() * 1000 + m.getStates().size();
        List<GroupEntry> entries = new ArrayList<>();
        for (ClientGroup g : m.getGroups()) {
            entries.add(new GroupEntry(g));
        }
        entries.sort((a, b) -> a.group.name.compareToIgnoreCase(b.group.name));
        list.setEntries(entries);
    }

    @Override
    public void onGroupChanged() {
        ClientGroup g = ClientManager.INSTANCE.getGroup();
        if (g != null) {
            mc.displayGuiScreen(new GroupScreen(g));
        }
    }

    private class GroupEntry extends ScrollList.Entry {
        final ClientGroup group;

        GroupEntry(ClientGroup group) {
            this.group = group;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                Gui.drawRect(x, y, x + width, y + height, 0x20FFFFFF);
            }
            GuiDraw.icon(GuiDraw.GROUP, x + 4, y + 4, 24);
            String title = group.type == GroupType.NORMAL ? group.name
                : I18n.format("message.voicechat.group_type_title", group.name, I18n.format(group.type.translationKey()));
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(title, width - 52), x + 32, y + 5, 0xFFFFFF);
            if (group.hasPassword) {
                GuiDraw.icon(GuiDraw.LOCK_LIGHT, x + width - 20, y + 2, 16);
            }
            List<PlayerState> members = ClientManager.INSTANCE.getGroupMembers(group.id);
            int hx = x + 32;
            int max = (width - 40) / 11;
            for (int i = 0; i < members.size() && i < max; i++) {
                GuiDraw.head(members.get(i).uuid, hx, y + 17, 10);
                hx += 11;
            }
            if (members.size() > max) {
                String more = I18n.format("message.voicechat.more_members", members.size() - max);
                mc.fontRenderer.drawString(more, hx + 2, y + 18, 0xAAAAAA);
            } else if (members.isEmpty()) {
                mc.fontRenderer.drawString(I18n.format("message.voicechat.no_group_members"), hx, y + 18, 0xAAAAAA);
            }
        }

        @Override
        public List<String> tooltip(int x, int y, int width, int height, int mouseX, int mouseY) {
            List<String> lines = new ArrayList<>();
            lines.add(group.name);
            lines.add(EnumChatFormatting.GRAY + I18n.format(group.type.translationKey()) + ": " + I18n.format(group.type.descriptionKey()));
            List<PlayerState> members = ClientManager.INSTANCE.getGroupMembers(group.id);
            if (!members.isEmpty()) {
                lines.add(I18n.format("message.voicechat.group_members"));
                for (int i = 0; i < members.size() && i < 10; i++) {
                    lines.add(EnumChatFormatting.GRAY + "  " + members.get(i).name);
                }
                if (members.size() > 10) {
                    lines.add(EnumChatFormatting.GRAY + "  " + I18n.format("message.voicechat.more_members", members.size() - 10));
                }
            }
            return lines.isEmpty() ? null : lines;
        }

        @Override
        public boolean click(int x, int y, int width, int height, int mouseX, int mouseY, int button) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1F));
            if (group.hasPassword) {
                mc.displayGuiScreen(new EnterPasswordScreen(JoinGroupScreen.this, group));
            } else {
                ClientManager.INSTANCE.joinGroup(group.id, null);
            }
            return true;
        }
    }

    static List<String> describe(GroupType type) {
        return Arrays.asList(I18n.format(type.translationKey()), EnumChatFormatting.GRAY + I18n.format(type.descriptionKey()));
    }
}
