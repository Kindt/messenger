package com.avandocmsg.messenger.desktop.sdk.conference;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.model.ConferenceResponse;
import com.avandocmsg.messenger.desktop.sdk.model.CreateConferenceRequest;

public final class ConferenceClient {

    private final KorusApiClient api;

    public ConferenceClient(KorusApiClient api) {
        this.api = api;
    }

    public ConferenceResponse create(String token, String chatId, String title) {
        return api.createConference(token, chatId, new CreateConferenceRequest(title));
    }
}
