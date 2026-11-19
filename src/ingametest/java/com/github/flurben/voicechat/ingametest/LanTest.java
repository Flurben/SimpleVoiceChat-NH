package com.github.flurben.voicechat.ingametest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.TestHooks;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;

/**
 * Two real game clients: the host opens its world to LAN, the guest joins it. They talk to each other with generated
 * voices and check what arrives. Steps are coordinated through files in a shared folder.
 */
class LanTest {

    private final InGameTestMod t;
    private final boolean host;
    private final File shared;
    private static final Minecraft mc = Minecraft.getMinecraft();

    LanTest(InGameTestMod t, String role, File shared) {
        this.t = t;
        this.host = role.equals("host");
        this.shared = shared;
    }

    private String role() {
        return host ? "host" : "guest";
    }

    private void signal(String step, String value) throws Exception {
        Files.write(new File(shared, role() + "-" + step).toPath(), value.getBytes(StandardCharsets.UTF_8));
        t.info("signal " + step + " = " + value);
    }

    private String await(String step, long millis) throws Exception {
        File f = new File(shared, (host ? "guest" : "host") + "-" + step);
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (f.isFile()) {
                String v = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                t.info("got " + step + " = " + v);
                return v;
            }
            InGameTestMod.sleep(100);
            t.heartbeat();
        }
        throw new RuntimeException("the other game did not reach step " + step);
    }

    private ClientManager m() {
        return ClientManager.INSTANCE;
    }

    private UUID other() {
        return t.client(() -> {
            UUID own = m().getOwnId();
            for (PlayerState s : m().getStates()) {
                if (!s.uuid.equals(own)) {
                    return s.uuid;
                }
            }
            return null;
        });
    }

    void run() throws Exception {
        boolean noVoice = Boolean.getBoolean("voicechat.ingametest.novoice");
        if (noVoice) {
            t.info("voice chat server disabled for this run, only testing the join");
            t.clientDo(() -> ClientManager.INSTANCE.config().runLocalServer.set(false));
        }
        if (host) {
            t.createWorldPublic();
            // Offline mode: no session server involved, and the guest gets an offline uuid the server knows it by
            t.serverDo(() -> MinecraftServer.getServer().setOnlineMode(false));
            String port = t.client(() -> mc.getIntegratedServer().shareToLAN(WorldSettings.GameType.CREATIVE, true));
            t.info("opened to LAN on port " + port);
            t.check(port != null, "could not open to LAN");
            signal("port", port);
            t.waitForLong(15 * 60 * 1000, () -> MinecraftServer.getServer().getConfigurationManager().playerEntityList.size() >= 2);
        } else {
            String port = await("port", 25 * 60 * 1000);
            int p = Integer.parseInt(port.trim());
            for (int attempt = 1;; attempt++) {
                if (attempt > 1 || !Boolean.getBoolean("voicechat.ingametest.autojoin")) {
                    t.clientDo(() -> mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, "127.0.0.1", p)));
                }
                boolean ended = t.waitFor(5 * 60 * 1000, () -> mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null
                    || mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected);
                boolean in = t.client(() -> mc.thePlayer != null && mc.theWorld != null);
                if (in) {
                    t.info("joined on attempt " + attempt);
                    break;
                }
                t.info("join attempt " + attempt + " failed: " + t.client(() -> InGameTestMod.describeScreen(mc.currentScreen)) + (ended ? "" : " (timed out)"));
                if (attempt == 3) {
                    throw new RuntimeException("could not join the LAN world in 3 attempts");
                }
                InGameTestMod.sleep(35000);
            }
            t.joinedPublic();
        }
        if (noVoice) {
            t.info("joined without the voice chat server");
            signal("done", "1");
            await("done", 15 * 60 * 1000);
            return;
        }
        t.check(t.waitFor(30000, () -> m().isConnected()), "voice chat did not connect");
        t.check(t.waitFor(30000, () -> {
            UUID o = other();
            return o != null && !m().getState(o).disconnected;
        }), "the other player does not show as connected");
        t.info("voice chat connected, " + (t.client(() -> m().getSession().isTunnel()) ? "tunnel" : "UDP") + ", other player " + t.client(() -> {
            UUID o = other();
            return o == null ? "?" : m().getState(o).name;
        }));
        t.clientDo(t::useVoiceActivationPublic);

        if (host) {
            // Guest 6 blocks to the west, host faces south
            String guestName = t.server(() -> ((EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(1)).getCommandSenderName());
            double[] p = t.playerPosPublic();
            t.serverCommand("/tp " + guestName + " " + (p[1] - 6) + " " + (p[2] - 1.62) + " " + p[3]);
            t.clientDo(() -> {
                mc.thePlayer.rotationYaw = 0;
                mc.thePlayer.prevRotationYaw = 0;
            });
        }
        signal("ready", "1");
        await("ready", 120000);
        InGameTestMod.sleep(1500);

        // A: host talks, guest listens. B: guest talks, host listens
        for (String step : new String[] {"A", "B"}) {
            boolean talking = host == step.equals("A");
            UUID o = other();
            TestHooks.reset();
            signal("start" + step, "1");
            await("start" + step, 60000);
            if (talking) {
                t.micTalkPublic(2000);
                signal("talked" + step, "1");
                String heard = await("heard" + step, 60000);
                t.check(Integer.parseInt(heard.split(" ")[0]) > 60, "step " + step + ": the other game only played " + heard);
            } else {
                await("talked" + step, 60000);
                InGameTestMod.sleep(800);
                int played = TestHooks.played(o);
                float[] g = TestHooks.lastGains.get(o);
                t.info("step " + step + ": played " + played + " frames, gains " + (g == null ? "none" : g[0] + " / " + g[1]));
                signal("heard" + step, played + " " + (g == null ? "" : g[0] + "/" + g[1]));
                t.check(played > 60, "step " + step + ": only " + played + " frames played");
                if (host) {
                    t.check(g != null && g[1] > g[0] * 2, "the guest stands west of the host, should be on the right");
                }
            }
        }
        t.screenshotPublic("lan-" + role() + "-near");

        // C: groups with a password, then group audio over a long distance
        if (host) {
            t.clientDo(() -> m().createGroup("LAN Group", "pw", GroupType.NORMAL));
            t.check(t.waitFor(5000, () -> m().getGroup() != null), "host could not create the group");
            signal("group", t.client(() -> m().getGroup().id.toString()));
            await("joined", 60000);
            UUID o = other();
            t.check(t.waitFor(5000, () -> m().getGroup().id.equals(m().getState(o).group)), "host does not see the guest in the group");
            String guestName = t.server(() -> ((EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(1)).getCommandSenderName());
            double[] p = t.playerPosPublic();
            t.serverCommand("/tp " + guestName + " " + (p[1] + 500) + " " + (p[2] - 1.62) + " " + p[3]);
            InGameTestMod.sleep(2000);
            signal("far", "1");
            await("far", 60000);
            t.micTalkPublic(2000);
            signal("talkedC", "1");
            String heard = await("heardC", 60000);
            t.check(Integer.parseInt(heard.split(" ")[0]) > 60, "group audio over 500 blocks: guest played " + heard);
            InGameTestMod.sleep(500);
            t.screenshotPublic("lan-host-group");
        } else {
            String id = await("group", 60000);
            UUID gid = UUID.fromString(id.trim());
            t.check(t.waitFor(5000, () -> {
                ClientGroup g = m().getGroup(gid);
                return g != null && g.hasPassword && g.name.equals("LAN Group");
            }), "guest does not see the group with its password");
            t.clientDo(() -> m().joinGroup(gid, "wrong"));
            InGameTestMod.sleep(1500);
            t.check(t.client(() -> m().getGroup() == null), "joined with a wrong password");
            t.clientDo(() -> m().joinGroup(gid, "pw"));
            t.check(t.waitFor(5000, () -> m().getGroup() != null && m().getGroup().id.equals(gid)), "guest could not join with the password");
            signal("joined", "1");
            await("far", 60000);
            UUID o = other();
            TestHooks.reset();
            signal("far", "1");
            await("talkedC", 60000);
            InGameTestMod.sleep(800);
            int played = TestHooks.played(o);
            float[] g = TestHooks.lastGains.get(o);
            t.info("group over 500 blocks: played " + played + ", gains " + (g == null ? "none" : g[0] + " / " + g[1]));
            t.check(g != null && g[0] == 1F && g[1] == 1F, "group audio should be heard at full volume");
            signal("heardC", String.valueOf(played));
            t.screenshotPublic("lan-guest-group");
        }

        signal("done", "1");
        await("done", 120000);
        if (!host) {
            t.clientDo(() -> {
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld(null);
                mc.displayGuiScreen(new GuiMainMenu());
            });
        } else {
            InGameTestMod.sleep(5000);
        }
    }
}
