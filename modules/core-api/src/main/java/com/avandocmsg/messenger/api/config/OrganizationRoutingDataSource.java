package com.avandocmsg.messenger.api.config;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * FR-OPT-09 phase-B: routes JDBC connections by {@link OrgRoutingContext} via {@link OrganizationShardRouter}.
 * Primary-only when shard pool unset or org context missing.
 */
public final class OrganizationRoutingDataSource implements DataSource {
    private final DataSource primary;
    private final DataSource shard;

    public OrganizationRoutingDataSource(DataSource primary, DataSource shard) {
        this.primary = primary;
        this.shard = shard;
    }

    private DataSource active() {
        return OrganizationShardRouter.routeForOrg(primary, shard, OrgRoutingContext.get());
    }

    @Override
    public Connection getConnection() throws SQLException {
        return active().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return active().getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return primary.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        primary.setLogWriter(out);
        if (shard != null) {
            shard.setLogWriter(out);
        }
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        primary.setLoginTimeout(seconds);
        if (shard != null) {
            shard.setLoginTimeout(seconds);
        }
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return primary.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return primary.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return primary.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || primary.isWrapperFor(iface);
    }
}
