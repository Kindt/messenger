package com.avandocmsg.messenger.common.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** JDBC query timeout helper (spec 025 FR-069). */
public final class JdbcQuerySupport {

    private static final String ENV_TIMEOUT = "API_JDBC_QUERY_TIMEOUT_SECONDS";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

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

    public static int defaultTimeoutSeconds() {
        var raw = System.getenv(ENV_TIMEOUT);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public static void applyDefaultTimeout(PreparedStatement stmt) throws SQLException {
        applyTimeout(stmt, defaultTimeoutSeconds());
    }
}
