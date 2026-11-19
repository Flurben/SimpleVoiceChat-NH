package com.github.flurben.voicechat.audio;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import com.github.flurben.voicechat.VoiceChatMod;

/** Lists and opens microphones and speakers through Java Sound, which works the same with and without lwjgl3ify. */
public final class AudioDevices {

    public static final AudioFormat MONO = new AudioFormat(AudioUtils.SAMPLE_RATE, 16, 1, true, false);
    public static final AudioFormat STEREO = new AudioFormat(AudioUtils.SAMPLE_RATE, 16, 2, true, false);

    private AudioDevices() {}

    public static List<String> getMicrophones() {
        return list(TargetDataLine.class);
    }

    public static List<String> getSpeakers() {
        return list(SourceDataLine.class);
    }

    private static List<String> list(Class<?> lineClass) {
        List<String> names = new ArrayList<>();
        try {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                try {
                    Mixer m = AudioSystem.getMixer(info);
                    if (m.isLineSupported(new DataLine.Info(lineClass, MONO)) || m.isLineSupported(new DataLine.Info(lineClass, STEREO))) {
                        if (info.getName().startsWith("Port ")) {
                            continue;
                        }
                        if (!names.contains(info.getName())) {
                            names.add(info.getName());
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException e) {
            VoiceChatMod.LOGGER.warn("Failed to list audio devices", e);
        }
        return names;
    }

    private static Mixer findMixer(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    /** An opened microphone line and whether it delivers stereo that has to be mixed down. */
    public static class Capture {
        public final TargetDataLine line;
        public final boolean stereo;

        Capture(TargetDataLine line, boolean stereo) {
            this.line = line;
            this.stereo = stereo;
        }
    }

    public static Capture openMicrophone(String name) throws LineUnavailableException {
        Mixer mixer = findMixer(name);
        if (name != null && !name.isEmpty() && mixer == null) {
            VoiceChatMod.LOGGER.warn("Microphone '{}' not found, using the default device", name);
        }
        LineUnavailableException last = null;
        for (AudioFormat format : new AudioFormat[] {MONO, STEREO}) {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) (mixer != null ? mixer.getLine(info) : AudioSystem.getLine(info));
                line.open(format, AudioUtils.FRAME_SIZE * format.getFrameSize() * 6);
                line.start();
                return new Capture(line, format.getChannels() == 2);
            } catch (LineUnavailableException e) {
                last = e;
            } catch (IllegalArgumentException e) {
                last = new LineUnavailableException(e.getMessage());
            }
        }
        throw last;
    }

    public static SourceDataLine openSpeaker(String name, int bufferFrames) throws LineUnavailableException {
        Mixer mixer = findMixer(name);
        if (name != null && !name.isEmpty() && mixer == null) {
            VoiceChatMod.LOGGER.warn("Speaker '{}' not found, using the default device", name);
        }
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, STEREO);
        SourceDataLine line;
        try {
            line = (SourceDataLine) (mixer != null ? mixer.getLine(info) : AudioSystem.getLine(info));
        } catch (IllegalArgumentException e) {
            throw new LineUnavailableException(e.getMessage());
        }
        line.open(STEREO, AudioUtils.FRAME_SIZE * 4 * bufferFrames);
        line.start();
        return line;
    }
}
