package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JdbcQuerySupportTest {

    @Test
    void applyTimeout_setsSecondsWhenPositive() throws Exception {
        var stmt = mock(PreparedStatement.class);
        JdbcQuerySupport.applyTimeout(stmt, 30);
        verify(stmt).setQueryTimeout(30);
    }

    @Test
    void applyTimeout_skipsWhenZeroOrNegative() throws Exception {
        var stmt = mock(PreparedStatement.class);
        JdbcQuerySupport.applyTimeout(stmt, 0);
        JdbcQuerySupport.applyTimeout(stmt, -1);
        verify(stmt, never()).setQueryTimeout(org.mockito.ArgumentMatchers.anyInt());
    }
}
