package com.avandocmsg.messenger.api.admin;

import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminExportComplianceOpenApiTest {

    @Test
    void openApi_documentsExportCompliancePrepPost() {
        var config = new SwaggerConfiguration()
            .resourcePackages(Set.of("com.avandocmsg.messenger.api"));
        var api = new Reader(config).read(Set.of(AdminResource.class));

        var path = api.getPaths().get("/v1/admin/export-compliance-prep");
        assertNotNull(path, "missing path /v1/admin/export-compliance-prep");
        assertNotNull(path.getPost(), "missing POST on export-compliance-prep");

        var op = path.getPost();
        assertTrue(op.getSummary() != null && !op.getSummary().isBlank());
        assertNotNull(op.getRequestBody());
        assertNotNull(op.getResponses().get("200"));
    }
}
