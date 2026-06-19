package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.devices.dto.DeviceResponse;

import java.util.List;
import java.util.UUID;

/** Push device registry. */
public interface DevicePort {
    DeviceResponse upsertPushDevice(UUID userId, String deviceName, String pushProvider, String pushToken);

    List<DeviceResponse> listForUser(UUID userId);

    boolean clearPushToken(UUID userId, String deviceName);
}
