package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.dto.MessageChangeEvent;
import com.avandocmsg.messenger.common.dto.ConferenceChangeEvent;
import com.avandocmsg.messenger.common.dto.PinChangeEvent;
import com.avandocmsg.messenger.common.dto.ReactionChangeEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.dto.RtcSignalEvent;
import com.avandocmsg.messenger.common.dto.TypingEvent;
import com.avandocmsg.messenger.common.nats.JetStreamMessagingSetup;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class MessagePipelineWorker {
    private static final Logger log = LoggerFactory.getLogger(MessagePipelineWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "pipeline-workers";
    private static final String RTC_QUEUE_GROUP = "rtc-pipeline-workers";
    private static final String TYPING_QUEUE_GROUP = "typing-pipeline-workers";
    private static final String CHANGE_QUEUE_GROUP = "change-pipeline-workers";
    private static final String REACTION_QUEUE_GROUP = "reaction-pipeline-workers";
    private static final String PIN_QUEUE_GROUP = "pin-pipeline-workers";
    private static final String CONFERENCE_QUEUE_GROUP = "conference-pipeline-workers";

    private final DataSource dataSource;
    private final Connection natsConnection;
    private final boolean jetStreamEnabled;

    public MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled) throws Exception {
        this.dataSource = dataSource;
        this.jetStreamEnabled = jetStreamEnabled;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("message-pipeline-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.natsConnection = Nats.connect(options);
        log.info("Connected to NATS at {} (JetStream mode: {})", natsUrl, jetStreamEnabled);
    }

    public void start() throws Exception {
        if (jetStreamEnabled) {
            JetStreamMessagingSetup.ensureSendStream(natsConnection);
            JetStream js = natsConnection.jetStream();
            var dispatcher = natsConnection.createDispatcher();
            PushSubscribeOptions opts = PushSubscribeOptions.builder()
                .configuration(ConsumerConfiguration.builder()
                    .durable("pipeline-msg-send")
                    .ackWait(Duration.ofSeconds(60))
                    .build())
                .build();
            JetStreamSubscription sub = js.subscribe(NatsSubjects.MSG_SEND, QUEUE_GROUP, dispatcher,
                this::handleJetStreamMessage, false, opts);
            log.info("JetStream subscribed to {} queue {} (consumer {}). Waiting...", sub.getSubject(), QUEUE_GROUP, "pipeline-msg-send");
        } else {
            var dispatcher = natsConnection.createDispatcher(this::handleCoreMessage);
            dispatcher.subscribe(NatsSubjects.MSG_SEND, QUEUE_GROUP);
            log.info("Subscribed to {} (queue: {}). Waiting for messages...", NatsSubjects.MSG_SEND, QUEUE_GROUP);
        }
        subscribeRtcFanout();
        subscribeTypingFanout();
        subscribeChangeFanout();
        subscribeReactionFanout();
        subscribePinFanout();
        subscribeConferenceFanout();
    }

    private void subscribeTypingFanout() {
        var typingDispatcher = natsConnection.createDispatcher(this::handleTypingCoreMessage);
        typingDispatcher.subscribe(NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for typing", NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP);
    }

    private void subscribeChangeFanout() {
        var changeDispatcher = natsConnection.createDispatcher(this::handleChangeCoreMessage);
        changeDispatcher.subscribe(NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for message change", NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP);
    }

    private void handleChangeCoreMessage(Message msg) {
        try {
            handleChangePayload(msg.getData());
        } catch (Exception e) {
            log.error("Message change fan-out failed", e);
        }
    }

    private void handleChangePayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, MessageChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.senderId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender)) {
            log.warn("msg.change dropped: user {} is not an active member of chat {}", sender, chatId);
            return;
        }
        var members = new java.util.ArrayList<>(
            PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender));
        var authorId = sender.toString();
        if (!members.contains(authorId)) {
            members.add(authorId);
        }
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug("msg.change {} in chat {} -> {} recipients", evt.change(), chatId, members.size());
    }

    private void subscribeReactionFanout() {
        var reactionDispatcher = natsConnection.createDispatcher(this::handleReactionCoreMessage);
        reactionDispatcher.subscribe(NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for reactions", NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP);
    }

    private void handleReactionCoreMessage(Message msg) {
        try {
            handleReactionPayload(msg.getData());
        } catch (Exception e) {
            log.error("Reaction fan-out failed", e);
        }
    }

    private void handleReactionPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, ReactionChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.userId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor)) {
            log.warn("msg.reaction dropped: user {} is not an active member of chat {}", actor, chatId);
            return;
        }
        var members = new java.util.ArrayList<>(
            PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, actor));
        var actorId = actor.toString();
        if (!members.contains(actorId)) {
            members.add(actorId);
        }
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug("msg.reaction {} on {} in chat {} -> {} recipients",
            evt.change(), evt.messageId(), chatId, members.size());
    }

    private void subscribePinFanout() {
        var pinDispatcher = natsConnection.createDispatcher(this::handlePinCoreMessage);
        pinDispatcher.subscribe(NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for pins", NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP);
    }

    private void handlePinCoreMessage(Message msg) {
        try {
            handlePinPayload(msg.getData());
        } catch (Exception e) {
            log.error("Pin fan-out failed", e);
        }
    }

    private void handlePinPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, PinChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.pinnedBy());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor)) {
            log.warn("msg.pin dropped: user {} is not an active member of chat {}", actor, chatId);
            return;
        }
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug("msg.pin {} on {} in chat {} -> {} recipients",
            evt.change(), evt.messageId(), chatId, members.size());
    }

    private void subscribeConferenceFanout() {
        var confDispatcher = natsConnection.createDispatcher(this::handleConferenceCoreMessage);
        confDispatcher.subscribe(NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for conferences", NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP);
    }

    private void handleConferenceCoreMessage(Message msg) {
        try {
            handleConferencePayload(msg.getData());
        } catch (Exception e) {
            log.error("Conference fan-out failed", e);
        }
    }

    private void handleConferencePayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, ConferenceChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.actorId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor)) {
            log.warn("msg.conference dropped: user {} is not an active member of chat {}", actor, chatId);
            return;
        }
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug("msg.conference {} {} in chat {} -> {} recipients",
            evt.change(), evt.conferenceId(), chatId, members.size());
    }

    private void handleTypingCoreMessage(Message msg) {
        try {
            handleTypingPayload(msg.getData());
        } catch (Exception e) {
            log.error("Typing fan-out failed", e);
        }
    }

    private void handleTypingPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, TypingEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.userId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender)) {
            log.warn("msg.typing dropped: user {} is not an active member of chat {}", sender, chatId);
            return;
        }
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug("msg.typing from {} in chat {} -> {} recipients", sender, chatId, members.size());
    }

    private void subscribeRtcFanout() {
        var rtcDispatcher = natsConnection.createDispatcher(this::handleRtcCoreMessage);
        rtcDispatcher.subscribe(NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {}) for WebRTC signaling", NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP);
    }

    private void handleRtcCoreMessage(Message msg) {
        try {
            handleRtcPayload(msg.getData());
        } catch (Exception e) {
            log.error("RTC fan-out failed", e);
        }
    }

    private void handleRtcPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, RtcSignalEvent.class);
        if (!RtcSignalEvent.TYPE.equals(evt.type())) {
            return;
        }
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.fromUserId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender)) {
            log.warn("rtc.signal dropped: sender {} is not an active member of chat {}", sender, chatId);
            return;
        }
        var out = MAPPER.writeValueAsBytes(evt);
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, out);
        }
        log.debug("rtc.signal from {} in chat {} -> {} recipients", sender, chatId, members.size());
    }

    private void handleCoreMessage(Message msg) {
        try {
            handleIncomingPayload(msg.getData(), false);
        } catch (Exception e) {
            log.error("Failed to process message", e);
        }
    }

    private void handleJetStreamMessage(Message msg) {
        try {
            handleIncomingPayload(msg.getData(), true);
            msg.ack();
        } catch (Exception e) {
            log.error("JetStream pipeline failed", e);
            try {
                msg.nak();
            } catch (Exception nakEx) {
                log.warn("nak() failed", nakEx);
            }
        }
    }

    /**
     * @param jetStreamMsg when true, JetStream caller performs ack/nak; exceptions propagate for nak.
     */
    private void handleIncomingPayload(byte[] raw, boolean jetStreamMsg) throws Exception {
        try {
            var payload = new String(raw, StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageSendEvent.class);
            log.debug("Received msg.send: {} in chat {}", event.messageId(), event.chatId());

            var memberIds = PipelineFanoutLogic.loadRecipientUserIds(dataSource,
                UUID.fromString(event.chatId()), UUID.fromString(event.senderId()));
            for (var memberId : memberIds) {
                var deliverSubject = NatsSubjects.MSG_DELIVER_PREFIX + memberId;
                natsConnection.publish(deliverSubject, raw);
                log.debug("Fanned out to {}", deliverSubject);
            }

            log.info("Processed message {} fanned out to {} members", event.messageId(), memberIds.size());
            try {
                publishDownstreamEvents(event);
            } catch (Exception e) {
                log.error("Fan-out ok but failed to publish msg.event.* for {}", event.messageId(), e);
                if (jetStreamMsg) {
                    throw e;
                }
            }
        } catch (Exception e) {
            if (jetStreamMsg) {
                throw e;
            }
            log.error("Failed to process message", e);
        }
    }

    private void publishDownstreamEvents(MessageSendEvent sendEvent) throws Exception {
        var workerEvent = MessageWorkerEvent.fromSendEvent(sendEvent);
        var payload = MAPPER.writeValueAsBytes(workerEvent);
        natsConnection.publish(NatsSubjects.MSG_EVENT_INDEX, payload);
        natsConnection.publish(NatsSubjects.MSG_EVENT_PUSH, payload);
        natsConnection.publish(NatsSubjects.MSG_EVENT_BOT, payload);
    }

    public void shutdown() {
        try {
            natsConnection.close();
        } catch (Exception e) {
            log.warn("Error closing NATS connection", e);
        }
    }

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var dbUrl = System.getenv().getOrDefault("DB_JDBC_URL", "jdbc:postgresql://localhost:5432/avandocmsg_hot");
        var dbUser = System.getenv().getOrDefault("DB_USER", "avandocmsg");
        var dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "avandocmsg");
        var jetStreamEnabled = "true".equalsIgnoreCase(System.getenv("NATS_JETSTREAM"));

        var config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(5);
        var ds = new HikariDataSource(config);

        try {
            var worker = new MessagePipelineWorker(natsUrl, ds, jetStreamEnabled);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }
}
