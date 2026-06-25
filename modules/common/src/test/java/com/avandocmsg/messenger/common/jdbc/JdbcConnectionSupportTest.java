package com.avandocmsg.messenger.common.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcConnectionSupportTest {

    @Test
    void prepareRead_setsReadOnlyAndAutoCommit() throws Exception {
        var conn = mock(Connection.class);
        JdbcConnectionSupport.prepareRead(conn);
        verify(conn).setReadOnly(true);
        verify(conn).setAutoCommit(true);
    }

    @Test
    void beginTransaction_disablesAutoCommit() throws Exception {
        var conn = mock(Connection.class);
        JdbcConnectionSupport.beginTransaction(conn);
        verify(conn).setReadOnly(false);
        verify(conn).setAutoCommit(false);
    }
}
