package com.avandocmsg.messenger.api.config;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** JDBC query timeout helper (PS-0.2); delegates to {@link com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport}. */
public final class JdbcQuerySupport {

    private JdbcQuerySupport() {
    }

    /**
     * @param timeoutSeconds {@code 0} or negative = no timeout (dev / disabled)
     */
    public static void applyTimeout(PreparedStatement stmt, int timeoutSeconds) throws SQLException {
        com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport.applyTimeout(stmt, timeoutSeconds);
    }

    public static void applyDefaultTimeout(PreparedStatement stmt) throws SQLException {
        com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport.applyDefaultTimeout(stmt);
    }
}
