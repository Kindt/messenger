package com.avandocmsg.messenger.desktop.sdk.attachments;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;

public final class AttachmentPathResolver {

    private final Path downloadsRoot;
    private final String profileSlug;

    public AttachmentPathResolver(Path downloadsRoot, String profileSlug) {
        this.downloadsRoot = downloadsRoot;
        this.profileSlug = profileSlug;
    }

    public Path resolve(String serverSlug, String fileId, String originalName) {
        return resolve(serverSlug, fileId, originalName, LocalDate.now());
    }

    public Path resolve(String serverSlug, String fileId, String originalName, LocalDate at) {
        var safeServer = slug(serverSlug);
        var safeName = slug(originalName);
        if (safeName.isBlank()) {
            safeName = "file";
        }
        return downloadsRoot
            .resolve("KorusMessenger")
            .resolve(slug(profileSlug))
            .resolve("attachments")
            .resolve(safeServer)
            .resolve(String.valueOf(at.getYear()))
            .resolve(String.format("%02d", at.getMonthValue()))
            .resolve(fileId + "-" + safeName);
    }

    private static String slug(String input) {
        var n = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (n.length() > 64) {
            n = n.substring(0, 64);
        }
        return n.isBlank() ? "item" : n;
    }
}
