package com.avandocmsg.messenger.api.polls;

import com.avandocmsg.messenger.api.polls.dto.CreatePollRequest;
import com.avandocmsg.messenger.api.polls.dto.PollResponse;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ChatPollPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ChatPollService {
    private static final Logger log = LoggerFactory.getLogger(ChatPollService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ChatPollPort chatPollPort;
    private final ChatPersistencePort chatPersistencePort;
    private final Clock clock;

    public ChatPollService(ChatPollPort chatPollPort, ChatPersistencePort chatPersistencePort, Clock clock) {
        this.chatPollPort = chatPollPort;
        this.chatPersistencePort = chatPersistencePort;
        this.clock = clock;
    }

    public Optional<PollResponse> create(UUID chatId, UUID userId, CreatePollRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()
            || request.options() == null || request.options().isEmpty()) {
            return Optional.empty();
        }
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            log.warn("Poll create denied: user {} not in chat {}", userId, chatId);
            return Optional.empty();
        }
        Instant closesAt = null;
        if (request.closesAt() != null && !request.closesAt().isBlank()) {
            try {
                closesAt = Instant.parse(request.closesAt().trim());
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        var allowMultiple = Boolean.TRUE.equals(request.allowMultiple());
        var id = chatPollPort.create(new ChatPollPort.CreatePoll(
            chatId, userId, request.question().trim(), request.options(), allowMultiple, closesAt));
        if (id == null) {
            return Optional.empty();
        }
        return chatPollPort.find(id).map(row -> toResponse(row, List.of()));
    }

    public List<PollResponse> listForChat(UUID chatId, UUID userId, int limit) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return List.of();
        }
        return chatPollPort.listForChat(chatId, limit).stream()
            .map(row -> toResponse(row, aggregateVotes(row)))
            .toList();
    }

    public Optional<PollResponse> get(UUID chatId, UUID pollId, UUID userId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        return chatPollPort.find(pollId)
            .filter(row -> row.chatId().equals(chatId))
            .map(row -> toResponse(row, aggregateVotes(row)));
    }

    /** @return empty when denied; {@code Optional.of(false)} when invalid vote; {@code Optional.of(true)} on success */
    public Optional<Boolean> vote(UUID chatId, UUID pollId, UUID userId, List<Integer> optionIndexes) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        var poll = chatPollPort.find(pollId).filter(row -> row.chatId().equals(chatId));
        if (poll.isEmpty()) {
            return Optional.empty();
        }
        var row = poll.get();
        if (isClosed(row)) {
            return Optional.of(false);
        }
        if (optionIndexes == null || optionIndexes.isEmpty()) {
            return Optional.of(false);
        }
        if (!row.allowMultiple() && optionIndexes.size() > 1) {
            return Optional.of(false);
        }
        var optionCount = row.options().size();
        for (var idx : optionIndexes) {
            if (idx == null || idx < 0 || idx >= optionCount) {
                return Optional.of(false);
            }
        }
        return Optional.of(chatPollPort.vote(pollId, userId, optionIndexes));
    }

    /** Manual close by poll creator. */
    public Optional<PollResponse> close(UUID chatId, UUID pollId, UUID userId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return Optional.empty();
        }
        var poll = chatPollPort.find(pollId).filter(row -> row.chatId().equals(chatId));
        if (poll.isEmpty()) {
            return Optional.empty();
        }
        var row = poll.get();
        if (!row.createdBy().equals(userId)) {
            return Optional.empty();
        }
        if (isClosed(row)) {
            return Optional.of(toResponse(row, aggregateVotes(row)));
        }
        if (!chatPollPort.setClosesAt(pollId, clock.instant())) {
            return Optional.empty();
        }
        return chatPollPort.find(pollId).map(r -> toResponse(r, aggregateVotes(r)));
    }

    private List<Integer> aggregateVotes(ChatPollPort.PollRow row) {
        var counts = new ArrayList<Integer>();
        for (int i = 0; i < row.options().size(); i++) {
            counts.add(0);
        }
        for (var vote : chatPollPort.listVotes(row.id())) {
            for (var idx : vote.optionIndexes()) {
                if (idx != null && idx >= 0 && idx < counts.size()) {
                    counts.set(idx, counts.get(idx) + 1);
                }
            }
        }
        return counts;
    }

    private boolean isClosed(ChatPollPort.PollRow row) {
        return row.closesAt() != null && !row.closesAt().isAfter(clock.instant());
    }

    private PollResponse toResponse(ChatPollPort.PollRow row, List<Integer> voteCounts) {
        return new PollResponse(
            row.id().toString(),
            row.chatId().toString(),
            row.createdBy().toString(),
            row.question(),
            row.options(),
            row.allowMultiple(),
            row.closesAt() != null ? ISO.format(row.closesAt()) : null,
            row.createdAt() != null ? ISO.format(row.createdAt()) : null,
            voteCounts,
            isClosed(row));
    }
}
