package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileApplicationServiceTest {

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    private final StubFilePort filePort = new StubFilePort();
    private final StubMessageRepository messageRepo = new StubMessageRepository();
    private final FileApplicationService service = new FileApplicationService(filePort, messageRepo);

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

    private StoredFile sampleFile() {
        return new StoredFile(FileId.of(fileId), "doc.pdf", "application/pdf", 42, UserId.of(ownerId));
    }

    static final class StubFilePort implements FileMetadataPort {
        StoredFile file;

        @Override
        public Optional<StoredFile> findById(FileId id) {
            return Optional.ofNullable(file);
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
