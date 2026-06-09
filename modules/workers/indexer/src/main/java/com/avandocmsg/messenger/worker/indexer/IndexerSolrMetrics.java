package com.avandocmsg.messenger.worker.indexer;

import io.prometheus.client.Counter;

final class IndexerSolrMetrics {
    private static final Counter DELETE_TOTAL = Counter.build()
        .name("indexer_solr_delete_total")
        .help("Solr documents deleted by indexer worker")
        .register();

    private static final Counter CONTENT_CLEAR_TOTAL = Counter.build()
        .name("indexer_solr_content_clear_total")
        .help("Solr content_txt cleared by indexer worker")
        .register();

    private static final Counter ERRORS_TOTAL = Counter.build()
        .name("indexer_solr_errors_total")
        .help("Solr indexer operation failures")
        .register();

    private IndexerSolrMetrics() {
    }

    static void deleteSuccess() {
        DELETE_TOTAL.inc();
    }

    static void contentClearSuccess() {
        CONTENT_CLEAR_TOTAL.inc();
    }

    static void error() {
        ERRORS_TOTAL.inc();
    }
}
