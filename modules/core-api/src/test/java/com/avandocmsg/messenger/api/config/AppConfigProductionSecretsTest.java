package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigProductionSecretsTest {

    @Test
    void validateProductionSecrets_pilotWarnsOnDevDefaults() {
        var cfg = configWithProfile("pilot");
        assertDoesNotThrow(cfg::validateProductionSecrets);
        assertEquals("pilot", cfg.deployProfile());
    }

    @Test
    void validateProductionSecrets_standardFailsOnDevDefaults() {
        var cfg = configWithProfile("standard");
        assertThrows(IllegalStateException.class, cfg::validateProductionSecrets);
    }

    @Test
    void validateProductionSecrets_devSkipsCheck() {
        var cfg = configWithProfile("dev");
        assertDoesNotThrow(cfg::validateProductionSecrets);
    }

    private static AppConfig configWithProfile(String profile) {
        return new AppConfig() {
            @Override
            public String deployProfile() {
                return profile;
            }

            @Override
            public String dbPassword() {
                return "avandocmsg";
            }

            @Override
            public String keycloakMasterPassword() {
                return "admin";
            }

            @Override
            public String minioSecretKey() {
                return "avandocmsg123";
            }
        };
    }
}
