package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatRetentionPolicyResponseTest {

    private static final class TestAppConfig extends AppConfig {
        @Override
        public Integer retentionDefaultHotBodyMaxAgeDays() {
            return 100;
        }

        @Override
        public Integer retentionDefaultHotMetadataMinAgeDays() {
            return 7;
        }

        @Override
        public boolean retentionDefaultArchiveMetadataEnabled() {
            return true;
        }

        @Override
        public boolean retentionDefaultDeepArchiveEnabled() {
            return true;
        }

        @Override
        public boolean retentionDefaultLegalHold() {
            return false;
        }
    }

    @Test
    void resolved_withoutBaseOrg_matchesPlatformDefaultsLayer() {
        var chatId = UUID.randomUUID();
        var app = new TestAppConfig();
        var plat = RetentionPolicyResponse.platformDefaults(app);
        var r = ChatRetentionPolicyResponse.resolved(chatId, Optional.empty(), app, Optional.empty(), Optional.empty());
        assertEquals(chatId.toString(), r.chatId());
        assertNull(r.baseOrgId());
        assertEquals(plat.hotMessageBodyMaxAgeDays(), r.hotMessageBodyMaxAgeDays());
        assertEquals(plat.hotMetadataMinAgeDays(), r.hotMetadataMinAgeDays());
        assertEquals(plat.archiveMetadataEnabled(), r.archiveMetadataEnabled());
        assertEquals(plat.deepArchiveEnabled(), r.deepArchiveEnabled());
        assertEquals(plat.legalHold(), r.legalHold());
        assertNull(r.updatedAt());
        assertNull(r.updatedBy());
    }

    @Test
    void resolved_withBaseOrg_usesOrgResolvedLayer() {
        var chatId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var app = new TestAppConfig();
        var ts = Instant.parse("2024-06-01T12:00:00Z");
        var orgStored = new RetentionPolicyPort.StoredRow(
            orgId, 20, 3, false, true, false, ts, null);
        var r = ChatRetentionPolicyResponse.resolved(chatId, Optional.of(orgId), app, Optional.of(orgStored), Optional.empty());
        assertEquals(orgId.toString(), r.baseOrgId());
        assertEquals(20, r.hotMessageBodyMaxAgeDays());
        assertEquals(3, r.hotMetadataMinAgeDays());
        assertFalse(r.archiveMetadataEnabled());
        assertTrue(r.deepArchiveEnabled());
        assertFalse(r.legalHold());
        assertEquals(ts, r.updatedAt());
        assertNull(r.updatedBy());
    }

    @Test
    void resolved_chatOverlay_nullIntsFallBackToOrgLayer() {
        var chatId = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var app = new TestAppConfig();
        var orgStored = new RetentionPolicyPort.StoredRow(
            orgId, 50, 8, true, false, false, Instant.now(), null);
        var chatStored = new ChatRetentionPolicyPort.StoredRow(
            chatId, null, null, false, true, true, Instant.parse("2024-07-01T00:00:00Z"), "actor");
        var r = ChatRetentionPolicyResponse.resolved(chatId, Optional.of(orgId), app, Optional.of(orgStored), Optional.of(chatStored));
        assertEquals(50, r.hotMessageBodyMaxAgeDays());
        assertEquals(8, r.hotMetadataMinAgeDays());
        assertFalse(r.archiveMetadataEnabled());
        assertTrue(r.deepArchiveEnabled());
        assertTrue(r.legalHold());
    }
}
