package com.avandocmsg.messenger.core.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code @uuid} and {@code @all} tokens from plaintext message bodies. */
public final class MessageMentionParser {
    private static final Pattern UUID_MENTION = Pattern.compile(
        "@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final Pattern ALL_MENTION = Pattern.compile("(?i)\\B@all\\b");

    private MessageMentionParser() {
    }

    public record ParsedMentions(boolean mentionAll, List<UUID> userIds) {}

    public static ParsedMentions parse(String content) {
        if (content == null || content.isBlank()) {
            return new ParsedMentions(false, List.of());
        }
        boolean all = ALL_MENTION.matcher(content).find();
        Set<UUID> ids = new LinkedHashSet<>();
        Matcher m = UUID_MENTION.matcher(content);
        while (m.find()) {
            try {
                ids.add(UUID.fromString(m.group(1)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed token
            }
        }
        return new ParsedMentions(all, List.copyOf(ids));
    }
}
