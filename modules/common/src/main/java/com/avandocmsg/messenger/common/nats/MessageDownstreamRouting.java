package com.avandocmsg.messenger.common.nats;

import com.avandocmsg.messenger.common.dto.MessageDownstreamEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Consumer-side routing for {@link NatsSubjects#MSG_EVENT_DOWNSTREAM} (spec 025 FR-012 Phase 2+).
 */
public final class MessageDownstreamRouting {

    public static final String ROUTE_INDEX = "index";
    public static final String ROUTE_PUSH = "push";
    public static final String ROUTE_BOT = "bot";

    public static final String ENV_LEGACY_SUBSCRIBE = "NATS_DOWNSTREAM_LEGACY_SUBSCRIBE";
    public static final String ENV_LEGACY_PUBLISH = "NATS_DOWNSTREAM_LEGACY_PUBLISH";

    private MessageDownstreamRouting() {
    }

    /** Default {@code true} — safe rollback during Phase 2 cutover. */
    public static boolean legacySubscribeEnabled() {
        return !"false".equalsIgnoreCase(trimEnv(System.getenv(ENV_LEGACY_SUBSCRIBE)));
    }

    /** Default {@code false} — Phase 3 publishes downstream only unless explicitly enabled. */
    public static boolean legacyPublishEnabled() {
        return "true".equalsIgnoreCase(trimEnv(System.getenv(ENV_LEGACY_PUBLISH)));
    }

    public static boolean routeTargetsConsumer(List<String> route, String consumerToken) {
        return route != null && route.contains(consumerToken);
    }

    public static MessageDownstreamEvent parseEnvelope(byte[] data, ObjectMapper mapper) throws java.io.IOException {
        return mapper.readValue(data, MessageDownstreamEvent.class);
    }

    /**
     * Applies push/bot preview truncation when consuming the consolidated envelope;
     * index/archiver/preview consumers receive the full payload.
     */
    public static MessageWorkerEvent payloadForRoute(MessageDownstreamEvent envelope, String consumerToken) {
        if (envelope == null || envelope.payload() == null) {
            return null;
        }
        var payload = envelope.payload();
        if (ROUTE_PUSH.equals(consumerToken) || ROUTE_BOT.equals(consumerToken)) {
            return payload.withSearchTextMaxChars(MessageWorkerEvent.PUSH_BOT_SEARCH_TEXT_MAX);
        }
        return payload;
    }

    /**
     * Primary {@link NatsSubjects#MSG_EVENT_DOWNSTREAM} subscription plus optional legacy subject fallback.
     */
    public static void dispatchDownstreamMessage(
        io.nats.client.Message msg,
        String routeToken,
        ObjectMapper mapper,
        Consumer<MessageWorkerEvent> handler
    ) {
        if (msg == null || msg.getData() == null || msg.getData().length == 0) {
            return;
        }
        try {
            if (NatsSubjects.MSG_EVENT_DOWNSTREAM.equals(msg.getSubject())) {
                var envelope = parseEnvelope(msg.getData(), mapper);
                if (!routeTargetsConsumer(envelope.route(), routeToken)) {
                    return;
                }
                var payload = payloadForRoute(envelope, routeToken);
                if (payload != null) {
                    handler.accept(payload);
                }
                return;
            }
            var legacy = mapper.readValue(msg.getData(), MessageWorkerEvent.class);
            handler.accept(legacy);
        } catch (Exception e) {
            throw new DownstreamDispatchException(e);
        }
    }

    public static final class DownstreamDispatchException extends RuntimeException {
        public DownstreamDispatchException(Throwable cause) {
            super(cause);
        }
    }

    private static String trimEnv(String raw) {
        if (raw == null) {
            return null;
        }
        var trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
