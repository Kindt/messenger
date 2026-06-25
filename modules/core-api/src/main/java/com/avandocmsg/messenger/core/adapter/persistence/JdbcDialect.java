package com.avandocmsg.messenger.core.adapter.persistence;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcDialect {
    private JdbcDialect() {
    }

    static boolean isPostgres(Connection conn) throws SQLException {
        return "PostgreSQL".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
    }
}
