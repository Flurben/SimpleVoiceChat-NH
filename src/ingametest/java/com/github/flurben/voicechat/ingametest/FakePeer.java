package com.github.flurben.voicechat.ingametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.flurben.voicechat.audio.AudioUtils;
import com.github.flurben.voicechat.audio.OpusCodec;
import com.github.flurben.voicechat.voice.common.Codec;
import com.github.flurben.voicechat.voice.common.PlayerState;
import com.github.flurben.voicechat.voice.common.VoiceCrypto;
import com.github.flurben.voicechat.voice.common.VoiceMessage;
import com.github.flurben.voicechat.voice.server.ClientConnection;
import com.github.flurben.voicechat.voice.server.PlayerPosition;
import com.github.flurben.voicechat.voice.server.ServerVoiceChat;

/**
 * A second voice chat client that only exists on the network: it is registered with the server like a player and
 * talks to it over real UDP, so both directions of the voice chat can be tested with one game.
 */
public class FakePeer {

    public final UUID id = UUID.fromString("5e7c0de0-0000-4000-8000-000000000042");
    public final String name = "TestPeer";
    private final byte[] key = VoiceCrypto.generateKey();
    private DatagramSocket socket;
    private volatile boolean running = true;
    volatile boolean authenticated;
    final AtomicInteger playerSounds = new AtomicInteger();
    final AtomicInteger groupSounds = new AtomicInteger();
    final AtomicInteger whisperSounds = new AtomicInteger();
    volatile VoiceMessage.Sound lastSound;
    private long sequence;

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        Field f = ServerVoiceChat.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    /** Runs on the server thread. */
    void register(ServerVoiceChat server, int dimension, double x, double y, double z) throws Exception {
        Map<UUID, PlayerState> states = field(server, "states");
        Map<UUID, ClientConnection> connections = field(server, "connections");
        PlayerState state = new PlayerState(id, name, false, false);
        states.put(id, state);
        connections.put(id, new ClientConnection(id, key));
        moveTo(server, dimension, x, y, z);
        broadcast(server);
    }

    void unregister(ServerVoiceChat server) throws Exception {
        Map<UUID, PlayerState> states = field(server, "states");
        Map<UUID, ClientConnection> connections = field(server, "connections");
        Map<UUID, PlayerPosition> positions = field(server, "positions");
        states.remove(id);
        connections.remove(id);
        positions.remove(id);
    }

    void broadcast(ServerVoiceChat server) throws Exception {
        Method m = ServerVoiceChat.class.getDeclaredMethod("broadcastState", PlayerState.class);
        m.setAccessible(true);
        m.invoke(server, server.getState(id));
    }

    void moveTo(ServerVoiceChat server, int dimension, double x, double y, double z) throws Exception {
        Map<UUID, PlayerPosition> positions = field(server, "positions");
        positions.put(id, new PlayerPosition(dimension, x, y, z));
    }

    void setGroup(ServerVoiceChat server, UUID group) throws Exception {
        server.getState(id).group = group;
        broadcast(server);
    }

    void connect(int port) throws Exception {
        socket = new DatagramSocket();
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        Thread t = new Thread(this::receive, "VoiceChatTestPeer");
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 40 && !authenticated; i++) {
            send(new VoiceMessage.Authenticate());
            Thread.sleep(100);
        }
        if (!authenticated) {
            throw new IllegalStateException("the peer did not get an answer from the voice server");
        }
    }

    private void receive() {
        byte[] buf = new byte[VoiceMessage.MAX_DATAGRAM];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                VoiceMessage msg = VoiceMessage.decodeFromServer(key, p.getData(), p.getLength());
                if (msg instanceof VoiceMessage.AuthenticateAck) {
                    authenticated = true;
                } else if (msg instanceof VoiceMessage.KeepAlive) {
                    send(new VoiceMessage.KeepAlive());
                } else if (msg instanceof VoiceMessage.Sound) {
                    VoiceMessage.Sound s = (VoiceMessage.Sound) msg;
                    if (s.data.length == 0) {
                        continue;
                    }
                    lastSound = s;
                    if (s.isGroup()) {
                        groupSounds.incrementAndGet();
                    } else {
                        playerSounds.incrementAndGet();
                    }
                    if (s.whispering) {
                        whisperSounds.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
            }
        }
    }

    void send(VoiceMessage msg) throws Exception {
        byte[] data = VoiceMessage.encodeFromClient(id, key, msg);
        socket.send(new DatagramPacket(data, data.length));
    }

    /** Talks for a while like a real client: Opus frames every 20 ms, then the end of the sentence. */
    void talk(long millis, boolean whisper) throws Exception {
        OpusCodec.Encoder enc = new OpusCodec.Encoder(Codec.VOIP, 1024);
        int frames = (int) (millis / 20);
        long next = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            send(new VoiceMessage.Mic(sequence++, whisper, enc.encode(InGameTestMod.voice(9000, i))));
            next += 20_000_000L;
            long sleep = next - System.nanoTime();
            if (sleep > 0) {
                Thread.sleep(sleep / 1_000_000L);
            }
        }
        send(new VoiceMessage.Mic(sequence++, whisper, new byte[0]));
    }

    void resetCounts() {
        playerSounds.set(0);
        groupSounds.set(0);
        whisperSounds.set(0);
        lastSound = null;
    }

    void close() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    static short[] silence() {
        return new short[AudioUtils.FRAME_SIZE];
    }
}
