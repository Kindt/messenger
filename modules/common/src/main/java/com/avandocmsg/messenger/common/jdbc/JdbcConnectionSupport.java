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

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute() throws Exception; // NOSONAR java:S112 — JDBC work may wrap checked SQLException and app exceptions
    }

    /** Begin/commit/rollback + restore autoCommit; keeps callers free of nested try blocks. */
    public static <T> T callInTransaction(Connection conn, SqlWork<T> work) throws Exception {
        beginTransaction(conn);
        try {
            T result = work.execute();
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            endTransaction(conn);
        }
    }

    public static void runInTransaction(Connection conn, SqlWork<Void> work) throws Exception {
        callInTransaction(conn, () -> {
            work.execute();
            return null;
        });
    }
}
