package com.github.flurben.voicechat.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.github.flurben.voicechat.voice.common.PlayerState;
import com.github.flurben.voicechat.voice.server.ClientConnection;
import com.github.flurben.voicechat.voice.server.Group;
import com.github.flurben.voicechat.voice.server.ServerVoiceChat;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;

/** {@code /voicechat} with help, test, invite, join and leave. */
public class VoiceChatCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "voicechat";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("vc");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/voicechat <help|test|invite|join|leave>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        ServerVoiceChat server = ServerVoiceChat.get();
        if (server == null) {
            throw new CommandException("Voice chat is not running");
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "test": {
                if (!sender.canCommandSenderUseCommand(2, "voicechat")) {
                    throw new CommandException("commands.generic.permission");
                }
                if (args.length < 2) {
                    throw new WrongUsageException("/voicechat test <player>");
                }
                EntityPlayerMP target = getPlayer(sender, args[1]);
                test(server, sender, target);
                return;
            }
            case "invite": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                if (args.length < 2) {
                    throw new WrongUsageException("/voicechat invite <player>");
                }
                EntityPlayerMP target = getPlayer(sender, args[1]);
                if (!server.config.groupsEnabled.get()) {
                    throw new CommandException("Groups are disabled on this server");
                }
                PlayerState state = server.getState(self.getUniqueID());
                Group group = state == null ? null : server.getGroup(state.group);
                if (group == null) {
                    throw new CommandException("You are not in a group");
                }
                if (!server.hasMod(target.getUniqueID())) {
                    throw new CommandException(target.getCommandSenderName() + " does not have Simple Voice Chat NH installed");
                }
                server.invite(self, target, group);
                sender.addChatMessage(ServerVoiceChat.text("Successfully invited " + target.getCommandSenderName(), EnumChatFormatting.GREEN));
                return;
            }
            case "join": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                if (args.length < 2) {
                    throw new WrongUsageException("/voicechat join <group> [password]");
                }
                // Group names may contain spaces: the longest leading words that name a group win, the rest is the password
                Group group = null;
                int nameEnd = 2;
                for (int end = args.length; end >= 2 && group == null; end--) {
                    String name = unquote(String.join(" ", Arrays.copyOfRange(args, 1, end)));
                    try {
                        group = findGroup(server, name);
                        nameEnd = end;
                    } catch (CommandException e) {
                        if (end == 2) {
                            throw e;
                        }
                    }
                }
                String password = args.length > nameEnd ? unquote(String.join(" ", Arrays.copyOfRange(args, nameEnd, args.length))) : null;
                server.joinGroup(self, group.id, password);
                PlayerState state = server.getState(self.getUniqueID());
                if (state != null && group.id.equals(state.group)) {
                    sender.addChatMessage(ServerVoiceChat.text("Successfully joined the group " + group.name, EnumChatFormatting.GREEN));
                } else if (group.password != null) {
                    sender.addChatMessage(ServerVoiceChat.text("Incorrect Password", EnumChatFormatting.RED));
                }
                return;
            }
            case "leave": {
                EntityPlayerMP self = getCommandSenderAsPlayer(sender);
                PlayerState state = server.getState(self.getUniqueID());
                if (state == null || state.group == null) {
                    throw new CommandException("You are not in a group");
                }
                server.leaveGroup(self);
                sender.addChatMessage(ServerVoiceChat.text("Successfully left the group", EnumChatFormatting.GREEN));
                return;
            }
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Group findGroup(ServerVoiceChat server, String arg) {
        try {
            Group g = server.getGroup(UUID.fromString(arg));
            if (g != null) {
                return g;
            }
        } catch (IllegalArgumentException ignored) {
        }
        Group found = null;
        for (Group g : server.getGroups()) {
            if (g.name.equalsIgnoreCase(arg)) {
                if (found != null) {
                    throw new CommandException("Group name is ambiguous");
                }
                found = g;
            }
        }
        if (found == null) {
            throw new CommandException("Such a group does not exist");
        }
        return found;
    }

    private static void test(ServerVoiceChat server, ICommandSender sender, EntityPlayerMP target) {
        String name = target.getCommandSenderName();
        ClientConnection c = server.getConnection(target.getUniqueID());
        if (c == null) {
            sender.addChatMessage(ServerVoiceChat.text(name + " does not have Simple Voice Chat NH installed", EnumChatFormatting.RED));
            return;
        }
        if (!c.authenticated) {
            sender.addChatMessage(ServerVoiceChat.text(name + " has the mod installed, but the voice chat is not connected", EnumChatFormatting.RED));
            sender.addChatMessage(ServerVoiceChat.text("Voice chat UDP port: " + server.getPort()
                + (server.config.allowTunnel.get() ? ". The client switches to the tunnel when UDP does not work." : ""), EnumChatFormatting.GRAY));
            return;
        }
        long age = System.currentTimeMillis() - c.lastKeepAlive;
        sender.addChatMessage(ServerVoiceChat.text(name + " is connected to the voice chat " + (c.tunnel ? "through the tunnel" : "via UDP " + c.address)
            + " (last response " + age + " ms ago)", EnumChatFormatting.GREEN));
    }

    private static void help(ICommandSender sender) {
        sender.addChatMessage(ServerVoiceChat.text("Simple Voice Chat NH commands:", EnumChatFormatting.GOLD));
        sender.addChatMessage(ServerVoiceChat.text("/voicechat test <player> - check the voice connection of a player (operators)", null));
        sender.addChatMessage(ServerVoiceChat.text("/voicechat invite <player> - invite a player to your group", null));
        sender.addChatMessage(ServerVoiceChat.text("/voicechat join <group> [password] - join a group", null));
        sender.addChatMessage(ServerVoiceChat.text("/voicechat leave - leave your group", null));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "help", "test", "invite", "join", "leave");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("test") || args[0].equalsIgnoreCase("invite"))) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            ServerVoiceChat server = ServerVoiceChat.get();
            if (server != null) {
                List<String> names = new ArrayList<>();
                for (Group g : server.getGroups()) {
                    if (!g.name.contains(" ")) {
                        names.add(g.name);
                    }
                }
                return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
            }
        }
        return null;
    }
}
