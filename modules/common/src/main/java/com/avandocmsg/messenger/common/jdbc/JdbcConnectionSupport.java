package com.avandocmsg.messenger.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/** JDBC connection hygiene (spec 025 FR-094 / FR-113 / FR-179). */
public final class JdbcConnectionSupport {

    private JdbcConnectionSupport() {
    }

    public static void prepareRead(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        conn.setReadOnly(true);
        conn.setAutoCommit(true);
    }

    public static void prepareWrite(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        conn.setReadOnly(false);
        conn.setAutoCommit(true);
    }

    public static void beginTransaction(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        conn.setReadOnly(false);
        conn.setAutoCommit(false);
    }

    public static void endTransaction(Connection conn) throws SQLException {
        if (conn == null) {
            return;
        }
        conn.setAutoCommit(true);
        conn.setReadOnly(false);
    }
}
