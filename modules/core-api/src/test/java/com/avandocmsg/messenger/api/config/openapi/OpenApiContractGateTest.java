package com.avandocmsg.messenger.api.config.openapi;

import com.avandocmsg.messenger.api.health.HealthResource;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static OpenAPI contract gate (spec 025 FR-163). */
class OpenApiContractGateTest {

  @Test
  void openApi_containsHealthAndFilePresignPaths() {
    var config = new SwaggerConfiguration()
        .resourcePackages(Set.of("com.avandocmsg.messenger.api"));
    var api = new Reader(config).read(Set.of(
        HealthResource.class,
        com.avandocmsg.messenger.api.files.FileResource.class));

    assertNotNull(api.getPaths().get("/v1/health"));
    assertNotNull(api.getPaths().get("/v1/health/live"));
    assertNotNull(api.getPaths().get("/v1/health/ready"));
    assertNotNull(api.getPaths().get("/v1/files/presign-upload"));
    assertTrue(api.getPaths().get("/v1/files/{fileId}/download").getGet() != null);
  }
}
