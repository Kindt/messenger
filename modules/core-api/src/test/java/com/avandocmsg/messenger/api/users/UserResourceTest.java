package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UserResourceTest {

    @Test
    void getById_invalidPathId_throwsInvalidUuidParameterException() {
        var resource = new UserResource(null, null, I18nTestFixtures.messagesEn());
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.getById("not-a-uuid"));
    }
}
