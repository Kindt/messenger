package com.avandocmsg.messenger.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkerMdcSupportTest {

    @AfterEach
    void tearDown() {
        WorkerMdcSupport.clear();
    }

    @Test
    void applyCorrelation_setsProvidedValues() {
        WorkerMdcSupport.applyCorrelation("req-abc", "user-1");
        assertEquals("user-1", MDC.get(WorkerMdcSupport.USER_ID));
        assertEquals("req-abc", MDC.get(WorkerMdcSupport.X_REQUEST_ID));
    }

    @Test
    void applyCorrelation_generatesRequestIdWhenMissing() {
        WorkerMdcSupport.applyCorrelation(null, null);
        assertNotNull(MDC.get(WorkerMdcSupport.X_REQUEST_ID));
    }
}
