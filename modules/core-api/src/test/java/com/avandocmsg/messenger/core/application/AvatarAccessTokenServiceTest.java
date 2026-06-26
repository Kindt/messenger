package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.avatar.AvatarTokenVerifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AvatarAccessTokenServiceTest {

    private static final String SECRET = "test-secret-for-avatar-tokens";
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID FILE = UUID.randomUUID();

    @Test
    void mintAndVerify_roundTrip() {
        var service = new AvatarAccessTokenService(SECRET, null, 3600);
        var token = service.mint(VIEWER, FILE, 128, 128);
        var parsed = service.verify(token);
        assertTrue(parsed.isPresent());
        assertEquals(VIEWER, parsed.get().viewerId());
        assertEquals(FILE, parsed.get().fileId());
        assertEquals(128, parsed.get().width());
        assertEquals(128, parsed.get().height());
    }

    @Test
    void verify_rejectsTamperedDimensions() {
        var service = new AvatarAccessTokenService(SECRET, null, 3600);
        var token = service.mint(VIEWER, FILE, 128, 128);
        var parsed = AvatarTokenVerifier.verify(token, SECRET, null).orElseThrow();
        var tampered = com.avandocmsg.messenger.common.avatar.AvatarTokenMint.mint(
            SECRET, parsed.viewerId(), parsed.fileId(), 256, 256, parsed.expEpochSeconds());
        var wrong = service.verify(tampered);
        assertTrue(wrong.isPresent());
        assertNotEquals(128, wrong.get().width());
    }

    @Test
    void verify_acceptsPreviousSecret() {
        var oldSecret = "old-secret";
        var newSecret = "new-secret";
        var exp = (System.currentTimeMillis() / 1000) + 600;
        var token = com.avandocmsg.messenger.common.avatar.AvatarTokenMint.mint(
            oldSecret, VIEWER, FILE, 64, 64, exp);
        var service = new AvatarAccessTokenService(newSecret, oldSecret, 3600);
        assertTrue(service.verify(token).isPresent());
    }

    @Test
    void verify_rejectsExpiredToken() {
        var exp = (System.currentTimeMillis() / 1000) - 10;
        var token = com.avandocmsg.messenger.common.avatar.AvatarTokenMint.mint(
            SECRET, VIEWER, FILE, 64, 64, exp);
        var service = new AvatarAccessTokenService(SECRET, null, 3600);
        assertTrue(service.verify(token).isEmpty());
    }

    @Test
    void clampDimension_capsAt512() {
        assertEquals(512, AvatarAccessTokenService.clampDimension(999));
        assertEquals(1, AvatarAccessTokenService.clampDimension(0));
    }
}
