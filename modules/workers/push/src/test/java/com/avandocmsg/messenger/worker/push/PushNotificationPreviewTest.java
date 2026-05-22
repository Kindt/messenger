package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushNotificationPreviewTest {

    @Test
    void bodyFor_plaintext_usesSearchText() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", null, 1L, "text", 0, false, 5, "Hello world", null);
        assertEquals("Hello world", PushNotificationPreview.bodyFor(event));
    }

    @Test
    void bodyFor_e2ee_showsLockMessage() {
        var event = new MessageWorkerEvent(
            "m1", "c1", "u1", null, 1L, "e2ee-text", 0, true, 100, null, null);
        assertTrue(PushNotificationPreview.bodyFor(event).contains("зашифрован"));
    }

    @Test
    void forEvent_usesChatTitleAndDeepLink() {
        var event = new MessageWorkerEvent(
            "m1", "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee", "u1", null, 1L, "text", 0, false, 1, "Hi", null);
        var preview = PushNotificationPreview.forEvent(event, "Team chat");
        assertEquals("Team chat", preview.title());
        assertEquals("Hi", preview.body());
        assertEquals("/?chat=aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee", preview.url());
    }
}
