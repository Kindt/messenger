package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** In-chat polls (spec 022 Phase 5). */
public interface ChatPollPort {
    UUID create(CreatePoll cmd);

    Optional<PollRow> find(UUID pollId);

    List<PollRow> listForChat(UUID chatId, int limit);

    boolean vote(UUID pollId, UUID userId, List<Integer> optionIndexes);

    /** Polls whose {@code closes_at} is in the past (notification / closure hook). */
    List<PollRow> listDue(Instant now, int limit);

    /** No-op for MVP (polls have no status column); reserved for future lifecycle. */
    boolean updateStatus(UUID pollId, String status);

    /** Set poll close time (manual close or schedule). */
    boolean setClosesAt(UUID pollId, Instant closesAt);

    List<VoteRow> listVotes(UUID pollId);

    record CreatePoll(
        UUID chatId,
        UUID createdBy,
        String question,
        List<String> options,
        boolean allowMultiple,
        Instant closesAt
    ) {}

    record PollRow(
        UUID id,
        UUID chatId,
        UUID createdBy,
        String question,
        List<String> options,
        boolean allowMultiple,
        Instant closesAt,
        Instant createdAt
    ) {}

    record VoteRow(
        UUID pollId,
        UUID userId,
        List<Integer> optionIndexes,
        Instant votedAt
    ) {}
}
