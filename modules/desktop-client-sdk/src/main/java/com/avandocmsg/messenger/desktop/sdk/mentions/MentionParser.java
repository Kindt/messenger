package com.avandocmsg.messenger.desktop.sdk.mentions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses @mentions similar to web composer (user id or @all). */
public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile("@([a-zA-Z0-9._-]+)");

    private MentionParser() {}

    public static List<String> parseMentionedUserIds(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        var out = new ArrayList<String>();
        Matcher m = MENTION.matcher(content);
        while (m.find()) {
            var id = m.group(1);
            if (!"all".equalsIgnoreCase(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    public static boolean mentionsAll(String content) {
        return content != null && content.toLowerCase().contains("@all");
    }
}
