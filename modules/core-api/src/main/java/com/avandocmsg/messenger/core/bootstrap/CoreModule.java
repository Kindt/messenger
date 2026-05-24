package com.avandocmsg.messenger.core.bootstrap;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;

import javax.sql.DataSource;

/** Composition-root helpers for hexagonal wiring (Phase 2a). */
public final class CoreModule {
    private CoreModule() {
    }

    public static ChatRepositoryPort chatRepositoryPort(DataSource dataSource) {
        return new JdbcChatRepositoryAdapter(dataSource);
    }

    public static ChatApplicationService chatApplicationService(DataSource dataSource, ChatRepository legacy) {
        return new ChatApplicationService(chatRepositoryPort(dataSource), legacy);
    }
}
