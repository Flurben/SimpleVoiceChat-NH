# What has been tested

Tested on Windows 11 with GT New Horizons 2.8.4 (230 mods, lwjgl3ify, Angelica, Java 25, LWJGL 3.3.3) through Prism Launcher.

| Part | How | Result |
| --- | --- | --- |
| Audio processing, Opus for all three codec modes, AES-GCM, voice packets, jitter buffer (reordering, loss with FEC, end of sentence, rejoin), 3D panning and distance, config files | Unit tests, `./gradlew build` | 10 of 10 pass |
| Whole mod in singleplayer with a simulated second player over real UDP | In-game test, `src/ingametest` | 23 of 23 pass |
| Two real game clients, one hosting a LAN world, two accounts | In-game LAN test | LAN_RESULT |

The single player in-game test covers: the config screen in the mod list, listing audio devices, the voice server starting with the world, the UDP connection, the first time setup with its microphone test, every screen (menu, settings in both activation modes, device selection, volumes, groups, create group, password, group members), hearing another player with direction and distance, sending with voice activation, silence not being sent, mute, disable (nothing in or out, and the server shows the player as disabled), push to talk, whisper and its shorter range, the voice distance, other dimensions, normal, open and isolated groups, group members hearing each other 1000 blocks apart and across dimensions, group passwords, all commands including group names with spaces, the M, N, H and V keys, HUD, group HUD and name tag icons, recording two aligned WAV tracks, the tunnel through the Minecraft connection in both directions and back to UDP, and leaving and rejoining a world.

The microphone is replaced by a generated voice and the speaker output is silenced in these tests. The real microphone and speaker devices are listed and the speaker is opened, but no real audio is captured or played.

**Not tested:**

- A dedicated server (starting one needs the EULA accepted). The server code is the same that runs inside singleplayer and LAN worlds.
- Speaking into a real microphone and listening with real ears.
- Linux and macOS, and Java 8.
- Other modpacks than GT New Horizons, and plain Forge without lwjgl3ify.
