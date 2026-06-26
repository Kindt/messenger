package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.dto.MessageChangeEvent;
import com.avandocmsg.messenger.common.dto.MentionEvent;
import com.avandocmsg.messenger.common.dto.ConferenceChangeEvent;
import com.avandocmsg.messenger.common.dto.LiveSessionChangeEvent;
import com.avandocmsg.messenger.common.dto.PinChangeEvent;
import com.avandocmsg.messenger.common.dto.ReadReceiptEvent;
import com.avandocmsg.messenger.common.dto.ReactionChangeEvent;
import com.avandocmsg.messenger.common.avatar.WorkerAvatarResizeUrl;
import com.avandocmsg.messenger.common.dto.MessageSendEvent;
import com.avandocmsg.messenger.common.dto.RtcSignalEvent;
import com.avandocmsg.messenger.common.dto.TypingEvent;
import com.avandocmsg.messenger.common.dto.ChatAvatarEvent;
import com.avandocmsg.messenger.common.dto.UserAvatarEvent;
import com.avandocmsg.messenger.common.dto.UserPresenceEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.logging.WorkerMdcSupport;
import com.avandocmsg.messenger.common.logging.WorkerNatsMdc;
import com.avandocmsg.messenger.common.nats.DeliverFanout;
import com.avandocmsg.messenger.common.nats.FanoutDedup;
import com.avandocmsg.messenger.common.nats.JetStreamMessagingSetup;
import com.avandocmsg.messenger.common.nats.MessageDownstreamPublisher;
import com.avandocmsg.messenger.common.nats.NatsConnectionOptions;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.common.health.WorkerDependencyHealth;
import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.PushSubscribeOptions;
import io.prometheus.client.hotspot.DefaultExports;
import io.nats.client.api.ConsumerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class MessagePipelineWorker {
    private static final Logger log = LoggerFactory.getLogger(MessagePipelineWorker.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();
    private static final String QUEUE_GROUP = "pipeline-workers";
    private static final String RTC_QUEUE_GROUP = "rtc-pipeline-workers";
    private static final String TYPING_QUEUE_GROUP = "typing-pipeline-workers";
    private static final String CHANGE_QUEUE_GROUP = "change-pipeline-workers";
    private static final String REACTION_QUEUE_GROUP = "reaction-pipeline-workers";
    private static final String PIN_QUEUE_GROUP = "pin-pipeline-workers";
    private static final String MENTION_QUEUE_GROUP = "mention-pipeline-workers";
    private static final String CONFERENCE_QUEUE_GROUP = "conference-pipeline-workers";
    private static final String LIVE_SESSION_QUEUE_GROUP = "live-session-pipeline-workers";
    private static final String READ_RECEIPT_QUEUE_GROUP = "read-receipt-pipeline-workers";
    private static final String USER_PRESENCE_QUEUE_GROUP = "user-presence-pipeline-workers";

    private final DataSource dataSource;
    private final Connection natsConnection;
    private final boolean jetStreamEnabled;
    private final UserMessageSource workerMessages;
    private final com.avandocmsg.messenger.common.nats.DeliverFanout.Config deliverConfig;
    private final FanoutDedup fanoutDedup;
    private final PipelineReadCacheInvalidator readCacheInvalidator;
    private final TypingFanoutDebouncer typingDebouncer;
    private final MessageDownstreamPublisher.Config downstreamPublishConfig;
    private final WorkerAvatarResizeUrl.Config avatarConfig;
    private WorkerHealthHttpServer metricsServer;

    public MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                                 UserMessageSource workerMessages) throws Exception {
        this(natsUrl, dataSource, jetStreamEnabled, workerMessages, DeliverFanout.Config.fromEnv());
    }

    MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                          UserMessageSource workerMessages,
                          com.avandocmsg.messenger.common.nats.DeliverFanout.Config deliverConfig) throws Exception {
        this(natsUrl, dataSource, jetStreamEnabled, workerMessages, deliverConfig, FanoutDedup.fromEnv());
    }

    MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                          UserMessageSource workerMessages,
                          com.avandocmsg.messenger.common.nats.DeliverFanout.Config deliverConfig,
                          FanoutDedup fanoutDedup) throws Exception {
        this(natsUrl, dataSource, jetStreamEnabled, workerMessages, deliverConfig, fanoutDedup,
            PipelineReadCacheInvalidator.fromEnv(), new TypingFanoutDebouncer(),
            MessageDownstreamPublisher.Config.fromEnv());
    }

    MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                          UserMessageSource workerMessages,
                          com.avandocmsg.messenger.common.nats.DeliverFanout.Config deliverConfig,
                          FanoutDedup fanoutDedup,
                          PipelineReadCacheInvalidator readCacheInvalidator,
                          TypingFanoutDebouncer typingDebouncer) throws Exception {
        this(natsUrl, dataSource, jetStreamEnabled, workerMessages, deliverConfig, fanoutDedup,
            readCacheInvalidator, typingDebouncer, MessageDownstreamPublisher.Config.fromEnv());
    }

    MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,
                          UserMessageSource workerMessages,
                          com.avandocmsg.messenger.common.nats.DeliverFanout.Config deliverConfig,
                          FanoutDedup fanoutDedup,
                          PipelineReadCacheInvalidator readCacheInvalidator,
                          TypingFanoutDebouncer typingDebouncer,
                          MessageDownstreamPublisher.Config downstreamPublishConfig) throws Exception {
        this.dataSource = dataSource;
        this.jetStreamEnabled = jetStreamEnabled;
        this.workerMessages = workerMessages;
        this.deliverConfig = deliverConfig;
        this.fanoutDedup = fanoutDedup;
        this.readCacheInvalidator = readCacheInvalidator != null
            ? readCacheInvalidator : PipelineReadCacheInvalidator.disabled();
        this.typingDebouncer = typingDebouncer != null ? typingDebouncer : new TypingFanoutDebouncer();
        this.downstreamPublishConfig = downstreamPublishConfig != null
            ? downstreamPublishConfig : MessageDownstreamPublisher.Config.fromEnv();
        this.avatarConfig = WorkerAvatarResizeUrl.Config.fromEnv();
        var options = NatsConnectionOptions.clientBuilder(natsUrl, "message-pipeline-worker").build();
        this.natsConnection = Nats.connect(options);
        log.info(workerMessages.format("worker.common.connected_nats_jetstream", natsUrl, jetStreamEnabled));
    }

    public void start() throws Exception {
        startMetricsServer();
        if (jetStreamEnabled) {
            JetStreamMessagingSetup.ensureSendStream(natsConnection);
            JetStream js = natsConnection.jetStream();
            var dispatcher = natsConnection.createDispatcher();
            PushSubscribeOptions opts = PushSubscribeOptions.builder()
                .configuration(ConsumerConfiguration.builder()
                    .durable("pipeline-msg-send")
                    .ackWait(Duration.ofSeconds(60))
                    .maxDeliver(JetStreamMessagingSetup.maxDeliver())
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
        subscribeMentionFanout();
        subscribeConferenceFanout();
        subscribeLiveSessionFanout();
        subscribeReadReceiptFanout();
        subscribeUserPresenceFanout();
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            evt.messageId());
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            evt.messageId());
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            evt.messageId());
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            evt.messageId());
        log.debug(workerMessages.format("worker.pipeline.pin_debug",
            evt.change(), evt.messageId(), chatId, members.size()));
    }

    private void subscribeMentionFanout() {
        var dispatcher = natsConnection.createDispatcher(this::handleMentionCoreMessage);
        dispatcher.subscribe(NatsSubjects.MSG_MENTION, MENTION_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_MENTION, MENTION_QUEUE_GROUP, "mentions"));
    }

    private void handleMentionCoreMessage(Message msg) {
        try {
            handleMentionPayload(msg.getData());
        } catch (Exception e) {
            log.error("mention fan-out failed", e);
        }
    }

    private void handleMentionPayload(byte[] raw) throws Exception {
        var evt = MAPPER.readValue(raw, MentionEvent.class);
        if (!MentionEvent.TYPE.equals(evt.type())) {
            return;
        }
        var chatId = UUID.fromString(evt.chatId());
        var sender = UUID.fromString(evt.senderId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, sender, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.pin_dropped", sender, chatId));
            return;
        }
        if (evt.mentionAll()) {
            var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages);
            DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
                fanoutDedupId("mention-all", evt.messageId(), evt.chatId()));
            log.debug("mention-all fan-out messageId={} chatId={} recipients={}", evt.messageId(), chatId, members.size());
            return;
        }
        if (evt.mentionedUserId() == null || evt.mentionedUserId().isBlank()) {
            return;
        }
        var target = UUID.fromString(evt.mentionedUserId());
        if (!PipelineFanoutLogic.isChatMember(dataSource, chatId, target, workerMessages)) {
            log.warn(workerMessages.format("worker.pipeline.pin_dropped", target, chatId));
            return;
        }
        DeliverFanout.publish(natsConnection, List.of(target.toString()), chatId.toString(), raw, deliverConfig, fanoutDedup,
            fanoutDedupId("mention", evt.messageId(), evt.mentionedUserId()));
        log.debug("mention fan-out messageId={} chatId={} target={}", evt.messageId(), chatId, target);
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            fanoutDedupId("conf", evt.conferenceId(), evt.change(), evt.actorId()));
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            fanoutDedupId("live", evt.liveSessionId(), evt.change(), evt.actorId()));
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
        if (!typingDebouncer.shouldFanout(evt.chatId(), evt.userId(), evt.ts())) {
            return;
        }
        var members = PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages);
        DeliverFanout.publish(natsConnection, members, chatId.toString(), raw, deliverConfig, fanoutDedup,
            fanoutDedupId("typing", evt.chatId(), evt.userId(), Long.toString(evt.ts())));
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
        DeliverFanout.publish(natsConnection, members, chatId.toString(), out, deliverConfig, fanoutDedup,
            fanoutDedupId("rtc", evt.chatId(), evt.fromUserId(), Integer.toHexString(java.util.Arrays.hashCode(raw))));
        log.debug(workerMessages.format("worker.pipeline.rtc_debug", sender, chatId, members.size()));
    }

    private void handleCoreMessage(Message msg) {
        WorkerNatsMdc.applyFromMessage(msg);
        try {
            handleIncomingPayload(msg.getData(), false);
        } catch (Exception e) {
            log.error(workerMessages.get("worker.common.process_message_failed"), e);
        } finally {
            WorkerMdcSupport.clear();
        }
    }

    private void handleJetStreamMessage(Message msg) {
        WorkerNatsMdc.applyFromMessage(msg);
        try {
            handleIncomingPayload(msg.getData(), true);
            msg.ack();
        } catch (Exception e) {
            log.error(workerMessages.get("worker.pipeline.jetstream_failed"), e);
            try {
                var meta = msg.metaData();
                long delivered = meta != null ? meta.deliveredCount() : 1L;
                if (delivered >= JetStreamMessagingSetup.maxDeliver()) {
                    natsConnection.publish(NatsSubjects.MSG_SEND_DLQ, msg.getData());
                    msg.ack();
                } else {
                    msg.nak();
                }
            } catch (Exception nakEx) {
                log.warn(workerMessages.get("worker.common.nak_failed"), nakEx);
            }
        } finally {
            WorkerMdcSupport.clear();
        }
    }

    /**
     * @param jetStreamMsg when true, JetStream caller performs ack/nak; exceptions propagate for nak.
     */
    private void handleIncomingPayload(byte[] raw, boolean jetStreamMsg) throws Exception {
        try {
            var event = MAPPER.readValue(raw, MessageSendEvent.class);
            var memberIds = PipelineFanoutLogic.loadRecipientUserIds(dataSource,
                UUID.fromString(event.chatId()), UUID.fromString(event.senderId()), workerMessages);
            DeliverFanout.publish(natsConnection, memberIds, event.chatId(), raw, deliverConfig, fanoutDedup,
                event.messageId());
            readCacheInvalidator.invalidateAfterMessageSend(memberIds, UUID.fromString(event.senderId()));

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
        MessageDownstreamPublisher.publish(natsConnection, sendEvent, MAPPER, downstreamPublishConfig);
    }

    private static String fanoutDedupId(String... parts) {
        return String.join("|", parts);
    }

    private void subscribeUserPresenceFanout() {
        var dispatcher = natsConnection.createDispatcher(this::handleUserPresenceCoreMessage);
        dispatcher.subscribe(NatsSubjects.USER_PRESENCE, USER_PRESENCE_QUEUE_GROUP);
        log.info(workerMessages.format("worker.common.subscribed_for",
            NatsSubjects.USER_PRESENCE, USER_PRESENCE_QUEUE_GROUP, "user presence"));
    }

    private void handleUserPresenceCoreMessage(Message msg) {
        try {
            handleUserPresencePayload(msg.getData());
        } catch (Exception e) {
            log.error("user presence fan-out failed", e);
        }
    }

    private void handleUserPresencePayload(byte[] raw) throws Exception {
        var root = MAPPER.readTree(raw);
        var type = root.path("type").asText("");
        if (UserAvatarEvent.TYPE.equals(type) || ChatAvatarEvent.TYPE.equals(type)) {
            PipelineAvatarFanout.dispatch(raw, root, dataSource, natsConnection, deliverConfig, fanoutDedup,
                avatarConfig, workerMessages);
            return;
        }
        var evt = MAPPER.treeToValue(root, UserPresenceEvent.class);
        if (!UserPresenceEvent.TYPE.equals(evt.type())) {
            return;
        }
        var userId = UUID.fromString(evt.userId());
        var orgId = evt.orgId() != null ? UUID.fromString(evt.orgId()) : null;
        if (orgId == null) {
            return;
        }
        var members = PipelineFanoutLogic.loadPresenceRecipientUserIds(dataSource, userId, workerMessages);
        members.add(userId.toString());
        DeliverFanout.publish(natsConnection, members, userId.toString(), raw, deliverConfig, fanoutDedup,
            fanoutDedupId("presence", evt.userId(), Long.toString(evt.ts())));
    }

    private void startMetricsServer() {
        var port = parsePort(System.getenv("PIPELINE_METRICS_PORT"), 9191);
        if (port <= 0) {
            return;
        }
        try {
            DefaultExports.initialize();
            metricsServer = WorkerHealthHttpServer.startWithMetrics(
                port,
                "pipeline-metrics",
                () -> WorkerDependencyHealth.natsAndJdbc(natsConnection, dataSource),
                workerMessages,
                PipelineMetrics::registerBuildInfoOnce);
            log.info(workerMessages.format("worker.pipeline.metrics_started", metricsServer.getPort()));
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.pipeline.metrics_failed", e.getMessage()));
        }
    }

    private static int parsePort(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    public void shutdown() {
        if (metricsServer != null) {
            metricsServer.close();
        }
        readCacheInvalidator.close();
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
