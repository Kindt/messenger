package com.avandocmsg.messenger.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DesktopCallAudioTest {

    @Test
    void pcm16leRoundTripKeepsSampleValues() {
        short[] samples = {0, 1, -1, 12345, -23456, Short.MAX_VALUE, Short.MIN_VALUE};
        var bytes = DesktopCallAudio.pcm16le(samples);
        assertEquals(samples.length * 2, bytes.length);
        assertArrayEquals(samples, DesktopCallAudio.pcm16leToShorts(bytes));
    }

    @Test
    void pcmuFrameSurvivesDesktopPcmBridge() {
        var pcm = new short[DesktopCallAudio.FRAME_SAMPLES];
        for (var i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (i * 80);
        }
        var decoded = DesktopCallAudio.pcm16leToShorts(
            DesktopCallAudio.decodeFrame(DesktopCallAudio.encodeFrame(DesktopCallAudio.pcm16le(pcm)))
        );
        assertEquals(pcm.length, decoded.length);
        for (var i = 0; i < pcm.length; i++) {
            var error = Math.abs(pcm[i] - decoded[i]);
            if (error > 260) {
                throw new AssertionError("PCMU quantization too large at " + i + ": " + error);
            }
        }
    }
}
