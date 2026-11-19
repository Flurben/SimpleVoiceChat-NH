package com.github.flurben.voicechat.voice.client;

public enum AudioType {
    NORMAL("message.voicechat.audio_type.normal"),
    REDUCED("message.voicechat.audio_type.reduced"),
    OFF("message.voicechat.audio_type.off");

    public final String translationKey;

    AudioType(String key) {
        this.translationKey = key;
    }

    /** How strongly left/right panning is applied. */
    public float panStrength() {
        switch (this) {
            case NORMAL:
                // Never fully on one side, the other ear still hears a little like with a real voice
                return 0.8F;
            case REDUCED:
                return 0.4F;
            default:
                return 0F;
        }
    }
}
