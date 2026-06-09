package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** Newly created opaque public download link. */
public record CreatedPublicLink(String id, String rawToken, Instant expiresAt) {}
