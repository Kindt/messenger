package com.avandocmsg.messenger.api.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** Optional Solr client for {@link com.avandocmsg.messenger.api.search.MessageSearchService}. */
public final class SolrClientFactory {
    private static final Logger log = LoggerFactory.getLogger(SolrClientFactory.class);

    private SolrClientFactory() {}

    public record Binding(SolrClient client, boolean cloudMode) {
        public static Binding empty() {
            return new Binding(null, false);
        }

        public boolean enabled() {
            return client != null;
        }
    }

    public static Binding create(AppConfig config) {
        var zk = config.solrZkHosts();
        if (!zk.isBlank()) {
            var zkHosts = List.of(zk.split("\\s*,\\s*"));
            var client = new CloudSolrClient.Builder(zkHosts, Optional.empty()).build();
            client.setDefaultCollection(config.solrCollection());
            log.info("Solr Cloud client for collection {}", config.solrCollection());
            return new Binding(client, true);
        }
        var url = config.solrHttpUrl();
        if (!url.isBlank()) {
            var base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            var collection = config.solrCollection();
            var coreUrl = base.contains("/solr/" + collection) ? base : base + "/solr/" + collection;
            var client = new Http2SolrClient.Builder(coreUrl).build();
            log.info("Solr HTTP client baseUrl={}", coreUrl);
            return new Binding(client, false);
        }
        log.debug("Solr disabled (set SOLR_ZK or SOLR_URL)");
        return Binding.empty();
    }
}
