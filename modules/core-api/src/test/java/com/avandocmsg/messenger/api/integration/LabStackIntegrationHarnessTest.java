package com.avandocmsg.messenger.api.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabStackIntegrationHarnessTest {

  private HikariDataSource ds;

  @AfterEach
  void tearDown() {
    if (ds != null) {
      ds.close();
    }
  }

  @Test
  void h2StandIn_pingDatabase() {
    var cfg = new HikariConfig();
    cfg.setJdbcUrl("jdbc:h2:mem:lab_harness;DB_CLOSE_DELAY=-1");
    cfg.setUsername("sa");
    cfg.setPassword("");
    ds = new HikariDataSource(cfg);
    var harness = new LabStackIntegrationHarness(ds);
    assertTrue(harness.pingDatabase());
  }

  @Test
  void dockerIntegration_disabledByDefault() {
    assertFalse(LabStackIntegrationHarness.dockerIntegrationEnabled());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "LAB_STACK_INTEGRATION", matches = "true")
  void dockerIntegration_flagDocumented() {
    assertTrue(LabStackIntegrationHarness.dockerIntegrationEnabled());
  }
}
