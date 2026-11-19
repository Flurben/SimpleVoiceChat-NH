package com.github.flurben.voicechat.voice.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.audio.AudioUtils;

/**
 * Records the voice chat into one WAV file per player, all aligned to the same timeline so they can be put on separate
 * tracks in an editor. Each file has the voice as heard, with direction and distance, before the volume settings.
 */
public class AudioRecorder {

    private static final int BYTES_PER_FRAME = AudioUtils.FRAME_SIZE * 4;

    private final File folder;
    private final long started = System.currentTimeMillis();
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Function<UUID, String> names;
    private long tick;
    private long totalBytes;

    private static class Track {
        final RandomAccessFile file;
        long frames;

        Track(RandomAccessFile file) {
            this.file = file;
        }
    }

    public AudioRecorder(File baseFolder, Function<UUID, String> names) {
        this.names = names;
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        this.folder = new File(baseFolder, stamp);
        folder.mkdirs();
    }

    public File getFolder() {
        return folder;
    }

    public synchronized void tick() {
        tick++;
    }

    public synchronized void addFrame(UUID player, short[] mono, float left, float right) {
        try {
            Track t = tracks.get(player);
            if (t == null) {
                t = open(player);
                tracks.put(player, t);
            }
            if (t.frames < tick) {
                byte[] silence = new byte[BYTES_PER_FRAME];
                while (t.frames < tick) {
                    t.file.write(silence);
                    t.frames++;
                    totalBytes += silence.length;
                }
            }
            byte[] b = new byte[BYTES_PER_FRAME];
            for (int i = 0; i < mono.length; i++) {
                short l = AudioUtils.clip(mono[i] * left);
                short r = AudioUtils.clip(mono[i] * right);
                b[i * 4] = (byte) l;
                b[i * 4 + 1] = (byte) (l >> 8);
                b[i * 4 + 2] = (byte) r;
                b[i * 4 + 3] = (byte) (r >> 8);
            }
            t.file.write(b);
            t.frames++;
            totalBytes += b.length;
        } catch (IOException e) {
            VoiceChatMod.LOGGER.error("Failed to write recording", e);
        }
    }

    private Track open(UUID player) throws IOException {
        String name = names.apply(player);
        if (name == null) {
            name = player.toString();
        }
        name = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
        File f = new File(folder, name + ".wav");
        int n = 2;
        while (f.exists()) {
            f = new File(folder, name + "_" + n++ + ".wav");
        }
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        raf.setLength(0);
        raf.write(new byte[44]);
        return new Track(raf);
    }

    public synchronized String getDuration() {
        long s = (System.currentTimeMillis() - started) / 1000L;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    public synchronized String getStorage() {
        double mb = totalBytes / (1024D * 1024D);
        if (mb >= 1024D) {
            return String.format("%.2f GB", mb / 1024D);
        }
        return String.format("%.1f MB", mb);
    }

    /** Writes the WAV headers and closes all files. */
    public synchronized void close() {
        for (Track t : tracks.values()) {
            try {
                long dataLen = t.frames * BYTES_PER_FRAME;
                t.file.seek(0);
                t.file.write(header(dataLen));
                t.file.close();
            } catch (IOException e) {
                VoiceChatMod.LOGGER.error("Failed to finish recording", e);
            }
        }
        tracks.clear();
        String[] files = folder.list();
        if (files != null && files.length == 0) {
            folder.delete();
        }
    }

    private static byte[] header(long dataLen) {
        int channels = 2, bits = 16, rate = AudioUtils.SAMPLE_RATE;
        int byteRate = rate * channels * bits / 8;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        putInt(h, 4, (int) Math.min(0xFFFFFFFFL, 36 + dataLen));
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        putInt(h, 16, 16);
        putShort(h, 20, 1);
        putShort(h, 22, channels);
        putInt(h, 24, rate);
        putInt(h, 28, byteRate);
        putShort(h, 32, channels * bits / 8);
        putShort(h, 34, bits);
        put(h, 36, "data");
        putInt(h, 40, (int) Math.min(0xFFFFFFFFL, dataLen));
        return h;
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < 4; i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }
}
