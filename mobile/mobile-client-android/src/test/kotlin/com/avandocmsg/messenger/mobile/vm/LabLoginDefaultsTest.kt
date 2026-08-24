package com.avandocmsg.messenger.mobile.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LabLoginDefaultsTest {
    @Test
    fun labFormUsesMessengerUserNotAdmin() {
        val state = AppState()
        assertEquals("user1", state.username)
        assertEquals("12345", state.password)
        assertNotEquals("csadmin", state.username)
    }
}
