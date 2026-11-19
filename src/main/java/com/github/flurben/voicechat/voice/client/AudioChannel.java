package com.github.flurben.voicechat.voice.client;

import java.util.TreeMap;
import java.util.UUID;

import com.github.flurben.voicechat.audio.OpusCodec;
import com.github.flurben.voicechat.voice.common.VoiceMessage;

/**
 * The audio of one other player: a jitter buffer ordered by sequence number and an Opus decoder. The mixer pulls one
 * 20 ms frame per tick. Late packets are waited for as long as the buffer allows, lost packets are recovered from the
 * forward error correction of the next one or concealed.
 */
public class AudioChannel {

    public final UUID sender;
    private final OpusCodec.Decoder decoder = new OpusCodec.Decoder();
    private final TreeMap<Long, VoiceMessage.Sound> buffer = new TreeMap<>();
    private long nextSequence;
    private boolean playing;
    private long firstArrival;
    private int concealed;
    private long lastActivity = System.currentTimeMillis();

    /** The most recent packet, for position, distance and whispering. */
    private volatile VoiceMessage.Sound last;

    // Gains of the previous frame, so changes are ramped instead of clicking
    float lastLeft = -1F, lastRight = -1F;

    public AudioChannel(UUID sender) {
        this.sender = sender;
    }

    public synchronized void add(VoiceMessage.Sound sound) {
        long now = System.currentTimeMillis();
        long idle = now - lastActivity;
        lastActivity = now;
        if (sound.sequence < nextSequence) {
            boolean restarted = sound.sequence <= nextSequence - 1000 || (!playing && buffer.isEmpty() && idle > 500L);
            if (!restarted) {
                // Late packet of something already played
                return;
            }
            // The sender restarted its counter (rejoined), start over
            buffer.clear();
            stop();
            nextSequence = 0;
        }
        if (buffer.isEmpty() && !playing) {
            firstArrival = lastActivity;
        }
        buffer.put(sound.sequence, sound);
        if (sound.data.length > 0) {
            last = sound;
        }
        while (buffer.size() > 64) {
            buffer.pollFirstEntry();
        }
    }

    public VoiceMessage.Sound getLast() {
        return last;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    /**
     * @param bufferSize      packets to collect before playback starts
     * @param packetThreshold how many packets may be skipped over before jumping ahead
     * @return the next frame, or null when the player is silent
     */
    public synchronized short[] nextFrame(int bufferSize, int packetThreshold) {
        if (!playing) {
            if (buffer.isEmpty()) {
                return null;
            }
            boolean ended = buffer.lastEntry().getValue().data.length == 0;
            if (buffer.size() < bufferSize && !ended && System.currentTimeMillis() - firstArrival < bufferSize * 20L) {
                return null;
            }
            playing = true;
            nextSequence = buffer.firstKey();
            concealed = 0;
        }
        VoiceMessage.Sound s = buffer.remove(nextSequence);
        if (s != null) {
            nextSequence++;
            concealed = 0;
            if (s.data.length == 0) {
                stop();
                return null;
            }
            return decoder.decode(s.data);
        }
        if (buffer.isEmpty()) {
            // Nothing arrived in time, conceal a few frames and then end the sentence
            if (++concealed > 3) {
                stop();
                return null;
            }
            nextSequence++;
            return decoder.decode(null);
        }
        long first = buffer.firstKey();
        if (first < nextSequence) {
            buffer.headMap(nextSequence).clear();
            return nextFrame(bufferSize, packetThreshold);
        }
        if (first - nextSequence > Math.max(1, packetThreshold)) {
            // Too far behind, jump to what we have
            nextSequence = first;
            return nextFrame(bufferSize, packetThreshold);
        }
        nextSequence++;
        concealed++;
        if (first == nextSequence && buffer.get(first).data.length > 0) {
            return decoder.decodeFec(buffer.get(first).data);
        }
        return decoder.decode(null);
    }

    private void stop() {
        playing = false;
        decoder.reset();
        lastLeft = -1F;
        lastRight = -1F;
    }
}
