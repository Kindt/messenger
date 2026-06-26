package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.AvatarAccessPort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileApplicationServiceTest {

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    private final StubFilePort filePort = new StubFilePort();
    private final StubMessageQueryPort messageQuery = new StubMessageQueryPort();
    private final StubObjectStorage storage = new StubObjectStorage();
    private final FileApplicationService service = new FileApplicationService(
        filePort, messageQuery, storage, () -> fileId, 1024, false);

    @Test
    void getMetadataForUser_returnsFileForOwner() {
        filePort.file = sampleFile();

        var result = service.getMetadataForUser(UserId.of(ownerId), FileId.of(fileId));
        assertTrue(result.isPresent());
    }

    @Test
    void getMetadataForUser_returnsFileForChatMember() {
        filePort.file = sampleFile();
        messageQuery.mayAccess = true;

        var result = service.getMetadataForUser(UserId.of(viewerId), FileId.of(fileId));
        assertTrue(result.isPresent());
    }

    @Test
    void getMetadataForUser_deniesOutsider() {
        filePort.file = sampleFile();
        messageQuery.mayAccess = false;

        assertTrue(service.getMetadataForUser(UserId.of(viewerId), FileId.of(fileId)).isEmpty());
    }

    @Test
    void mayView_allowsAvatarAccessPort() {
        filePort.file = sampleFile();
        messageQuery.mayAccess = false;
        var avatarService = new FileApplicationService(
            filePort, messageQuery, storage, new StubAvatarAccessPort(true), () -> fileId, 1024, false);

        assertTrue(avatarService.mayView(sampleFile(), UserId.of(viewerId)));
    }

    @Test
    void mayView_deniesWhenAvatarAccessPortFalse() {
        filePort.file = sampleFile();
        messageQuery.mayAccess = false;
        var avatarService = new FileApplicationService(
            filePort, messageQuery, storage, new StubAvatarAccessPort(false), () -> fileId, 1024, false);

        assertFalse(avatarService.mayView(sampleFile(), UserId.of(viewerId)));
    }

    @Test
    void findById_returnsMetadataWithoutAcl() {
        filePort.file = sampleFile();

        assertTrue(service.findById(FileId.of(fileId)).isPresent());
    }

    @Test
    void upload_storesObjectAndMetadata() throws IOException {
        var data = "hello".getBytes(StandardCharsets.UTF_8);
        var result = service.upload(
            new ByteArrayInputStream(data), "doc.txt", "text/plain", data.length, UserId.of(ownerId));
        assertTrue(result.isPresent());
        assertTrue(storage.objects.containsKey(fileId + "/doc.txt"));
        assertNotNull(filePort.inserted);
    }

    @Test
    void upload_rejectsOversizedPayload() {
        var data = new byte[2048];
        assertTrue(service.upload(
            new ByteArrayInputStream(data), "big.bin", "application/octet-stream", data.length, UserId.of(ownerId))
            .isEmpty());
    }

    @Test
    void download_returnsStoredStream() {
        filePort.file = sampleFile();
        storage.objects.put(fileId + "/doc.pdf", "payload".getBytes(StandardCharsets.UTF_8));

        var stream = service.download(FileId.of(fileId));
        assertNotNull(stream);
    }

    @Test
    void presignedDownloadUrl_returnsUrlWhenObjectStored() {
        filePort.file = sampleFile();
        storage.objects.put(fileId + "/doc.pdf", "payload".getBytes(StandardCharsets.UTF_8));

        var url = service.presignedDownloadUrl(FileId.of(fileId), 300);
        assertTrue(url.isPresent());
        assertTrue(url.get().contains("doc.pdf"));
    }

    @Test
    void beginPresignedUpload_issuesPutUrlAndMetadata() {
        var result = service.beginPresignedUpload("pic.png", "image/png", 100, UserId.of(ownerId), 300);
        assertTrue(result.isPresent());
        assertTrue(result.get().uploadUrl().contains("pic.png"));
        assertNotNull(filePort.inserted);
    }

    @Test
    void delete_removesMetadataAndObject() {
        filePort.file = sampleFile();
        storage.objects.put(fileId + "/doc.pdf", new byte[] {1});
        filePort.deleteOk = true;

        assertTrue(service.delete(FileId.of(fileId)));
        assertFalse(storage.objects.containsKey(fileId + "/doc.pdf"));
    }

    @Test
    void upload_dedup_republishesMissingObject() throws IOException {
        var dedupService = new FileApplicationService(
            filePort, messageQuery, storage, () -> UUID.randomUUID(), 1024, true);
        var data = "same-content".getBytes(StandardCharsets.UTF_8);
        dedupService.uploadStream(
            new ByteArrayInputStream(data), "a.txt", "text/plain", UserId.of(ownerId)).orElseThrow();
        var storageKey = storage.objects.keySet().iterator().next();
        storage.objects.remove(storageKey);
        var second = dedupService.uploadStream(
            new ByteArrayInputStream(data), "b.txt", "text/plain", UserId.of(ownerId)).orElseThrow();
        assertNotNull(second.file().id());
        assertTrue(storage.objects.containsKey(storageKey));
        assertEquals(1, storage.objects.size());
    }

    private StoredFile sampleFile() {
        return new StoredFile(FileId.of(fileId), "doc.pdf", "application/pdf", 42, UserId.of(ownerId));
    }

    static final class StubFilePort implements FileMetadataPort {
        StoredFile file;
        StoredFile inserted;
        boolean deleteOk;
        final java.util.Map<String, com.avandocmsg.messenger.core.domain.FileBlob> blobs = new HashMap<>();

        @Override
        public Optional<StoredFile> findById(FileId id) {
            return Optional.ofNullable(file);
        }

        @Override
        public Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size, UserId uploadedBy) {
            inserted = new StoredFile(id, filename, mimeType, size, uploadedBy);
            file = inserted;
            return Optional.of(inserted);
        }

        @Override
        public boolean delete(FileId id) {
            file = null;
            return deleteOk;
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.domain.FileBlob> findBlobByContentHash(String contentHash) {
            return Optional.ofNullable(blobs.get(contentHash));
        }

        @Override
        public boolean insertBlob(String contentHash, String storageKey, long blobSize) {
            if (blobs.containsKey(contentHash)) {
                return false;
            }
            blobs.put(contentHash, new com.avandocmsg.messenger.core.domain.FileBlob(contentHash, storageKey, blobSize, 1));
            return true;
        }

        @Override
        public boolean incrementBlobRefCount(String contentHash) {
            var blob = blobs.get(contentHash);
            if (blob == null) {
                return false;
            }
            blobs.put(contentHash, new com.avandocmsg.messenger.core.domain.FileBlob(
                blob.contentHash(), blob.storageKey(), blob.blobSize(), blob.refCount() + 1));
            return true;
        }

        @Override
        public Optional<Integer> decrementBlobRefCount(String contentHash) {
            var blob = blobs.get(contentHash);
            if (blob == null) {
                return Optional.empty();
            }
            var next = blob.refCount() - 1;
            blobs.put(contentHash, new com.avandocmsg.messenger.core.domain.FileBlob(
                blob.contentHash(), blob.storageKey(), blob.blobSize(), next));
            return Optional.of(next);
        }

        @Override
        public Optional<StoredFile> insertWithStorage(FileId id, String filename, String mimeType, long size,
                                                      UserId uploadedBy, String contentHash, String storageKey) {
            inserted = new StoredFile(id, filename, mimeType, size, uploadedBy, contentHash, storageKey);
            file = inserted;
            return Optional.of(inserted);
        }
    }

    static final class StubObjectStorage implements ObjectStoragePort {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String objectName, java.io.InputStream data, long size, String contentType) throws Exception {
            objects.put(objectName, data.readAllBytes());
        }

        @Override
        public java.io.InputStream get(String objectName) throws Exception {
            var bytes = objects.get(objectName);
            return bytes != null ? new ByteArrayInputStream(bytes) : null;
        }

        @Override
        public void delete(String objectName) {
            objects.remove(objectName);
        }

        @Override
        public Optional<String> presignedGetUrl(String objectName, int ttlSeconds) {
            return Optional.of("https://minio.test/" + objectName + "?ttl=" + ttlSeconds);
        }

        @Override
        public Optional<String> presignedPutUrl(String objectName, int ttlSeconds, String contentType) {
            return Optional.of("https://minio.test/upload/" + objectName + "?ttl=" + ttlSeconds);
        }
    }

    static final class StubAvatarAccessPort implements AvatarAccessPort {
        private final boolean allow;

        StubAvatarAccessPort(boolean allow) {
            this.allow = allow;
        }

        @Override
        public boolean viewerMayAccessAsAvatar(UserId viewerId, FileId fileId) {
            return allow;
        }

        @Override
        public Optional<UserId> findUserIdByAvatarFile(FileId fileId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.domain.ChatId> findChatIdByAvatarFile(FileId fileId) {
            return Optional.empty();
        }
    }

    static final class StubMessageQueryPort implements MessageQueryPort {
        boolean mayAccess = false;

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findByChatId(
            UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse> findVersions(UUID msgId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.ReactionResponse> getReactions(UUID messageId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse> getPinnedMessages(
            UUID chatId) {
            return java.util.List.of();
        }

        @Override
        public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
            return mayAccess;
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.port.FileMessageRef> findLatestMessageRefForViewer(
            UUID fileId, UUID viewerId) {
            return Optional.empty();
        }

        @Override
        public Optional<MessageId> findLatestMessageId(ChatId chatId) {
            return Optional.empty();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> searchPlaintextForUser(
            UserId userId, java.util.List<UUID> chatIds, String queryText, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> loadMessagesForSearchResults(
            UserId userId, java.util.List<String> messageIdsInOrder, int limit) {
            return java.util.List.of();
        }
    }
}
