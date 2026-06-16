package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.dto.MessageChangeEvent;
import com.avandocmsg.messenger.common.dto.ConferenceChangeEvent;
import com.avandocmsg.messenger.common.dto.LiveSessionChangeEvent;
import com.avandocmsg.messenger.common.dto.PinChangeEvent;
import com.avandocmsg.messenger.common.dto.ReadReceiptEvent;
import com.avandocmsg.messenger.common.dto.ReactionChangeEvent;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.ReadCacheInvalidateEvent;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.dto.RtcSignalEvent;
import com.avandocmsg.messenger.common.dto.TypingEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
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
import java.util.List;
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
    private static final String LIVE_SESSION_QUEUE_GROUP = "live-session-pipeline-workers";
    private static final String READ_RECEIPT_QUEUE_GROUP = "read-receipt-pipeline-workers";

    private final DataSource dataSource;
    private final Connection natsConnection;
    private final boolean jetStreamEnabled;
    private final UserMessageSource workerMessages;

    public MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                                 UserMessageSource workerMessages) throws Exception {
        this.dataSource = dataSource;
        this.jetStreamEnabled = jetStreamEnabled;
        this.workerMessages = workerMessages;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("message-pipeline-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.natsConnection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats_jetstream", natsUrl, jetStreamEnabled));
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
            log.info(workerMessages.format("worker.common.subscribed_jetstream", sub.getSubject(), QUEUE_GROUP, "pipeline-msg-send"));
        } else {
            var dispatcher = natsConnection.createDispatcher(this::handleCoreMessage);
            dispatcher.subscribe(NatsSubjects.MSG_SEND, QUEUE_GROUP);
            log.info(workerMessages.format("worker.common.subscribed_waiting", NatsSubjects.MSG_SEND, QUEUE_GROUP));
        }
        subscribeRtcFanout();
        subscribeTypingFanout();
        subscribeChangeFanout();
        subscribeReactionFanout();
        subscribePinFanout();
        subscribeConferenceFanout();
        subscribeLiveSessionFanout();
        subscribeReadReceiptFanout();
    }

    private void subscribeReadReceiptFanout() {
        var dispatcher = natsConnection.createDispatcher(this::handleReadReceiptCoreMessage);
        dispatcher.subscribe(NatsSubjects.MSG_READ_RECEIPT, READ_RECEIPT_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_READ_RECEIPT, READ_RECEIPT_QUEUE_GROUP, "read receipts"));
    }

    private void handleReadReceiptCoreMessage(Message msg) {
        try {
            handleReadReceiptPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.read_receipt_fanout_failed"), e);
        }
    }

    private void handleReadReceiptPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, ReadReceiptEvent.class);
        if (!ReadReceiptEvent.TYPE.equals(evt.type())) {
            return;
        }
        var chatId = UUID.fromString(evt.chatId());
        var reader = UUID.fromString(evt.userId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, reader, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.read_receipt_dropped", reader, chatId));
            return;
        }
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, reader, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.read_receipt_debug", reader, chatId, members.size()));
    }

    private void subscribeTypingFanout() {
        var typingDispatcher = natsConnection.createDispatcher(this::handleTypingCoreMessage);
        typingDispatcher.subscribe(NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP, "typing"));
    }

    private void subscribeChangeFanout() {
        var changeDispatcher = natsConnection.createDispatcher(this::handleChangeCoreMessage);
        changeDispatcher.subscribe(NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP, "message change"));
    }

    private void handleChangeCoreMessage(Message msg) {
        try {
            handleChangePayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.change_fanout_failed"), e);
        }
    }

    private void handleChangePayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, MessageChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.senderId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.change_dropped", sender, chatId));
            return;
        }
        var members = new java.util.ArrayList<>(
            PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages));
        var authorId = sender.toString();
        if (!members.contains(authorId)) {
            members.add(authorId);
        }
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.change_debug", evt.change(), chatId, members.size()));
    }

    private void subscribeReactionFanout() {
        var reactionDispatcher = natsConnection.createDispatcher(this::handleReactionCoreMessage);
        reactionDispatcher.subscribe(NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP, "reactions"));
    }

    private void handleReactionCoreMessage(Message msg) {
        try {
            handleReactionPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.reaction_fanout_failed"), e);
        }
    }

    private void handleReactionPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, ReactionChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.userId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.reaction_dropped", actor, chatId));
            return;
        }
        var members = new java.util.ArrayList<>(
            PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, actor, workerMessages));
        var actorId = actor.toString();
        if (!members.contains(actorId)) {
            members.add(actorId);
        }
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.reaction_debug",
            evt.change(), evt.messageId(), chatId, members.size()));
    }

    private void subscribePinFanout() {
        var pinDispatcher = natsConnection.createDispatcher(this::handlePinCoreMessage);
        pinDispatcher.subscribe(NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP, "pins"));
    }

    private void handlePinCoreMessage(Message msg) {
        try {
            handlePinPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.pin_fanout_failed"), e);
        }
    }

    private void handlePinPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, PinChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.pinnedBy());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.pin_dropped", actor, chatId));
            return;
        }
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.pin_debug",
            evt.change(), evt.messageId(), chatId, members.size()));
    }

    private void subscribeConferenceFanout() {
        var confDispatcher = natsConnection.createDispatcher(this::handleConferenceCoreMessage);
        confDispatcher.subscribe(NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP, "conferences"));
    }

    private void handleConferenceCoreMessage(Message msg) {
        try {
            handleConferencePayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.conference_fanout_failed"), e);
        }
    }

    private void handleConferencePayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, ConferenceChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.actorId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.conference_dropped", actor, chatId));
            return;
        }
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.conference_debug",
            evt.change(), evt.conferenceId(), chatId, members.size()));
    }

    private void subscribeLiveSessionFanout() {
        var dispatcher = natsConnection.createDispatcher(this::handleLiveSessionCoreMessage);
        dispatcher.subscribe(NatsSubjects.LIVE_SESSION, LIVE_SESSION_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.LIVE_SESSION, LIVE_SESSION_QUEUE_GROUP, "live sessions"));
    }

    private void handleLiveSessionCoreMessage(Message msg) {
        try {
            handleLiveSessionPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.live_session_fanout_failed"), e);
        }
    }

    private void handleLiveSessionPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, LiveSessionChangeEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var actor = UUID.fromString(evt.actorId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, actor, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.live_session_dropped", actor, chatId));
            return;
        }
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.live_session_debug",
            evt.change(), evt.liveSessionId(), chatId, members.size()));
    }

    private void handleTypingCoreMessage(Message msg) {
        try {
            handleTypingPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.typing_fanout_failed"), e);
        }
    }

    private void handleTypingPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, TypingEvent.class);
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.userId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.typing_dropped", sender, chatId));
            return;
        }
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, raw);
        }
        log.debug(workerMessages.format("worker.pipeline.typing_debug", sender, chatId, members.size()));
    }

    private void subscribeRtcFanout() {
        var rtcDispatcher = natsConnection.createDispatcher(this::handleRtcCoreMessage);
        rtcDispatcher.subscribe(NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP, "WebRTC signaling"));
    }

    private void handleRtcCoreMessage(Message msg) {
        try {
            handleRtcPayload(msg.getData());
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.rtc_fanout_failed"), e);
        }
    }

    private void handleRtcPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, RtcSignalEvent.class);
        if (!RtcSignalEvent.TYPE.equals(evt.type())) {
            return;
        }
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.fromUserId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.rtc_dropped", sender, chatId));
            return;
        }
        var out = MAPPER.writeValueAsBytes(evt);
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages);
        for (var memberId : members) {
            natsConnection.publish(NatsSubjects.MSG_DELIVER_PREFIX + memberId, out);
        }
        log.debug(workerMessages.format("worker.pipeline.rtc_debug", sender, chatId, members.size()));
    }

    private void handleCoreMessage(Message msg) {
        try {
            handleIncomingPayload(msg.getData(), false);
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.process_message_failed"), e);
        }
    }

    private void handleJetStreamMessage(Message msg) {
        try {
            handleIncomingPayload(msg.getData(), true);
            msg.ack();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.jetstream_failed"), e);
            try {
                msg.nak();
            } catch (Exception nakEx) {
                log.warn(workerMessages.get("worker.common.nak_failed"), nakEx);
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
            log.debug(workerMessages.format("worker.pipeline.received_send", event.messageId(), event.chatId()));

            var memberIds = PipelineFanoutLogic.loadRecipientUserIds(dataSource,
                UUID.fromString(event.chatId()), UUID.fromString(event.senderId()), workerMessages);
            for (var memberId : memberIds) {
                var deliverSubject = NatsSubjects.MSG_DELIVER_PREFIX + memberId;
                natsConnection.publish(deliverSubject, raw);
                log.debug(workerMessages.format("worker.pipeline.fanned_out", deliverSubject));
            }
            publishReadCacheInvalidation(memberIds, UUID.fromString(event.senderId()));

            log.info(workerMessages.format("worker.pipeline.processed", event.messageId(), memberIds.size()));
            try {
                publishDownstreamEvents(event);
            } catch (Exception e) {
                log.error(workerMessages.format("worker.pipeline.downstream_publish_failed", event.messageId()), e);
                if (jetStreamMsg) {
                    throw e;
                }
            }
        } catch (Exception e) {
            if (jetStreamMsg) {
                throw e;
            }
            log.error(workerMessages.get("worker.common.process_message_failed"), e);
        }
    }

    private void publishDownstreamEvents(MessageSendEvent sendEvent) throws Exception {
        var workerEvent = MessageWorkerEvent.fromSendEvent(sendEvent);
        var payload = MAPPER.writeValueAsBytes(workerEvent);
        natsConnection.publish(NatsSubjects.MSG_EVENT_INDEX, payload);
        natsConnection.publish(NatsSubjects.MSG_EVENT_PUSH, payload);
        natsConnection.publish(NatsSubjects.MSG_EVENT_BOT, payload);
    }

    private void publishReadCacheInvalidation(List<String> recipientIds, UUID senderId) {
        try {
            var userIds = new java.util.ArrayList<String>(recipientIds.size() + 1);
            userIds.add(senderId.toString());
            for (var memberId : recipientIds) {
                userIds.add(memberId);
            }
            var event = new ReadCacheInvalidateEvent(userIds, true, true);
            natsConnection.publish(NatsSubjects.MSG_CACHE_INVALIDATE, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.debug("read-cache invalidate publish failed: {}", e.getMessage());
        }
    }

    public void shutdown() {
        try {
            natsConnection.close();
        } catch (Exception e) {
            log.warn(workerMessages.get("worker.common.nats_close_error"), e);
        }
    }

    public static void main(String[] args) {
        var workerMessages = WorkerMessageSources.forWorker(
            MessagePipelineWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_message_pipeline");
        log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));
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
            var worker = new MessagePipelineWorker(natsUrl, ds, jetStreamEnabled, workerMessages);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.fatal_error"), e);
            System.exit(1);
        }
    }
}
