package com.github.flurben.voicechat.voice.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.audio.AudioDevices;
import com.github.flurben.voicechat.audio.AudioUtils;
import com.github.flurben.voicechat.voice.common.VoiceMessage;

/**
 * Mixes every audible channel into one stereo stream with distance attenuation and panning, and plays it on the chosen
 * speaker. Writing to the speaker paces the loop at 20 ms per frame; without a speaker a timer does it instead, so
 * the talk icons and recordings still work.
 */
public class AudioOutput implements Runnable {

    private final Map<UUID, AudioChannel> channels = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<short[]> loopback = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<short[]> testSound = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;
    private volatile boolean speakerAvailable;
    private volatile String error;
    private SourceDataLine line;
    private Thread thread;
    private volatile AudioRecorder recorder;

    public void start() {
        thread = new Thread(this, "VoiceChatOutput");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isSpeakerAvailable() {
        return speakerAvailable;
    }

    public String getError() {
        return error;
    }

    public void setRecorder(AudioRecorder recorder) {
        this.recorder = recorder;
    }

    public void addSound(VoiceMessage.Sound sound) {
        channels.computeIfAbsent(sound.sender, AudioChannel::new).add(sound);
    }

    /** Plays the own microphone back while testing it. */
    public void addLoopback(short[] frame) {
        if (loopback.size() < 10) {
            loopback.add(frame.clone());
        }
    }

    public void playTestSound() {
        testSound.clear();
        int total = AudioUtils.SAMPLE_RATE * 6 / 5;
        double[] notes = {523.25D, 659.25D, 783.99D, 1046.5D};
        int noteLen = total / notes.length;
        short[] all = new short[total];
        for (int i = 0; i < total; i++) {
            int n = Math.min(notes.length - 1, i / noteLen);
            int inNote = i - n * noteLen;
            double env = Math.min(1D, inNote / 400D) * Math.exp(-3.5D * inNote / noteLen);
            double t = i / (double) AudioUtils.SAMPLE_RATE;
            all[i] = (short) (Math.sin(2D * Math.PI * notes[n] * t) * env * 9000D);
        }
        for (int off = 0; off + AudioUtils.FRAME_SIZE <= total; off += AudioUtils.FRAME_SIZE) {
            short[] f = new short[AudioUtils.FRAME_SIZE];
            System.arraycopy(all, off, f, 0, AudioUtils.FRAME_SIZE);
            testSound.add(f);
        }
    }

    @Override
    public void run() {
        ClientManager mgr = ClientManager.INSTANCE;
        try {
            line = AudioDevices.openSpeaker(mgr.config().speaker.get(), mgr.config().outputBufferSize.get() + 3);
            speakerAvailable = true;
            error = null;
        } catch (LineUnavailableException | RuntimeException e) {
            VoiceChatMod.LOGGER.error("Failed to open the speaker", e);
            error = e.getMessage();
            speakerAvailable = false;
        }
        float[] mix = new float[AudioUtils.FRAME_SIZE * 2];
        short[] out = new short[AudioUtils.FRAME_SIZE * 2];
        byte[] bytes = new byte[AudioUtils.FRAME_SIZE * 4];
        long nextTick = System.nanoTime();
        while (running) {
            try {
                java.util.Arrays.fill(mix, 0F);
                boolean disabled = mgr.isDisabled();
                double master = mgr.config().voiceChatVolume.get();
                int bufferSize = mgr.config().outputBufferSize.get();
                int threshold = mgr.config().audioPacketThreshold.get();
                AudioType audioType = mgr.config().audioType.get();
                AudioRecorder rec = recorder;
                long now = System.currentTimeMillis();
                for (AudioChannel ch : channels.values()) {
                    short[] frame = ch.nextFrame(bufferSize, threshold);
                    if (frame == null) {
                        if (now - ch.getLastActivity() > 30_000L) {
                            channels.remove(ch.sender);
                        }
                        continue;
                    }
                    VoiceMessage.Sound s = ch.getLast();
                    if (s == null) {
                        continue;
                    }
                    TalkCache.update(ch.sender, s.whispering);
                    if (disabled) {
                        continue;
                    }
                    float[] lr = computeGains(s, audioType);
                    TestHooks.played(ch.sender, lr[0], lr[1]);
                    float volume = (float) (master * mgr.getVolumes().getVolume(ch.sender));
                    float l = lr[0] * volume, r = lr[1] * volume;
                    mixIn(mix, frame, ch.lastLeft < 0F ? l : ch.lastLeft, ch.lastRight < 0F ? r : ch.lastRight, l, r);
                    ch.lastLeft = l;
                    ch.lastRight = r;
                    if (rec != null) {
                        rec.addFrame(ch.sender, frame, lr[0], lr[1]);
                    }
                }
                short[] lb = loopback.poll();
                if (lb != null) {
                    mixIn(mix, lb, (float) master, (float) master, (float) master, (float) master);
                }
                short[] ts = testSound.poll();
                if (ts != null) {
                    mixIn(mix, ts, (float) master, (float) master, (float) master, (float) master);
                }
                if (rec != null) {
                    rec.tick();
                }
                boolean mute = TestHooks.muteOutput;
                for (int i = 0; i < out.length; i++) {
                    out[i] = mute ? 0 : AudioUtils.softClip(mix[i]);
                }
                if (line != null) {
                    AudioUtils.shortsToBytesLE(out, out.length, bytes);
                    line.write(bytes, 0, bytes.length);
                } else {
                    nextTick += 20_000_000L;
                    long sleep = nextTick - System.nanoTime();
                    if (sleep > 0) {
                        Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                    } else if (sleep < -200_000_000L) {
                        nextTick = System.nanoTime();
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (RuntimeException e) {
                VoiceChatMod.LOGGER.error("Voice chat output error", e);
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    private static void mixIn(float[] mix, short[] frame, float l0, float r0, float l1, float r1) {
        int n = frame.length;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float l = l0 + (l1 - l0) * t;
            float r = r0 + (r1 - r0) * t;
            mix[i * 2] += frame[i] * l;
            mix[i * 2 + 1] += frame[i] * r;
        }
    }

    /** Left and right gain of a sound for the listener, from distance (linear like OpenAL) and direction. */
    static float[] computeGains(VoiceMessage.Sound s, AudioType type) {
        if (s.isGroup()) {
            return new float[] {1F, 1F};
        }
        ClientPositions.Listener listener = ClientPositions.getListener();
        if (listener == null) {
            return new float[] {1F, 1F};
        }
        double sx = s.x, sy = s.y, sz = s.z;
        if (s.kind == VoiceMessage.PLAYER_SOUND) {
            ClientPositions.Pos p = ClientPositions.getPlayer(s.sender);
            if (p != null) {
                sx = p.x;
                sy = p.y;
                sz = p.z;
            }
        }
        double dx = sx - listener.x, dy = sy - listener.y, dz = sz - listener.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dist = Math.sqrt(horizontal * horizontal + dy * dy);
        float maxDistance = s.distance <= 0F ? 48F : s.distance;
        float volume = (float) Math.max(0D, 1D - dist / maxDistance);
        if (volume <= 0F) {
            return new float[] {0F, 0F};
        }
        float pan = 0F;
        if (dist > 0.01D && type != AudioType.OFF) {
            double yaw = Math.toRadians(listener.yaw);
            double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
            pan = (float) ((dx * rightX + dz * rightZ) / dist);
            // Close sounds are less directional, like a real voice next to you
            pan *= (float) Math.min(1D, dist / 2D);
            pan *= type.panStrength();
        }
        double angle = (pan + 1D) * Math.PI / 4D;
        float left = (float) Math.min(1D, Math.cos(angle) * Math.sqrt(2D));
        float right = (float) Math.min(1D, Math.sin(angle) * Math.sqrt(2D));
        return new float[] {left * volume, right * volume};
    }
}
