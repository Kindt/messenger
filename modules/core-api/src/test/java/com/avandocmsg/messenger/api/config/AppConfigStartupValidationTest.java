package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigStartupValidationTest {

  @Test
  void validateStartup_passesWithDefaults() {
    assertDoesNotThrow(() -> new AppConfig().validateStartup());
  }

  @Test
  void validateStartup_rejectsPresignTtlOutOfRange() {
    var cfg = new AppConfig() {
      @Override
      public int minioPresignTtlSeconds() {
        return 30;
      }
    };
    assertThrows(IllegalStateException.class, cfg::validateStartup);
  }
}
