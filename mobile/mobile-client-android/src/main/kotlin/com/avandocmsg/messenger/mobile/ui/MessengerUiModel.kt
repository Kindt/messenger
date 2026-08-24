package com.avandocmsg.messenger.mobile.ui

internal fun avatarInitials(label: String?): String {
    val words = label
        .orEmpty()
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "?"
    }
}

internal fun chatDisplayTitle(chatId: String, title: String?): String {
    val realTitle = title?.trim().orEmpty()
    if (realTitle.isNotEmpty()) return realTitle

    return "Чат ${chatId.take(8)}"
}

internal fun userFacingError(error: String?): String {
    val detail = error?.trim().orEmpty()
    return when {
        detail.startsWith("Queued offline:", ignoreCase = true) ->
            "Не удалось отправить. Сообщение сохранено в очередь"
        detail.isEmpty() -> "Не удалось выполнить действие"
        else -> detail
    }
}
