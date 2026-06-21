package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import org.apache.solr.client.solrj.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Message search: Solr ({@code content_txt}) when configured, else SQL ILIKE on plaintext rows.
 * ACL: membership + block directions (ТЗ п. 10) applied in SQL; Solr path post-filters by chat membership.
 */
public class MessageSearchService {
    private static final Logger log = LoggerFactory.getLogger(MessageSearchService.class);

    private final ChatPersistencePort chatPersistencePort;
    private final SearchBackendBinding backendBinding;

    public MessageSearchService(AppConfig appConfig, MessageQueryPort messageQueryPort,
                                ChatPersistencePort chatPersistencePort, SolrClient solrClient, boolean solrCloud) {
        this(chatPersistencePort, new SearchBackendBinding(
            new SolrMessageSearchBackend(appConfig, messageQueryPort, solrClient, solrCloud),
            new SqlMessageSearchBackend(messageQueryPort)
        ));
    }

    MessageSearchService(ChatPersistencePort chatPersistencePort, SearchBackendBinding backendBinding) {
        this.chatPersistencePort = chatPersistencePort;
        this.backendBinding = backendBinding;
    }

    public boolean solrEnabled() {
        return backendBinding.primaryEnabled();
    }

    public List<MessageResponse> search(UUID userId, String rawQuery, int limit) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        int lim = Math.min(Math.max(limit, 1), 100);
        var chatIds = chatPersistencePort.listChatIdsForUser(userId);
        if (chatIds.isEmpty()) {
            return List.of();
        }
        if (backendBinding.primaryEnabled()) {
            try {
                return backendBinding.primary().search(userId, chatIds, rawQuery.trim(), lim);
            } catch (Exception e) {
                log.warn("{} search failed, falling back to {}: {}",
                    backendBinding.primary().profileId(),
                    backendBinding.fallback() != null ? backendBinding.fallback().profileId() : "none",
                    e.getMessage());
            }
        }
        if (!backendBinding.fallbackEnabled()) {
            return List.of();
        }
        try {
            return backendBinding.fallback().search(userId, chatIds, rawQuery.trim(), lim);
        } catch (Exception e) {
            log.warn("{} search failed: {}", backendBinding.fallback().profileId(), e.getMessage());
            return List.of();
        }
    }
}
