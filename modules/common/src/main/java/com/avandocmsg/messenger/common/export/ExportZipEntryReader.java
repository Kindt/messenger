package com.avandocmsg.messenger.common.export;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads a single named entry from an export zip stream. */
public final class ExportZipEntryReader {

    private ExportZipEntryReader() {
    }

    public static boolean containsEntry(InputStream zipStream, String entryName) throws IOException {
        try (var zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean streamEntry(InputStream zipStream, String entryName, Consumer<InputStream> consumer)
        throws IOException {
        try (var zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    consumer.accept(zis);
                    return true;
                }
            }
        }
        return false;
    }
}
