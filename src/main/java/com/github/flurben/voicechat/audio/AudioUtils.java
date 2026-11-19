package com.github.flurben.voicechat.audio;

public final class AudioUtils {

    public static final int SAMPLE_RATE = 48000;
    /** 20 ms */
    public static final int FRAME_SIZE = 960;
    public static final double LOWEST_DB = -127D;

    private AudioUtils() {}

    public static double dbToMultiplier(double db) {
        return Math.pow(10D, db / 20D);
    }

    public static double multiplierToDb(double m) {
        if (m <= 0D) {
            return LOWEST_DB;
        }
        return Math.max(LOWEST_DB, 20D * Math.log10(m));
    }

    /** RMS level of a frame in dBFS. */
    public static double rmsDb(short[] samples) {
        return multiplierToDb(rms(samples) / 32768D);
    }

    public static double rms(short[] samples) {
        double sum = 0D;
        for (short s : samples) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / samples.length);
    }

    public static int peak(short[] samples) {
        int p = 0;
        for (short s : samples) {
            int a = Math.abs((int) s);
            if (a > p) {
                p = a;
            }
        }
        return p;
    }

    /** Peak level of a frame in dBFS, which the voice activation threshold uses. */
    public static double peakDb(short[] samples) {
        return multiplierToDb(peak(samples) / 32768D);
    }

    public static short clip(float v) {
        if (v > 32767F) {
            return 32767;
        }
        if (v < -32768F) {
            return -32768;
        }
        return (short) v;
    }

    public static void applyGain(short[] samples, float gain) {
        if (gain == 1F) {
            return;
        }
        for (int i = 0; i < samples.length; i++) {
            samples[i] = clip(samples[i] * gain);
        }
    }

    /** Soft clipping that leaves quiet signals untouched and bends loud ones instead of hard clipping. */
    public static short softClip(float v) {
        float x = v / 32768F;
        float a = Math.abs(x);
        if (a > 0.8F) {
            float over = a - 0.8F;
            float y = 0.8F + 0.2F * (float) Math.tanh(over / 0.2F);
            x = Math.signum(x) * y;
        }
        return clip(x * 32767F);
    }

    public static void shortsToBytesLE(short[] in, int count, byte[] out) {
        for (int i = 0; i < count; i++) {
            out[i * 2] = (byte) in[i];
            out[i * 2 + 1] = (byte) (in[i] >> 8);
        }
    }

    public static void bytesToShortsLE(byte[] in, int byteCount, short[] out) {
        for (int i = 0; i < byteCount / 2; i++) {
            out[i] = (short) ((in[i * 2] & 0xFF) | (in[i * 2 + 1] << 8));
        }
    }
}
