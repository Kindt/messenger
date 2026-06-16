package com.avandocmsg.messenger.worker.exchangebridge.i18n;

import com.avandocmsg.messenger.common.i18n.BundleParityTestUtil;
import org.junit.jupiter.api.Test;

class MessagesExchangeBridgeBundleParityTest {

    private static final String BASE = "com/avandocmsg/messenger/i18n/messages_worker_exchange_bridge";

    @Test
    void ruEnSameKeys() throws Exception {
        BundleParityTestUtil.assertSameKeys(MessagesExchangeBridgeBundleParityTest.class.getClassLoader(), BASE);
    }
}
