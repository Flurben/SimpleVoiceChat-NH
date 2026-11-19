package com.github.flurben.voicechat.config;

import java.io.File;

import com.github.flurben.voicechat.voice.common.Codec;

public class ServerConfig {

    public final PropertiesConfig config;

    public final PropertiesConfig.IntEntry port;
    public final PropertiesConfig.StringEntry bindAddress;
    public final PropertiesConfig.DoubleEntry voiceDistance;
    public final PropertiesConfig.DoubleEntry whisperDistance;
    public final PropertiesConfig.EnumEntry<Codec> codec;
    public final PropertiesConfig.IntEntry mtuSize;
    public final PropertiesConfig.IntEntry keepAlive;
    public final PropertiesConfig.BoolEntry groupsEnabled;
    public final PropertiesConfig.StringEntry voiceHost;
    public final PropertiesConfig.BoolEntry allowRecording;
    public final PropertiesConfig.BoolEntry forceVoiceChat;
    public final PropertiesConfig.IntEntry loginTimeout;
    public final PropertiesConfig.DoubleEntry broadcastRange;
    public final PropertiesConfig.BoolEntry allowTunnel;
    public final PropertiesConfig.BoolEntry crossDimension;

    public ServerConfig(File file) {
        config = new PropertiesConfig(file, "Simple Voice Chat NH server config");
        port = config.intEntry("port", 24454, -1, 65535,
            "The port number to use for the voice chat communication (UDP).\n"
                + "Audio packets are always transmitted via this port, unless the tunnel fallback is used.\n"
                + "-1 means a random port is chosen. Singleplayer and LAN worlds always use a random port.");
        bindAddress = config.stringEntry("bind_address", "",
            "The server IP address to bind the voice chat to.\nLeave blank to use the 'server-ip' property from the 'server.properties' config file.\n"
                + "To bind to the wildcard IP address, use '*'.");
        voiceDistance = config.doubleEntry("max_voice_distance", 48D, 1D, 1_000_000D, "The distance to which the voice can be heard");
        whisperDistance = config.doubleEntry("whisper_distance", 24D, 1D, 1_000_000D, "The distance to which the voice can be heard when whispering");
        codec = config.enumEntry("codec", Codec.VOIP,
            "The Opus codec\nValid values are 'VOIP', 'AUDIO', and 'RESTRICTED_LOWDELAY'");
        mtuSize = config.intEntry("mtu_size", 1024, 256, 10_000, "The maximum size that audio packets are allowed to have (in bytes)\nSetting this to lower values might cause issues");
        keepAlive = config.intEntry("keep_alive", 1000, 1000, Integer.MAX_VALUE, "The frequency at which keep alive packets are sent (in milliseconds)\nSetting this to a higher value may result in timeouts");
        groupsEnabled = config.boolEntry("enable_groups", true, "If group chats are allowed");
        voiceHost = config.stringEntry("voice_host", "",
            "The host name that clients should use to connect to the voice chat\nThis may also include a port, e.g. 'example.com:24454'\n"
                + "Leave blank to use the address the player connected to the Minecraft server with");
        allowRecording = config.boolEntry("allow_recording", true, "If players are allowed to record the voice chat audio");
        forceVoiceChat = config.boolEntry("force_voice_chat", false, "If players without the voice chat mod should be kicked from the server");
        loginTimeout = config.intEntry("login_timeout", 10_000, 100, Integer.MAX_VALUE,
            "The amount of milliseconds the server should wait to check if a player has the mod installed\nOnly relevant when 'force_voice_chat' is set to 'true'");
        broadcastRange = config.doubleEntry("broadcast_range", -1D, -1D, Double.MAX_VALUE,
            "The range in which voice chat packets are sent to other players\nA value less than 0 means 'max_voice_distance'");
        allowTunnel = config.boolEntry("allow_tunnel", true,
            "If clients that cannot reach the UDP port may send their audio through the Minecraft connection instead\n"
                + "This keeps voice chat working on hosts where the UDP port cannot be opened, at the cost of slightly higher latency");
        crossDimension = config.boolEntry("cross_dimension_groups", true, "If group members can hear each other when they are in different dimensions");
        config.save();
    }

    public double getBroadcastRange(double distance) {
        double r = broadcastRange.get();
        return r < 0 ? distance + 1D : r;
    }
}
