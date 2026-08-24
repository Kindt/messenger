package com.avandocmsg.messenger.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MessengerUiModelTest {
    @Test
    fun avatarInitialsUseTheFirstTwoWords() {
        assertEquals("АК", avatarInitials("Анна Каренина"))
    }

    @Test
    fun avatarInitialsUseTwoLettersForASingleWord() {
        assertEquals("LA", avatarInitials("Lab"))
    }

    @Test
    fun avatarInitialsHaveAnHonestFallback() {
        assertEquals("?", avatarInitials("  "))
    }

    @Test
    fun chatDisplayTitleUsesRealTitle() {
        assertEquals("Команда продукта", chatDisplayTitle("chat-12345678", "  Команда продукта  "))
    }

    @Test
    fun chatDisplayTitleFallsBackToTheRealIdentifier() {
        assertEquals("Чат chat-123", chatDisplayTitle("chat-12345678", null))
    }

    @Test
    fun queuedFailureDoesNotClaimTheDeviceIsOffline() {
        assertEquals(
            "Не удалось отправить. Сообщение сохранено в очередь",
            userFacingError("Queued offline: timeout")
        )
    }

    @Test
    fun userFacingErrorKeepsUsefulSafeText() {
        assertEquals("Неверный логин", userFacingError("Неверный логин"))
    }
}
