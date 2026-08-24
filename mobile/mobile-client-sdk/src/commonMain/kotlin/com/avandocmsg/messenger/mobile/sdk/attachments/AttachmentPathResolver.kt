package com.avandocmsg.messenger.mobile.sdk.attachments

import java.nio.file.Path
import java.time.LocalDate
import java.text.Normalizer

class AttachmentPathResolver(
    private val downloadsRoot: Path,
    private val profileSlug: String
) {
    fun resolve(serverSlug: String, fileId: String, originalName: String, at: LocalDate = LocalDate.now()): Path {
        val safeServer = slug(serverSlug)
        val safeName = slug(originalName).ifBlank { "file" }
        val dir = downloadsRoot
            .resolve("KorusMessenger")
            .resolve(slug(profileSlug))
            .resolve("attachments")
            .resolve(safeServer)
            .resolve(at.year.toString())
            .resolve(at.monthValue.toString().padStart(2, '0'))
        return dir.resolve("$fileId-$safeName")
    }

    private fun slug(input: String): String {
        val n = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .take(64)
        return n.ifBlank { "item" }
    }
}
