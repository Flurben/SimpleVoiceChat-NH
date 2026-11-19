package com.github.flurben.voicechat.voice.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.github.flurben.voicechat.Tags;
import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.config.ServerConfig;
import com.github.flurben.voicechat.net.SecretData;
import com.github.flurben.voicechat.net.VoiceNetwork;
import com.github.flurben.voicechat.voice.common.ByteBufUtil;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;
import com.github.flurben.voicechat.voice.common.VoiceCrypto;
import com.github.flurben.voicechat.voice.common.VoiceMessage;
import com.mojang.authlib.properties.Property;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The voice chat server: hands out secrets over the Minecraft connection, receives audio on its own UDP socket and
 * forwards it to everyone who should hear it. The world is only touched on the server thread, the voice threads work
 * on snapshots taken every tick.
 */
public class ServerVoiceChat {

    public static final Pattern GROUP_NAME = Pattern.compile("^[^\\n\\r\\t\\s][^\\n\\r\\t]{0,23}$");

    private static ServerVoiceChat instance;

    private final MinecraftServer server;
    public final ServerConfig config;

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<SocketAddress, ClientConnection> byAddress = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPosition> positions = new ConcurrentHashMap<>();
    private final Map<UUID, EntityPlayerMP> players = new ConcurrentHashMap<>();
    /** Players without the mod that have to request a secret before the deadline (force_voice_chat). */
    private final Map<UUID, Long> loginDeadlines = new ConcurrentHashMap<>();
    /** Group invites: player -> group. An invited player can join without the password. */
    private final Map<UUID, UUID> invites = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    private DatagramSocket socket;
    private Thread receiveThread;
    private Thread processThread;
    private final LinkedBlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>(16384);
    private volatile boolean running;
    private long lastKeepAlive;

    private static class Incoming {
        final ClientConnection connection;
        final SocketAddress address;
        final VoiceMessage message;

        Incoming(ClientConnection connection, SocketAddress address, VoiceMessage message) {
            this.connection = connection;
            this.address = address;
            this.message = message;
        }
    }

    public ServerVoiceChat(MinecraftServer server, ServerConfig config) {
        this.server = server;
        this.config = config;
    }

    public static ServerVoiceChat get() {
        return instance;
    }

    public static void start(MinecraftServer server, ServerConfig config) {
        stop();
        ServerVoiceChat s = new ServerVoiceChat(server, config);
        instance = s;
        s.open();
    }

    public static void stop() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private void open() {
        running = true;
        try {
            int port = config.port.get();
            if (!server.isDedicatedServer() || port < 0) {
                port = 0;
            }
            InetAddress bind = null;
            String b = config.bindAddress.get().trim();
            if (b.isEmpty() && server.isDedicatedServer()) {
                b = server.getServerHostname();
            }
            if (!b.isEmpty() && !b.equals("*")) {
                try {
                    bind = InetAddress.getByName(b);
                } catch (IOException e) {
                    VoiceChatMod.LOGGER.warn("Invalid voice chat bind address '{}', binding to all addresses", b);
                }
            }
            socket = new DatagramSocket(new InetSocketAddress(bind, port));
            socket.setTrafficClass(0x04);
            VoiceChatMod.LOGGER.info("Voice chat server started at {}:{}", bind == null ? "*" : bind.getHostAddress(), socket.getLocalPort());
        } catch (SocketException e) {
            socket = null;
            VoiceChatMod.LOGGER.error("Failed to open the voice chat UDP port {}. Clients will use the tunnel through the Minecraft connection if it is allowed.",
                config.port.get(), e);
        }
        if (socket != null) {
            receiveThread = new Thread(this::receiveLoop, "VoiceChatServerReceiver");
            receiveThread.setDaemon(true);
            receiveThread.start();
        }
        processThread = new Thread(this::processLoop, "VoiceChatServerProcessor");
        processThread.setDaemon(true);
        processThread.start();
    }

    private void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        if (processThread != null) {
            processThread.interrupt();
        }
        connections.clear();
        byAddress.clear();
    }

    public int getPort() {
        return socket == null ? -1 : socket.getLocalPort();
    }

    // ---------------------------------------------------------------- UDP

    private void receiveLoop() {
        byte[] buf = new byte[VoiceMessage.MAX_DATAGRAM];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                UUID sender = VoiceMessage.readClientSender(p.getData(), p.getLength());
                if (sender == null) {
                    continue;
                }
                ClientConnection c = connections.get(sender);
                if (c == null) {
                    continue;
                }
                VoiceMessage msg;
                try {
                    msg = VoiceMessage.decodeFromClient(c.key, p.getData(), p.getLength());
                } catch (Exception e) {
                    continue;
                }
                incoming.offer(new Incoming(c, p.getSocketAddress(), msg));
            } catch (IOException e) {
                if (running) {
                    VoiceChatMod.LOGGER.debug("Voice chat receive error", e);
                }
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Voice chat server error", e);
            }
        }
    }

    /** A voice packet that arrived through the Minecraft connection. */
    public void onTunnelPacket(EntityPlayerMP player, byte[] data) {
        if (!config.allowTunnel.get()) {
            return;
        }
        UUID id = player.getUniqueID();
        ClientConnection c = connections.get(id);
        if (c == null) {
            return;
        }
        UUID sender = VoiceMessage.readClientSender(data, data.length);
        if (!id.equals(sender)) {
            return;
        }
        try {
            VoiceMessage msg = VoiceMessage.decodeFromClient(c.key, data, data.length);
            incoming.offer(new Incoming(c, null, msg));
        } catch (Exception ignored) {
        }
    }

    private void processLoop() {
        while (running) {
            try {
                Incoming in = incoming.poll(250, TimeUnit.MILLISECONDS);
                if (in != null) {
                    process(in);
                }
                long now = System.currentTimeMillis();
                if (now - lastKeepAlive >= config.keepAlive.get()) {
                    lastKeepAlive = now;
                    keepAlive(now);
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Voice chat processing error", e);
            }
        }
    }

    private void process(Incoming in) {
        ClientConnection c = in.connection;
        VoiceMessage msg = in.message;
        if (msg instanceof VoiceMessage.Authenticate) {
            if (in.address != null) {
                if (c.address != null && !c.address.equals(in.address)) {
                    byAddress.remove(c.address);
                }
                c.address = in.address;
                c.tunnel = false;
                byAddress.put(in.address, c);
            } else {
                c.tunnel = true;
            }
            boolean first = !c.authenticated;
            c.authenticated = true;
            c.lastKeepAlive = System.currentTimeMillis();
            send(c, new VoiceMessage.AuthenticateAck());
            if (first) {
                VoiceChatMod.LOGGER.info("Player {} connected to the voice chat{}", nameOf(c.player), c.tunnel ? " (tunnel)" : "");
                runOnMain(() -> setDisconnected(c.player, false));
            }
            return;
        }
        if (!c.authenticated) {
            return;
        }
        c.lastKeepAlive = System.currentTimeMillis();
        if (msg instanceof VoiceMessage.Mic) {
            onMic(c.player, (VoiceMessage.Mic) msg);
        }
    }

    private void keepAlive(long now) {
        long timeout = config.keepAlive.get() * 10L;
        for (ClientConnection c : connections.values()) {
            if (!c.authenticated) {
                continue;
            }
            if (now - c.lastKeepAlive > timeout) {
                VoiceChatMod.LOGGER.info("Player {} timed out from the voice chat", nameOf(c.player));
                c.authenticated = false;
                if (c.address != null) {
                    byAddress.remove(c.address);
                }
                runOnMain(() -> setDisconnected(c.player, true));
                continue;
            }
            send(c, new VoiceMessage.KeepAlive());
        }
    }

    private void send(ClientConnection c, VoiceMessage msg) {
        try {
            byte[] data = VoiceMessage.encodeFromServer(c.key, msg);
            if (c.tunnel || c.address == null) {
                EntityPlayerMP p = players.get(c.player);
                if (p != null) {
                    VoiceNetwork.sendToPlayer(p, VoiceNetwork.tunnelToClient(data));
                }
            } else if (socket != null) {
                socket.send(new DatagramPacket(data, data.length, c.address));
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.debug("Failed to send voice packet", e);
        }
    }

    // ---------------------------------------------------------------- Routing

    private void onMic(UUID sender, VoiceMessage.Mic mic) {
        PlayerState state = states.get(sender);
        PlayerPosition pos = positions.get(sender);
        if (state == null || pos == null || state.disabled) {
            return;
        }
        Group group = state.group == null ? null : groups.get(state.group);
        if (group != null) {
            VoiceMessage.Sound sound = new VoiceMessage.Sound(VoiceMessage.GROUP_SOUND, sender, mic.sequence, mic.whispering, 0F, pos.x, pos.y, pos.z, mic.data);
            for (PlayerState s : states.values()) {
                if (s.uuid.equals(sender) || !group.id.equals(s.group)) {
                    continue;
                }
                if (!config.crossDimension.get()) {
                    PlayerPosition rp = positions.get(s.uuid);
                    if (rp == null || rp.dimension != pos.dimension) {
                        continue;
                    }
                }
                sendSound(s, sound);
            }
            if (!group.isOpen()) {
                return;
            }
        }
        float distance = (float) (double) (mic.whispering ? config.whisperDistance.get() : config.voiceDistance.get());
        double range = config.getBroadcastRange(distance);
        double rangeSq = range * range;
        VoiceMessage.Sound sound = new VoiceMessage.Sound(VoiceMessage.PLAYER_SOUND, sender, mic.sequence, mic.whispering, distance, pos.x, pos.y, pos.z, mic.data);
        for (Map.Entry<UUID, PlayerPosition> e : positions.entrySet()) {
            UUID receiver = e.getKey();
            if (receiver.equals(sender)) {
                continue;
            }
            PlayerPosition rp = e.getValue();
            if (rp.dimension != pos.dimension || rp.distanceSq(pos) > rangeSq) {
                continue;
            }
            PlayerState rs = states.get(receiver);
            if (rs == null) {
                continue;
            }
            if (rs.group != null) {
                if (group != null && rs.group.equals(group.id)) {
                    // Already heard through the group
                    continue;
                }
                Group rg = groups.get(rs.group);
                if (rg != null && rg.type == GroupType.ISOLATED) {
                    continue;
                }
            }
            sendSound(rs, sound);
        }
    }

    private void sendSound(PlayerState receiver, VoiceMessage.Sound sound) {
        if (receiver.disabled) {
            return;
        }
        ClientConnection c = connections.get(receiver.uuid);
        if (c == null || !c.authenticated) {
            return;
        }
        send(c, sound);
    }

    // ---------------------------------------------------------------- Server thread

    public void runOnMain(Runnable r) {
        mainThreadTasks.add(r);
    }

    /** Called at the end of every server tick. */
    public void tick() {
        Runnable r;
        while ((r = mainThreadTasks.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Voice chat task failed", e);
            }
        }
        List<?> list = server.getConfigurationManager().playerEntityList;
        for (Object o : list) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            positions.put(p.getUniqueID(), new PlayerPosition(p.dimension, p.posX, p.boundingBox.minY + 1.62D, p.posZ));
            // Respawning and changing dimensions replace the entity
            if (players.get(p.getUniqueID()) != p) {
                players.put(p.getUniqueID(), p);
            }
        }
        if (!loginDeadlines.isEmpty()) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> e : loginDeadlines.entrySet()) {
                if (now < e.getValue()) {
                    continue;
                }
                loginDeadlines.remove(e.getKey());
                EntityPlayerMP p = players.get(e.getKey());
                if (p != null && !connections.containsKey(e.getKey())) {
                    p.playerNetServerHandler.kickPlayerFromServer("You need Simple Voice Chat NH " + Tags.VERSION + " to play on this server");
                }
            }
        }
    }

    public void onPlayerJoin(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        players.put(id, player);
        PlayerState state = new PlayerState(id, player.getCommandSenderName(), false, true);
        Collection<Property> textures = player.getGameProfile().getProperties().get("textures");
        for (Property p : textures) {
            state.textures = p.getValue();
            state.texturesSignature = p.getSignature();
        }
        states.put(id, state);
        positions.put(id, new PlayerPosition(player.dimension, player.posX, player.boundingBox.minY + 1.62D, player.posZ));
        broadcastState(state);
        if (config.forceVoiceChat.get() && !isLocalOwner(player)) {
            loginDeadlines.put(id, System.currentTimeMillis() + config.loginTimeout.get());
        }
    }

    private boolean isLocalOwner(EntityPlayerMP player) {
        return !server.isDedicatedServer() && player.getCommandSenderName().equals(server.getServerOwner());
    }

    public void onPlayerLeave(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        players.remove(id);
        positions.remove(id);
        loginDeadlines.remove(id);
        invites.remove(id);
        ClientConnection c = connections.remove(id);
        if (c != null && c.address != null) {
            byAddress.remove(c.address);
        }
        PlayerState state = states.remove(id);
        if (state != null && state.group != null) {
            UUID g = state.group;
            state.group = null;
            cleanupGroup(g);
        }
        VoiceNetwork.sendToPlayers(moddedPlayers(), buf -> ByteBufUtil.writeUUID(buf, id), VoiceNetwork.S_REMOVE_PLAYER_STATE);
    }

    public void onDimensionChange(EntityPlayerMP player) {
        positions.put(player.getUniqueID(), new PlayerPosition(player.dimension, player.posX, player.boundingBox.minY + 1.62D, player.posZ));
    }

    /** All players that requested a secret, so they have the mod. */
    public List<EntityPlayerMP> moddedPlayers() {
        List<EntityPlayerMP> list = new ArrayList<>();
        for (UUID id : connections.keySet()) {
            EntityPlayerMP p = players.get(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public boolean hasMod(UUID player) {
        return connections.containsKey(player);
    }

    public boolean isConnected(UUID player) {
        ClientConnection c = connections.get(player);
        return c != null && c.authenticated;
    }

    public ClientConnection getConnection(UUID player) {
        return connections.get(player);
    }

    public PlayerState getState(UUID player) {
        return states.get(player);
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }

    public Group getGroup(UUID id) {
        return id == null ? null : groups.get(id);
    }

    private void broadcastState(PlayerState state) {
        for (EntityPlayerMP p : moddedPlayers()) {
            VoiceNetwork.sendToPlayer(p, VoiceNetwork.playerState(state));
        }
    }

    private void setDisconnected(UUID player, boolean disconnected) {
        PlayerState s = states.get(player);
        if (s != null && s.disconnected != disconnected) {
            s.disconnected = disconnected;
            broadcastState(s);
        }
    }

    // ---------------------------------------------------------------- Packets from clients (server thread)

    public void handlePacket(EntityPlayerMP player, ByteBuf buf) {
        int id = buf.readByte();
        UUID uuid = player.getUniqueID();
        switch (id) {
            case VoiceNetwork.C_REQUEST_SECRET: {
                int protocol = buf.readInt();
                onRequestSecret(player, protocol);
                break;
            }
            case VoiceNetwork.C_UPDATE_STATE: {
                boolean disabled = buf.readBoolean();
                PlayerState s = states.get(uuid);
                if (s != null && s.disabled != disabled) {
                    s.disabled = disabled;
                    broadcastState(s);
                }
                break;
            }
            case VoiceNetwork.C_CREATE_GROUP: {
                String name = ByteBufUtil.readString(buf, 64);
                String password = ByteBufUtil.readNullableString(buf, 64);
                GroupType type = GroupType.byId(buf.readByte());
                createGroup(player, name, password, type);
                break;
            }
            case VoiceNetwork.C_JOIN_GROUP: {
                UUID group = ByteBufUtil.readUUID(buf);
                String password = ByteBufUtil.readNullableString(buf, 64);
                joinGroup(player, group, password);
                break;
            }
            case VoiceNetwork.C_LEAVE_GROUP:
                leaveGroup(player);
                break;
            case VoiceNetwork.C_TUNNEL: {
                byte[] data = ByteBufUtil.readBytes(buf, VoiceMessage.MAX_DATAGRAM);
                onTunnelPacket(player, data);
                break;
            }
            case VoiceNetwork.C_USE_TUNNEL: {
                ClientConnection c = connections.get(uuid);
                if (c != null && config.allowTunnel.get()) {
                    c.tunnel = true;
                }
                break;
            }
            default:
                break;
        }
    }

    private void onRequestSecret(EntityPlayerMP player, int protocol) {
        UUID uuid = player.getUniqueID();
        loginDeadlines.remove(uuid);
        if (protocol != VoiceNetwork.PROTOCOL) {
            VoiceChatMod.LOGGER.warn("{} has an incompatible voice chat version (protocol {}, server {})", player.getCommandSenderName(), protocol, VoiceNetwork.PROTOCOL);
            VoiceNetwork.sendToPlayer(player, VoiceNetwork.incompatible(Tags.VERSION, VoiceNetwork.PROTOCOL));
            return;
        }
        ClientConnection old = connections.remove(uuid);
        if (old != null && old.address != null) {
            byAddress.remove(old.address);
        }
        byte[] key = VoiceCrypto.generateKey();
        connections.put(uuid, new ClientConnection(uuid, key));
        SecretData data = new SecretData(uuid, key, getPort(), config.voiceHost.get().trim(), config.codec.get(), config.mtuSize.get(), config.voiceDistance.get(),
            config.whisperDistance.get(), config.keepAlive.get(), config.groupsEnabled.get(), config.allowRecording.get(), config.allowTunnel.get());
        VoiceNetwork.sendToPlayer(player, VoiceNetwork.secret(data));
        VoiceNetwork.sendToPlayer(player, VoiceNetwork.playerStates(states.values()));
        for (Group g : groups.values()) {
            VoiceNetwork.sendToPlayer(player, VoiceNetwork.addGroup(g.toClientGroup()));
        }
        PlayerState s = states.get(uuid);
        if (s != null && s.group != null) {
            Group g = groups.get(s.group);
            VoiceNetwork.sendToPlayer(player, VoiceNetwork.joinedGroup(g == null ? null : g.toClientGroup(), false));
        }
    }

    public Group createGroup(EntityPlayerMP player, String name, String password, GroupType type) {
        if (!config.groupsEnabled.get()) {
            player.addChatMessage(text("Groups are disabled on this server", EnumChatFormatting.RED));
            return null;
        }
        name = name.trim();
        if (!GROUP_NAME.matcher(name).matches()) {
            return null;
        }
        if (password != null && password.isEmpty()) {
            password = null;
        }
        Group group = new Group(UUID.randomUUID(), name, password, type, false);
        groups.put(group.id, group);
        ClientGroup cg = group.toClientGroup();
        for (EntityPlayerMP p : moddedPlayers()) {
            VoiceNetwork.sendToPlayer(p, VoiceNetwork.addGroup(cg));
        }
        setGroup(player, group);
        return group;
    }

    public void joinGroup(EntityPlayerMP player, UUID groupId, String password) {
        if (!config.groupsEnabled.get()) {
            player.addChatMessage(text("Groups are disabled on this server", EnumChatFormatting.RED));
            return;
        }
        Group group = groups.get(groupId);
        if (group == null) {
            player.addChatMessage(text("Such a group does not exist", EnumChatFormatting.RED));
            return;
        }
        boolean invited = groupId.equals(invites.get(player.getUniqueID()));
        if (group.password != null && !invited && !group.password.equals(password)) {
            VoiceNetwork.sendToPlayer(player, VoiceNetwork.joinedGroup(null, true));
            return;
        }
        invites.remove(player.getUniqueID());
        setGroup(player, group);
    }

    public void leaveGroup(EntityPlayerMP player) {
        setGroup(player, null);
    }

    private void setGroup(EntityPlayerMP player, Group group) {
        PlayerState s = states.get(player.getUniqueID());
        if (s == null) {
            return;
        }
        UUID old = s.group;
        s.group = group == null ? null : group.id;
        broadcastState(s);
        VoiceNetwork.sendToPlayer(player, VoiceNetwork.joinedGroup(group == null ? null : group.toClientGroup(), false));
        if (old != null && (group == null || !old.equals(group.id))) {
            cleanupGroup(old);
        }
    }

    private void cleanupGroup(UUID groupId) {
        Group g = groups.get(groupId);
        if (g == null || g.persistent) {
            return;
        }
        for (PlayerState s : states.values()) {
            if (groupId.equals(s.group)) {
                return;
            }
        }
        groups.remove(groupId);
        for (EntityPlayerMP p : moddedPlayers()) {
            VoiceNetwork.sendToPlayer(p, VoiceNetwork.removeGroup(groupId));
        }
    }

    public void invite(EntityPlayerMP inviter, EntityPlayerMP target, Group group) {
        invites.put(target.getUniqueID(), group.id);
        IChatComponent accept = new ChatComponentText("[ACCEPT]");
        accept.getChatStyle().setColor(EnumChatFormatting.GREEN)
            .setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND, "/voicechat join " + group.id))
            .setChatHoverEvent(new net.minecraft.event.HoverEvent(net.minecraft.event.HoverEvent.Action.SHOW_TEXT, new ChatComponentText("Click to accept the invitation")));
        IChatComponent msg = new ChatComponentText(inviter.getCommandSenderName() + " invited you to the group " + group.name + ". ");
        msg.appendSibling(accept);
        target.addChatMessage(msg);
    }

    private String nameOf(UUID id) {
        PlayerState s = states.get(id);
        return s == null ? id.toString() : s.name;
    }

    public static IChatComponent text(String s, EnumChatFormatting color) {
        IChatComponent c = new ChatComponentText(s);
        if (color != null) {
            c.getChatStyle().setColor(color);
        }
        return c;
    }
}
