# Simple Voice Chat NH

Proximity voice chat for **Minecraft 1.7.10** and **[GT New Horizons](https://www.gtnewhorizons.com/)**, modeled on [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) by Max Henkel: the same menus, keys, icons, groups and settings, rebuilt for Forge 1.7.10.

> This is an unofficial, independent reimplementation. It is not affiliated with or endorsed by the authors of Simple Voice Chat. Upstream code and art are "All Rights Reserved", so none of it is included: the code and the icons in this repository were written and drawn from scratch. Only the behavior and the layout of the screens follow the original. It does not talk to Simple Voice Chat on newer Minecraft versions, and addons for the original do not work with it.

## Features

- **Proximity voice chat** with 3D audio: voices come from where the player stands and fade out with distance (48 blocks by default)
- **Push to talk** (Caps Lock by default) or **voice activation**, with automatic voice detection or a manual threshold
- **Whisper** key, heard only half as far
- **Groups**: normal, open and isolated, with optional passwords and invites. Group members hear each other at any distance, also across dimensions
- **Noise suppression**, **automatic gain control** and manual microphone amplification
- **Microphone test** with a live level bar, and a **speaker test** sound
- **Per player volumes** (0 to 400 %), with search
- **Recording** of the voice chat, one WAV file per player on a shared timeline
- **Opus** audio, **AES-GCM encryption** for every packet
- **Tunnel fallback**: when the UDP port cannot be reached, the audio goes through the Minecraft connection instead, so voice chat still works on hosts where you cannot open ports
- Works in **singleplayer, LAN and on dedicated servers**
- **Icons**: your own status on the HUD, icons next to name tags, a group HUD with player heads
- **First time setup** that walks through microphone, speaker and how to talk
- Pure Java: no native libraries, so it runs on Windows, Linux and macOS, on Java 8 and on Java 17 to 25 with lwjgl3ify

## Installation

Install the jar on **every client and on the server**. Players without the mod can still join, they just do not get voice chat (unless the server sets `force_voice_chat`).

### Server setup

The voice chat uses its own **UDP port 24454**. On a dedicated server, open or forward this port (UDP, not TCP). The port and other settings are in `config/voicechat/voicechat-server.properties`.

If the port cannot be opened, clients fall back to sending audio through the Minecraft connection by themselves after about five seconds (`allow_tunnel=true`, the default). This costs a little latency and server bandwidth, but needs no setup.

Singleplayer and LAN worlds pick a free port automatically.

## Usage

Press **V** in game. The first time, this opens a short setup.

| Key | Action |
| --- | --- |
| `V` | Voice chat menu |
| `Caps Lock` | Push to talk |
| `M` | Mute microphone (voice activation) |
| `N` | Disable voice chat (no sound in or out) |
| `H` | Hide voice chat icons |
| unbound | Whisper, group menu, settings, volumes, toggle recording |

All keys can be changed in the controls menu under *Voice Chat*.

### Icons

| Where | Icon | Meaning |
| --- | --- | --- |
| HUD | microphone | You are talking (gray: whispering) |
| HUD | crossed microphone | Microphone muted |
| HUD | crossed speaker | Voice chat disabled |
| HUD | crossed plug | Voice chat not connected, or the server does not have the mod |
| Name tag | speaker | Player is talking (gray: whispering) |
| Name tag | heads | Player is in your group |
| Name tag | crossed speaker / plug | Player disabled the voice chat / is not connected |

### Groups

Open the group menu with the *Group* button in the voice chat menu. Create a group or click one to join it.

| Type | Others hear you | You hear others |
| --- | --- | --- |
| Normal | no | yes |
| Open | yes | yes |
| Isolated | no | no |

### Commands

| Command | |
| --- | --- |
| `/voicechat help` | List the commands |
| `/voicechat invite <player>` | Invite a player to your group, they can join without the password |
| `/voicechat join <group> [password]` | Join a group by name |
| `/voicechat leave` | Leave your group |
| `/voicechat test <player>` | Show the voice connection of a player (operators) |

`/vc` works as a short form.

### Recording

The record button in the voice chat menu (or its key) records everyone you hear. Each player gets a WAV file (48 kHz stereo, as heard with direction and distance) in `.minecraft/voicechat_recordings/<date>/`. All files start at the same moment, so they line up when put on separate tracks in an editor. Servers can turn recording off with `allow_recording=false`.

## Configuration

- `config/voicechat/voicechat-client.properties`: everything from the settings screen, HUD positions and scales, buffer sizes, delays. Most options are also in the mod list under *Config*.
- `config/voicechat/voicechat-volumes.properties`: per player volumes.
- `config/voicechat/voicechat-server.properties`: port, distances, codec, groups, recording, tunnel, `force_voice_chat`.

| Server option | Default | |
| --- | --- | --- |
| `port` | 24454 | UDP port, -1 for a random one |
| `bind_address` | | Address to bind to, `*` for all |
| `max_voice_distance` | 48 | |
| `whisper_distance` | 24 | |
| `codec` | VOIP | `VOIP`, `AUDIO` or `RESTRICTED_LOWDELAY` |
| `mtu_size` | 1024 | |
| `keep_alive` | 1000 | ms |
| `enable_groups` | true | |
| `voice_host` | | Host (and port) clients should connect to, if not the server address |
| `allow_recording` | true | |
| `force_voice_chat` | false | Kick players without the mod |
| `login_timeout` | 10000 | ms, for `force_voice_chat` |
| `broadcast_range` | -1 | -1 means the voice distance |
| `allow_tunnel` | true | Audio through the Minecraft connection when UDP fails |
| `cross_dimension_groups` | true | Group members hear each other across dimensions |

## Differences from Simple Voice Chat

| | Simple Voice Chat | This mod |
| --- | --- | --- |
| Audio devices | OpenAL | Java Sound (works the same with and without lwjgl3ify) |
| Opus | Native, Java fallback | Java ([Concentus](https://github.com/lostromb/concentus)) |
| Noise suppression | RNNoise (native) | Spectral noise suppression written in Java |
| Gain control | Speex (native) | Written in Java |
| Recordings | MP3 | WAV |
| Unreachable UDP port | No voice chat | Tunnel through the Minecraft connection |
| Addon API, volume categories | Yes | No |
| Spectator features, freecam modes, permissions | Yes | No (not in 1.7.10) |
| Network protocol | Own | Own, not compatible |

## Building

Requires JDK 25 (Gradle downloads the toolchains it needs).

```bash
./gradlew build
```

The jar is in `build/libs`. `./gradlew build` also runs the unit tests (audio processing, Opus, encryption, jitter buffer, 3D panning, config files).

### In-game test

`src/ingametest` contains a test that plays through the mod in a real game. It is only compiled with `-PingameTest` and only runs with `-Dvoicechat.ingametest=true` (add `-Dvoicechat.ingametest.slow=true` in a large modpack). It creates a world, replaces the microphone with a generated voice and silences the speaker (it never records or plays real audio), and simulates a second player on the network. It writes `voicechat-ingametest.txt` and screenshots to the game folder, then closes the game.

With `-Dvoicechat.ingametest.lan=host` in one game and `=guest` in another (plus `-Dvoicechat.ingametest.shared=<folder>` for both), the host opens a LAN world and the guest joins, and they talk to each other.

## Tested

See [TESTING.md](TESTING.md).

## Credits and license

- Inspired by and modeled on [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) by Max Henkel. Go support the original on [Modrinth](https://modrinth.com/mod/simple-voice-chat) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat).
- Opus codec: [Concentus](https://github.com/lostromb/concentus) (BSD license, included in the jar with its license).
- Built on the [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) build script.

This mod is licensed under the [MIT License](LICENSE).
