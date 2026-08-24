package com.avandocmsg.messenger.media;

public final class PcmuCodec {

    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;
    private static final int[] ENCODE_EXP = {
        0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

    private PcmuCodec() {}

    public static byte[] encode(short[] pcm) {
        if (pcm == null) {
            throw new IllegalArgumentException("pcm required");
        }
        var encoded = new byte[pcm.length];
        for (var i = 0; i < pcm.length; i++) {
            encoded[i] = encodeSample(pcm[i]);
        }
        return encoded;
    }

    public static short[] decode(byte[] pcmu) {
        if (pcmu == null) {
            throw new IllegalArgumentException("pcmu required");
        }
        var pcm = new short[pcmu.length];
        for (var i = 0; i < pcmu.length; i++) {
            pcm[i] = decodeSample(pcmu[i]);
        }
        return pcm;
    }

    private static byte encodeSample(short sample) {
        var sign = (sample >> 8) & 0x80;
        if (sign != 0) {
            sample = (short) -sample;
        }
        if (sample > CLIP) {
            sample = CLIP;
        }
        sample = (short) (sample + BIAS);
        var exponent = ENCODE_EXP[(sample >> 7) & 0xff];
        var mantissa = (sample >> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    private static short decodeSample(byte value) {
        var sample = ~Byte.toUnsignedInt(value);
        var sign = sample & 0x80;
        var exponent = (sample >> 4) & 0x07;
        var mantissa = sample & 0x0f;
        var magnitude = ((mantissa << 3) + BIAS) << exponent;
        magnitude -= BIAS;
        return (short) (sign != 0 ? -magnitude : magnitude);
    }
}
