package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.devices.dto.DeviceResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcDeviceAdapter;
import com.avandocmsg.messenger.core.port.DevicePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Legacy façade for device JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcDeviceAdapter}.
 */
public class DeviceRepository {
    private final DevicePort port;

    public DeviceRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.port = new JdbcDeviceAdapter(dataSource, clock, uuidGenerator);
    }

    DeviceRepository(DevicePort port) {
        this.port = port;
    }

    public DeviceResponse upsertPushDevice(UUID userId, String deviceName, String pushProvider, String pushToken) {
        return port.upsertPushDevice(userId, deviceName, pushProvider, pushToken);
    }

    public List<DeviceResponse> listForUser(UUID userId) {
        return port.listForUser(userId);
    }

    public boolean clearPushToken(UUID userId, String deviceName) {
        return port.clearPushToken(userId, deviceName);
    }
}
