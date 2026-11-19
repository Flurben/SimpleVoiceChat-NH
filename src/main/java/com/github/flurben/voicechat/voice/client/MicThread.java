package com.github.flurben.voicechat.voice.client;

import javax.sound.sampled.LineUnavailableException;

import com.github.flurben.voicechat.VoiceChatMod;
import com.github.flurben.voicechat.audio.AudioDevices;
import com.github.flurben.voicechat.audio.AudioUtils;
import com.github.flurben.voicechat.audio.AutomaticGainControl;
import com.github.flurben.voicechat.audio.Denoiser;
import com.github.flurben.voicechat.audio.OpusCodec;
import com.github.flurben.voicechat.audio.VoiceActivityDetector;
import com.github.flurben.voicechat.config.ClientConfig;
import com.github.flurben.voicechat.voice.common.Codec;

/**
 * Reads the microphone in 20 ms frames, cleans it up (noise suppression, gain), decides whether the player is talking
 * (push to talk or voice activation) and sends the Opus encoded audio.
 */
public class MicThread implements Runnable {

    private volatile boolean running = true;
    private volatile String error;
    private volatile boolean opened;
    private volatile double level = AudioUtils.LOWEST_DB;
    private volatile boolean active;
    private volatile boolean whispering;
    private volatile boolean speech;
    private Thread thread;

    private final Denoiser denoiser = new Denoiser();
    private final AutomaticGainControl agc = new AutomaticGainControl();
    private final VoiceActivityDetector vad = new VoiceActivityDetector();
    private OpusCodec.Encoder encoder;
    private Codec encoderCodec;
    private long sequence;
    private int holdFrames;
    private boolean wasSending;

    public void start() {
        thread = new Thread(this, "VoiceChatMicrophone");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isOpened() {
        return opened;
    }

    public String getError() {
        return error;
    }

    /** Peak level after processing, in dBFS. */
    public double getLevel() {
        return level;
    }

    /** If the microphone is currently sending (or would send, while testing). */
    public boolean isActive() {
        return active;
    }

    public boolean isWhispering() {
        return whispering;
    }

    /** If automatic voice detection hears speech right now. */
    public boolean isSpeech() {
        return speech;
    }

    @Override
    public void run() {
        ClientManager mgr = ClientManager.INSTANCE;
        if (TestHooks.microphone != null) {
            runGenerated(mgr);
            return;
        }
        AudioDevices.Capture capture = null;
        while (running && capture == null) {
            try {
                capture = AudioDevices.openMicrophone(mgr.config().microphone.get());
                error = null;
            } catch (LineUnavailableException | RuntimeException e) {
                if (error == null) {
                    VoiceChatMod.LOGGER.error("Failed to open the microphone", e);
                }
                error = String.valueOf(e.getMessage());
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        }
        if (capture == null) {
            return;
        }
        opened = true;
        int channels = capture.stereo ? 2 : 1;
        byte[] raw = new byte[AudioUtils.FRAME_SIZE * 2 * channels];
        short[] samples = new short[AudioUtils.FRAME_SIZE * channels];
        try {
            while (running) {
                int read = 0;
                while (read < raw.length && running) {
                    int r = capture.line.read(raw, read, raw.length - read);
                    if (r <= 0) {
                        Thread.sleep(2L);
                        continue;
                    }
                    read += r;
                }
                if (!running) {
                    break;
                }
                AudioUtils.bytesToShortsLE(raw, raw.length, samples);
                short[] frame = new short[AudioUtils.FRAME_SIZE];
                if (channels == 2) {
                    for (int i = 0; i < AudioUtils.FRAME_SIZE; i++) {
                        frame[i] = (short) ((samples[i * 2] + samples[i * 2 + 1]) / 2);
                    }
                } else {
                    System.arraycopy(samples, 0, frame, 0, AudioUtils.FRAME_SIZE);
                }
                processFrame(mgr, frame);
            }
        } catch (InterruptedException ignored) {
        } catch (RuntimeException e) {
            VoiceChatMod.LOGGER.error("Microphone error", e);
            error = e.getMessage();
        } finally {
            opened = false;
            capture.line.stop();
            capture.line.close();
        }
    }

    /** The in-game test feeds a generated signal instead of a real microphone, at the same 20 ms pace. */
    private void runGenerated(ClientManager mgr) {
        opened = true;
        long next = System.nanoTime();
        try {
            while (running) {
                java.util.function.Supplier<short[]> source = TestHooks.microphone;
                if (source == null) {
                    break;
                }
                processFrame(mgr, source.get());
                next += 20_000_000L;
                long sleep = next - System.nanoTime();
                if (sleep > 0) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            opened = false;
        }
    }

    private void processFrame(ClientManager mgr, short[] frame) {
        ClientConfig cfg = mgr.config();
        if (cfg.denoiser.get()) {
            denoiser.process(frame);
        }
        if (cfg.automaticGainControl.get()) {
            agc.process(frame);
        } else {
            AudioUtils.applyGain(frame, (float) AudioUtils.dbToMultiplier(cfg.microphoneGain.get()));
        }
        double lvl = AudioUtils.peakDb(frame);
        level = lvl;

        boolean whisperKey = mgr.isWhisperKeyDown();
        boolean wanted;
        int delay;
        if (cfg.microphoneActivationType.get() == MicrophoneActivationType.PTT) {
            wanted = mgr.isPttKeyDown() || whisperKey;
            delay = cfg.pttDeactivationDelay.get();
        } else {
            boolean isSpeech;
            if (cfg.voiceActivityDetection.get()) {
                isSpeech = vad.isSpeech(frame);
            } else {
                // 0 dB deactivates the microphone
                isSpeech = cfg.voiceActivationThreshold.get() < 0D && lvl > cfg.voiceActivationThreshold.get();
            }
            speech = isSpeech;
            wanted = isSpeech;
            delay = cfg.voiceDeactivationDelay.get();
        }
        if (wanted) {
            holdFrames = delay;
        } else if (holdFrames > 0) {
            holdFrames--;
            wanted = true;
        }
        boolean testing = mgr.isMicTesting();
        boolean blocked = mgr.isDisabled() || (cfg.microphoneActivationType.get() == MicrophoneActivationType.VOICE && mgr.isMuted());
        if (testing) {
            boolean ptt = cfg.microphoneActivationType.get() == MicrophoneActivationType.PTT;
            active = ptt || wanted;
            whispering = false;
            if (active) {
                AudioOutput out = mgr.getOutput();
                if (out != null) {
                    out.addLoopback(frame);
                }
            }
            endSentence(mgr);
            return;
        }
        boolean send = wanted && !blocked;
        active = send;
        whispering = send && whisperKey;
        ClientVoiceChat session = mgr.getSession();
        if (session == null || !session.isConnected()) {
            wasSending = false;
            return;
        }
        if (send) {
            OpusCodec.Encoder enc = getEncoder(session);
            byte[] data = enc.encode(frame);
            session.sendMic(sequence++, whisperKey, data);
            wasSending = true;
            TalkCache.update(mgr.getOwnId(), whisperKey);
            AudioRecorder rec = session.getRecorder();
            if (rec != null) {
                rec.addFrame(mgr.getOwnId(), frame, 1F, 1F);
            }
        } else {
            endSentence(mgr);
        }
    }

    private void endSentence(ClientManager mgr) {
        if (!wasSending) {
            return;
        }
        wasSending = false;
        ClientVoiceChat session = mgr.getSession();
        if (session != null) {
            session.sendMic(sequence++, false, new byte[0]);
        }
        if (encoder != null) {
            encoder.reset();
        }
    }

    private OpusCodec.Encoder getEncoder(ClientVoiceChat session) {
        Codec codec = session.getData().codec;
        if (encoder == null || encoderCodec != codec) {
            encoder = new OpusCodec.Encoder(codec, session.getData().mtuSize);
            encoderCodec = codec;
        }
        return encoder;
    }
}
