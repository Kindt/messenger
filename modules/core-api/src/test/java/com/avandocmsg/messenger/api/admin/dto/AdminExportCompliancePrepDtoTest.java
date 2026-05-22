package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminExportCompliancePrepDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void request_roundTrip_includeFile() throws Exception {
        var req = new AdminExportCompliancePrepRequest(null, true, 2, true, "smoke.txt");
        var json = mapper.writeValueAsString(req);
        assertTrue(json.contains("\"include_file\":true"));
        assertTrue(json.contains("\"file_name\":\"smoke.txt\""));

        var back = mapper.readValue(json, AdminExportCompliancePrepRequest.class);
        assertEquals(true, back.includeFile());
        assertEquals("smoke.txt", back.fileName());
    }

    @Test
    void response_omitsNullFileFields() throws Exception {
        var res = new AdminExportCompliancePrepResponse("c1", List.of("m1"), true, null, null);
        var json = mapper.writeValueAsString(res);
        assertTrue(json.contains("\"chat_id\":\"c1\""));
        assertTrue(!json.contains("file_id"));
    }

    @Test
    void response_includesFileFieldsWhenSet() throws Exception {
        var res = new AdminExportCompliancePrepResponse(
            "c1", List.of("m1", "m2"), true, "f1", "m2");
        var json = mapper.writeValueAsString(res);
        assertTrue(json.contains("\"file_id\":\"f1\""));
        assertTrue(json.contains("\"file_message_id\":\"m2\""));
    }
}
