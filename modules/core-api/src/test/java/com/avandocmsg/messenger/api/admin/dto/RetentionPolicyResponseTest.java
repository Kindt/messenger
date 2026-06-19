package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyResponseTest {

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
    void resolved_withoutStoredRow_usesPlatformDefaults() {
        var orgId = UUID.randomUUID();
        var r = RetentionPolicyResponse.resolved(orgId, new TestAppConfig(), Optional.empty());
        assertEquals(orgId.toString(), r.orgId());
        assertEquals(100, r.hotMessageBodyMaxAgeDays());
        assertEquals(7, r.hotMetadataMinAgeDays());
        assertTrue(r.archiveMetadataEnabled());
        assertTrue(r.deepArchiveEnabled());
        assertFalse(r.legalHold());
        assertNull(r.updatedAt());
        assertNull(r.updatedBy());
    }

    @Test
    void platformDefaults_sameNumericAndBoolsAsResolvedWithoutRow_orgIdNull() {
        var app = new TestAppConfig();
        var orgId = UUID.randomUUID();
        var resolvedNoRow = RetentionPolicyResponse.resolved(orgId, app, Optional.empty());
        var plat = RetentionPolicyResponse.platformDefaults(app);
        assertNull(plat.orgId());
        assertEquals(resolvedNoRow.hotMessageBodyMaxAgeDays(), plat.hotMessageBodyMaxAgeDays());
        assertEquals(resolvedNoRow.hotMetadataMinAgeDays(), plat.hotMetadataMinAgeDays());
        assertEquals(resolvedNoRow.archiveMetadataEnabled(), plat.archiveMetadataEnabled());
        assertEquals(resolvedNoRow.deepArchiveEnabled(), plat.deepArchiveEnabled());
        assertEquals(resolvedNoRow.legalHold(), plat.legalHold());
        assertNull(plat.updatedAt());
        assertNull(plat.updatedBy());
    }

    @Test
    void resolved_withStoredRow_nullIntsFallBackToPlatformDefaults() {
        var orgId = UUID.randomUUID();
        var ts = Instant.parse("2024-06-01T12:00:00Z");
        var stored = new RetentionPolicyPort.StoredRow(
            orgId, null, null, false, true, true, ts, null);
        var r = RetentionPolicyResponse.resolved(orgId, new TestAppConfig(), Optional.of(stored));
        assertEquals(100, r.hotMessageBodyMaxAgeDays());
        assertEquals(7, r.hotMetadataMinAgeDays());
        assertFalse(r.archiveMetadataEnabled());
        assertTrue(r.deepArchiveEnabled());
        assertTrue(r.legalHold());
        assertEquals(ts, r.updatedAt());
        assertNull(r.updatedBy());
    }
}
