package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.fleet.FleetSnapshotService;
import com.avandocmsg.messenger.api.admin.ui.dto.AdminManifestResponse;
import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionDescriptor;
import com.avandocmsg.messenger.common.admin.ui.AdminUiSectionKind;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminUiResourceTest {

    @Test
    void manifest_returnsSections() {
        var manifest = AdminUiManifest.of(List.of(
            new AdminUiSectionDescriptor("t1", "Test", 1, AdminUiSectionKind.CUSTOM, null)));
        AdminStatsPort stats = () -> new AdminServerStatsResponse(
            "0",
            new AdminServerStatsResponse.JvmStats(1, 2, 3, 4, 5),
            new AdminServerStatsResponse.DependencyHealth(true, true, true),
            new AdminServerStatsResponse.TableCounts(0, 0, 0, false),
            AdminServerStatsResponse.ExportCompliance.unavailable());
        var appConfig = new AppConfig() {
            @Override
            public String version() {
                return "9.8.7-test";
            }
        };
        var fleet = new FleetSnapshotService(
            com.avandocmsg.messenger.api.admin.fleet.FleetTargetRegistry.fromJson("[]"),
            stats,
            appConfig,
            null,
            30_000L
        );
        var resource = new AdminUiResource(manifest, stats, fleet, appConfig);
        Response r = resource.manifest();
        var body = (AdminManifestResponse) r.getEntity();
        assertEquals(1, body.sections().size());
        assertEquals("t1", body.sections().get(0).id());
        assertEquals("9.8.7-test", body.apiVersion());
    }

    @Test
    void exportComplianceGuide_includesCompletenessPolicy() {
        var manifest = AdminUiManifest.of(List.of());
        AdminStatsPort stats = () -> new AdminServerStatsResponse(
            "0",
            new AdminServerStatsResponse.JvmStats(1, 2, 3, 4, 5),
            new AdminServerStatsResponse.DependencyHealth(true, true, true),
            new AdminServerStatsResponse.TableCounts(0, 0, 0, false),
            AdminServerStatsResponse.ExportCompliance.unavailable());
        var appConfig = new AppConfig() {
            @Override
            public String version() {
                return "9.8.7-test";
            }

            @Override
            public java.util.Set<String> exportRequiredFields() {
                return java.util.Set.of("messages", "chat", "gdpr_disclosures");
            }

            @Override
            public boolean exportCompletenessStrict() {
                return false;
            }
        };
        var fleet = new FleetSnapshotService(
            com.avandocmsg.messenger.api.admin.fleet.FleetTargetRegistry.fromJson("[]"),
            stats,
            appConfig,
            null,
            30_000L
        );
        var resource = new AdminUiResource(manifest, stats, fleet, appConfig);
        var guide = resource.exportComplianceGuide();
        assertEquals(3, guide.completenessPolicy().requiredFields().size());
        assertEquals(false, guide.completenessPolicy().strict());
    }
}
