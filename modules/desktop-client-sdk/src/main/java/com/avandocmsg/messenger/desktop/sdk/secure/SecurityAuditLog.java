package com.avandocmsg.messenger.desktop.sdk.secure;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;

/** Append-only security audit trail (FSTEC ИС audit control). */
public final class SecurityAuditLog {

    private final Path file;

    public SecurityAuditLog(Path stateDir) throws IOException {
        Files.createDirectories(stateDir);
        this.file = stateDir.resolve("security-audit.jsonl");
        if (!Files.exists(file)) {
            Files.writeString(file, "");
        }
    }

    public synchronized void record(String event, String detail) {
        try {
            var row = new HashMap<String, String>();
            row.put("ts", Instant.now().toString());
            row.put("event", event);
            row.put("detail", detail == null ? "" : detail);
            var line = JsonSupport.mapper().writeValueAsString(row) + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("audit log", e);
        }
    }

    public Path file() {
        return file;
    }

    public long lineCount() throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        try (var lines = Files.lines(file)) {
            return lines.count();
        }
    }
}
