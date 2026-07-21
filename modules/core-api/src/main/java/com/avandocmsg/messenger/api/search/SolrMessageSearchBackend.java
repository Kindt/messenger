package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SolrMessageSearchBackend implements MessageSearchBackend {

    private final AppConfig appConfig;
    private final MessageQueryPort messageQueryPort;
    private final SolrClient solrClient;
    private final boolean solrCloud;

    public SolrMessageSearchBackend(
        AppConfig appConfig,
        MessageQueryPort messageQueryPort,
        SolrClient solrClient,
        boolean solrCloud
    ) {
        this.appConfig = appConfig;
        this.messageQueryPort = messageQueryPort;
        this.solrClient = solrClient;
        this.solrCloud = solrCloud;
    }

    @Override
    public String profileId() {
        return solrCloud ? "solr-cloud" : "solr-http";
    }

    @Override
    public boolean enabled() {
        return solrClient != null;
    }

    @Override
    public List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit)
        throws MessageSearchBackend.MessageSearchException {
        var allowedChats = new HashSet<>(chatIds);
        var contentQuery = buildContentQuery(query);
        if (contentQuery.isEmpty()) {
            return List.of();
        }

        var solrQuery = new SolrQuery(contentQuery);
        solrQuery.setRows(Math.min(limit * 25, 500));

        QueryResponse response;
        try {
            response = solrCloud
                ? solrClient.query(appConfig.solrCollection(), solrQuery)
                : solrClient.query(solrQuery);
        } catch (SolrServerException | IOException e) {
            throw new MessageSearchBackend.MessageSearchException("solr search failed", e);
        }

        var idOrder = collectAllowedMessageIds(response, allowedChats, limit * 5);
        if (idOrder.isEmpty()) {
            return List.of();
        }
        return messageQueryPort.loadMessagesForSearchResults(UserId.of(userId), idOrder, limit);
    }

    private static String buildContentQuery(String query) {
        var terms = query.split("\\s+");
        var qb = new StringBuilder();
        for (var term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (qb.length() > 0) {
                qb.append(" AND ");
            }
            qb.append("content_txt:").append(ClientUtils.escapeQueryChars(term));
        }
        return qb.toString();
    }

    private static List<String> collectAllowedMessageIds(
        QueryResponse response,
        Set<UUID> allowedChats,
        int maxIds
    ) {
        var idOrder = new ArrayList<String>();
        for (var doc : response.getResults()) {
            if (idOrder.size() >= maxIds) {
                break;
            }
            eligibleMessageId(doc, allowedChats).ifPresent(idOrder::add);
        }
        return idOrder;
    }

    private static Optional<String> eligibleMessageId(SolrDocument doc, Set<UUID> allowedChats) {
        var mid = (String) doc.getFieldValue("id");
        var cid = doc.getFieldValue("chat_id_s");
        if (mid == null || cid == null) {
            return Optional.empty();
        }
        try {
            if (!allowedChats.contains(UUID.fromString(cid.toString()))) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(mid);
    }
}
