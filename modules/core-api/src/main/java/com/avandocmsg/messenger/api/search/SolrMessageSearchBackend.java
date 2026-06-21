package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    public List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) throws Exception {
        var allowedChats = new HashSet<>(chatIds);
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
        if (qb.isEmpty()) {
            return List.of();
        }

        var solrQuery = new SolrQuery(qb.toString());
        solrQuery.setRows(Math.min(limit * 25, 500));

        QueryResponse response = solrCloud
            ? solrClient.query(appConfig.solrCollection(), solrQuery)
            : solrClient.query(solrQuery);

        var idOrder = new ArrayList<String>();
        for (var doc : response.getResults()) {
            var mid = (String) doc.getFieldValue("id");
            var cid = doc.getFieldValue("chat_id_s");
            if (mid == null || cid == null) {
                continue;
            }
            try {
                if (!allowedChats.contains(UUID.fromString(cid.toString()))) {
                    continue;
                }
            } catch (IllegalArgumentException ex) {
                continue;
            }
            idOrder.add(mid);
            if (idOrder.size() >= limit * 5) {
                break;
            }
        }
        if (idOrder.isEmpty()) {
            return List.of();
        }
        return messageQueryPort.loadMessagesForSearchResults(UserId.of(userId), idOrder, limit);
    }
}
