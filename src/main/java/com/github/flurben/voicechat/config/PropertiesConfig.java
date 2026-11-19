package com.github.flurben.voicechat.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.github.flurben.voicechat.VoiceChatMod;

/**
 * A small typed config backed by a {@code .properties} file. Entries keep the order they were declared in and are
 * written back with their comments, so the file stays readable when edited by hand.
 */
public class PropertiesConfig {

    private final File file;
    private final String header;
    private final List<Entry<?>> entries = new ArrayList<>();
    private final Map<String, String> loaded = new LinkedHashMap<>();

    public PropertiesConfig(File file, String header) {
        this.file = file;
        this.header = header;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            p.load(r);
            for (String k : p.stringPropertyNames()) {
                loaded.put(k, p.getProperty(k));
            }
        } catch (IOException e) {
            VoiceChatMod.LOGGER.error("Failed to read config {}", file, e);
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(header).append('\n');
            for (Entry<?> e : entries) {
                sb.append('\n');
                for (String line : e.comment.split("\n")) {
                    sb.append("# ").append(line).append('\n');
                }
                sb.append(escape(e.key, true)).append('=').append(escape(e.serialize(), false)).append('\n');
            }
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
                w.write(sb.toString());
            }
        } catch (IOException e) {
            VoiceChatMod.LOGGER.error("Failed to save config {}", file, e);
        }
    }

    private static String escape(String s, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '=':
                case ':':
                case '#':
                case '!':
                    sb.append('\\').append(c);
                    break;
                case ' ':
                    if (key || i == 0) {
                        sb.append("\\ ");
                    } else {
                        sb.append(' ');
                    }
                    break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private <T extends Entry<?>> T add(T entry) {
        entries.add(entry);
        String raw = loaded.get(entry.key);
        if (raw != null) {
            try {
                entry.parse(raw.trim());
            } catch (RuntimeException ex) {
                VoiceChatMod.LOGGER.warn("Invalid value '{}' for {} in {}, using default", raw, entry.key, file.getName());
            }
        }
        return entry;
    }

    public IntEntry intEntry(String key, int def, int min, int max, String comment) {
        return add(new IntEntry(this, key, def, min, max, comment));
    }

    public DoubleEntry doubleEntry(String key, double def, double min, double max, String comment) {
        return add(new DoubleEntry(this, key, def, min, max, comment));
    }

    public BoolEntry boolEntry(String key, boolean def, String comment) {
        return add(new BoolEntry(this, key, def, comment));
    }

    public StringEntry stringEntry(String key, String def, String comment) {
        return add(new StringEntry(this, key, def, comment));
    }

    public <E extends Enum<E>> EnumEntry<E> enumEntry(String key, E def, String comment) {
        return add(new EnumEntry<>(this, key, def, comment));
    }

    public abstract static class Entry<T> {
        protected final PropertiesConfig config;
        public final String key;
        protected final T def;
        protected final String comment;
        protected T value;

        Entry(PropertiesConfig config, String key, T def, String comment) {
            this.config = config;
            this.key = key;
            this.def = def;
            this.value = def;
            this.comment = comment;
        }

        public T get() {
            return value;
        }

        public T getDefault() {
            return def;
        }

        public Entry<T> set(T v) {
            this.value = fix(v);
            return this;
        }

        public void save() {
            config.save();
        }

        protected T fix(T v) {
            return v;
        }

        abstract void parse(String raw);

        String serialize() {
            return String.valueOf(value);
        }
    }

    public static class IntEntry extends Entry<Integer> {
        public final int min, max;

        IntEntry(PropertiesConfig c, String key, int def, int min, int max, String comment) {
            super(c, key, def, comment);
            this.min = min;
            this.max = max;
        }

        @Override
        protected Integer fix(Integer v) {
            return Math.max(min, Math.min(max, v));
        }

        @Override
        void parse(String raw) {
            set(Integer.parseInt(raw));
        }
    }

    public static class DoubleEntry extends Entry<Double> {
        public final double min, max;

        DoubleEntry(PropertiesConfig c, String key, double def, double min, double max, String comment) {
            super(c, key, def, comment);
            this.min = min;
            this.max = max;
        }

        @Override
        protected Double fix(Double v) {
            if (v.isNaN()) {
                return def;
            }
            return Math.max(min, Math.min(max, v));
        }

        @Override
        void parse(String raw) {
            set(Double.parseDouble(raw));
        }
    }

    public static class BoolEntry extends Entry<Boolean> {
        BoolEntry(PropertiesConfig c, String key, boolean def, String comment) {
            super(c, key, def, comment);
        }

        @Override
        void parse(String raw) {
            set(Boolean.parseBoolean(raw));
        }
    }

    public static class StringEntry extends Entry<String> {
        StringEntry(PropertiesConfig c, String key, String def, String comment) {
            super(c, key, def, comment);
        }

        @Override
        protected String fix(String v) {
            return v == null ? "" : v;
        }

        @Override
        void parse(String raw) {
            set(raw);
        }
    }

    public static class EnumEntry<E extends Enum<E>> extends Entry<E> {
        EnumEntry(PropertiesConfig c, String key, E def, String comment) {
            super(c, key, def, comment);
        }

        @Override
        protected E fix(E v) {
            return v == null ? def : v;
        }

        @Override
        void parse(String raw) {
            set(Enum.valueOf(def.getDeclaringClass(), raw.toUpperCase()));
        }

        public E[] values() {
            return def.getDeclaringClass().getEnumConstants();
        }
    }
}
