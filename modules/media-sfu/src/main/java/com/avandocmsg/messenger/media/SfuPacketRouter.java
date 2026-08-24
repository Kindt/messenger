package com.avandocmsg.messenger.media;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SfuPacketRouter {

    private final InMemoryMediaRoomService rooms;
    private final int lastN;
    private final Map<UUID, Map<UUID, Set<UUID>>> subscriptions = new ConcurrentHashMap<>();

    public SfuPacketRouter(InMemoryMediaRoomService rooms, int lastN) {
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        if (lastN < 1 || lastN > 64) {
            throw new IllegalArgumentException("lastN must be between 1 and 64");
        }
        this.lastN = lastN;
    }

    public List<UUID> updateSubscriptions(
        UUID sessionId,
        UUID viewerParticipantId,
        List<UUID> orderedPublisherIds
    ) {
        var selected = rooms.selectSubscriptions(sessionId, viewerParticipantId, orderedPublisherIds, lastN);
        var sessionSubscriptions = subscriptions.computeIfAbsent(
            sessionId,
            ignored -> java.util.Collections.synchronizedMap(new LinkedHashMap<>())
        );
        sessionSubscriptions.put(viewerParticipantId, new LinkedHashSet<>(selected));
        return selected;
    }

    public List<ForwardedPacket> route(UUID sessionId, UUID publisherParticipantId, RtpPacket packet) {
        Objects.requireNonNull(packet, "packet");
        var publisher = rooms.requireParticipant(sessionId, publisherParticipantId);
        if (publisher.state() != ParticipantState.CONNECTED) {
            return List.of();
        }
        var sessionSubscriptions = subscriptions.get(sessionId);
        if (sessionSubscriptions == null) {
            return List.of();
        }
        var targets = new ArrayList<ForwardedPacket>();
        synchronized (sessionSubscriptions) {
            sessionSubscriptions.forEach((viewerId, publisherIds) -> {
                if (!viewerId.equals(publisherParticipantId) && publisherIds.contains(publisherParticipantId)) {
                    var viewer = rooms.requireParticipant(sessionId, viewerId);
                    if (viewer.state() == ParticipantState.CONNECTED) {
                        targets.add(new ForwardedPacket(viewerId, packet));
                    }
                }
            });
        }
        return List.copyOf(targets);
    }

    public void removeSession(UUID sessionId) {
        subscriptions.remove(sessionId);
    }

    public record ForwardedPacket(UUID participantId, RtpPacket packet) {}
}
