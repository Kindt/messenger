package com.avandocmsg.messenger.api.blocks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Заблокировать пользователя по идентификатору (полная блокировка по ТЗ). */
public record BlockUserRequest(@JsonProperty("user_id") String userId) {}
