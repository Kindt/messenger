package com.avandocmsg.messenger.api.crypto;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoResourceTest {

    @Test
    void deleteKeyPackage_invalidId_throwsInvalidUuidParameterException() {
        var resource = new CryptoResource(null, null, null, null, null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.deleteKeyPackage("not-a-uuid"));
    }
}
