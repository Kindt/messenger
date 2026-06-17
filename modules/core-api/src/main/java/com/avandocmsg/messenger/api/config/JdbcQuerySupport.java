package com.avandocmsg.messenger.api.config;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** JDBC query timeout helper (PS-0.2). */
public final class JdbcQuerySupport {

    private JdbcQuerySupport() {
    }

    /**
     * @param timeoutSeconds {@code 0} or negative = no timeout (dev / disabled)
     */
    public static void applyTimeout(PreparedStatement stmt, int timeoutSeconds) throws SQLException {
        if (stmt == null || timeoutSeconds <= 0) {
            return;
        }
        stmt.setQueryTimeout(timeoutSeconds);
    }
}
