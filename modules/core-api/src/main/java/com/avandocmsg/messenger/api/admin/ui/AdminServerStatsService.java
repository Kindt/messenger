package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;

/**
 * Сводная статистика для встроенной админ-панели «Статистика сервера».
 */
public final class AdminServerStatsService implements AdminStatsPort {

    private final DataSource dataSource;
    private final AppConfig appConfig;
    private final NatsConnectionStatus natsConnectionStatus;

    public AdminServerStatsService(DataSource dataSource, AppConfig appConfig, NatsConnectionStatus natsConnectionStatus) {
        this.dataSource = dataSource;
        this.appConfig = appConfig;
        this.natsConnectionStatus = natsConnectionStatus;
    }

    @Override
    public AdminServerStatsResponse snapshot() {
        var rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapCommitted = rt.totalMemory();
        long heapMax = rt.maxMemory();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        boolean dbOk = pingDatabase();
        boolean redisOk = pingRedis();
        boolean natsOk = natsConnectionStatus.natsClientConnected();

        TableScan counts = countTables();

        return new AdminServerStatsResponse(
            appConfig.version(),
            new AdminServerStatsResponse.JvmStats(heapUsed, heapCommitted, heapMax,
                rt.availableProcessors(), uptime),
            new AdminServerStatsResponse.DependencyHealth(dbOk, redisOk, natsOk),
            new AdminServerStatsResponse.TableCounts(counts.users, counts.chats, counts.messages, counts.ok)
        );
    }

    private boolean pingDatabase() {
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement("SELECT 1");
             var rs = st.executeQuery()) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean pingRedis() {
        try (var client = RedisClient.create(RedisURI.create(appConfig.redisUri()));
             var conn = client.connect()) {
            return "PONG".equals(conn.sync().ping());
        } catch (Exception e) {
            return false;
        }
    }

    private TableScan countTables() {
        try (var conn = dataSource.getConnection()) {
            long users = count(conn, "SELECT COUNT(*) FROM users");
            long chats = count(conn, "SELECT COUNT(*) FROM chats");
            long messages = count(conn, "SELECT COUNT(*) FROM messages");
            return new TableScan(users, chats, messages, true);
        } catch (Exception e) {
            return new TableScan(0, 0, 0, false);
        }
    }

    private static long count(java.sql.Connection conn, String sql) throws Exception {
        try (var st = conn.prepareStatement(sql);
             var rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        }
    }

    private record TableScan(long users, long chats, long messages, boolean ok) {}
}
