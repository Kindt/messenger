package com.avandocmsg.messenger.media;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EmbeddedWebRtcMediaNode implements AutoCloseable, MediaSignalingProcessor {

    private final InMemoryMediaRoomService rooms;
    private final InetAddress bindAddress;
    private final InetAddress publicAddress;
    private final int portMin;
    private final int portMax;
    private final DtlsIdentity identity;
    private final SecureRandom random = new SecureRandom();
    private final SfuPacketRouter router;
    private final DirectAudioBindings directAudioBindings = new DirectAudioBindings();
    private final Map<TransportKey, WebRtcParticipantTransport> transports = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public EmbeddedWebRtcMediaNode(
        InMemoryMediaRoomService rooms,
        InetAddress bindAddress,
        InetAddress publicAddress,
        int portMin,
        int portMax,
        DtlsIdentity identity
    ) {
        this(rooms, bindAddress, publicAddress, portMin, portMax, 4, identity);
    }

    public EmbeddedWebRtcMediaNode(
        InMemoryMediaRoomService rooms,
        InetAddress bindAddress,
        InetAddress publicAddress,
        int portMin,
        int portMax,
        int lastN,
        DtlsIdentity identity
    ) {
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.publicAddress = Objects.requireNonNull(publicAddress, "publicAddress");
        if ((portMin == 0) != (portMax == 0) || portMin < 0 || portMax < portMin || portMax > 65_535) {
            throw new IllegalArgumentException("invalid UDP media port range");
        }
        this.portMin = portMin;
        this.portMax = portMax;
        this.identity = Objects.requireNonNull(identity, "identity");
        router = new SfuPacketRouter(rooms, lastN);
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("korus-media-cleanup-", 0).factory()
        );
        cleanupExecutor.scheduleWithFixedDelay(this::sweepIdleRooms, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void processPending(UUID sessionId) {
        for (var signal : rooms.drainNodeSignals(sessionId, 256)) {
            switch (signal.type()) {
                case OFFER -> handleOffer(signal);
                case HANGUP -> closeTransport(signal.sessionId(), signal.participantId());
                case ANSWER, ICE, ERROR, PARTICIPANT_LEFT, SESSION_ENDED -> {
                    // The media node is ICE-lite and is the SDP answerer.
                }
            }
        }
    }

    public int activeTransportCount() {
        return transports.size();
    }

    Optional<UUID> directAudioRemoteParticipant(UUID sessionId, UUID participantId) {
        return directAudioBindings.remoteParticipant(sessionId, participantId);
    }

    int directAudioBindingCount(UUID sessionId) {
        return directAudioBindings.bindingCount(sessionId);
    }

    int activeDirectAudioSessionCount() {
        return directAudioBindings.activeSessionCount();
    }

    public int sweepIdleRooms() {
        var removedRoomCount = rooms.removeIdleRooms();
        if (removedRoomCount == 0) {
            return 0;
        }
        var removedSessions = ConcurrentHashMap.<UUID>newKeySet();
        transports.entrySet().removeIf(entry -> {
            if (rooms.containsSession(entry.getKey().sessionId())) {
                return false;
            }
            removedSessions.add(entry.getKey().sessionId());
            entry.getValue().close();
            return true;
        });
        removedSessions.forEach(sessionId -> {
            router.removeSession(sessionId);
            directAudioBindings.removeSession(sessionId);
        });
        return removedRoomCount;
    }

    @Override
    public void endSession(UUID sessionId) {
        transports.entrySet().removeIf(entry -> {
            if (!entry.getKey().sessionId().equals(sessionId)) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
        router.removeSession(sessionId);
        directAudioBindings.removeSession(sessionId);
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
        transports.values().forEach(WebRtcParticipantTransport::close);
        transports.clear();
        directAudioBindings.clear();
    }

    private void handleOffer(MediaSignal signal) {
        var offer = WebRtcSdpOffer.parse(signal.sdp());
        var session = rooms.requireSession(signal.sessionId());
        var directPcmuAudio = session.kind() == CallKind.DIRECT && !hasOutboundVideo(offer);
        var key = new TransportKey(signal.sessionId(), signal.participantId());
        var newlyBoundDirectAudio = false;
        if (directPcmuAudio) {
            var negotiation = rooms.selectDirectAudioCodec(
                signal.sessionId(),
                signal.participantId(),
                offeredAudioCodecs(offer)
            );
            if (!negotiation.accepted()) {
                rejectOffer(signal, key, negotiation.error());
                return;
            }
            var alreadyBound = directAudioBindings.containsParticipant(
                signal.sessionId(),
                signal.participantId()
            );
            var bindingError = directAudioBindings.tryBind(
                signal.sessionId(),
                signal.participantId()
            );
            if (bindingError.isPresent()) {
                rejectOffer(signal, key, bindingError.orElseThrow());
                return;
            }
            newlyBoundDirectAudio = !alreadyBound;
        }
        var localUfrag = randomToken(8);
        var localPassword = randomToken(24);
        DatagramSocket socket;
        try {
            socket = openSocket();
        } catch (RuntimeException error) {
            if (newlyBoundDirectAudio) {
                directAudioBindings.removeParticipant(signal.sessionId(), signal.participantId());
            }
            throw error;
        }
        var transport = new WebRtcParticipantTransport(
            socket,
            localUfrag,
            localPassword,
            offer.iceUfrag(),
            offer.fingerprint(),
            identity,
            random,
            packet -> forward(signal.sessionId(), signal.participantId(), packet),
            packet -> forwardRtcp(signal.sessionId(), signal.participantId(), packet)
        );
        var previous = transports.put(key, transport);
        if (previous != null) {
            previous.close();
        }
        transport.start();
        var answerPolicy = directPcmuAudio
            ? WebRtcSdpAnswerPolicy.DIRECT_PCMU_AUDIO
            : WebRtcSdpAnswerPolicy.OFFERED_MEDIA;
        var answer = new WebRtcSdpAnswerBuilder(
            localUfrag,
            localPassword,
            identity.sha256Fingerprint(),
            new InetSocketAddress(publicAddress, socket.getLocalPort())
        ).answer(offer, answerPolicy);
        rooms.publishNodeSignal(
            signal.sessionId(),
            signal.participantId(),
            SignalType.ANSWER,
            answer,
            null
        );
    }

    private void rejectOffer(MediaSignal signal, TransportKey key, MediaErrorCode errorCode) {
        rooms.publishNodeError(signal.sessionId(), signal.participantId(), errorCode);
        if (!transports.containsKey(key)) {
            var participant = rooms.requireParticipant(signal.sessionId(), signal.participantId());
            rooms.leave(signal.sessionId(), signal.participantId(), participant.userId());
        }
    }

    private static List<RtpCodecDescriptor> offeredAudioCodecs(WebRtcSdpOffer offer) {
        return offer.media().stream()
            .filter(media -> media.type().equals("audio"))
            .flatMap(media -> media.codecs().stream())
            .toList();
    }

    private static boolean hasOutboundVideo(WebRtcSdpOffer offer) {
        return offer.media().stream().anyMatch(media ->
            media.type().equals("video")
                && (media.direction().equals("sendrecv") || media.direction().equals("sendonly"))
        );
    }

    private void forward(UUID sessionId, UUID publisherId, RtpPacket packet) {
        rooms.touch(sessionId);
        if (directAudioBindings.containsParticipant(sessionId, publisherId)) {
            for (var forwarding : directAudioBindings.route(sessionId, publisherId, packet)) {
                var target = transports.get(new TransportKey(sessionId, forwarding.participantId()));
                if (target != null) {
                    target.sendRtp(forwarding.packet());
                }
            }
            return;
        }
        var participants = rooms.listConnectedParticipants(sessionId);
        var publishers = participants.stream().map(CallParticipant::participantId).toList();
        for (var viewer : participants) {
            if (!viewer.participantId().equals(publisherId)) {
                router.updateSubscriptions(sessionId, viewer.participantId(), publishers);
            }
        }
        for (var forwarding : router.route(sessionId, publisherId, packet)) {
            var target = transports.get(new TransportKey(sessionId, forwarding.participantId()));
            if (target != null) {
                target.sendRtp(forwarding.packet());
            }
        }
    }

    private void forwardRtcp(UUID sessionId, UUID sourceParticipantId, byte[] packet) {
        rooms.touch(sessionId);
        if (directAudioBindings.containsParticipant(sessionId, sourceParticipantId)) {
            directAudioBindings.remoteParticipant(sessionId, sourceParticipantId).ifPresent(remoteId -> {
                var target = transports.get(new TransportKey(sessionId, remoteId));
                if (target != null) {
                    target.sendRtcp(packet);
                }
            });
            return;
        }
        for (var participant : rooms.listConnectedParticipants(sessionId)) {
            if (participant.participantId().equals(sourceParticipantId)) {
                continue;
            }
            var target = transports.get(new TransportKey(sessionId, participant.participantId()));
            if (target != null) {
                target.sendRtcp(packet);
            }
        }
    }

    private DatagramSocket openSocket() {
        if (portMin == 0) {
            try {
                return new DatagramSocket(new InetSocketAddress(bindAddress, 0));
            } catch (IOException error) {
                throw new IllegalStateException("cannot allocate media UDP socket", error);
            }
        }
        for (var port = portMin; port <= portMax; port++) {
            try {
                return new DatagramSocket(new InetSocketAddress(bindAddress, port));
            } catch (IOException ignored) {
                // Try the next room-affine UDP port.
            }
        }
        throw new IllegalStateException("media UDP port range exhausted");
    }

    private void closeTransport(UUID sessionId, UUID participantId) {
        var transport = transports.remove(new TransportKey(sessionId, participantId));
        if (transport != null) {
            transport.close();
        }
        directAudioBindings.removeParticipant(sessionId, participantId);
    }

    private String randomToken(int byteCount) {
        var bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record TransportKey(UUID sessionId, UUID participantId) {}
}
