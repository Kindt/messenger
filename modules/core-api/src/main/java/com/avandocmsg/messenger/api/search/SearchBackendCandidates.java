package com.avandocmsg.messenger.api.search;

public final class SearchBackendCandidates {

    private SearchBackendCandidates() {
    }

    public static MessageSearchBackend opensearch() {
        return new CandidateMessageSearchBackend("opensearch-candidate");
    }

    public static MessageSearchBackend elasticsearch() {
        return new CandidateMessageSearchBackend("elasticsearch-candidate");
    }
}
