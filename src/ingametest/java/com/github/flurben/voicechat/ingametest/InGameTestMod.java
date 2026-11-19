package com.github.flurben.voicechat.ingametest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.lwjgl.input.Keyboard;

import com.github.flurben.voicechat.audio.AudioDevices;
import com.github.flurben.voicechat.audio.AudioUtils;
import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.gui.AdjustVolumesScreen;
import com.github.flurben.voicechat.gui.CreateGroupScreen;
import com.github.flurben.voicechat.gui.EnterPasswordScreen;
import com.github.flurben.voicechat.gui.GroupScreen;
import com.github.flurben.voicechat.gui.JoinGroupScreen;
import com.github.flurben.voicechat.gui.SelectDeviceScreen;
import com.github.flurben.voicechat.gui.VoiceChatConfigScreen;
import com.github.flurben.voicechat.gui.VoiceChatScreen;
import com.github.flurben.voicechat.gui.VoiceChatSettingsScreen;
import com.github.flurben.voicechat.gui.onboarding.OnboardingScreens;
import com.github.flurben.voicechat.gui.widgets.VButton;
import com.github.flurben.voicechat.voice.client.ClientManager;
import com.github.flurben.voicechat.voice.client.ClientVoiceChat;
import com.github.flurben.voicechat.voice.client.KeyEvents;
import com.github.flurben.voicechat.voice.client.MicrophoneActivationType;
import com.github.flurben.voicechat.voice.client.TalkCache;
import com.github.flurben.voicechat.voice.client.TestHooks;
import com.github.flurben.voicechat.voice.common.ClientGroup;
import com.github.flurben.voicechat.voice.common.GroupType;
import com.github.flurben.voicechat.voice.common.PlayerState;
import com.github.flurben.voicechat.voice.common.VoiceMessage;
import com.github.flurben.voicechat.voice.server.ServerVoiceChat;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Plays through the voice chat inside a real game and writes the results to voicechat-ingametest.txt. Only compiled
 * with -PingameTest and only active with -Dvoicechat.ingametest=true. The microphone is replaced by a generated voice
 * and the speaker is silenced: the test never records or plays real audio. A second client is simulated on the
 * network by {@link FakePeer}.
 */
@Mod(modid = "voicechat_ingametest", name = "Voice Chat In-Game Test", version = "1", dependencies = "required-after:voicechat", acceptableRemoteVersions = "*")
public class InGameTestMod {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final boolean SLOW = Boolean.getBoolean("voicechat.ingametest.slow");
    private static final long TIMEOUT_SECONDS = SLOW ? 1200 : 300;
    private static final long TASK_TIMEOUT_SECONDS = SLOW ? 120 : 45;
    private static final long WATCHDOG_MILLIS = (SLOW ? 50 : 15) * 60 * 1000;
    private static final long LOADING_STALL_MILLIS = (SLOW ? 12 : 3) * 60 * 1000;
    private static final long STALL_MILLIS = (SLOW ? 3 : 1) * 60 * 1000;
    private static final long TEST_LIMIT_MILLIS = (SLOW ? 6 : 2) * 60 * 1000;

    private final Queue<FutureTask<?>> clientTasks = new ConcurrentLinkedQueue<>();
    private final Queue<FutureTask<?>> serverTasks = new ConcurrentLinkedQueue<>();
    private PrintWriter out;
    private int passed, failed;
    private final List<String> problems = new ArrayList<>();
    private volatile long lastProgress;
    private volatile boolean inWorld, everInWorld;
    private volatile long testStarted;
    private volatile String testName;
    private volatile String pendingShot;
    private volatile CountDownLatch shotDone;
    private FakePeer peer;
    private String world;
    private volatile boolean micTalking;
    private int micFrame;

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (!Boolean.getBoolean("voicechat.ingametest")) {
            return;
        }
        // How a launcher starts the game is hard to change between runs, so the run can be chosen with this file
        File options = new File(mc.mcDataDir, "voicechat-ingametest.properties");
        if (options.isFile()) {
            try (java.io.InputStream input = new java.io.FileInputStream(options)) {
                System.getProperties().load(input);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        FMLCommonHandler.instance().bus().register(this);
        String lan = System.getProperty("voicechat.ingametest.lan");
        Thread script = new Thread(lan == null ? this::runScript : () -> runLan(lan), "Voice chat in-game test");
        script.setDaemon(true);
        script.start();
        Thread watchdog = new Thread(() -> {
            long begin = System.currentTimeMillis();
            lastProgress = begin;
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                long stall = inWorld || everInWorld ? STALL_MILLIS : LOADING_STALL_MILLIS;
                boolean tooLong = lan == null && inWorld && testStarted > 0 && now - testStarted > TEST_LIMIT_MILLIS;
                if (now - lastProgress > stall || now - begin > WATCHDOG_MILLIS || tooLong) {
                    line("ABORT  " + (tooLong ? "test " + testName + " runs for " + (now - testStarted) / 1000 + " s" : "no progress for " + (now - lastProgress) / 1000 + " s")
                        + ", screen " + mc.currentScreen + ", state of all threads:");
                    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                        line("THREAD " + entry.getKey().getName() + " " + entry.getKey().getState());
                        StackTraceElement[] trace = entry.getValue();
                        for (int i = 0; i < Math.min(30, trace.length); i++) {
                            line("         at " + trace[i]);
                        }
                    }
                    FMLCommonHandler.instance().exitJava(3, true);
                }
            }
        }, "Voice chat test watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ---------------------------------------------------------------- game threads

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            drain(clientTasks);
        }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            drain(serverTasks);
        }
    }

    @SubscribeEvent
    public void renderEnd(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        String shot = pendingShot;
        if (shot != null) {
            pendingShot = null;
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, "voicechat-" + shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            shotDone.countDown();
        }
        drain(clientTasks);
    }

    private static void drain(Queue<FutureTask<?>> tasks) {
        FutureTask<?> task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    private <T> T on(Queue<FutureTask<?>> queue, Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        queue.add(task);
        try {
            return task.get(inWorld ? TASK_TIMEOUT_SECONDS : TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("game thread did not respond: " + e, e);
        }
    }

    <T> T client(Callable<T> c) {
        return on(clientTasks, c);
    }

    void clientDo(Runnable r) {
        client(() -> {
            r.run();
            return null;
        });
    }

    <T> T server(Callable<T> c) {
        MinecraftServer s = MinecraftServer.getServer();
        if (s == null || !s.isServerRunning()) {
            throw new RuntimeException("the server is not running");
        }
        return on(serverTasks, c);
    }

    void serverDo(ThrowingRunnable r) {
        server(() -> {
            r.run();
            return null;
        });
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    boolean waitFor(long millis, Callable<Boolean> condition) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (client(condition)) {
                return true;
            }
            sleep(25);
        }
        return false;
    }

    boolean waitForAny(long millis, Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (condition.call()) {
                return true;
            }
            sleep(25);
        }
        return false;
    }

    void screenshot(String name) {
        sleep(300);
        shotDone = new CountDownLatch(1);
        pendingShot = name;
        try {
            if (!shotDone.await(30, TimeUnit.SECONDS)) {
                problems.add("screenshot " + name + " was not taken");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        info("screenshot voicechat-" + name + ".png");
    }

    // ---------------------------------------------------------------- reporting

    private synchronized void line(String text) {
        lastProgress = System.currentTimeMillis();
        System.out.println("[voicechat-test] " + text);
        if (out != null) {
            out.println(text);
            out.flush();
        }
    }

    void info(String text) {
        line("INFO   " + text);
    }

    void check(boolean condition, String message) {
        if (!condition) {
            problems.add(message);
        }
    }

    interface Body {
        void run() throws Exception;
    }

    private void test(String name, Body body) {
        String only = System.getProperty("voicechat.ingametest.only");
        if (only != null) {
            boolean selected = false;
            for (String part : only.split(",")) {
                selected |= name.contains(part);
            }
            if (!selected) {
                return;
            }
        }
        problems.clear();
        long start = System.currentTimeMillis();
        testName = name;
        testStarted = start;
        line("START  " + name);
        try {
            body.run();
        } catch (Throwable e) {
            StringBuilder trace = new StringBuilder(e.toString());
            StackTraceElement[] el = e.getStackTrace();
            for (int i = 0; i < Math.min(8, el.length); i++) {
                trace.append(" | ").append(el[i]);
            }
            Throwable cause = e.getCause();
            if (cause != null) {
                trace.append(" | caused by ").append(cause);
                for (int i = 0; i < Math.min(6, cause.getStackTrace().length); i++) {
                    trace.append(" | ").append(cause.getStackTrace()[i]);
                }
            }
            problems.add("EXCEPTION " + trace);
        }
        testStarted = 0;
        try {
            clientDo(() -> {
                if (mc.currentScreen != null) {
                    mc.displayGuiScreen(null);
                }
            });
        } catch (RuntimeException ignored) {
        }
        long took = System.currentTimeMillis() - start;
        if (problems.isEmpty()) {
            passed++;
            line("PASS   " + name + " (" + took + " ms)");
        } else {
            failed++;
            line("FAIL   " + name + " (" + took + " ms)");
            for (String p : problems) {
                line("         " + p);
            }
        }
    }

    // ---------------------------------------------------------------- audio

    /** A voice like signal: harmonics of 150 Hz with a syllable rhythm. */
    static short[] voice(double amplitude, int frameIndex) {
        short[] f = new short[AudioUtils.FRAME_SIZE];
        for (int i = 0; i < f.length; i++) {
            double t = (frameIndex * AudioUtils.FRAME_SIZE + i) / (double) AudioUtils.SAMPLE_RATE;
            double s = 0;
            for (int h = 1; h <= 8; h++) {
                s += Math.sin(2 * Math.PI * 150 * h * t) / h;
            }
            double env = 0.6 + 0.4 * Math.sin(2 * Math.PI * 4 * t);
            f[i] = AudioUtils.clip((float) (s * env * amplitude / 2));
        }
        return f;
    }

    private short[] micFrame() {
        if (!micTalking) {
            return new short[AudioUtils.FRAME_SIZE];
        }
        return voice(9000, micFrame++);
    }

    private ClientManager m() {
        return ClientManager.INSTANCE;
    }

    private ClientConfig cfg() {
        return ClientManager.INSTANCE.config();
    }

    private UUID ownId() {
        return client(() -> m().getOwnId());
    }

    /** Talks into the generated microphone for a while. */
    private void micTalk(long millis) {
        micTalking = true;
        sleep(millis);
        micTalking = false;
        sleep(700);
    }

    private void useVoiceActivation() {
        cfg().microphoneActivationType.set(MicrophoneActivationType.VOICE);
        cfg().voiceActivityDetection.set(false);
        cfg().voiceActivationThreshold.set(-45D);
        cfg().voiceDeactivationDelay.set(2);
        cfg().muted.set(false);
        cfg().disabled.set(false);
        TestHooks.pttKey = null;
        TestHooks.whisperKey = false;
    }

    private double[] playerPos() {
        return server(() -> {
            EntityPlayerMP p = (EntityPlayerMP) MinecraftServer.getServer().getConfigurationManager().playerEntityList.get(0);
            return new double[] {p.dimension, p.posX, p.boundingBox.minY + 1.62, p.posZ};
        });
    }

    private void peerAt(double dx, double dy, double dz) {
        double[] p = playerPos();
        serverDo(() -> peer.moveTo(ServerVoiceChat.get(), (int) p[0], p[1] + dx, p[2] + dy, p[3] + dz));
    }

    private void peerInDimension(int dim) {
        double[] p = playerPos();
        serverDo(() -> peer.moveTo(ServerVoiceChat.get(), dim, p[1], p[2], p[3]));
    }

    // ---------------------------------------------------------------- script

    private void runScript() {
        try {
            out = new PrintWriter(new FileOutputStream(new File(mc.mcDataDir, "voicechat-ingametest.txt"), false), true);
            line("BEGIN  java " + System.getProperty("java.version") + " lwjgl " + org.lwjgl.Sys.getVersion());
            TestHooks.muteOutput = true;
            TestHooks.microphone = this::micFrame;
            TestHooks.whisperKey = false;
            waitFor(TIMEOUT_SECONDS * 1000, () -> mc.currentScreen != null);
            line("REACHED main menu");
            clientDo(() -> {
                cfg().onboardingFinished.set(false);
                cfg().forceTunnel.set(false);
                cfg().hideIcons.set(false);
                cfg().disabled.set(false);
                cfg().config.save();
            });

            test("config screen from the mod list", this::testConfigScreen);
            test("audio devices are listed", this::testDevices);

            createWorld();
            line("REACHED world");

            test("voice server starts and the client connects over UDP", this::testConnect);
            test("onboarding", this::testOnboarding);
            test("menu screens", this::testScreens);
            test("fake peer joins", this::testPeerJoins);
            test("peer is heard with direction and distance", this::testPeerToClient);
            test("own voice reaches the peer (voice activation)", this::testClientToPeer);
            test("mute and disable", this::testMuteDisable);
            test("push to talk and whisper", this::testPushToTalk);
            test("voice distance and dimensions", this::testDistance);
            test("normal group", () -> testGroupType(GroupType.NORMAL));
            test("open group", () -> testGroupType(GroupType.OPEN));
            test("isolated group", () -> testGroupType(GroupType.ISOLATED));
            test("group members hear each other at any distance", this::testGroupAudio);
            test("group password", this::testPassword);
            test("group screens", this::testGroupScreens);
            test("commands", this::testCommands);
            test("key bindings", this::testKeys);
            test("HUD and name tag icons", this::testHud);
            test("recording", this::testRecording);
            test("tunnel through the Minecraft connection", this::testTunnel);
            test("leave and rejoin the world", this::testRejoin);

            line("DONE   passed=" + passed + " failed=" + failed);
            try {
                if (peer != null) {
                    peer.close();
                }
                clientDo(() -> {
                    if (mc.theWorld != null) {
                        mc.theWorld.sendQuittingDisconnectingPacket();
                        mc.loadWorld(null);
                    }
                    mc.displayGuiScreen(new GuiMainMenu());
                });
                sleep(1500);
            } finally {
                clientDo(mc::shutdown);
                sleep(20000);
                line("INFO   the game did not close by itself, ending it");
                FMLCommonHandler.instance().exitJava(0, true);
            }
        } catch (Throwable e) {
            line("ABORT  " + e);
            e.printStackTrace();
            if (out != null) {
                e.printStackTrace(out);
            }
            FMLCommonHandler.instance().exitJava(2, true);
        }
    }

    // ---------------------------------------------------------------- LAN test with two games

    private void runLan(String role) {
        try {
            out = new PrintWriter(new FileOutputStream(new File(mc.mcDataDir, "voicechat-lan-" + role + ".txt"), false), true);
            line("BEGIN  LAN " + role + ", java " + System.getProperty("java.version"));
            TestHooks.muteOutput = true;
            TestHooks.microphone = this::micFrame;
            TestHooks.whisperKey = false;
            waitFor(TIMEOUT_SECONDS * 1000, () -> mc.currentScreen != null);
            line("REACHED main menu");
            clientDo(() -> {
                cfg().onboardingFinished.set(true);
                cfg().runLocalServer.set(true);
                cfg().forceTunnel.set(false);
                cfg().disabled.set(false);
                cfg().config.save();
            });
            File shared = new File(System.getProperty("voicechat.ingametest.shared"));
            shared.mkdirs();
            LanTest lan = new LanTest(this, role, shared);
            test("LAN " + role, lan::run);
            line("DONE   passed=" + passed + " failed=" + failed);
            sleep(2000);
        } catch (Throwable e) {
            line("ABORT  " + e);
            if (out != null) {
                e.printStackTrace(out);
            }
        } finally {
            try {
                clientDo(mc::shutdown);
            } catch (RuntimeException ignored) {
            }
            sleep(20000);
            FMLCommonHandler.instance().exitJava(0, true);
        }
    }

    /** The screen and its text fields, so a disconnect screen shows its reason. */
    static String describeScreen(GuiScreen screen) {
        if (screen == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder(screen.getClass().getName());
        for (java.lang.reflect.Field f : screen.getClass().getDeclaredFields()) {
            if (f.getType() == String.class || net.minecraft.util.IChatComponent.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(screen);
                    sb.append(" | ").append(v instanceof net.minecraft.util.IChatComponent ? ((net.minecraft.util.IChatComponent) v).getUnformattedText() : v);
                } catch (Exception ignored) {
                }
            }
        }
        return sb.toString();
    }

    void heartbeat() {
        lastProgress = System.currentTimeMillis();
    }

    void createWorldPublic() {
        createWorld();
    }

    /** Joined a server of another game, there is no integrated server here. */
    void joinedPublic() {
        if (!waitFor(10 * 60 * 1000, () -> mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null)) {
            throw new RuntimeException("could not join the LAN world, screen " + client(() -> describeScreen(mc.currentScreen)));
        }
        sleep(1000);
        inWorld = true;
        everInWorld = true;
    }

    void waitForLong(long millis, Callable<Boolean> condition) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            heartbeat();
            if (client(condition)) {
                return;
            }
            sleep(250);
        }
        throw new RuntimeException("timed out");
    }

    void useVoiceActivationPublic() {
        useVoiceActivation();
    }

    double[] playerPosPublic() {
        return playerPos();
    }

    void micTalkPublic(long millis) {
        micTalk(millis);
    }

    void screenshotPublic(String name) {
        screenshot(name);
    }

    private void createWorld() {
        world = "voicechattest" + System.currentTimeMillis();
        inWorld = false;
        clientDo(() -> {
            mc.gameSettings.pauseOnLostFocus = false;
            WorldSettings settings = new WorldSettings(4242L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
            settings.enableCommands();
            mc.launchIntegratedServer(world, world, settings);
        });
        joined();
        serverCommand("/gamerule doMobSpawning false");
        serverCommand("/gamerule doDaylightCycle false");
        serverCommand("/time set 6000");
    }

    private void joined() {
        if (!waitFor(TIMEOUT_SECONDS * 1000, () -> mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null && MinecraftServer.getServer() != null
            && !MinecraftServer.getServer().getConfigurationManager().playerEntityList.isEmpty())) {
            throw new RuntimeException("could not join the world");
        }
        clientDo(() -> mc.inGameHasFocus = true);
        sleep(1000);
        inWorld = true;
        everInWorld = true;
    }

    void serverCommand(String command) {
        server(() -> {
            MinecraftServer s = MinecraftServer.getServer();
            EntityPlayerMP sender = (EntityPlayerMP) s.getConfigurationManager().playerEntityList.get(0);
            s.getCommandManager().executeCommand(sender, command);
            return null;
        });
    }

    private void open(GuiScreen screen, String shot) {
        clientDo(() -> mc.displayGuiScreen(screen));
        sleep(400);
        check(client(() -> mc.currentScreen == screen), shot + " did not stay open, now " + client(() -> mc.currentScreen));
        screenshot(shot);
    }

    /** Presses the button with this text on the current screen. */
    private void press(String text) {
        Boolean found = client(() -> {
            if (mc.currentScreen == null) {
                return false;
            }
            for (Object o : buttonList(mc.currentScreen)) {
                if (o instanceof VButton && text.equals(((VButton) o).displayString) && ((VButton) o).enabled && ((VButton) o).visible) {
                    ((VButton) o).onPress();
                    return true;
                }
            }
            return false;
        });
        check(found, "no button '" + text + "' on " + client(() -> mc.currentScreen));
        sleep(250);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> buttonList(GuiScreen screen) throws Exception {
        java.lang.reflect.Field f = cpw.mods.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
        return (List<Object>) f.get(screen);
    }

    // ---------------------------------------------------------------- tests

    private void testConfigScreen() {
        GuiScreen menu = client(() -> mc.currentScreen);
        open(new VoiceChatConfigScreen(menu), "config");
        open(new VoiceChatSettingsScreen(menu), "settings-menu");
        clientDo(() -> mc.displayGuiScreen(menu));
    }

    private void testDevices() {
        int mics = AudioDevices.getMicrophones().size();
        int speakers = AudioDevices.getSpeakers().size();
        info(mics + " microphones and " + speakers + " speakers found (names not logged)");
        check(speakers > 0, "no speaker found");
    }

    private void testConnect() {
        ServerVoiceChat s = ServerVoiceChat.get();
        check(s != null, "no voice server in singleplayer");
        if (s == null) {
            return;
        }
        info("voice server port " + s.getPort());
        check(s.getPort() > 0, "voice server has no UDP port");
        check(waitFor(15000, () -> m().isConnected()), "client did not connect");
        ClientVoiceChat session = client(() -> m().getSession());
        check(session != null && !session.isTunnel(), "client should use UDP");
        UUID own = ownId();
        check(waitFor(5000, () -> {
            PlayerState st = m().getState(own);
            return st != null && !st.disconnected;
        }), "own player state should be connected");
        check(server(() -> ServerVoiceChat.get().isConnected(own)), "server does not see the connection");
        check(waitFor(5000, () -> m().getOutput() != null && m().getOutput().isSpeakerAvailable()), "speaker did not open: "
            + client(() -> m().getOutput() == null ? "no output" : m().getOutput().getError()));
        check(waitFor(5000, () -> m().getMic() != null && m().getMic().isOpened()), "microphone thread did not start");
    }

    private void testOnboarding() {
        GuiScreen first = client(() -> OnboardingScreens.first(null));
        open(first, "onboarding-1-introduction");
        String next = client(() -> net.minecraft.client.resources.I18n.format("message.voicechat.onboarding.next"));
        press(next);
        screenshot("onboarding-2-microphone");
        press(next);
        screenshot("onboarding-3-speaker");
        press(next);
        screenshot("onboarding-4-activation");
        press(client(() -> net.minecraft.client.resources.I18n.format("message.voicechat.onboarding.voice.title")));
        clientDo(() -> m().setMicTesting(true));
        micTalking = true;
        sleep(800);
        screenshot("onboarding-5-voice-test");
        micTalking = false;
        check(client(() -> m().getMic() != null && m().getMic().getLevel() > -127), "no level shown while testing");
        press(next);
        check(client(() -> !m().isMicTesting()), "microphone test still on after leaving the page");
        screenshot("onboarding-6-final");
        press(client(() -> net.minecraft.client.resources.I18n.format("message.voicechat.onboarding.finish")));
        check(client(() -> cfg().onboardingFinished.get()), "onboarding not marked finished");
        check(client(() -> mc.currentScreen instanceof VoiceChatScreen), "voice chat menu should open after the onboarding");
    }

    private void testScreens() {
        open(new VoiceChatScreen(), "menu");
        open(new VoiceChatSettingsScreen(null), "settings");
        clientDo(() -> cfg().microphoneActivationType.set(MicrophoneActivationType.PTT));
        open(new VoiceChatSettingsScreen(null), "settings-ptt");
        clientDo(() -> cfg().microphoneActivationType.set(MicrophoneActivationType.VOICE));
        open(new SelectDeviceScreen(null, true), "select-microphone");
        open(new SelectDeviceScreen(null, false), "select-speaker");
        open(new AdjustVolumesScreen(null), "volumes-empty");
        open(new JoinGroupScreen(), "groups-empty");
        open(new CreateGroupScreen(null), "create-group");
    }

    private void testPeerJoins() throws Exception {
        peer = new FakePeer();
        double[] p = playerPos();
        serverDo(() -> peer.register(ServerVoiceChat.get(), (int) p[0], p[1] - 5, p[2], p[3]));
        peer.connect(ServerVoiceChat.get().getPort());
        check(peer.authenticated, "peer not authenticated");
        check(waitFor(3000, () -> m().getState(peer.id) != null), "client does not know the peer");
        open(new AdjustVolumesScreen(null), "volumes");
    }

    private void testPeerToClient() throws Exception {
        // Face south, the peer stands 5 blocks to the west: on the right
        clientDo(() -> {
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.prevRotationYaw = 0;
            mc.thePlayer.rotationPitch = 0;
        });
        peerAt(-5, 0, 0);
        TestHooks.reset();
        Thread talk = new Thread(() -> {
            try {
                peer.talk(1500, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        talk.start();
        sleep(700);
        check(client(() -> TalkCache.isTalking(peer.id)), "peer not shown as talking");
        talk.join();
        sleep(600);
        int played = TestHooks.played(peer.id);
        info("played " + played + " frames of 75 sent");
        check(played >= 60, "only " + played + " of 75 frames played");
        float[] g = TestHooks.lastGains.get(peer.id);
        check(g != null && g[1] > g[0] * 2, "peer on the west should be louder on the right: " + (g == null ? "none" : g[0] + " / " + g[1]));
        check(g != null && Math.abs(Math.max(g[0], g[1]) - (1 - 5 / 48F)) < 0.1 && Math.min(g[0], g[1]) > 0.05F, "distance attenuation or panning off: " + (g == null ? "" : g[0] + " " + g[1]));
        sleep(400);
        check(!client(() -> TalkCache.isTalking(peer.id)), "peer still shown as talking");
    }

    private void testClientToPeer() {
        clientDo(this::useVoiceActivation);
        peerAt(-5, 0, 0);
        peer.resetCounts();
        micTalking = true;
        sleep(600);
        check(client(() -> m().isTalking()), "HUD does not show own talking");
        sleep(900);
        micTalking = false;
        sleep(700);
        int got = peer.playerSounds.get();
        info("peer received " + got + " packets in 1.5 s");
        check(got >= 60, "peer received only " + got + " packets");
        VoiceMessage.Sound last = peer.lastSound;
        check(last != null && last.kind == VoiceMessage.PLAYER_SOUND && Math.abs(last.distance - 48F) < 0.01, "wrong sound packet " + last);
        check(last != null && last.sender.equals(ownId()), "sound from the wrong sender");
        // Silence does not send
        peer.resetCounts();
        sleep(1000);
        check(peer.playerSounds.get() == 0, "silence was sent: " + peer.playerSounds.get());
    }

    private void testMuteDisable() throws Exception {
        clientDo(() -> m().setMuted(true));
        peer.resetCounts();
        micTalk(1000);
        check(peer.playerSounds.get() == 0, "muted microphone sent " + peer.playerSounds.get());
        clientDo(() -> m().setMuted(false));

        clientDo(() -> m().setDisabled(true));
        UUID own = ownId();
        check(waitFor(3000, () -> m().getState(own) != null && m().getState(own).disabled), "server did not mark the player disabled");
        peer.resetCounts();
        TestHooks.reset();
        micTalk(800);
        check(peer.playerSounds.get() == 0, "disabled voice chat still sent audio");
        peer.talk(800, false);
        sleep(500);
        check(TestHooks.played(peer.id) == 0, "disabled voice chat still played " + TestHooks.played(peer.id));
        clientDo(() -> m().setDisabled(false));
        check(waitFor(3000, () -> !m().getState(own).disabled), "server did not mark the player enabled again");
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() > 20, "enabled again but nothing sent");
    }

    private void testPushToTalk() {
        clientDo(() -> {
            cfg().microphoneActivationType.set(MicrophoneActivationType.PTT);
            cfg().pttDeactivationDelay.set(1);
        });
        TestHooks.pttKey = false;
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() == 0, "sent without the push to talk key: " + peer.playerSounds.get());
        TestHooks.pttKey = true;
        peer.resetCounts();
        micTalk(800);
        TestHooks.pttKey = false;
        check(peer.playerSounds.get() > 30, "push to talk sent only " + peer.playerSounds.get());
        // Whisper key talks quietly, with the whisper distance
        TestHooks.whisperKey = true;
        peer.resetCounts();
        micTalk(800);
        TestHooks.whisperKey = false;
        VoiceMessage.Sound last = peer.lastSound;
        check(peer.whisperSounds.get() > 30, "whisper sent only " + peer.whisperSounds.get());
        check(last != null && Math.abs(last.distance - 24F) < 0.01, "whisper distance should be 24 but was " + (last == null ? "none" : last.distance));
        TestHooks.pttKey = null;
        clientDo(this::useVoiceActivation);
    }

    private void testDistance() throws Exception {
        clientDo(this::useVoiceActivation);
        peerAt(-30, 0, 0);
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() > 20, "peer at 30 blocks should hear normal speech");
        // Whispering does not reach 30 blocks
        TestHooks.whisperKey = true;
        peer.resetCounts();
        micTalk(800);
        TestHooks.whisperKey = false;
        check(peer.whisperSounds.get() == 0, "whisper reached 30 blocks");
        // Out of range
        peerAt(-100, 0, 0);
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() == 0, "peer at 100 blocks heard " + peer.playerSounds.get());
        TestHooks.reset();
        peer.talk(600, false);
        sleep(400);
        check(TestHooks.played(peer.id) == 0, "client heard a peer 100 blocks away");
        // Another dimension
        peerInDimension(-1);
        peer.resetCounts();
        micTalk(600);
        check(peer.playerSounds.get() == 0, "peer in the nether heard the overworld");
        peerAt(-5, 0, 0);
    }

    private ClientGroup createGroup(String name, String password, GroupType type) {
        clientDo(() -> m().createGroup(name, password, type));
        check(waitFor(3000, () -> m().getGroup() != null && m().getGroup().name.equals(name)), "group " + name + " was not joined");
        return client(() -> m().getGroup());
    }

    private void leaveGroup() {
        clientDo(() -> m().leaveGroup());
        check(waitFor(3000, () -> m().getGroup() == null), "could not leave the group");
    }

    private void testGroupType(GroupType type) throws Exception {
        clientDo(this::useVoiceActivation);
        peerAt(-5, 0, 0);
        ClientGroup g = createGroup("t_" + type.id, null, type);
        check(g != null && g.type == type, "group has the wrong type");
        // Outsider (peer) talks
        TestHooks.reset();
        peer.talk(800, false);
        sleep(500);
        boolean hearsOutside = TestHooks.played(peer.id) > 20;
        boolean expectedHear = type != GroupType.ISOLATED;
        check(hearsOutside == expectedHear, type + ": member hears outsider = " + hearsOutside + " (" + TestHooks.played(peer.id) + " frames)");
        // Member talks
        peer.resetCounts();
        micTalk(800);
        boolean outsiderHears = peer.playerSounds.get() > 20;
        boolean expectedOut = type == GroupType.OPEN;
        check(outsiderHears == expectedOut, type + ": outsider hears member = " + outsiderHears + " (" + peer.playerSounds.get() + " packets)");
        leaveGroup();
        check(waitFor(3000, () -> m().getGroup(g.id) == null), "empty group was not removed");
    }

    private void testGroupAudio() throws Exception {
        clientDo(this::useVoiceActivation);
        ClientGroup g = createGroup("t_far", null, GroupType.NORMAL);
        peerAt(-1000, 0, 0);
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), g.id));
        peer.resetCounts();
        micTalk(800);
        check(peer.groupSounds.get() > 20, "group member 1000 blocks away got " + peer.groupSounds.get());
        check(peer.playerSounds.get() == 0, "group member also got proximity audio");
        TestHooks.reset();
        peer.talk(800, false);
        sleep(500);
        check(TestHooks.played(peer.id) > 20, "did not hear the far group member");
        float[] gains = TestHooks.lastGains.get(peer.id);
        check(gains != null && gains[0] == 1F && gains[1] == 1F, "group audio should not be positional");
        peerInDimension(-1);
        peer.resetCounts();
        micTalk(600);
        check(peer.groupSounds.get() > 10, "group member in another dimension should still hear (cross_dimension_groups)");
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), null));
        peerAt(-5, 0, 0);
        leaveGroup();
    }

    private void testPassword() {
        ClientGroup g = createGroup("t_secret", "pw123", GroupType.NORMAL);
        check(g != null && g.hasPassword, "group should have a password");
        // Keep the group alive with the peer in it while we leave
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), g.id));
        leaveGroup();
        open(new EnterPasswordScreen(new JoinGroupScreen(), g), "password");
        clientDo(() -> m().joinGroup(g.id, "wrong"));
        sleep(800);
        check(client(() -> m().getGroup() == null), "joined with a wrong password");
        clientDo(() -> m().joinGroup(g.id, "pw123"));
        check(waitFor(3000, () -> m().getGroup() != null && m().getGroup().id.equals(g.id)), "could not join with the right password");
    }

    private void testGroupScreens() {
        ClientGroup g = client(() -> m().getGroup());
        check(g != null, "not in a group");
        if (g == null) {
            return;
        }
        Thread talk = new Thread(() -> {
            try {
                peer.talk(2500, false);
            } catch (Exception ignored) {
            }
        });
        talk.start();
        open(new GroupScreen(g), "group");
        try {
            talk.join();
        } catch (InterruptedException ignored) {
        }
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), null));
        leaveGroup();
        clientDo(() -> m().createGroup("Miners", null, GroupType.OPEN));
        waitFor(3000, () -> m().getGroup() != null);
        clientDo(() -> m().leaveGroup());
        waitFor(3000, () -> m().getGroup() == null);
        serverDo(() -> {
            // A second group that stays, so the list has entries
            ServerVoiceChat s = ServerVoiceChat.get();
            java.lang.reflect.Field f = ServerVoiceChat.class.getDeclaredField("groups");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, com.github.flurben.voicechat.voice.server.Group> groups = (Map<UUID, com.github.flurben.voicechat.voice.server.Group>) f.get(s);
            com.github.flurben.voicechat.voice.server.Group keep = new com.github.flurben.voicechat.voice.server.Group(UUID.randomUUID(), "Base Builders", "x", GroupType.ISOLATED,
                true);
            groups.put(keep.id, keep);
            com.github.flurben.voicechat.net.VoiceNetwork.sendToPlayers(s.moddedPlayers(), keep.toClientGroup()::write, com.github.flurben.voicechat.net.VoiceNetwork.S_ADD_GROUP);
            peer.setGroup(s, keep.id);
        });
        sleep(500);
        open(new JoinGroupScreen(), "groups");
    }

    private void testCommands() {
        String self = client(() -> mc.thePlayer.getCommandSenderName());
        serverCommand("/voicechat help");
        serverCommand("/voicechat test " + self);
        serverCommand("/vc test " + self);
        createGroup("t_cmd", null, GroupType.NORMAL);
        serverCommand("/voicechat invite " + self);
        serverCommand("/voicechat leave");
        check(waitFor(3000, () -> m().getGroup() == null), "/voicechat leave did not leave");
        serverCommand("/voicechat join Base Builders x");
        check(waitFor(3000, () -> m().getGroup() != null && m().getGroup().name.equals("Base Builders")), "/voicechat join with password failed");
        serverCommand("/voicechat leave");
        waitFor(3000, () -> m().getGroup() == null);
        sleep(300);
        screenshot("chat-commands");
    }

    private void pressKey(KeyBinding key) {
        clientDo(() -> {
            KeyBinding.onTick(key.getKeyCode());
            FMLCommonHandler.instance().bus().post(new InputEvent.KeyInputEvent());
        });
        sleep(200);
    }

    private void testKeys() {
        boolean muted = client(() -> m().isMuted());
        pressKey(KeyEvents.MUTE);
        check(client(() -> m().isMuted()) != muted, "M did not toggle mute");
        pressKey(KeyEvents.MUTE);
        boolean disabled = client(() -> m().isDisabled());
        pressKey(KeyEvents.DISABLE);
        check(client(() -> m().isDisabled()) != disabled, "N did not toggle the voice chat");
        pressKey(KeyEvents.DISABLE);
        pressKey(KeyEvents.HIDE_ICONS);
        check(client(() -> cfg().hideIcons.get()), "H did not hide the icons");
        pressKey(KeyEvents.HIDE_ICONS);
        check(!client(() -> cfg().hideIcons.get()), "H did not show the icons again");
        pressKey(KeyEvents.VOICE_CHAT);
        check(client(() -> mc.currentScreen instanceof VoiceChatScreen), "V did not open the voice chat menu");
        clientDo(() -> mc.displayGuiScreen(null));
        check(KeyEvents.PTT.getKeyCode() == Keyboard.KEY_CAPITAL, "push to talk should default to caps lock");
    }

    private void testHud() {
        clientDo(this::useVoiceActivation);
        ClientGroup g = createGroup("HUD Test", null, GroupType.NORMAL);
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), g.id));
        peerAt(0, 0, 4);
        // A client side copy of the peer so its name tag shows, standing in front of us
        clientDo(() -> {
            EntityOtherPlayerMP e = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(peer.id, peer.name));
            e.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ + 4, 180F, 0F);
            mc.theWorld.addEntityToWorld(-4242, e);
            mc.thePlayer.rotationYaw = 0;
            mc.thePlayer.rotationPitch = 10;
            mc.gameSettings.hideGUI = false;
        });
        Thread talk = new Thread(() -> {
            try {
                peer.talk(2000, false);
            } catch (Exception ignored) {
            }
        });
        talk.start();
        micTalking = true;
        sleep(800);
        screenshot("hud-talking");
        UUID own = ownId();
        info("own skin: entity " + client(() -> mc.thePlayer.getLocationSkin()) + ", head " + client(() -> com.github.flurben.voicechat.gui.GuiDraw.getSkin(own)));
        micTalking = false;
        try {
            talk.join();
        } catch (InterruptedException ignored) {
        }
        sleep(500);
        clientDo(() -> m().setMuted(true));
        sleep(200);
        screenshot("hud-muted");
        clientDo(() -> m().setDisabled(true));
        sleep(200);
        screenshot("hud-disabled");
        clientDo(() -> {
            m().setDisabled(false);
            m().setMuted(false);
            mc.theWorld.removeEntityFromWorld(-4242);
        });
        serverDo(() -> peer.setGroup(ServerVoiceChat.get(), null));
        leaveGroup();
    }

    private void testRecording() throws Exception {
        clientDo(this::useVoiceActivation);
        peerAt(-5, 0, 0);
        clientDo(() -> m().toggleRecording());
        check(client(() -> m().isRecording()), "recording did not start");
        File folder = client(() -> m().getSession().getRecorder().getFolder());
        open(new VoiceChatScreen(), "menu-recording");
        clientDo(() -> mc.displayGuiScreen(null));
        peer.talk(1000, false);
        micTalk(1000);
        clientDo(() -> m().toggleRecording());
        check(!client(() -> m().isRecording()), "recording did not stop");
        File[] files = folder.listFiles();
        check(files != null && files.length == 2, "expected 2 tracks but found " + (files == null ? 0 : files.length));
        if (files != null) {
            for (File f : files) {
                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                    byte[] h = new byte[44];
                    raf.readFully(h);
                    String riff = new String(h, 0, 4, "US-ASCII");
                    int dataLen = (h[40] & 0xFF) | (h[41] & 0xFF) << 8 | (h[42] & 0xFF) << 16 | (h[43] & 0xFF) << 24;
                    check(riff.equals("RIFF") && dataLen == raf.length() - 44, f.getName() + " has a bad header");
                    double seconds = dataLen / (48000.0 * 4);
                    info("track " + (f.getName().startsWith("TestPeer") ? "peer" : "own") + ": " + String.format("%.2f", seconds) + " s");
                    check(seconds > 0.8, f.getName() + " is too short: " + seconds + " s");
                }
                f.delete();
            }
        }
        folder.delete();
    }

    private void testTunnel() throws Exception {
        clientDo(this::useVoiceActivation);
        clientDo(() -> {
            cfg().forceTunnel.set(true);
            m().disconnect();
        });
        check(waitFor(15000, () -> m().isConnected()), "client did not reconnect");
        check(client(() -> m().getSession() != null && m().getSession().isTunnel()), "client is not using the tunnel");
        UUID own = ownId();
        check(server(() -> ServerVoiceChat.get().getConnection(own).tunnel), "server does not use the tunnel for the client");
        TestHooks.reset();
        peer.talk(800, false);
        sleep(500);
        check(TestHooks.played(peer.id) > 20, "audio through the tunnel was not played: " + TestHooks.played(peer.id));
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() > 20, "audio sent through the tunnel did not arrive: " + peer.playerSounds.get());
        clientDo(() -> {
            cfg().forceTunnel.set(false);
            cfg().config.save();
            m().disconnect();
        });
        check(waitFor(15000, () -> m().isConnected() && !m().getSession().isTunnel()), "client did not go back to UDP");
    }

    private void testRejoin() throws Exception {
        clientDo(() -> {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        });
        inWorld = false;
        sleep(1500);
        check(client(() -> m().getSession() == null && m().getOutput() == null && m().getMic() == null), "voice chat still running after leaving the world");
        check(ServerVoiceChat.get() == null, "voice server still running after leaving the world");
        clientDo(() -> mc.launchIntegratedServer(world, world, null));
        joined();
        check(waitFor(15000, () -> m().isConnected()), "client did not connect after rejoining");
        // The old peer is gone with the old server, register a new one
        peer.close();
        peer = new FakePeer();
        double[] p = playerPos();
        serverDo(() -> peer.register(ServerVoiceChat.get(), (int) p[0], p[1] - 5, p[2], p[3]));
        peer.connect(ServerVoiceChat.get().getPort());
        clientDo(this::useVoiceActivation);
        peer.resetCounts();
        micTalk(800);
        check(peer.playerSounds.get() > 20, "no audio after rejoining");
    }
}
