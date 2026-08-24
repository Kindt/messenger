package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient;
import com.avandocmsg.messenger.media.PcmuCodec;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/** Java Sound capture/playback for in-process PCMU calls (8 kHz / 16-bit / mono). */
public final class DesktopCallAudio implements AutoCloseable {

    public static final float SAMPLE_RATE = 8_000f;
    public static final int FRAME_SAMPLES = 160;

    private final TargetDataLine microphone;
    private final SourceDataLine speakers;
    private Thread capture;
    private volatile boolean running = true;

    private DesktopCallAudio(TargetDataLine microphone, SourceDataLine speakers, Thread capture) {
        this.microphone = microphone;
        this.speakers = speakers;
        this.capture = capture;
    }

    public static DesktopCallAudio start(InProcessCallClient client) {
        Objects.requireNonNull(client, "client");
        var format = pcm16Mono();
        var speakers = openSpeakers(format);
        var microphone = openMicrophone(format);
        if (speakers != null) {
            client.onPcmu(payload -> play(speakers, decodeFrame(payload)));
        }
        Thread capture = null;
        var audio = new DesktopCallAudio(microphone, speakers, null);
        if (microphone != null) {
            audio.capture = Thread.ofVirtual().name("korus-desktop-mic").start(() -> audio.captureLoop(client, microphone));
        }
        return audio;
    }

    public boolean captureEnabled() {
        return microphone != null;
    }

    public boolean playbackEnabled() {
        return speakers != null;
    }

    static AudioFormat pcm16Mono() {
        return new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    static byte[] pcm16le(short[] samples) {
        if (samples == null) {
            throw new IllegalArgumentException("samples required");
        }
        var bytes = new byte[samples.length * 2];
        for (var i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) samples[i];
            bytes[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return bytes;
    }

    static short[] pcm16leToShorts(byte[] bytes) {
        if (bytes == null || (bytes.length & 1) != 0) {
            throw new IllegalArgumentException("pcm16le required");
        }
        var samples = new short[bytes.length / 2];
        for (var i = 0; i < samples.length; i++) {
            samples[i] = (short) ((bytes[i * 2] & 0xff) | (bytes[i * 2 + 1] << 8));
        }
        return samples;
    }

    static byte[] encodeFrame(byte[] pcm16le) {
        return PcmuCodec.encode(pcm16leToShorts(pcm16le));
    }

    static byte[] decodeFrame(byte[] pcmu) {
        return pcm16le(PcmuCodec.decode(pcmu));
    }

    @Override
    public void close() {
        running = false;
        if (capture != null) {
            capture.interrupt();
        }
        closeLine(microphone);
        closeLine(speakers);
    }

    private void captureLoop(InProcessCallClient client, TargetDataLine microphone) {
        var pcm = new byte[FRAME_SAMPLES * 2];
        while (running && !Thread.currentThread().isInterrupted() && client.mediaReady()) {
            var read = microphone.read(pcm, 0, pcm.length);
            if (read < pcm.length) {
                continue;
            }
            client.sendPcmu(encodeFrame(pcm));
        }
    }

    private static void play(SourceDataLine speakers, byte[] pcm16le) {
        if (pcm16le != null && pcm16le.length > 0) {
            speakers.write(pcm16le, 0, pcm16le.length);
        }
    }

    private static TargetDataLine openMicrophone(AudioFormat format) {
        try {
            var info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return null;
            }
            var line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, FRAME_SAMPLES * 8);
            line.start();
            return line;
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static SourceDataLine openSpeakers(AudioFormat format) {
        try {
            var info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return null;
            }
            var line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, FRAME_SAMPLES * 8);
            line.start();
            return line;
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static void closeLine(AutoCloseable line) {
        if (line == null) {
            return;
        }
        try {
            if (line instanceof DataLine dataLine) {
                dataLine.stop();
                dataLine.flush();
            }
            line.close();
        } catch (Exception ignored) {
            // Device teardown races are expected.
        }
    }
}
