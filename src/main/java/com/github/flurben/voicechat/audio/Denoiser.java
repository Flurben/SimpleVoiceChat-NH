package com.github.flurben.voicechat.audio;

/**
 * Noise suppression with a short time Fourier transform: a decision directed Wiener filter with a noise estimate that
 * follows the minimum of the smoothed spectrum. Steady noise like fans, hum and hiss is removed while speech passes.
 * Works on 20 ms frames in two 10 ms hops and adds 10 ms of delay.
 */
public class Denoiser {

    private static final int WIN = 960;
    private static final int HOP = 480;
    private static final int FFT = 1024;
    private static final int BINS = FFT / 2 + 1;
    private static final float MIN_GAIN = 0.12F;
    private static final float ALPHA = 0.96F;

    private final float[] window = new float[WIN];
    private final float[] input = new float[WIN];
    private final float[] overlap = new float[HOP];
    private final float[] re = new float[FFT];
    private final float[] im = new float[FFT];
    private final float[] smoothed = new float[BINS];
    private final float[] noise = new float[BINS];
    private final float[] prevGain = new float[BINS];
    private final float[] prevPower = new float[BINS];
    private final float[] cos = new float[FFT / 2];
    private final float[] sin = new float[FFT / 2];
    private final int[] reverse = new int[FFT];
    private int frames;

    public Denoiser() {
        for (int i = 0; i < WIN; i++) {
            window[i] = (float) Math.sqrt(0.5D - 0.5D * Math.cos(2D * Math.PI * i / WIN));
        }
        for (int i = 0; i < FFT / 2; i++) {
            cos[i] = (float) Math.cos(-2D * Math.PI * i / FFT);
            sin[i] = (float) Math.sin(-2D * Math.PI * i / FFT);
        }
        int bits = Integer.numberOfTrailingZeros(FFT);
        for (int i = 0; i < FFT; i++) {
            reverse[i] = Integer.reverse(i) >>> (32 - bits);
        }
        java.util.Arrays.fill(prevGain, 1F);
    }

    public void reset() {
        java.util.Arrays.fill(input, 0F);
        java.util.Arrays.fill(overlap, 0F);
        java.util.Arrays.fill(smoothed, 0F);
        java.util.Arrays.fill(noise, 0F);
        java.util.Arrays.fill(prevGain, 1F);
        java.util.Arrays.fill(prevPower, 0F);
        frames = 0;
    }

    /** Denoises a 960 sample frame in place. */
    public void process(short[] frame) {
        float[] out = new float[WIN];
        for (int h = 0; h < 2; h++) {
            System.arraycopy(input, HOP, input, 0, HOP);
            for (int i = 0; i < HOP; i++) {
                input[HOP + i] = frame[h * HOP + i];
            }
            processHop(out, h * HOP);
        }
        for (int i = 0; i < WIN; i++) {
            frame[i] = AudioUtils.clip(out[i]);
        }
    }

    private void processHop(float[] out, int outOffset) {
        for (int i = 0; i < FFT; i++) {
            re[i] = i < WIN ? input[i] * window[i] : 0F;
            im[i] = 0F;
        }
        fft(re, im, false);
        frames++;
        for (int k = 0; k < BINS; k++) {
            float p = re[k] * re[k] + im[k] * im[k] + 1e-3F;
            if (frames <= 1) {
                smoothed[k] = p;
                noise[k] = p;
            }
            smoothed[k] = 0.7F * smoothed[k] + 0.3F * p;
            if (smoothed[k] < noise[k]) {
                noise[k] = 0.8F * noise[k] + 0.2F * smoothed[k];
            } else {
                // Rises by about 3 dB per second, so speech is not learnt as noise but changing noise is followed
                noise[k] *= 1.0035F;
            }
            float n = noise[k] * 1.6F;
            float post = p / n;
            float prio = ALPHA * prevGain[k] * prevGain[k] * prevPower[k] / n + (1F - ALPHA) * Math.max(post - 1F, 0F);
            float g = prio / (1F + prio);
            if (g < MIN_GAIN) {
                g = MIN_GAIN;
            }
            prevGain[k] = g;
            prevPower[k] = p;
            re[k] *= g;
            im[k] *= g;
            if (k > 0 && k < FFT / 2) {
                re[FFT - k] = re[k];
                im[FFT - k] = -im[k];
            }
        }
        fft(re, im, true);
        for (int i = 0; i < HOP; i++) {
            out[outOffset + i] = overlap[i] + re[i] * window[i];
            overlap[i] = re[HOP + i] * window[HOP + i];
        }
    }

    private void fft(float[] r, float[] m, boolean inverse) {
        for (int i = 0; i < FFT; i++) {
            int j = reverse[i];
            if (j > i) {
                float t = r[i];
                r[i] = r[j];
                r[j] = t;
                t = m[i];
                m[i] = m[j];
                m[j] = t;
            }
        }
        for (int size = 2; size <= FFT; size <<= 1) {
            int half = size >> 1;
            int step = FFT / size;
            for (int start = 0; start < FFT; start += size) {
                for (int j = 0; j < half; j++) {
                    float c = cos[j * step];
                    float s = inverse ? -sin[j * step] : sin[j * step];
                    int a = start + j;
                    int b = a + half;
                    float tr = r[b] * c - m[b] * s;
                    float ti = r[b] * s + m[b] * c;
                    r[b] = r[a] - tr;
                    m[b] = m[a] - ti;
                    r[a] += tr;
                    m[a] += ti;
                }
            }
        }
        if (inverse) {
            for (int i = 0; i < FFT; i++) {
                r[i] /= FFT;
                m[i] /= FFT;
            }
        }
    }
}
