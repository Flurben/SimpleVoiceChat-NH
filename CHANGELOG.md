# Changelog

## 1.0.0

First release for Minecraft 1.7.10 and GT New Horizons.

- Proximity voice chat with 3D audio (normal, reduced or off), whispering and per player volumes
- Push to talk or voice activation, with automatic voice detection or a manual threshold
- Noise suppression, automatic gain control and manual microphone gain
- Opus audio (pure Java, no natives), AES-GCM encrypted UDP
- Tunnel through the Minecraft connection when the UDP port cannot be reached
- Groups: normal, open and isolated, with optional passwords and invites
- Voice chat menu, settings, device selection, volume adjustment, group screens and a first time setup
- HUD icon, group HUD with player heads, icons next to name tags
- Recording into one WAV file per player
- `/voicechat` commands: help, test, invite, join, leave
- Client and server configs in `config/voicechat/`, plus a config screen in the mod list
