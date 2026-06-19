package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.devices.dto.DeviceResponse;
import com.avandocmsg.messenger.api.repository.DeviceRepository;
import com.avandocmsg.messenger.core.port.DevicePort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class JdbcDeviceAdapter implements DevicePort {
    private final DeviceRepository delegate;

    public JdbcDeviceAdapter(DeviceRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcDeviceAdapter(DataSource dataSource, Clock clock,
                             com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator) {
        this.delegate = new DeviceRepository(dataSource, clock, uuidGenerator);
    }

    @Override
    public DeviceResponse upsertPushDevice(UUID userId, String deviceName, String pushProvider, String pushToken) {
        return delegate.upsertPushDevice(userId, deviceName, pushProvider, pushToken);
    }

    @Override
    public List<DeviceResponse> listForUser(UUID userId) {
        return delegate.listForUser(userId);
    }

    @Override
    public boolean clearPushToken(UUID userId, String deviceName) {
        return delegate.clearPushToken(userId, deviceName);
    }
}
