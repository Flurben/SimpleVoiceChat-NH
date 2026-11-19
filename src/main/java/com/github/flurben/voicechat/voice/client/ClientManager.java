package com.github.flurben.voicechat.voice.client;

import java.io.File;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.config.VolumeConfig;
import com.github.flurben.voicechat.net.SecretData;
import com.github.flurben.voicechat.net.VoiceNetwork;
import com.github.flurben.voicechat.voice.common.ByteBufUtil;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Everything the client knows about the voice chat, and the audio threads it runs. */
public class ClientManager {

    public static ClientManager INSTANCE;

    private final ClientConfig config;
    private final VolumeConfig volumes;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ClientGroup> groups = new ConcurrentHashMap<>();
    private volatile ClientGroup group;
    private volatile ClientVoiceChat session;
    private volatile AudioOutput output;
    private volatile MicThread mic;
    private volatile UUID ownId;
    private volatile boolean pttKeyDown;
    private volatile boolean whisperKeyDown;
    private volatile boolean micTesting;
    private volatile boolean requested;
    private volatile String incompatibleVersion;
    private volatile boolean wrongPassword;
    private int requestDelay;

    public ClientManager(File configDir) {
        INSTANCE = this;
        config = new ClientConfig(new File(configDir, "voicechat-client.properties"));
        volumes = new VolumeConfig(new File(configDir, "voicechat-volumes.properties"));
    }

    public ClientConfig config() {
        return config;
    }

    public VolumeConfig getVolumes() {
        return volumes;
    }

    // ---------------------------------------------------------------- Lifecycle

    /** Called every client tick. */
    public void tick(Minecraft mc) {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            if (requested || session != null) {
                disconnect();
            }
            return;
        }
        if (!requested) {
            // Give the server a moment to set up the player
            if (++requestDelay < 10) {
                return;
            }
            requested = true;
            ByteBuf buf = VoiceNetwork.buffer(VoiceNetwork.C_REQUEST_SECRET);
            buf.writeInt(VoiceNetwork.PROTOCOL);
            ClientVoiceChat.sendCustomPayload(buf);
        }
    }

    public void disconnect() {
        requested = false;
        requestDelay = 0;
        incompatibleVersion = null;
        closeSession();
        states.clear();
        groups.clear();
        group = null;
        TalkCache.clear();
        ClientPositions.clear();
        if (!micTesting) {
            stopAudio();
        }
    }

    private void closeSession() {
        ClientVoiceChat s = session;
        session = null;
        if (s != null) {
            boolean recording = s.getRecorder() != null;
            s.close();
            if (recording) {
                AudioOutput out = output;
                if (out != null) {
                    out.setRecorder(null);
                }
            }
        }
    }

    private void startSession(SecretData data) {
        closeSession();
        ownId = data.playerId;
        SocketAddress address = ClientVoiceChat.resolve(data);
        ClientVoiceChat s = new ClientVoiceChat(data, data.playerId, address);
        session = s;
        s.start(config.forceTunnel.get());
        if (config.muteOnJoin.get()) {
            config.muted.set(true).save();
        }
        startAudio();
        sendState();
    }

    public void startAudio() {
        if (output == null) {
            AudioOutput o = new AudioOutput();
            output = o;
            o.start();
        }
        if (mic == null) {
            MicThread m = new MicThread();
            mic = m;
            m.start();
        }
    }

    public void stopAudio() {
        if (mic != null) {
            mic.close();
            mic = null;
        }
        if (output != null) {
            output.close();
            output = null;
        }
    }

    /** Reopens microphone and speaker, after a device or audio setting changed. */
    public void reloadAudio() {
        boolean running = output != null || mic != null;
        stopAudio();
        if (running || session != null || micTesting) {
            startAudio();
            ClientVoiceChat s = session;
            if (s != null && s.getRecorder() != null) {
                output.setRecorder(s.getRecorder());
            }
        }
    }

    // ---------------------------------------------------------------- State

    public ClientVoiceChat getSession() {
        return session;
    }

    public AudioOutput getOutput() {
        return output;
    }

    public MicThread getMic() {
        return mic;
    }

    public UUID getOwnId() {
        if (ownId != null) {
            return ownId;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null ? mc.thePlayer.getUniqueID() : mc.getSession().func_148256_e().getId();
    }

    public boolean isConnected() {
        ClientVoiceChat s = session;
        return s != null && s.isConnected();
    }

    /** If the server has the mod and sent the voice chat details. */
    public boolean hasSession() {
        return session != null;
    }

    public boolean isMuted() {
        return config.muted.get();
    }

    public void setMuted(boolean muted) {
        config.muted.set(muted).save();
    }

    public boolean isDisabled() {
        return config.disabled.get();
    }

    public void setDisabled(boolean disabled) {
        config.disabled.set(disabled).save();
        sendState();
    }

    public boolean isPttKeyDown() {
        return pttKeyDown;
    }

    public void setPttKeyDown(boolean down) {
        pttKeyDown = down;
    }

    public boolean isWhisperKeyDown() {
        return whisperKeyDown;
    }

    public void setWhisperKeyDown(boolean down) {
        whisperKeyDown = down;
    }

    public boolean isMicTesting() {
        return micTesting;
    }

    public void setMicTesting(boolean testing) {
        micTesting = testing;
        if (testing) {
            startAudio();
        } else if (session == null) {
            stopAudio();
        }
    }

    /** If the own microphone is sending right now, for the HUD. */
    public boolean isTalking() {
        MicThread m = mic;
        return m != null && m.isActive() && !micTesting;
    }

    public boolean isWhispering() {
        MicThread m = mic;
        return m != null && m.isWhispering();
    }

    public String getIncompatibleVersion() {
        return incompatibleVersion;
    }

    public PlayerState getState(UUID id) {
        return states.get(id);
    }

    public Collection<PlayerState> getStates() {
        return states.values();
    }

    public Collection<ClientGroup> getGroups() {
        return groups.values();
    }

    public ClientGroup getGroup() {
        return group;
    }

    public ClientGroup getGroup(UUID id) {
        return id == null ? null : groups.get(id);
    }

    /** Takes the wrong password answer, so the password screen can show it once. */
    public boolean consumeWrongPassword() {
        boolean w = wrongPassword;
        wrongPassword = false;
        return w;
    }

    public List<PlayerState> getGroupMembers(UUID groupId) {
        List<PlayerState> list = new ArrayList<>();
        if (groupId == null) {
            return list;
        }
        for (PlayerState s : states.values()) {
            if (groupId.equals(s.group)) {
                list.add(s);
            }
        }
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    public String getName(UUID id) {
        PlayerState s = states.get(id);
        if (s != null) {
            return s.name;
        }
        String n = volumes.getName(id);
        if (n != null) {
            return n;
        }
        if (id.equals(getOwnId())) {
            return Minecraft.getMinecraft().getSession().getUsername();
        }
        return null;
    }

    private void sendState() {
        if (session == null) {
            return;
        }
        ByteBuf buf = VoiceNetwork.buffer(VoiceNetwork.C_UPDATE_STATE);
        buf.writeBoolean(isDisabled());
        ClientVoiceChat.sendCustomPayload(buf);
    }

    // ---------------------------------------------------------------- Groups and recording

    public void createGroup(String name, String password, GroupType type) {
        ByteBuf buf = VoiceNetwork.buffer(VoiceNetwork.C_CREATE_GROUP);
        ByteBufUtil.writeString(buf, name);
        ByteBufUtil.writeNullableString(buf, password == null || password.isEmpty() ? null : password);
        buf.writeByte(type.ordinal());
        ClientVoiceChat.sendCustomPayload(buf);
    }

    public void joinGroup(UUID id, String password) {
        ByteBuf buf = VoiceNetwork.buffer(VoiceNetwork.C_JOIN_GROUP);
        ByteBufUtil.writeUUID(buf, id);
        ByteBufUtil.writeNullableString(buf, password);
        ClientVoiceChat.sendCustomPayload(buf);
    }

    public void leaveGroup() {
        ClientVoiceChat.sendCustomPayload(VoiceNetwork.buffer(VoiceNetwork.C_LEAVE_GROUP));
    }

    public boolean isRecording() {
        ClientVoiceChat s = session;
        return s != null && s.getRecorder() != null;
    }

    public boolean canRecord() {
        ClientVoiceChat s = session;
        return s != null && (s.getData().allowRecording || s.getRecorder() != null);
    }

    public void toggleRecording() {
        ClientVoiceChat s = session;
        Minecraft mc = Minecraft.getMinecraft();
        if (s == null || mc.thePlayer == null) {
            return;
        }
        AudioRecorder rec = s.getRecorder();
        if (rec == null) {
            if (!s.getData().allowRecording) {
                mc.thePlayer.addChatMessage(colored(I18n.format("message.voicechat.recording_disabled"), EnumChatFormatting.RED));
                return;
            }
            String dest = config.recordingDestination.get().trim();
            File base = dest.isEmpty() ? new File(mc.mcDataDir, "voicechat_recordings") : new File(dest);
            rec = new AudioRecorder(base, this::getName);
            s.setRecorder(rec);
            AudioOutput out = output;
            if (out != null) {
                out.setRecorder(rec);
            }
            mc.thePlayer.addChatMessage(colored(I18n.format("message.voicechat.recording_started"), EnumChatFormatting.DARK_RED));
        } else {
            s.setRecorder(null);
            AudioOutput out = output;
            if (out != null) {
                out.setRecorder(null);
            }
            rec.close();
            mc.thePlayer.addChatMessage(colored(I18n.format("message.voicechat.recording_stopped"), EnumChatFormatting.DARK_RED));
            IChatComponent open = new ChatComponentText(I18n.format("message.voicechat.open_folder"));
            open.getChatStyle().setColor(EnumChatFormatting.GREEN).setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, rec.getFolder().getAbsolutePath()))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(rec.getFolder().getAbsolutePath())));
            IChatComponent msg = new ChatComponentText(I18n.format("message.voicechat.save_session", "")).appendSibling(open);
            mc.thePlayer.addChatMessage(msg);
        }
    }

    public static IChatComponent colored(String text, EnumChatFormatting color) {
        IChatComponent c = new ChatComponentText(text);
        c.getChatStyle().setColor(color);
        return c;
    }

    // ---------------------------------------------------------------- Packets (network thread)

    public void handlePacket(ByteBuf payload) {
        ByteBuf buf = Unpooled.copiedBuffer(payload);
        int id = buf.readByte();
        if (id == VoiceNetwork.S_TUNNEL) {
            byte[] data = ByteBufUtil.readBytes(buf, 8192);
            ClientVoiceChat s = session;
            if (s != null) {
                s.onTunnelData(data);
            }
            return;
        }
        Minecraft.getMinecraft().func_152344_a(() -> {
            try {
                handleOnMain(id, buf);
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.warn("Invalid voice chat packet {}", id, e);
            }
        });
    }

    private void handleOnMain(int id, ByteBuf buf) {
        Minecraft mc = Minecraft.getMinecraft();
        switch (id) {
            case VoiceNetwork.S_SECRET:
                startSession(SecretData.read(buf));
                break;
            case VoiceNetwork.S_PLAYER_STATES: {
                int n = buf.readInt();
                states.clear();
                for (int i = 0; i < n; i++) {
                    PlayerState s = PlayerState.read(buf);
                    states.put(s.uuid, s);
                    rememberName(s);
                }
                break;
            }
            case VoiceNetwork.S_PLAYER_STATE: {
                PlayerState s = PlayerState.read(buf);
                states.put(s.uuid, s);
                rememberName(s);
                break;
            }
            case VoiceNetwork.S_REMOVE_PLAYER_STATE:
                states.remove(ByteBufUtil.readUUID(buf));
                break;
            case VoiceNetwork.S_ADD_GROUP: {
                ClientGroup g = ClientGroup.read(buf);
                groups.put(g.id, g);
                break;
            }
            case VoiceNetwork.S_REMOVE_GROUP:
                groups.remove(ByteBufUtil.readUUID(buf));
                break;
            case VoiceNetwork.S_JOINED_GROUP: {
                ClientGroup g = buf.readBoolean() ? ClientGroup.read(buf) : null;
                boolean wrong = buf.readBoolean();
                if (wrong) {
                    wrongPassword = true;
                } else {
                    group = g;
                    if (g != null) {
                        groups.put(g.id, g);
                    }
                }
                VoiceChatMod.proxy.onGroupChanged();
                break;
            }
            case VoiceNetwork.S_INCOMPATIBLE: {
                String version = ByteBufUtil.readString(buf, 64);
                incompatibleVersion = version;
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(colored(I18n.format("message.voicechat.incompatible_version", version, VoiceChatMod.NAME), EnumChatFormatting.RED));
                }
                break;
            }
            default:
                break;
        }
    }

    private void rememberName(PlayerState s) {
        if (volumes.getVolumes().containsKey(s.uuid) && !s.name.equals(volumes.getName(s.uuid))) {
            volumes.setVolume(s.uuid, s.name, volumes.getVolume(s.uuid));
        }
    }
}
