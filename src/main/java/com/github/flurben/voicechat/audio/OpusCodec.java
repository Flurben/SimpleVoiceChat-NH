package com.github.flurben.voicechat.audio;

import org.concentus.OpusApplication;
import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.concentus.OpusException;
import org.concentus.OpusSignal;

import com.github.flurben.voicechat.voice.common.Codec;

/** Opus encoding and decoding through Concentus, a pure Java port of libopus, so no natives are needed. */
public final class OpusCodec {

    private OpusCodec() {}

    public static class Encoder {
        private final OpusEncoder encoder;
        private final byte[] buffer;

        public Encoder(Codec codec, int maxPayload) {
            try {
                OpusApplication app;
                switch (codec) {
                    case AUDIO:
                        app = OpusApplication.OPUS_APPLICATION_AUDIO;
                        break;
                    case RESTRICTED_LOWDELAY:
                        app = OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY;
                        break;
                    default:
                        app = OpusApplication.OPUS_APPLICATION_VOIP;
                }
                encoder = new OpusEncoder(AudioUtils.SAMPLE_RATE, 1, app);
                if (app == OpusApplication.OPUS_APPLICATION_VOIP) {
                    encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
                }
                encoder.setUseInbandFEC(true);
                encoder.setPacketLossPercent(5);
                encoder.setComplexity(5);
            } catch (OpusException e) {
                throw new IllegalStateException("Failed to create the Opus encoder", e);
            }
            buffer = new byte[Math.max(256, maxPayload)];
        }

        public byte[] encode(short[] frame) {
            try {
                int len = encoder.encode(frame, 0, AudioUtils.FRAME_SIZE, buffer, 0, buffer.length);
                byte[] out = new byte[len];
                System.arraycopy(buffer, 0, out, 0, len);
                return out;
            } catch (OpusException e) {
                return new byte[0];
            }
        }

        public void reset() {
            encoder.resetState();
        }
    }

    public static class Decoder {
        private final OpusDecoder decoder;

        public Decoder() {
            try {
                decoder = new OpusDecoder(AudioUtils.SAMPLE_RATE, 1);
            } catch (OpusException e) {
                throw new IllegalStateException("Failed to create the Opus decoder", e);
            }
        }

        /** Decodes a packet, or conceals a lost one when data is null. */
        public short[] decode(byte[] data) {
            short[] out = new short[AudioUtils.FRAME_SIZE];
            try {
                if (data == null || data.length == 0) {
                    decoder.decode(null, 0, 0, out, 0, AudioUtils.FRAME_SIZE, false);
                } else {
                    decoder.decode(data, 0, data.length, out, 0, AudioUtils.FRAME_SIZE, false);
                }
            } catch (OpusException | RuntimeException e) {
                return new short[AudioUtils.FRAME_SIZE];
            }
            return out;
        }

        /** Recovers the previous, lost frame from the forward error correction data in the next packet. */
        public short[] decodeFec(byte[] next) {
            short[] out = new short[AudioUtils.FRAME_SIZE];
            try {
                decoder.decode(next, 0, next.length, out, 0, AudioUtils.FRAME_SIZE, true);
            } catch (OpusException | RuntimeException e) {
                return decode(null);
            }
            return out;
        }

        public void reset() {
            decoder.resetState();
        }
    }
}
