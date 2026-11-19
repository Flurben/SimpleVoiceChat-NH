package com.github.flurben.voicechat.audio;

/**
 * Automatic voice activation: speech is detected when the level rises clearly above the tracked background noise and
 * the frame has the zero crossing rate of voice rather than of clicks or hiss.
 */
public class VoiceActivityDetector {

    private double noiseFloor = -60D;
    private boolean initialized;

    public void reset() {
        initialized = false;
        noiseFloor = -60D;
    }

    public boolean isSpeech(short[] frame) {
        double level = AudioUtils.rmsDb(frame);
        if (!initialized) {
            noiseFloor = Math.min(level, -40D);
            initialized = true;
        }
        if (level < noiseFloor) {
            noiseFloor = noiseFloor * 0.7D + level * 0.3D;
        } else {
            noiseFloor += 0.02D;
        }
        noiseFloor = Math.max(-90D, Math.min(-25D, noiseFloor));
        if (level < -58D) {
            return false;
        }
        int crossings = 0;
        for (int i = 1; i < frame.length; i++) {
            if ((frame[i - 1] >= 0) != (frame[i] >= 0)) {
                crossings++;
            }
        }
        double zcr = crossings / (double) frame.length;
        return level > noiseFloor + 10D && zcr < 0.35D;
    }
}
