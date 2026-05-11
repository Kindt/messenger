package com.avandocmsg.messenger.worker.retention;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetentionShutdownTest {

    @Test
    void runCloseables_invokesInOrder() throws Exception {
        var order = new ArrayList<Integer>();
        RetentionShutdown.runCloseables(
            List.of(() -> order.add(1), () -> order.add(2), () -> order.add(3))
        );
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void runCloseables_continuesAfterFailure() throws Exception {
        var order = new ArrayList<Integer>();
        RetentionShutdown.runCloseables(
            List.of(
                () -> order.add(1),
                () -> {
                    throw new RuntimeException("boom");
                },
                () -> order.add(3)
            )
        );
        assertEquals(List.of(1, 3), order);
    }

    @Test
    void runCloseables_skipsNulls() throws Exception {
        var n = new AtomicInteger();
        List<AutoCloseable> list = new ArrayList<>();
        list.add(null);
        list.add(() -> {
            n.incrementAndGet();
        });
        RetentionShutdown.runCloseables(list);
        assertEquals(1, n.get());
    }
}
