package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigProductionSecretsTest {

    @Test
    void validateProductionSecrets_baseOnlySkipsCheck() {
        var cfg = configWithAddons("");
        assertDoesNotThrow(cfg::validateProductionSecrets);
    }

    @Test
    void validateProductionSecrets_withAddonsFailsOnDevDefaults() {
        var cfg = configWithAddons("addon-engage,addon-search");
        assertThrows(IllegalStateException.class, cfg::validateProductionSecrets);
    }

    @Test
    void validateProductionSecrets_labAllowSkipsCheck() {
        var cfg = new AppConfig() {
            @Override
            public String korusProductAddons() {
                return "addon-engage";
            }

            @Override
            public boolean korusLabAllowDevSecrets() {
                return true;
            }

            @Override
            public String dbPassword() {
                return "avandocmsg";
            }
        };
        assertDoesNotThrow(cfg::validateProductionSecrets);
    }

    private static AppConfig configWithAddons(String addons) {
        return new AppConfig() {
            @Override
            public String korusProductAddons() {
                return addons;
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
