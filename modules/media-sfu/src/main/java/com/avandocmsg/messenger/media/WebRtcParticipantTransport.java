package com.avandocmsg.messenger.media;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;

public final class WebRtcParticipantTransport implements AutoCloseable {

    private static final int MAX_DATAGRAM_BYTES = 65_535;

    private final DatagramSocket socket;
    private final String localIceUfrag;
    private final String localIcePassword;
    private final String remoteIceUfrag;
    private final String remoteDtlsFingerprint;
    private final DtlsIdentity identity;
    private final SecureRandom random;
    private final Consumer<RtpPacket> packetConsumer;
    private final Consumer<byte[]> rtcpConsumer;
    private final Map<Long, SequenceState> inboundSequences = new ConcurrentHashMap<>();
    private final Map<Long, SequenceState> outboundSequences = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile SocketAddress remoteAddress;
    private volatile AesCmSrtpCipher inboundCipher;
    private volatile AesCmSrtpCipher outboundCipher;
    private volatile AesCmSrtcpCipher inboundRtcpCipher;
    private volatile AesCmSrtcpCipher outboundRtcpCipher;
    private final AtomicInteger outboundRtcpIndex = new AtomicInteger();
    private final SrtcpReplayIndexTracker inboundRtcpReplay = new SrtcpReplayIndexTracker();
    private volatile DTLSTransport dtlsTransport;
    private Thread worker;

    public WebRtcParticipantTransport(
        DatagramSocket socket,
        String localIceUfrag,
        String localIcePassword,
        String remoteIceUfrag,
        String remoteDtlsFingerprint,
        DtlsIdentity identity,
        SecureRandom random,
        Consumer<RtpPacket> packetConsumer,
        Consumer<byte[]> rtcpConsumer
    ) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.localIceUfrag = required(localIceUfrag, "localIceUfrag");
        this.localIcePassword = required(localIcePassword, "localIcePassword");
        this.remoteIceUfrag = required(remoteIceUfrag, "remoteIceUfrag");
        this.remoteDtlsFingerprint = required(remoteDtlsFingerprint, "remoteDtlsFingerprint");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.random = Objects.requireNonNull(random, "random");
        this.packetConsumer = Objects.requireNonNull(packetConsumer, "packetConsumer");
        this.rtcpConsumer = Objects.requireNonNull(rtcpConsumer, "rtcpConsumer");
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("korus-webrtc-" + socket.getLocalPort()).start(this::run);
    }

    public int localPort() {
        return socket.getLocalPort();
    }

    public boolean mediaReady() {
        return inboundCipher != null
            && outboundCipher != null
            && inboundRtcpCipher != null
            && outboundRtcpCipher != null;
    }

    public void sendRtp(RtpPacket packet) {
        var cipher = outboundCipher;
        var target = remoteAddress;
        if (!running || cipher == null || target == null) {
            return;
        }
        var plain = packet.wireBytes();
        var sequence = sequenceNumber(plain);
        var state = outboundSequences.computeIfAbsent(packet.ssrc(), ignored -> new SequenceState());
        var roc = state.rolloverCounter(sequence);
        var protectedPacket = cipher.protect(plain, roc);
        state.commit(sequence, roc);
        try {
            socket.send(new DatagramPacket(protectedPacket, protectedPacket.length, target));
        } catch (IOException ignored) {
            // Transport teardown races are expected during hangup.
        }
    }

    public void sendRtcp(byte[] packet) {
        var cipher = outboundRtcpCipher;
        var target = remoteAddress;
        if (!running || cipher == null || target == null) {
            return;
        }
        var index = outboundRtcpIndex.getAndIncrement();
        if (index < 0) {
            close();
            return;
        }
        var protectedPacket = cipher.protect(packet, index);
        try {
            socket.send(new DatagramPacket(protectedPacket, protectedPacket.length, target));
        } catch (IOException ignored) {
            // Transport teardown races are expected during hangup.
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        socket.close();
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void run() {
        try {
            var peer = new WebRtcDtlsServer(identity, random, remoteDtlsFingerprint);
            var iceTransport = new IceAwareDatagramTransport();
            dtlsTransport = new DTLSServerProtocol().accept(peer, iceTransport);
            var keys = peer.exportSrtpKeyMaterial();
            inboundCipher = new AesCmSrtpCipher(
                SrtpSessionKeys.derive(keys.clientWriteKey(), keys.clientWriteSalt())
            );
            outboundCipher = new AesCmSrtpCipher(
                SrtpSessionKeys.derive(keys.serverWriteKey(), keys.serverWriteSalt())
            );
            inboundRtcpCipher = new AesCmSrtcpCipher(
                SrtpSessionKeys.deriveRtcp(keys.clientWriteKey(), keys.clientWriteSalt())
            );
            outboundRtcpCipher = new AesCmSrtcpCipher(
                SrtpSessionKeys.deriveRtcp(keys.serverWriteKey(), keys.serverWriteSalt())
            );
            receiveMedia();
        } catch (IOException | RuntimeException error) {
            if (running && !(error instanceof SocketException)) {
                // The owner exposes readiness and can replace a failed transport on a new offer.
                inboundCipher = null;
                outboundCipher = null;
                inboundRtcpCipher = null;
                outboundRtcpCipher = null;
            }
        } finally {
            running = false;
        }
    }

    private void receiveMedia() throws IOException {
        var buffer = new byte[MAX_DATAGRAM_BYTES];
        socket.setSoTimeout(1_000);
        while (running) {
            var datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
            } catch (SocketTimeoutException ignored) {
                continue;
            }
            if (!Objects.equals(remoteAddress, datagram.getSocketAddress())) {
                continue;
            }
            var length = datagram.getLength();
            if (StunMessage.looksLike(datagram.getData(), length)) {
                respondToStun(datagram);
                continue;
            }
            if (length < 22 || (datagram.getData()[0] & 0xc0) != 0x80) {
                continue;
            }
            var protectedPacket = Arrays.copyOf(datagram.getData(), length);
            if (isRtcp(protectedPacket)) {
                receiveRtcp(protectedPacket);
                continue;
            }
            var ssrc = unsignedInt(protectedPacket, 8);
            var sequence = sequenceNumber(protectedPacket);
            var state = inboundSequences.computeIfAbsent(ssrc, ignored -> new SequenceState());
            var roc = state.rolloverCounter(sequence);
            try {
                var plain = inboundCipher.unprotect(protectedPacket, roc);
                state.commit(sequence, roc);
                packetConsumer.accept(RtpPacket.parse(plain));
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Invalid, replayed, or non-RTP packets are discarded.
            }
        }
    }

    private void receiveRtcp(byte[] protectedPacket) {
        try {
            var unprotected = inboundRtcpCipher.unprotect(protectedPacket);
            var plain = unprotected.bytes();
            if (!inboundRtcpReplay.accept(unsignedInt(plain, 4), unprotected.index())) {
                return;
            }
            rtcpConsumer.accept(plain);
        } catch (IllegalArgumentException | SecurityException ignored) {
            // Invalid and replayed SRTCP packets are discarded.
        }
    }

    private boolean respondToStun(DatagramPacket datagram) throws IOException {
        var requestBytes = Arrays.copyOf(datagram.getData(), datagram.getLength());
        var request = StunMessage.parse(requestBytes);
        if (request.type() != StunMessage.BINDING_REQUEST
            || !(localIceUfrag + ":" + remoteIceUfrag).equals(request.username())
            || !request.verifyMessageIntegrity(localIcePassword)
            || !request.verifyFingerprint()) {
            return false;
        }
        if (remoteAddress == null || request.useCandidate()) {
            remoteAddress = datagram.getSocketAddress();
        }
        var response = request.bindingSuccess(
            new InetSocketAddress(datagram.getAddress(), datagram.getPort()),
            localIcePassword,
            "Korus media"
        );
        socket.send(new DatagramPacket(response, response.length, datagram.getSocketAddress()));
        return true;
    }

    private final class IceAwareDatagramTransport implements DatagramTransport {

        @Override
        public int getReceiveLimit() {
            return 16_384;
        }

        @Override
        public int getSendLimit() {
            return 16_384;
        }

        @Override
        public int receive(byte[] buffer, int offset, int length, int waitMillis) throws IOException {
            socket.setSoTimeout(waitMillis);
            while (running) {
                var datagram = new DatagramPacket(new byte[Math.min(length, MAX_DATAGRAM_BYTES)], Math.min(length, MAX_DATAGRAM_BYTES));
                try {
                    socket.receive(datagram);
                } catch (SocketTimeoutException timeout) {
                    return -1;
                }
                if (StunMessage.looksLike(datagram.getData(), datagram.getLength())) {
                    respondToStun(datagram);
                    continue;
                }
                if (remoteAddress == null || !remoteAddress.equals(datagram.getSocketAddress())) {
                    continue;
                }
                var count = Math.min(datagram.getLength(), length);
                System.arraycopy(datagram.getData(), 0, buffer, offset, count);
                return count;
            }
            return -1;
        }

        @Override
        public void send(byte[] buffer, int offset, int length) throws IOException {
            var target = remoteAddress;
            if (target == null) {
                throw new IOException("ICE remote address is not nominated");
            }
            socket.send(new DatagramPacket(buffer, offset, length, target));
        }

        @Override
        public void close() {
            WebRtcParticipantTransport.this.close();
        }
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

    private static int sequenceNumber(byte[] packet) {
        return (Byte.toUnsignedInt(packet[2]) << 8) | Byte.toUnsignedInt(packet[3]);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) Byte.toUnsignedInt(bytes[offset]) << 24)
            | ((long) Byte.toUnsignedInt(bytes[offset + 1]) << 16)
            | ((long) Byte.toUnsignedInt(bytes[offset + 2]) << 8)
            | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private static boolean isRtcp(byte[] packet) {
        var packetType = Byte.toUnsignedInt(packet[1]);
        return packetType >= 192 && packetType <= 223;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value;
    }
}
