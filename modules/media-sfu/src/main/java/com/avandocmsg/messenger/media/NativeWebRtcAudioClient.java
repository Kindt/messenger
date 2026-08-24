package com.avandocmsg.messenger.media;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.UDPTransport;

public final class NativeWebRtcAudioClient implements AutoCloseable {

    private static final int MAX_DATAGRAM_BYTES = 65_535;
    private static final int PCMU_PAYLOAD_TYPE = 0;

    private final DtlsIdentity identity;
    private final SecureRandom random;
    private final String iceUfrag;
    private final String icePassword;
    private final long ssrc;
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger timestamp = new AtomicInteger();
    private final Object sendLock = new Object();
    private volatile Consumer<byte[]> pcmuListener = ignored -> {};
    private volatile boolean running;
    private volatile boolean mediaReady;
    private DatagramSocket socket;
    private InetSocketAddress remote;
    private AesCmSrtpCipher outboundCipher;
    private AesCmSrtpCipher inboundCipher;
    private SequenceState outboundSequence = new SequenceState();
    private SequenceState inboundSequence = new SequenceState();
    private Thread receiver;

    private NativeWebRtcAudioClient(DtlsIdentity identity, SecureRandom random) {
        this.identity = identity;
        this.random = random;
        this.iceUfrag = randomToken(8);
        this.icePassword = randomToken(24);
        this.ssrc = random.nextInt() & 0xffffffffL;
    }

    public static NativeWebRtcAudioClient create() {
        var random = new SecureRandom();
        return new NativeWebRtcAudioClient(DtlsIdentity.generate(Clock.systemUTC(), random), random);
    }

    public String fingerprint() {
        return identity.sha256Fingerprint();
    }

    public String createOffer() {
        return """
            v=0\r
            o=- %d 2 IN IP4 127.0.0.1\r
            s=-\r
            t=0 0\r
            a=group:BUNDLE 0\r
            a=ice-ufrag:%s\r
            a=ice-pwd:%s\r
            a=fingerprint:sha-256 %s\r
            a=setup:actpass\r
            m=audio 9 UDP/TLS/RTP/SAVPF 0\r
            c=IN IP4 0.0.0.0\r
            a=mid:0\r
            a=sendrecv\r
            a=rtcp-mux\r
            a=rtpmap:0 PCMU/8000\r
            """.formatted(Math.abs(random.nextLong()), iceUfrag, icePassword, identity.sha256Fingerprint());
    }

    public void onPcmu(Consumer<byte[]> listener) {
        pcmuListener = listener == null ? ignored -> {} : listener;
    }

    public boolean mediaReady() {
        return mediaReady;
    }

    public void connect(String answerSdp) throws IOException {
        var answer = WebRtcSdpAnswer.parse(answerSdp);
        socket = new DatagramSocket();
        socket.setSoTimeout(1_000);
        remote = answer.candidate();
        nominate(answer);
        socket.connect(remote);
        var client = new WebRtcDtlsClient(identity, random);
        new DTLSClientProtocol().connect(client, new UDPTransport(socket, 1500));
        var keys = client.exportSrtpKeyMaterial();
        outboundCipher = new AesCmSrtpCipher(SrtpSessionKeys.derive(keys.clientWriteKey(), keys.clientWriteSalt()));
        inboundCipher = new AesCmSrtpCipher(SrtpSessionKeys.derive(keys.serverWriteKey(), keys.serverWriteSalt()));
        running = true;
        mediaReady = true;
        receiver = Thread.ofVirtual().name("korus-native-audio-" + socket.getLocalPort()).start(this::receiveLoop);
    }

    public void sendPcmu(byte[] payload) {
        if (!mediaReady || payload == null || payload.length == 0) {
            return;
        }
        var packet = RtpPacket.of(
            PCMU_PAYLOAD_TYPE,
            sequence.getAndUpdate(value -> (value + 1) & 0xffff),
            timestamp.getAndAdd(payload.length) & 0xffffffffL,
            ssrc,
            payload
        );
        var wire = packet.wireBytes();
        var seq = (Byte.toUnsignedInt(wire[2]) << 8) | Byte.toUnsignedInt(wire[3]);
        synchronized (sendLock) {
            var roc = outboundSequence.rolloverCounter(seq);
            var protectedPacket = outboundCipher.protect(wire, roc);
            outboundSequence.commit(seq, roc);
            try {
                socket.send(new DatagramPacket(protectedPacket, protectedPacket.length, remote));
            } catch (IOException ignored) {
                // Hangup races are expected.
            }
        }
    }

    @Override
    public void close() {
        running = false;
        mediaReady = false;
        if (socket != null) {
            socket.close();
        }
        if (receiver != null) {
            receiver.interrupt();
        }
    }

    private void nominate(WebRtcSdpAnswer answer) throws IOException {
        var transactionId = new byte[12];
        var deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            random.nextBytes(transactionId);
            var request = StunMessage.bindingRequest(
                transactionId,
                answer.iceUfrag() + ":" + iceUfrag,
                answer.icePassword()
            );
            socket.send(new DatagramPacket(request, request.length, remote));
            var buffer = new byte[MAX_DATAGRAM_BYTES];
            var datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
            } catch (SocketTimeoutException ignored) {
                continue;
            }
            var wire = Arrays.copyOf(datagram.getData(), datagram.getLength());
            if (!StunMessage.looksLike(wire, wire.length)) {
                continue;
            }
            var response = StunMessage.parse(wire);
            if (response.type() == StunMessage.BINDING_SUCCESS) {
                return;
            }
        }
        throw new IOException("ICE nomination timed out");
    }

    private void receiveLoop() {
        var buffer = new byte[MAX_DATAGRAM_BYTES];
        while (running) {
            var datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
            } catch (SocketTimeoutException ignored) {
                continue;
            } catch (IOException closed) {
                return;
            }
            var length = datagram.getLength();
            if (StunMessage.looksLike(datagram.getData(), length) || length < 22) {
                continue;
            }
            var protectedPacket = Arrays.copyOf(datagram.getData(), length);
            var seq = (Byte.toUnsignedInt(protectedPacket[2]) << 8) | Byte.toUnsignedInt(protectedPacket[3]);
            var roc = inboundSequence.rolloverCounter(seq);
            try {
                var plain = inboundCipher.unprotect(protectedPacket, roc);
                inboundSequence.commit(seq, roc);
                var packet = RtpPacket.parse(plain);
                if (packet.payloadType() == PCMU_PAYLOAD_TYPE) {
                    pcmuListener.accept(packet.payload());
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Invalid or replayed packets are discarded.
            }
        }
    }

    private String randomToken(int byteCount) {
        var bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class SequenceState {
        private int highestSequence = -1;
        private long rolloverCounter;

        private synchronized long rolloverCounter(int sequence) {
            if (highestSequence < 0) {
                return rolloverCounter;
            }
            if (highestSequence >= 0x8000 && sequence < highestSequence - 0x8000) {
                return rolloverCounter + 1;
            }
            if (highestSequence < 0x8000 && sequence > highestSequence + 0x8000 && rolloverCounter > 0) {
                return rolloverCounter - 1;
            }
            return rolloverCounter;
        }

        private synchronized void commit(int sequence, long guessedRoc) {
            var candidate = (guessedRoc << 16) | sequence;
            var highest = highestSequence < 0 ? -1 : (rolloverCounter << 16) | highestSequence;
            if (candidate > highest) {
                rolloverCounter = guessedRoc;
                highestSequence = sequence;
            }
        }
    }
}
