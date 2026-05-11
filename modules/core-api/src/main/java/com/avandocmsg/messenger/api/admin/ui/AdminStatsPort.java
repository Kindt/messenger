package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;

@FunctionalInterface
public interface AdminStatsPort {

    AdminServerStatsResponse snapshot();
}
