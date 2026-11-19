package com.github.flurben.voicechat.voice.client;

public enum MicrophoneActivationType {
    PTT("message.voicechat.activation_type.ptt"),
    VOICE("message.voicechat.activation_type.voice");

    public final String translationKey;

    MicrophoneActivationType(String key) {
        this.translationKey = key;
    }
}
