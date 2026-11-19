package com.github.flurben.voicechat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.github.flurben.voicechat.audio.AudioUtils;
import com.github.flurben.voicechat.audio.AutomaticGainControl;
import com.github.flurben.voicechat.audio.Denoiser;
import com.github.flurben.voicechat.audio.OpusCodec;
import com.github.flurben.voicechat.audio.VoiceActivityDetector;
import com.github.flurben.voicechat.config.PropertiesConfig;
import com.github.flurben.voicechat.voice.client.AudioChannel;
import com.github.flurben.voicechat.voice.common.Codec;
import com.github.flurben.voicechat.voice.common.VoiceCrypto;
import com.github.flurben.voicechat.voice.common.VoiceMessage;

class AudioPipelineTest {

    private static short[] tone(double freq, double amplitude, int frameIndex) {
        short[] f = new short[AudioUtils.FRAME_SIZE];
        for (int i = 0; i < f.length; i++) {
            double t = (frameIndex * AudioUtils.FRAME_SIZE + i) / (double) AudioUtils.SAMPLE_RATE;
            f[i] = (short) (Math.sin(2 * Math.PI * freq * t) * amplitude);
        }
        return f;
    }

    /** A voice like signal: harmonics of 150 Hz, amplitude modulated like syllables. */
    private static short[] voice(double amplitude, int frameIndex) {
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

    private static short[] noise(Random r, double amplitude) {
        short[] f = new short[AudioUtils.FRAME_SIZE];
        for (int i = 0; i < f.length; i++) {
            f[i] = (short) (r.nextGaussian() * amplitude);
        }
        return f;
    }

    @Test
    void cryptoRoundTripAndTamperDetection() throws Exception {
        byte[] key = VoiceCrypto.generateKey();
        byte[] plain = "hello voice".getBytes("UTF-8");
        byte[] enc = VoiceCrypto.encrypt(key, plain, 0, plain.length);
        assertArrayEquals(plain, VoiceCrypto.decrypt(key, enc, 0, enc.length));
        enc[enc.length - 1] ^= 1;
        byte[] tampered = enc;
        assertThrows(Exception.class, () -> VoiceCrypto.decrypt(key, tampered, 0, tampered.length));
        byte[] other = VoiceCrypto.generateKey();
        byte[] enc2 = VoiceCrypto.encrypt(key, plain, 0, plain.length);
        assertThrows(Exception.class, () -> VoiceCrypto.decrypt(other, enc2, 0, enc2.length));
    }

    @Test
    void voiceMessagesRoundTrip() throws Exception {
        UUID player = UUID.randomUUID();
        byte[] key = VoiceCrypto.generateKey();
        byte[] opus = {1, 2, 3, 4, 5};
        byte[] fromClient = VoiceMessage.encodeFromClient(player, key, new VoiceMessage.Mic(42L, true, opus));
        assertEquals(player, VoiceMessage.readClientSender(fromClient, fromClient.length));
        VoiceMessage.Mic mic = (VoiceMessage.Mic) VoiceMessage.decodeFromClient(key, fromClient, fromClient.length);
        assertEquals(42L, mic.sequence);
        assertTrue(mic.whispering);
        assertArrayEquals(opus, mic.data);

        VoiceMessage.Sound s = new VoiceMessage.Sound(VoiceMessage.PLAYER_SOUND, player, 7L, false, 48F, 1.5, 64.62, -3.25, opus);
        byte[] fromServer = VoiceMessage.encodeFromServer(key, s);
        VoiceMessage.Sound back = (VoiceMessage.Sound) VoiceMessage.decodeFromServer(key, fromServer, fromServer.length);
        assertEquals(player, back.sender);
        assertEquals(7L, back.sequence);
        assertEquals(48F, back.distance);
        assertEquals(64.62, back.y, 1e-9);
        assertFalse(back.isGroup());

        assertNull(VoiceMessage.readClientSender(new byte[] {0, 1, 2}, 3));
        byte[] auth = VoiceMessage.encodeFromClient(player, key, new VoiceMessage.Authenticate());
        assertTrue(VoiceMessage.decodeFromClient(key, auth, auth.length) instanceof VoiceMessage.Authenticate);
    }

    @Test
    void opusRoundTripKeepsTheSignal() {
        for (Codec codec : Codec.values()) {
            OpusCodec.Encoder enc = new OpusCodec.Encoder(codec, 1024);
            OpusCodec.Decoder dec = new OpusCodec.Decoder();
            double inEnergy = 0, outEnergy = 0;
            for (int i = 0; i < 50; i++) {
                short[] in = voice(8000, i);
                byte[] packet = enc.encode(in);
                assertTrue(packet.length > 0 && packet.length < 1024, "packet size " + packet.length);
                short[] out = dec.decode(packet);
                assertEquals(AudioUtils.FRAME_SIZE, out.length);
                if (i >= 10) {
                    inEnergy += AudioUtils.rms(in);
                    outEnergy += AudioUtils.rms(out);
                }
            }
            double ratioDb = 20 * Math.log10(outEnergy / inEnergy);
            assertTrue(Math.abs(ratioDb) < 3, codec + " level changed by " + ratioDb + " dB");
            // Loss concealment and forward error correction must not throw
            assertEquals(AudioUtils.FRAME_SIZE, dec.decode(null).length);
            assertEquals(AudioUtils.FRAME_SIZE, dec.decodeFec(enc.encode(voice(8000, 60))).length);
        }
    }

    @Test
    void denoiserRemovesSteadyNoiseAndKeepsVoice() {
        Random r = new Random(1);
        Denoiser d = new Denoiser();
        // Learn the noise
        double noiseIn = 0, noiseOut = 0;
        for (int i = 0; i < 200; i++) {
            short[] f = noise(r, 600);
            double before = AudioUtils.rms(f);
            d.process(f);
            for (short s : f) {
                assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
            }
            if (i >= 100) {
                noiseIn += before;
                noiseOut += AudioUtils.rms(f);
            }
        }
        double reduction = 20 * Math.log10(noiseOut / noiseIn);
        assertTrue(reduction < -10, "noise only reduced by " + reduction + " dB");
        // Voice over the same noise keeps most of its level
        double vIn = 0, vOut = 0;
        for (int i = 0; i < 100; i++) {
            short[] v = voice(8000, i);
            short[] f = noise(r, 600);
            for (int k = 0; k < f.length; k++) {
                f[k] = AudioUtils.clip(f[k] + v[k]);
            }
            double before = AudioUtils.rms(f);
            d.process(f);
            if (i >= 20) {
                vIn += before;
                vOut += AudioUtils.rms(f);
            }
        }
        double voiceChange = 20 * Math.log10(vOut / vIn);
        assertTrue(voiceChange > -3, "voice lost " + voiceChange + " dB");
    }

    @Test
    void agcRaisesQuietAndLowersLoudWithoutClipping() {
        AutomaticGainControl agc = new AutomaticGainControl();
        double last = 0;
        for (int i = 0; i < 500; i++) {
            short[] f = voice(300, i);
            agc.process(f);
            last = AudioUtils.rmsDb(f);
        }
        assertTrue(last > -30, "quiet voice only reached " + last + " dB");
        agc.reset();
        for (int i = 0; i < 200; i++) {
            short[] f = voice(30000, i);
            agc.process(f);
            assertTrue(AudioUtils.peak(f) < 32767, "clipped");
            last = AudioUtils.rmsDb(f);
        }
        assertTrue(last < -10, "loud voice still at " + last + " dB");
    }

    @Test
    void voiceActivityDetection() {
        Random r = new Random(2);
        VoiceActivityDetector vad = new VoiceActivityDetector();
        int falsePositives = 0;
        for (int i = 0; i < 150; i++) {
            if (vad.isSpeech(noise(r, 40))) {
                falsePositives++;
            }
        }
        assertTrue(falsePositives < 5, falsePositives + " noise frames detected as speech");
        int detected = 0;
        for (int i = 0; i < 50; i++) {
            short[] v = voice(6000, i);
            short[] n = noise(r, 40);
            for (int k = 0; k < v.length; k++) {
                v[k] = AudioUtils.clip(v[k] + n[k]);
            }
            if (vad.isSpeech(v)) {
                detected++;
            }
        }
        assertTrue(detected > 45, "only " + detected + " of 50 voice frames detected");
    }

    private static VoiceMessage.Sound sound(UUID sender, long seq, byte[] data) {
        return new VoiceMessage.Sound(VoiceMessage.PLAYER_SOUND, sender, seq, false, 48F, 0, 0, 0, data);
    }

    @Test
    void jitterBufferOrdersBuffersAndConceals() throws InterruptedException {
        UUID sender = UUID.randomUUID();
        OpusCodec.Encoder enc = new OpusCodec.Encoder(Codec.VOIP, 1024);
        byte[][] packets = new byte[20][];
        for (int i = 0; i < packets.length; i++) {
            packets[i] = enc.encode(voice(8000, i));
        }
        AudioChannel ch = new AudioChannel(sender);
        assertNull(ch.nextFrame(3, 3), "nothing buffered yet");
        ch.add(sound(sender, 100, packets[0]));
        ch.add(sound(sender, 102, packets[2]));
        assertNull(ch.nextFrame(3, 3), "waits for the buffer to fill");
        ch.add(sound(sender, 101, packets[1]));
        assertNotNull(ch.nextFrame(3, 3), "plays once 3 packets are there");
        assertNotNull(ch.nextFrame(3, 3));
        assertNotNull(ch.nextFrame(3, 3));
        // 103 is lost, 104 arrives: recovered from FEC of 104
        ch.add(sound(sender, 104, packets[4]));
        assertNotNull(ch.nextFrame(3, 3));
        assertNotNull(ch.nextFrame(3, 3));
        // A late duplicate of something played is dropped
        ch.add(sound(sender, 101, packets[1]));
        // End of the sentence
        ch.add(sound(sender, 105, new byte[0]));
        assertNull(ch.nextFrame(3, 3));
        assertFalse(ch.isPlaying());
        // Nothing comes anymore: stays silent
        assertNull(ch.nextFrame(3, 3));
        // The sender rejoined and counts from 0 again
        Thread.sleep(600);
        ch.add(sound(sender, 0, packets[5]));
        ch.add(sound(sender, 1, packets[6]));
        ch.add(sound(sender, 2, packets[7]));
        assertNotNull(ch.nextFrame(3, 3));
    }

    @Test
    void jitterBufferStopsAfterSilence() {
        UUID sender = UUID.randomUUID();
        OpusCodec.Encoder enc = new OpusCodec.Encoder(Codec.VOIP, 1024);
        AudioChannel ch = new AudioChannel(sender);
        for (int i = 0; i < 3; i++) {
            ch.add(sound(sender, i, enc.encode(voice(8000, i))));
        }
        for (int i = 0; i < 3; i++) {
            assertNotNull(ch.nextFrame(3, 3));
        }
        // End packet lost: a few concealed frames, then silence
        int concealed = 0;
        while (ch.nextFrame(3, 3) != null) {
            concealed++;
            assertTrue(concealed < 10);
        }
        assertTrue(concealed <= 3);
        assertFalse(ch.isPlaying());
    }

    @Test
    void propertiesConfigRoundTrip() throws Exception {
        File f = Files.createTempFile("svc", ".properties").toFile();
        f.delete();
        PropertiesConfig c = new PropertiesConfig(f, "test");
        PropertiesConfig.IntEntry i = c.intEntry("port", 24454, -1, 65535, "port");
        PropertiesConfig.StringEntry s = c.stringEntry("name", "", "a name\nwith two lines");
        PropertiesConfig.EnumEntry<Codec> e = c.enumEntry("codec", Codec.VOIP, "codec");
        c.save();
        i.set(70000);
        assertEquals(65535, i.get());
        i.set(1234);
        s.set("Mic: Realtek = #1 é");
        e.set(Codec.AUDIO);
        c.save();
        PropertiesConfig c2 = new PropertiesConfig(f, "test");
        assertEquals(1234, c2.intEntry("port", 24454, -1, 65535, "").get());
        assertEquals("Mic: Realtek = #1 é", c2.stringEntry("name", "", "").get());
        assertEquals(Codec.AUDIO, c2.enumEntry("codec", Codec.VOIP, "").get());
        f.delete();
    }
}
