package com.github.flurben.voicechat.config;

import java.io.File;

import com.github.flurben.voicechat.voice.client.AudioType;
import com.github.flurben.voicechat.voice.client.GroupPlayerIconOrientation;
import com.github.flurben.voicechat.voice.client.MicrophoneActivationType;

public class ClientConfig {

    public static final double MIN_GAIN = -40D;
    public static final double MAX_GAIN = 24D;
    public static final double LOWEST_DB = -127D;

    public final PropertiesConfig config;

    public final PropertiesConfig.BoolEntry onboardingFinished;
    public final PropertiesConfig.DoubleEntry voiceChatVolume;
    public final PropertiesConfig.DoubleEntry voiceActivationThreshold;
    public final PropertiesConfig.BoolEntry voiceActivityDetection;
    public final PropertiesConfig.DoubleEntry microphoneGain;
    public final PropertiesConfig.BoolEntry automaticGainControl;
    public final PropertiesConfig.EnumEntry<MicrophoneActivationType> microphoneActivationType;
    public final PropertiesConfig.IntEntry outputBufferSize;
    public final PropertiesConfig.IntEntry audioPacketThreshold;
    public final PropertiesConfig.IntEntry voiceDeactivationDelay;
    public final PropertiesConfig.IntEntry pttDeactivationDelay;
    public final PropertiesConfig.StringEntry microphone;
    public final PropertiesConfig.StringEntry speaker;
    public final PropertiesConfig.BoolEntry muted;
    public final PropertiesConfig.BoolEntry disabled;
    public final PropertiesConfig.BoolEntry hideIcons;
    public final PropertiesConfig.BoolEntry showNametagIcons;
    public final PropertiesConfig.BoolEntry showHudIcons;
    public final PropertiesConfig.BoolEntry showGroupHud;
    public final PropertiesConfig.BoolEntry showOwnGroupIcon;
    public final PropertiesConfig.DoubleEntry groupHudIconScale;
    public final PropertiesConfig.EnumEntry<GroupPlayerIconOrientation> groupPlayerIconOrientation;
    public final PropertiesConfig.IntEntry groupPlayerIconPosX;
    public final PropertiesConfig.IntEntry groupPlayerIconPosY;
    public final PropertiesConfig.IntEntry hudIconPosX;
    public final PropertiesConfig.IntEntry hudIconPosY;
    public final PropertiesConfig.DoubleEntry hudIconScale;
    public final PropertiesConfig.StringEntry recordingDestination;
    public final PropertiesConfig.BoolEntry denoiser;
    public final PropertiesConfig.BoolEntry runLocalServer;
    public final PropertiesConfig.BoolEntry offlinePlayerVolumeAdjustment;
    public final PropertiesConfig.EnumEntry<AudioType> audioType;
    public final PropertiesConfig.BoolEntry muteOnJoin;
    public final PropertiesConfig.BoolEntry forceTunnel;

    public ClientConfig(File file) {
        config = new PropertiesConfig(file, "Simple Voice Chat NH client config");
        onboardingFinished = config.boolEntry("onboarding_finished", false, "If the voice chat onboarding process has been finished");
        voiceChatVolume = config.doubleEntry("voice_chat_volume", 1D, 0D, 2D, "The voice chat volume");
        voiceActivationThreshold = config.doubleEntry("voice_activation_threshold", -50D, LOWEST_DB, 0D, "The threshold for the voice activation method (in dB)");
        voiceActivityDetection = config.boolEntry("voice_activity_detection", true, "If automatic voice detection should be used");
        microphoneGain = config.doubleEntry("microphone_gain", 0D, MIN_GAIN, MAX_GAIN, "The voice chat microphone gain (in dB)");
        automaticGainControl = config.boolEntry("automatic_gain_control", true, "Enable automatic gain control");
        microphoneActivationType = config.enumEntry("microphone_activation_type", MicrophoneActivationType.PTT, "Valid values are 'PTT' and 'VOICE'");
        outputBufferSize = config.intEntry("output_buffer_size", 5, 1, 16,
            "The size of the audio output buffer (in packets)\nHigher values mean a higher latency but less crackling\nIncrease this value if you have an unstable internet connection");
        audioPacketThreshold = config.intEntry("audio_packet_threshold", 3, 0, 16,
            "The maximum number of audio packets that should be held back if a packet arrives out of order or is dropped\n"
                + "This prevents audio packets that are only slightly out of order from being discarded\nSet this to 0 to disable");
        voiceDeactivationDelay = config.intEntry("voice_deactivation_delay", 25, 0, 100,
            "The time it takes for the microphone to deactivate when using voice activation\nA value of 1 means 20 milliseconds, 2=40 ms, 3=60 ms, and so on");
        pttDeactivationDelay = config.intEntry("ptt_deactivation_delay", 5, 0, 100,
            "The time it takes for the microphone to deactivate when using push to talk\nA value of 1 means 20 milliseconds, 2=40 ms, 3=60 ms, and so on");
        microphone = config.stringEntry("microphone", "", "The name of the microphone\nLeave blank to use the default device");
        speaker = config.stringEntry("speaker", "", "The name of the speaker\nLeave blank to use the default device");
        muted = config.boolEntry("muted", true, "If the microphone is muted (only relevant for the voice activation method)");
        disabled = config.boolEntry("disabled", false, "If the voice chat is disabled (both sound and microphone off)");
        hideIcons = config.boolEntry("hide_icons", false, "If the voice chat HUD, group chat HUD, and other in-game icons should be hidden");
        showNametagIcons = config.boolEntry("show_nametag_icons", true, "If the voice chat icons next to player names should be visible");
        showHudIcons = config.boolEntry("show_hud_icons", true, "If the voice chat icons on the HUD should be visible");
        showGroupHud = config.boolEntry("show_group_hud", true, "If the group chat HUD should be visible");
        showOwnGroupIcon = config.boolEntry("show_own_group_icon", true, "If your own player icon should be displayed in the group chat HUD when you are in a group");
        groupHudIconScale = config.doubleEntry("group_hud_icon_scale", 2D, 0.01D, 10D, "The scale of the player icons in the group chat HUD");
        groupPlayerIconOrientation = config.enumEntry("group_player_icon_orientation", GroupPlayerIconOrientation.VERTICAL, "Valid values are 'VERTICAL' and 'HORIZONTAL'");
        groupPlayerIconPosX = config.intEntry("group_player_icon_pos_x", 4, Integer.MIN_VALUE, Integer.MAX_VALUE, "The X position of the player icons in the group chat HUD\nNegative values mean anchoring to the right instead");
        groupPlayerIconPosY = config.intEntry("group_player_icon_pos_y", 4, Integer.MIN_VALUE, Integer.MAX_VALUE, "The Y position of the player icons in the group chat HUD\nNegative values mean anchoring to the bottom instead");
        hudIconPosX = config.intEntry("hud_icon_pos_x", 16, Integer.MIN_VALUE, Integer.MAX_VALUE, "The X position of the icons in the voice chat HUD\nNegative values mean anchoring to the right instead");
        hudIconPosY = config.intEntry("hud_icon_pos_y", -16, Integer.MIN_VALUE, Integer.MAX_VALUE, "The Y position of the icons in the voice chat HUD\nNegative values mean anchoring to the bottom instead");
        hudIconScale = config.doubleEntry("hud_icon_scale", 1D, 0.01D, 10D, "The scale of the icons in the voice chat HUD, such as microphone or connection status");
        recordingDestination = config.stringEntry("recording_destination", "", "The location where recordings should be saved\nLeave blank to use the default location");
        denoiser = config.boolEntry("denoiser", true, "If noise suppression should be enabled");
        runLocalServer = config.boolEntry("run_local_server", true, "If the voice chat should work in singleplayer or in worlds shared over LAN");
        offlinePlayerVolumeAdjustment = config.boolEntry("offline_player_volume_adjustment", false, "If the volume adjustment interface should also display offline players");
        audioType = config.enumEntry("audio_type", AudioType.NORMAL, "Valid values are 'NORMAL', 'REDUCED', and 'OFF'");
        muteOnJoin = config.boolEntry("mute_on_join", false, "If enabled, you will be automatically muted when joining a world");
        forceTunnel = config.boolEntry("force_tunnel", false,
            "If enabled, audio is always sent through the Minecraft connection instead of UDP (if the server allows it)\nOnly useful when your network blocks UDP");
        config.save();
    }
}
