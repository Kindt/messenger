package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Message search: Solr ({@code content_txt}) when configured, else SQL ILIKE on plaintext rows.
 * ACL: membership + block directions (ТЗ п. 10) applied in SQL; Solr path post-filters by chat membership.
 */
public class MessageSearchService {
    private static final Logger log = LoggerFactory.getLogger(MessageSearchService.class);

    private final AppConfig appConfig;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final SolrClient solrClient;
    private final boolean solrCloud;

    public MessageSearchService(AppConfig appConfig, MessageRepository messageRepository,
                                  ChatRepository chatRepository, SolrClient solrClient, boolean solrCloud) {
        this.appConfig = appConfig;
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
        this.solrClient = solrClient;
        this.solrCloud = solrCloud;
    }

    public boolean solrEnabled() {
        return solrClient != null;
    }

    public List<MessageResponse> search(UUID userId, String rawQuery, int limit) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        int lim = Math.min(Math.max(limit, 1), 100);
        var chatIds = chatRepository.listChatIdsForUser(userId);
        if (chatIds.isEmpty()) {
            return List.of();
        }
        if (solrClient != null) {
            try {
                return searchSolr(userId, chatIds, rawQuery.trim(), lim);
            } catch (Exception e) {
                log.warn("Solr search failed, falling back to SQL: {}", e.getMessage());
            }
        }
        return messageRepository.searchPlaintextForUser(userId, chatIds, rawQuery.trim(), lim);
    }

    private List<MessageResponse> searchSolr(UUID userId, List<UUID> chatIds, String q, int limit) throws Exception {
        var allowedChats = new HashSet<>(chatIds);
        var terms = q.split("\\s+");
        var qb = new StringBuilder();
        for (var term : terms) {
            if (term.isBlank()) continue;
            if (qb.length() > 0) qb.append(" AND ");
            qb.append("content_txt:").append(ClientUtils.escapeQueryChars(term));
        }
        if (qb.isEmpty()) {
            return List.of();
        }

        var solrQuery = new SolrQuery(qb.toString());
        solrQuery.setRows(Math.min(limit * 25, 500));

        QueryResponse response = solrCloud
            ? solrClient.query(appConfig.solrCollection(), solrQuery)
            : solrClient.query(solrQuery);

        var docs = response.getResults();
        var idOrder = new ArrayList<String>();
        for (var doc : docs) {
            var mid = (String) doc.getFieldValue("id");
            var cid = doc.getFieldValue("chat_id_s");
            if (mid == null || cid == null) continue;
            try {
                var chatUuid = UUID.fromString(cid.toString());
                if (!allowedChats.contains(chatUuid)) continue;
            } catch (IllegalArgumentException ex) {
                continue;
            }
            idOrder.add(mid);
            if (idOrder.size() >= limit * 5) break;
        }
        if (idOrder.isEmpty()) {
            return List.of();
        }
        return messageRepository.loadMessagesForSearchResults(userId, idOrder, limit);
    }
}
