package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushNotificationPreviewTest {

    private static final UserMessageSource MESSAGES = WorkerMessageSources.forWorker(
        PushWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_push");

    @Test
    void bodyFor_plaintext_usesSearchText() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", null, 1L, "text", 0, false, 5, "Hello world", null);
        assertEquals("Hello world", PushNotificationPreview.bodyFor(event, MESSAGES));
    }

    @Test
    void bodyFor_e2ee_showsLockMessage() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", null, 1L, "e2ee-text", 0, true, 100, null, null);
        assertTrue(PushNotificationPreview.bodyFor(event, MESSAGES).contains("encrypted")
            || PushNotificationPreview.bodyFor(event, MESSAGES).contains("зашифрован"));
    }

    @Test
    void forEvent_usesChatTitleAndDeepLink() {
        var event = new MessageWorkerEvent(
            "m1", "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee", "u1", null, 1L, "text", 0, false, 1, "Hi", null);
        var preview = PushNotificationPreview.forEvent(event, "Team chat", MESSAGES);
        assertEquals("Team chat", preview.title());
        assertEquals("Hi", preview.body());
        assertEquals("/?chat=aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee", preview.url());
    }
}
