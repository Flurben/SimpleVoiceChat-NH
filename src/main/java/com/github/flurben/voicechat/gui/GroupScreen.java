package com.github.flurben.voicechat.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.flurben.voicechat.gui.widgets.ImageButton;
import com.github.flurben.voicechat.gui.widgets.ScrollList;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.TalkCache;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

/** The group you are in: its members, who is talking, and buttons to leave or hide the group HUD. */
public class GroupScreen extends VoiceChatScreenBase {

    private final ClientGroup group;
    private ScrollList list;
    private int lastHash;

    public GroupScreen(ClientGroup group) {
        super(null, group.type == GroupType.NORMAL ? group.name : I18n.format("message.voicechat.group_type_title", group.name, I18n.format(group.type.translationKey())),
            236, 219);
        this.group = group;
    }

    @Override
    public void initGui() {
        super.initGui();
        ClientManager m = ClientManager.INSTANCE;
        list = addList(new ScrollList(guiLeft + 10, guiTop + 20, xSize - 20, ySize - 20 - 32, 24));
        list.setEmptyText(I18n.format("message.voicechat.no_group_members"));
        int by = guiTop + ySize - 28;
        add(new ImageButton(guiLeft + 10, by, () -> m.config().showGroupHud.get() ? GuiDraw.BUTTON_GROUP_HUD : GuiDraw.BUTTON_GROUP_HUD_OFF,
            () -> m.config().showGroupHud.set(!m.config().showGroupHud.get()).save()))
            .tooltip(() -> Collections.singletonList(I18n.format(m.config().showGroupHud.get() ? "message.voicechat.show_group_hud.enabled" : "message.voicechat.show_group_hud.disabled")));
        add(new ImageButton(guiLeft + 32, by, () -> GuiDraw.BUTTON_VOLUMES, () -> mc.displayGuiScreen(new AdjustVolumesScreen(this))))
            .tooltip(I18n.format("message.voicechat.adjust_volumes"));
        add(new ImageButton(guiLeft + xSize - 10 - 20, by, () -> GuiDraw.LEAVE, () -> {
            m.leaveGroup();
            mc.displayGuiScreen(new JoinGroupScreen());
        })).tooltip(I18n.format("message.voicechat.leave_group"));
        refresh();
    }

    @Override
    protected int closeKey() {
        return KeyEvents.GROUP.getKeyCode();
    }

    @Override
    public void onGroupChanged() {
        ClientGroup g = ClientManager.INSTANCE.getGroup();
        if (g == null) {
            mc.displayGuiScreen(new JoinGroupScreen());
        } else if (!g.id.equals(group.id)) {
            mc.displayGuiScreen(new GroupScreen(g));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        List<PlayerState> members = ClientManager.INSTANCE.getGroupMembers(group.id);
        int hash = members.hashCode();
        if (hash != lastHash) {
            refresh();
        }
    }

    private void refresh() {
        List<PlayerState> members = ClientManager.INSTANCE.getGroupMembers(group.id);
        lastHash = members.hashCode();
        List<MemberEntry> entries = new ArrayList<>();
        for (PlayerState s : members) {
            entries.add(new MemberEntry(s));
        }
        list.setEntries(entries);
    }

    private static class MemberEntry extends ScrollList.Entry {
        final PlayerState state;

        MemberEntry(PlayerState state) {
            this.state = state;
        }

        @Override
        public void draw(Minecraft mc, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
            ClientManager m = ClientManager.INSTANCE;
            boolean self = state.uuid.equals(m.getOwnId());
            boolean talking = self ? m.isTalking() : TalkCache.isTalking(state.uuid);
            if (talking) {
                Gui.drawRect(x + 3, y + 3, x + 21, y + 21, 0xFFFFFFFF);
            }
            GuiDraw.head(state.uuid, x + 4, y + 4, 16);
            mc.fontRenderer.drawStringWithShadow(state.name, x + 26, y + 8, 0xFFFFFF);
            int icon = -1;
            if (state.disconnected) {
                icon = GuiDraw.DISCONNECTED;
            } else if (state.disabled) {
                icon = GuiDraw.SPEAKER_OFF;
            } else if (talking) {
                icon = (self ? m.isWhispering() : TalkCache.isWhispering(state.uuid)) ? GuiDraw.SPEAKER_WHISPER : GuiDraw.SPEAKER;
            }
            if (icon >= 0) {
                GuiDraw.icon(icon, x + width - 20, y + 4, 16);
            }
        }

        @Override
        public List<String> tooltip(int x, int y, int width, int height, int mouseX, int mouseY) {
            if (mouseX >= x + width - 20) {
                if (state.disconnected) {
                    return Collections.singletonList(EnumChatFormatting.RED + I18n.format("message.voicechat.voice_chat_not_connected"));
                }
                if (state.disabled) {
                    return Collections.singletonList(I18n.format("message.voicechat.muted"));
                }
            }
            return null;
        }
    }
}
