package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Ответ для диагностики: JWT/sync пользователя и версия API (только realm admin). */
public record AdminSessionResponse(
    @JsonProperty("user_id") String userId,
    String username,
    @JsonProperty("realm_roles") List<String> realmRoles,
    @JsonProperty("api_version") String apiVersion
) {}
