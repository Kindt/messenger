package com.avandocmsg.messenger.mobile.sdk.io

import java.nio.file.Files
import java.nio.file.Path

internal object KorusFileIo {
    fun readText(path: Path): String {
        if (!Files.exists(path)) return ""
        return path.toFile().readText(Charsets.UTF_8)
    }

    fun writeText(path: Path, text: String) {
        Files.createDirectories(path.parent)
        path.toFile().writeText(text, Charsets.UTF_8)
    }

    fun exists(path: Path): Boolean = Files.exists(path)

    fun createDirectories(path: Path) {
        Files.createDirectories(path)
    }
}
