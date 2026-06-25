package com.avandocmsg.messenger.api.integration;

import javax.sql.DataSource;

/**
 * Test harness entry point for optional Docker-backed lab stack (spec 025 FR-160).
 * CI uses H2 stand-in; enable {@code LAB_STACK_INTEGRATION=true} on a Docker host for PG/NATS/Redis/MinIO wiring.
 */
public final class LabStackIntegrationHarness {

  private final DataSource dataSource;

  public LabStackIntegrationHarness(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public static boolean dockerIntegrationEnabled() {
    return Boolean.parseBoolean(System.getenv().getOrDefault("LAB_STACK_INTEGRATION", "false"));
  }

  public boolean pingDatabase() {
    try (var conn = dataSource.getConnection()) {
      return conn.isValid(2);
    } catch (Exception e) {
      return false;
    }
  }
}
