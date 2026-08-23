package com.avandocmsg.messenger.desktop.sdk.parity;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-checks VPP web ui-block manifest vs desktop implementation map (offline).
 */
public final class WebParityAuditor {

    private static final Set<String> DESKTOP_IMPL = Set.of(
        "auth",
        "sidebar",
        "thread-header",
        "thread-body",
        "composer",
        "settings-general",
        "settings-profile",
        "settings-notifications",
        "settings-security",
        "call-panel",
        "integrations-panel",
        "productivity-actions",
        "collaboration-panel",
        "e2ee-controls"
    );

    private static final Set<String> DESKTOP_DEFER = Set.of(
        "admin-shell",
        "admin-product-modules",
        "admin-retention",
        "admin-plugins"
    );

    private final ObjectMapper mapper = JsonSupport.mapper();

    public ParityReport audit(Path uiBlockManifest, Path featureMatrix) throws IOException {
        var blocks = mapper.readTree(Files.readString(uiBlockManifest)).get("blocks");
        var matrix = mapper.readTree(Files.readString(featureMatrix)).get("rows");
        var rows = new ArrayList<ParityRow>();
        for (JsonNode block : blocks) {
            var id = block.get("id").asText();
            var status = classify(id);
            rows.add(new ParityRow(id, block.path("zone").asText(""), status, noteFor(id)));
        }
        var matrixIds = new ArrayList<String>();
        for (JsonNode row : matrix) {
            matrixIds.add(row.get("id").asText());
        }
        return new ParityReport(rows, matrixIds);
    }

    private static String classify(String blockId) {
        if (DESKTOP_IMPL.contains(blockId)) {
            return "implemented_ui";
        }
        if (DESKTOP_DEFER.contains(blockId)) {
            return "deferred_browser_only";
        }
        return "gap";
    }

    private static String noteFor(String blockId) {
        return switch (blockId) {
            case "settings-general", "settings-profile", "settings-notifications", "settings-security" ->
                "SettingsView tabs (offline)";
            case "call-panel" -> "Open URL / demo stub";
            case "e2ee-controls" -> "Deferred ADR";
            case "admin-shell", "admin-product-modules", "admin-retention", "admin-plugins" ->
                "Admin stays in browser";
            default -> "";
        };
    }

    public record ParityRow(String blockId, String zone, String status, String note) {}

    public record ParityReport(List<ParityRow> webBlocks, List<String> matrixRowIds) {
        public Map<String, Long> countsByStatus() {
            var m = new LinkedHashMap<String, Long>();
            for (var r : webBlocks) {
                m.merge(r.status(), 1L, Long::sum);
            }
            return m;
        }

        public List<ParityRow> gaps() {
            return webBlocks.stream().filter(r -> "gap".equals(r.status)).toList();
        }
    }
}
