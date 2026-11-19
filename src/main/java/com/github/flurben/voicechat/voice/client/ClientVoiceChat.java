package com.github.flurben.voicechat.voice.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.net.SecretData;
import com.github.flurben.voicechat.net.VoiceNetwork;
import com.github.flurben.voicechat.voice.common.ByteBufUtil;
import com.github.flurben.voicechat.voice.common.VoiceMessage;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C17PacketCustomPayload;

/**
 * The voice connection to one server. Authenticates over UDP and falls back to the tunnel through the Minecraft
 * connection when no answer comes back, then keeps the connection alive and hands received audio to the output.
 */
public class ClientVoiceChat {

    private static final long AUTH_INTERVAL = 500L;
    private static final int UDP_ATTEMPTS = 10;

    private final SecretData data;
    private final UUID ownId;
    private final SocketAddress serverAddress;
    private DatagramSocket socket;
    private volatile boolean running = true;
    private volatile boolean connected;
    private volatile boolean tunnel;
    private volatile long lastReceived;
    private Thread receiveThread;
    private Thread authThread;
    private volatile AudioRecorder recorder;

    public ClientVoiceChat(SecretData data, UUID ownId, SocketAddress serverAddress) {
        this.data = data;
        this.ownId = ownId;
        this.serverAddress = serverAddress;
    }

    public SecretData getData() {
        return data;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isTunnel() {
        return tunnel;
    }

    public AudioRecorder getRecorder() {
        return recorder;
    }

    public void setRecorder(AudioRecorder recorder) {
        this.recorder = recorder;
    }

    public void start(boolean forceTunnel) {
        boolean udp = data.port > 0 && serverAddress != null && !(forceTunnel && data.allowTunnel);
        if (udp) {
            try {
                socket = new DatagramSocket();
                socket.setTrafficClass(0x04);
                receiveThread = new Thread(this::receiveLoop, "VoiceChatClientReceiver");
                receiveThread.setDaemon(true);
                receiveThread.start();
            } catch (IOException e) {
                VoiceChatMod.LOGGER.error("Failed to open the voice chat socket", e);
                udp = false;
            }
        }
        if (!udp) {
            if (!data.allowTunnel) {
                VoiceChatMod.LOGGER.error("The voice chat server has no UDP port and does not allow the tunnel");
                return;
            }
            switchToTunnel();
        }
        authThread = new Thread(this::authLoop, "VoiceChatClientConnection");
        authThread.setDaemon(true);
        authThread.start();
    }

    private void switchToTunnel() {
        if (tunnel) {
            return;
        }
        tunnel = true;
        VoiceChatMod.LOGGER.info("Using the voice chat tunnel through the Minecraft connection");
        sendCustomPayload(VoiceNetwork.buffer(VoiceNetwork.C_USE_TUNNEL));
    }

    public void close() {
        running = false;
        connected = false;
        if (socket != null) {
            socket.close();
        }
        if (authThread != null) {
            authThread.interrupt();
        }
        AudioRecorder r = recorder;
        recorder = null;
        if (r != null) {
            r.close();
        }
    }

    private void authLoop() {
        int attempts = 0;
        long timeout = data.keepAlive * 10L;
        while (running) {
            try {
                if (!connected) {
                    if (!tunnel && attempts >= UDP_ATTEMPTS && data.allowTunnel) {
                        VoiceChatMod.LOGGER.warn("No answer from the voice chat server at {} over UDP", serverAddress);
                        switchToTunnel();
                    }
                    send(new VoiceMessage.Authenticate());
                    attempts++;
                    Thread.sleep(AUTH_INTERVAL);
                } else {
                    if (System.currentTimeMillis() - lastReceived > timeout) {
                        VoiceChatMod.LOGGER.warn("Voice chat connection timed out, reconnecting");
                        connected = false;
                        attempts = 0;
                        continue;
                    }
                    Thread.sleep(250L);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[VoiceMessage.MAX_DATAGRAM];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                onData(p.getData(), p.getLength(), false);
            } catch (IOException e) {
                if (running) {
                    VoiceChatMod.LOGGER.debug("Voice chat receive error", e);
                }
            }
        }
    }

    /** A packet that came through the tunnel, called on the network thread. */
    public void onTunnelData(byte[] bytes) {
        onData(bytes, bytes.length, true);
    }

    private void onData(byte[] bytes, int len, boolean viaTunnel) {
        VoiceMessage msg;
        try {
            msg = VoiceMessage.decodeFromServer(data.key, bytes, len);
        } catch (Exception e) {
            return;
        }
        lastReceived = System.currentTimeMillis();
        if (msg instanceof VoiceMessage.AuthenticateAck) {
            if (!connected) {
                connected = true;
                if (viaTunnel) {
                    tunnel = true;
                }
                VoiceChatMod.LOGGER.info("Connected to the voice chat server{}", tunnel ? " (tunnel)" : "");
            }
        } else if (msg instanceof VoiceMessage.KeepAlive) {
            send(new VoiceMessage.KeepAlive());
        } else if (msg instanceof VoiceMessage.Ping) {
            send(msg);
        } else if (msg instanceof VoiceMessage.Sound) {
            AudioOutput out = ClientManager.INSTANCE.getOutput();
            if (out != null) {
                out.addSound((VoiceMessage.Sound) msg);
            }
        }
    }

    public void sendMic(long sequence, boolean whispering, byte[] opus) {
        if (connected) {
            send(new VoiceMessage.Mic(sequence, whispering, opus));
        }
    }

    private void send(VoiceMessage msg) {
        try {
            byte[] bytes = VoiceMessage.encodeFromClient(ownId, data.key, msg);
            if (tunnel) {
                ByteBuf buf = VoiceNetwork.buffer(VoiceNetwork.C_TUNNEL);
                ByteBufUtil.writeBytes(buf, bytes);
                sendCustomPayload(buf);
            } else if (socket != null) {
                socket.send(new DatagramPacket(bytes, bytes.length, serverAddress));
            }
        } catch (Exception e) {
            VoiceChatMod.LOGGER.debug("Failed to send voice packet", e);
        }
    }

    static void sendCustomPayload(ByteBuf buf) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return;
        }
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        mc.getNetHandler().getNetworkManager().scheduleOutboundPacket(new C17PacketCustomPayload(VoiceNetwork.CHANNEL, b));
    }

    /** Works out where the voice server is: the configured voice host, or the address of the Minecraft server. */
    public static SocketAddress resolve(SecretData data) {
        if (data.port <= 0 && data.host.isEmpty()) {
            return null;
        }
        String host = data.host;
        int port = data.port;
        try {
            if (!host.isEmpty()) {
                String h = host;
                if (h.startsWith("[") && h.contains("]")) {
                    int end = h.indexOf(']');
                    String rest = h.substring(end + 1);
                    h = h.substring(1, end);
                    if (rest.startsWith(":")) {
                        port = Integer.parseInt(rest.substring(1));
                    }
                } else if (h.indexOf(':') >= 0 && h.indexOf(':') == h.lastIndexOf(':')) {
                    port = Integer.parseInt(h.substring(h.indexOf(':') + 1));
                    h = h.substring(0, h.indexOf(':'));
                }
                return new InetSocketAddress(InetAddress.getByName(h), port);
            }
            Minecraft mc = Minecraft.getMinecraft();
            SocketAddress addr = mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getSocketAddress();
            if (addr instanceof InetSocketAddress) {
                return new InetSocketAddress(((InetSocketAddress) addr).getAddress(), port);
            }
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        } catch (IOException | RuntimeException e) {
            VoiceChatMod.LOGGER.error("Failed to resolve the voice chat host '{}'", host, e);
            return null;
        }
    }
}
