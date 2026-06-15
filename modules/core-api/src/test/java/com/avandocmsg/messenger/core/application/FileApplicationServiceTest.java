package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
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
    private final StubMessageRepository messageRepo = new StubMessageRepository();
    private final StubObjectStorage storage = new StubObjectStorage();
    private final FileApplicationService service = new FileApplicationService(
        filePort, messageRepo, storage, () -> fileId, 1024, false);

    @Test
    void getMetadataForUser_returnsFileForOwner() {
        filePort.file = sampleFile();

        var result = service.getMetadataForUser(UserId.of(ownerId), FileId.of(fileId));
        assertTrue(result.isPresent());
    }

    @Test
    void getMetadataForUser_returnsFileForChatMember() {
        filePort.file = sampleFile();
        messageRepo.mayAccess = true;

        var result = service.getMetadataForUser(UserId.of(viewerId), FileId.of(fileId));
        assertTrue(result.isPresent());
    }

    @Test
    void getMetadataForUser_deniesOutsider() {
        filePort.file = sampleFile();
        messageRepo.mayAccess = false;

        assertTrue(service.getMetadataForUser(UserId.of(viewerId), FileId.of(fileId)).isEmpty());
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
    void delete_removesMetadataAndObject() {
        filePort.file = sampleFile();
        storage.objects.put(fileId + "/doc.pdf", new byte[] {1});
        filePort.deleteOk = true;

        assertTrue(service.delete(FileId.of(fileId)));
        assertFalse(storage.objects.containsKey(fileId + "/doc.pdf"));
    }

    @Test
    void upload_dedup_reusesBlobWithoutSecondPut() throws IOException {
        var dedupService = new FileApplicationService(
            filePort, messageRepo, storage, () -> UUID.randomUUID(), 1024, true);
        var data = "same-content".getBytes(StandardCharsets.UTF_8);
        var first = dedupService.uploadStream(
            new ByteArrayInputStream(data), "a.txt", "text/plain", UserId.of(ownerId)).orElseThrow();
        var second = dedupService.uploadStream(
            new ByteArrayInputStream(data), "b.txt", "text/plain", UserId.of(ownerId)).orElseThrow();
        assertNotEquals(first.file().id(), second.file().id());
        assertEquals(1, storage.objects.size());
        assertTrue(storage.objects.keySet().iterator().next().startsWith("objects/sha256/"));
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
    }

    static final class StubMessageRepository extends MessageRepository {
        boolean mayAccess = false;

        StubMessageRepository() {
            super(null, java.time.Clock.systemUTC());
        }

        @Override
        public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
            return mayAccess;
        }
    }
}
