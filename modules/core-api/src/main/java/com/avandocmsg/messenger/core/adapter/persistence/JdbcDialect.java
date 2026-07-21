package com.avandocmsg.messenger.core.adapter.persistence;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcDialect {
    private JdbcDialect() {
    }

    static boolean isPostgres(Connection conn) throws SQLException {
        if (conn == null) {
            return false;
        }
        var meta = conn.getMetaData();
        if (meta == null) {
            return false;
        }
        var name = meta.getDatabaseProductName();
        return name != null && "PostgreSQL".equalsIgnoreCase(name);
    }
}
