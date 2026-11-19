package com.github.flurben.voicechat.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.flurben.voicechat.VoiceChatMod;

/**
 * Per player volume adjustments, stored as {@code uuid=volume} lines. Also remembers the names of players so offline
 * players can be listed in the volume screen.
 */
public class VolumeConfig {

    private final File file;
    private final Map<UUID, Double> volumes = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public VolumeConfig(File file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(line.substring(0, eq).trim());
                    String rest = line.substring(eq + 1).trim();
                    String name = null;
                    int sp = rest.indexOf(' ');
                    if (sp > 0) {
                        name = rest.substring(sp + 1).trim();
                        rest = rest.substring(0, sp);
                    }
                    volumes.put(id, Math.max(0D, Math.min(4D, Double.parseDouble(rest))));
                    if (name != null && !name.isEmpty()) {
                        names.put(id, name);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException e) {
            VoiceChatMod.LOGGER.error("Failed to read {}", file, e);
        }
    }

    public synchronized void save() {
        try {
            file.getParentFile().mkdirs();
            try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                w.write("# Player volumes (1.0 = 100%), followed by the last known player name\n");
                for (Map.Entry<UUID, Double> e : volumes.entrySet()) {
                    String n = names.get(e.getKey());
                    w.write(e.getKey() + "=" + e.getValue() + (n == null ? "" : " " + n) + "\n");
                }
            }
        } catch (IOException e) {
            VoiceChatMod.LOGGER.error("Failed to save {}", file, e);
        }
    }

    public double getVolume(UUID id) {
        Double d = volumes.get(id);
        return d == null ? 1D : d;
    }

    public void setVolume(UUID id, String name, double v) {
        volumes.put(id, Math.max(0D, Math.min(4D, v)));
        if (name != null) {
            names.put(id, name);
        }
    }

    public Map<UUID, Double> getVolumes() {
        return volumes;
    }

    public String getName(UUID id) {
        return names.get(id);
    }
}
