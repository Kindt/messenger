package com.avandocmsg.messenger.core.adapter.mls;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.api.mls.SessionRepository.MlsSession;
import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OpenMlsBindingAdapterTest {

    @Test
    void hybrid_encryptDecryptRoundTrip() {
        var mlsService = new MlsService(new InMemorySessionRepository(), new E2EEService());
        var binding = new HybridOpenMlsBindingAdapter(mlsService);
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();

        var combined = binding.encryptWire(chatId, senderId, "binding-roundtrip");
        assertNotNull(combined);
        assertTrue(OpenMlsWireLayout.isValidCombined(combined));

        var decrypted = binding.decryptWireBase64(chatId, Base64.getEncoder().encodeToString(combined));
        assertEquals("binding-roundtrip", decrypted);
        assertFalse(binding.nativeBindingAvailable());
        assertEquals(OpenMlsWireLayout.WIRE_PROFILE, binding.wireProfile());
    }

    @Test
    void nativeAdapter_fallsBackWhenLibraryMissing() {
        var appConfig = new AppConfig() {
            @Override
            public boolean openmlsNativeEnabled() {
                return true;
            }
        };
        var hybrid = new HybridOpenMlsBindingAdapter(new MlsService(new InMemorySessionRepository(), new E2EEService()));
        var nativeBinding = new OpenMlsNativeBindingAdapter(appConfig, hybrid, false);

        assertFalse(nativeBinding.nativeBindingAvailable());
        assertEquals(OpenMlsWireLayout.LIBRARY_VERSION, nativeBinding.libraryVersion());
    }

    @Test
    void factory_returnsHybridByDefault() {
        var mlsService = new MlsService(new InMemorySessionRepository(), new E2EEService());
        var binding = OpenMlsBindingFactory.create(new AppConfig(), mlsService);
        assertInstanceOf(HybridOpenMlsBindingAdapter.class, binding);
    }

    static final class InMemorySessionRepository extends SessionRepository {
        private final Map<UUID, MlsSession> sessions = new HashMap<>();

        InMemorySessionRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public Optional<MlsSession> findByChatId(UUID chatId, long epochValue) {
            var row = sessions.get(chatId);
            if (row == null || row.epoch() != epochValue) {
                return Optional.empty();
            }
            return Optional.of(row);
        }

        @Override
        public Optional<MlsSession> findLatestByChatId(UUID chatId) {
            return Optional.ofNullable(sessions.get(chatId));
        }

        @Override
        public MlsSession create(UUID chatId, String cipherSuite) {
            var session = new MlsSession(
                UUID.randomUUID(), chatId, 0L, cipherSuite,
                null, null, null, Instant.now(), Instant.now());
            sessions.put(chatId, session);
            return session;
        }
    }
}
