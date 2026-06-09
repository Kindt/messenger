from pathlib import Path

p = Path(r"D:\proj\korus_messenger\modules\workers\message-pipeline\src\main\java\com\avandocmsg\messenger\worker\pipeline\MessagePipelineWorker.java")
text = p.read_text(encoding="utf-8")
if "UserMessageSource" not in text.split("WorkerMessageSources")[0]:
    text = text.replace(
        "import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
        "import com.avandocmsg.messenger.common.i18n.UserMessageSource;\nimport com.avandocmsg.messenger.common.i18n.WorkerMessageSources;",
    )
text = text.replace(
    "    private final boolean jetStreamEnabled;\n",
    "    private final boolean jetStreamEnabled;\n    private final UserMessageSource workerMessages;\n",
)
text = text.replace(
    "    public MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled) throws Exception {\n        this.dataSource = dataSource;\n        this.jetStreamEnabled = jetStreamEnabled;",
    "    public MessagePipelineWorker(String natsUrl, DataSource dataSource, boolean jetStreamEnabled,\n                                 UserMessageSource workerMessages) throws Exception {\n        this.dataSource = dataSource;\n        this.jetStreamEnabled = jetStreamEnabled;\n        this.workerMessages = workerMessages;",
)
replacements = [
    ('log.info("Connected to NATS at {} (JetStream mode: {})", natsUrl, jetStreamEnabled);',
     'log.info(workerMessages.format("worker.common.connected_nats_jetstream", natsUrl, jetStreamEnabled));'),
    ('log.info("JetStream subscribed to {} queue {} (consumer {}). Waiting...", sub.getSubject(), QUEUE_GROUP, "pipeline-msg-send");',
     'log.info(workerMessages.format("worker.common.subscribed_jetstream", sub.getSubject(), QUEUE_GROUP, "pipeline-msg-send"));'),
    ('log.info("Subscribed to {} (queue: {}). Waiting for messages...", NatsSubjects.MSG_SEND, QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_waiting", NatsSubjects.MSG_SEND, QUEUE_GROUP));'),
    ('log.info("Subscribed to {} (queue: {}) for read receipts", NatsSubjects.MSG_READ_RECEIPT, READ_RECEIPT_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_READ_RECEIPT, READ_RECEIPT_QUEUE_GROUP, "read receipts"));'),
    ('log.error("Read receipt fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.read_receipt_fanout_failed"), e);'),
    ('log.warn("msg.read_receipt dropped: user {} is not an active member of chat {}", reader, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.read_receipt_dropped", reader, chatId));'),
    ('log.debug("msg.read_receipt from {} in chat {} -> {} recipients", reader, chatId, members.size());',
     'log.debug(workerMessages.format("worker.pipeline.read_receipt_debug", reader, chatId, members.size()));'),
    ('log.info("Subscribed to {} (queue: {}) for typing", NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_TYPING, TYPING_QUEUE_GROUP, "typing"));'),
    ('log.info("Subscribed to {} (queue: {}) for message change", NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_CHANGE, CHANGE_QUEUE_GROUP, "message change"));'),
    ('log.error("Message change fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.change_fanout_failed"), e);'),
    ('log.warn("msg.change dropped: user {} is not an active member of chat {}", sender, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.change_dropped", sender, chatId));'),
    ('log.debug("msg.change {} in chat {} -> {} recipients", evt.change(), chatId, members.size());',
     'log.debug(workerMessages.format("worker.pipeline.change_debug", evt.change(), chatId, members.size()));'),
    ('log.info("Subscribed to {} (queue: {}) for reactions", NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_REACTION, REACTION_QUEUE_GROUP, "reactions"));'),
    ('log.error("Reaction fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.reaction_fanout_failed"), e);'),
    ('log.warn("msg.reaction dropped: user {} is not an active member of chat {}", actor, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.reaction_dropped", actor, chatId));'),
    ('log.info("Subscribed to {} (queue: {}) for pins", NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_PIN, PIN_QUEUE_GROUP, "pins"));'),
    ('log.error("Pin fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.pin_fanout_failed"), e);'),
    ('log.warn("msg.pin dropped: user {} is not an active member of chat {}", actor, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.pin_dropped", actor, chatId));'),
    ('log.info("Subscribed to {} (queue: {}) for conferences", NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.MSG_CONFERENCE, CONFERENCE_QUEUE_GROUP, "conferences"));'),
    ('log.error("Conference fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.conference_fanout_failed"), e);'),
    ('log.warn("msg.conference dropped: user {} is not an active member of chat {}", actor, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.conference_dropped", actor, chatId));'),
    ('log.error("Typing fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.typing_fanout_failed"), e);'),
    ('log.warn("msg.typing dropped: user {} is not an active member of chat {}", sender, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.typing_dropped", sender, chatId));'),
    ('log.debug("msg.typing from {} in chat {} -> {} recipients", sender, chatId, members.size());',
     'log.debug(workerMessages.format("worker.pipeline.typing_debug", sender, chatId, members.size()));'),
    ('log.info("Subscribed to {} (queue: {}) for WebRTC signaling", NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP);',
     'log.info(workerMessages.format("worker.common.subscribed_for", NatsSubjects.RTC_SIGNAL, RTC_QUEUE_GROUP, "WebRTC signaling"));'),
    ('log.error("RTC fan-out failed", e);', 'log.error(workerMessages.get("worker.pipeline.rtc_fanout_failed"), e);'),
    ('log.warn("rtc.signal dropped: sender {} is not an active member of chat {}", sender, chatId);',
     'log.warn(workerMessages.format("worker.pipeline.rtc_dropped", sender, chatId));'),
    ('log.debug("rtc.signal from {} in chat {} -> {} recipients", sender, chatId, members.size());',
     'log.debug(workerMessages.format("worker.pipeline.rtc_debug", sender, chatId, members.size()));'),
    ('log.error("JetStream pipeline failed", e);', 'log.error(workerMessages.get("worker.pipeline.jetstream_failed"), e);'),
    ('log.warn("nak() failed", nakEx);', 'log.warn(workerMessages.get("worker.common.nak_failed"), nakEx);'),
    ('log.debug("Received msg.send: {} in chat {}", event.messageId(), event.chatId());',
     'log.debug(workerMessages.format("worker.pipeline.received_send", event.messageId(), event.chatId()));'),
    ('log.debug("Fanned out to {}", deliverSubject);',
     'log.debug(workerMessages.format("worker.pipeline.fanned_out", deliverSubject));'),
    ('log.info("Processed message {} fanned out to {} members", event.messageId(), memberIds.size());',
     'log.info(workerMessages.format("worker.pipeline.processed", event.messageId(), memberIds.size()));'),
    ('log.warn("Error closing NATS connection", e);', 'log.warn(workerMessages.get("worker.common.nats_close_error"), e);'),
    ('log.info("Worker i18n locale={}", workerMessages.locale());',
     'log.info(workerMessages.format("worker.common.locale", workerMessages.locale()));'),
    ('var worker = new MessagePipelineWorker(natsUrl, ds, jetStreamEnabled);',
     'var worker = new MessagePipelineWorker(natsUrl, ds, jetStreamEnabled, workerMessages);'),
]
for old, new in replacements:
    if old not in text:
        print("MISSING:", old[:70])
    else:
        text = text.replace(old, new)
# multiline debug logs
text = text.replace(
    'log.debug("msg.reaction {} on {} in chat {} -> {} recipients",\n            evt.change(), evt.messageId(), chatId, members.size());',
    'log.debug(workerMessages.format("worker.pipeline.reaction_debug",\n            evt.change(), evt.messageId(), chatId, members.size()));',
)
text = text.replace(
    'log.debug("msg.pin {} on {} in chat {} -> {} recipients",\n            evt.change(), evt.messageId(), chatId, members.size());',
    'log.debug(workerMessages.format("worker.pipeline.pin_debug",\n            evt.change(), evt.messageId(), chatId, members.size()));',
)
text = text.replace(
    'log.debug("msg.conference {} {} in chat {} -> {} recipients",\n            evt.change(), evt.conferenceId(), chatId, members.size());',
    'log.debug(workerMessages.format("worker.pipeline.conference_debug",\n            evt.change(), evt.conferenceId(), chatId, members.size()));',
)
text = text.replace(
    'log.error("Failed to process message", e);',
    'log.error(workerMessages.get("worker.common.process_message_failed"), e);',
)
text = text.replace(
    'log.error("Fan-out ok but failed to publish msg.event.* for {}", event.messageId(), e);',
    'log.error(workerMessages.format("worker.pipeline.downstream_publish_failed", event.messageId()), e);',
)
text = text.replace('log.error("Fatal error", e);', 'log.error(workerMessages.get("worker.common.fatal_error"), e);')
for pat, repl in [
    ("PipelineFanoutLogic.isChatMember(dataSource, chatId, reader)", "PipelineFanoutLogic.isChatMember(dataSource, chatId, reader, workerMessages)"),
    ("PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, reader)", "PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, reader, workerMessages)"),
    ("PipelineFanoutLogic.isChatMember(dataSource, chatId, sender)", "PipelineFanoutLogic.isChatMember(dataSource, chatId, sender, workerMessages)"),
    ("PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender)", "PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, sender, workerMessages)"),
    ("PipelineFanoutLogic.isChatMember(dataSource, chatId, actor)", "PipelineFanoutLogic.isChatMember(dataSource, chatId, actor, workerMessages)"),
    ("PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, actor)", "PipelineFanoutLogic.loadRecipientUserIds(dataSource, chatId, actor, workerMessages)"),
    ("PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId)", "PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId, workerMessages)"),
]:
    text = text.replace(pat, repl)
text = text.replace(
    "PipelineFanoutLogic.loadRecipientUserIds(dataSource,\n                UUID.fromString(event.chatId()), UUID.fromString(event.senderId()))",
    "PipelineFanoutLogic.loadRecipientUserIds(dataSource,\n                UUID.fromString(event.chatId()), UUID.fromString(event.senderId()), workerMessages)",
)
p.write_text(text, encoding="utf-8")
print("done")
