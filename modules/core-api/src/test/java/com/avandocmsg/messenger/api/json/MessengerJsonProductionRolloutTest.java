package com.avandocmsg.messenger.api.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-035/FR-036: production sources must use shared {@link com.avandocmsg.messenger.common.json.MessengerJson}. */
class MessengerJsonProductionRolloutTest {

    private static final Pattern RAW_MAPPER = Pattern.compile("new\\s+ObjectMapper\\s*\\(\\s*\\)");

    @Test
    void productionJavaSources_doNotInstantiateObjectMapper() throws IOException {
        var repoRoot = Path.of("..", "..").toAbsolutePath().normalize();
        var offenders = new ArrayList<String>();
        for (var moduleRoot : List.of("modules", "services")) {
            var base = repoRoot.resolve(moduleRoot);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (var walk = Files.walk(base)) {
                walk.filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> scanFile(p, offenders));
            }
        }
        assertTrue(offenders.isEmpty(), "raw ObjectMapper in production: " + offenders);
    }

    private static void scanFile(Path file, List<String> offenders) {
        try {
            var text = Files.readString(file);
            if (RAW_MAPPER.matcher(text).find()) {
                offenders.add(file.toString());
            }
        } catch (IOException e) {
            offenders.add(file + " (read failed)");
        }
    }
}
