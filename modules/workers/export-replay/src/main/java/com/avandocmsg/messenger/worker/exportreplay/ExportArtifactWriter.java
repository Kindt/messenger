package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams export JSON to disk or zip entries without materializing the full payload as a String/byte[]. */
final class ExportArtifactWriter {

    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private ExportArtifactWriter() {
    }

    static void writePrettyJson(Path path, JsonNode root) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            writePrettyJson(out, root);
        }
    }

    static void writePrettyJson(OutputStream out, JsonNode root) throws IOException {
        var writer = MAPPER.writerWithDefaultPrettyPrinter();
        try (JsonGenerator gen = writer.createGenerator(out)) {
            gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            writer.writeValue(gen, root);
        }
    }

    static void writePrettyJsonZipEntry(ZipOutputStream zos, String entryName, JsonNode root) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        writePrettyJson(zos, root);
        zos.closeEntry();
    }
}
