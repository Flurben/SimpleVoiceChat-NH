package com.github.flurben.voicechat.audio;

/**
 * Brings speech to a steady level: quiet microphones are raised, loud ones lowered. The gain only changes while
 * someone is speaking, drops quickly and rises slowly, and a limiter keeps peaks from clipping.
 */
public class AutomaticGainControl {

    private static final double TARGET_DB = -18D;
    private static final double MIN_GAIN_DB = -12D;
    private static final double MAX_GAIN_DB = 30D;
    private static final double SPEECH_DB = -55D;
    /** dB per 20 ms frame */
    private static final double RISE = 0.12D;
    private static final double FALL = 1.5D;

    private double gainDb = 0D;
    private float lastGain = 1F;

    public void reset() {
        gainDb = 0D;
        lastGain = 1F;
    }

    public double getGainDb() {
        return gainDb;
    }

    public void process(short[] frame) {
        double level = AudioUtils.rmsDb(frame);
        if (level > SPEECH_DB) {
            double desired = Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, TARGET_DB - level));
            if (desired < gainDb) {
                gainDb = Math.max(desired, gainDb - FALL);
            } else {
                gainDb = Math.min(desired, gainDb + RISE);
            }
        }
        float gain = (float) AudioUtils.dbToMultiplier(gainDb);
        int peak = AudioUtils.peak(frame);
        if (peak * gain > 31000F) {
            gain = 31000F / Math.max(1, peak);
        }
        float start = lastGain;
        for (int i = 0; i < frame.length; i++) {
            float g = start + (gain - start) * i / frame.length;
            frame[i] = AudioUtils.clip(frame[i] * g);
        }
        lastGain = gain;
    }
}
