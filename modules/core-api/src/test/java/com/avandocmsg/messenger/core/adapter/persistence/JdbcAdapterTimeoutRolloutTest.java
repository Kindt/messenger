package com.avandocmsg.messenger.core.adapter.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-069 inventory: every JDBC adapter/repository applies query timeout on prepareStatement paths. */
class JdbcAdapterTimeoutRolloutTest {

    private static final Path PERSISTENCE = Path.of(
        "src/main/java/com/avandocmsg/messenger/core/adapter/persistence");

    private static final Pattern ADAPTER_FILE = Pattern.compile("Jdbc.*(Adapter|Repository)\\.java");
    private static final Pattern PREPARE = Pattern.compile("\\.prepareStatement\\(");
    private static final Pattern TIMEOUT = Pattern.compile(
        "applyDefaultTimeout|applyTimeout\\(|applyQueryTimeout\\(");

    @Test
    void everyJdbcAdapterRepositoryAppliesQueryTimeout() throws IOException {
        if (!Files.isDirectory(PERSISTENCE)) {
            return;
        }
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.list(PERSISTENCE)) {
            files.filter(p -> ADAPTER_FILE.matcher(p.getFileName().toString()).matches())
                .forEach(p -> checkFile(p, violations));
        }
        assertTrue(violations.isEmpty(), "Missing JDBC query timeout:\n" + String.join("\n", violations));
    }

    private static void checkFile(Path file, List<String> violations) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            violations.add(file.getFileName() + ": unreadable");
            return;
        }
        if (!PREPARE.matcher(content).find()) {
            return;
        }
        int prepares = count(PREPARE, content);
        int timeouts = count(TIMEOUT, content);
        if (timeouts < prepares) {
            violations.add(file.getFileName() + ": prepareStatement=" + prepares + " timeoutCalls=" + timeouts);
            return;
        }
        var lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("prepareStatement")) {
                continue;
            }
            if (hasTimeoutBeforeExecute(lines, i)) {
                continue;
            }
            violations.add(file.getFileName() + ":" + (i + 1) + " prepareStatement without timeout before execute");
        }
    }

    private static boolean hasTimeoutBeforeExecute(String[] lines, int prepareLine) {
        for (int j = prepareLine; j < Math.min(prepareLine + 12, lines.length); j++) {
            var line = lines[j];
            if (TIMEOUT.matcher(line).find()) {
                return true;
            }
            if (line.contains("executeQuery") || line.contains("executeUpdate") || line.contains("execute(")) {
                return false;
            }
        }
        return false;
    }

    private static int count(Pattern pattern, String content) {
        int n = 0;
        var m = pattern.matcher(content);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
