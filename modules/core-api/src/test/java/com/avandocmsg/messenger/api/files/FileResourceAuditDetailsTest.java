package com.avandocmsg.messenger.api.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileResourceAuditDetailsTest {

    @Test
    void publicLinkCreate_serializesLinkIdAndKind() throws Exception {
        var linkId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var tree = new ObjectMapper().readTree(FileResource.publicLinkCreateAuditDetails(linkId, 'B'));
        assertEquals(linkId, tree.get("link_id").asText());
        assertEquals("B", tree.get("kind").asText());
        assertEquals(2, tree.size());
    }

    @Test
    void publicLinkRevoke_serializesLinkId() throws Exception {
        var linkId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var tree = new ObjectMapper().readTree(FileResource.publicLinkRevokeAuditDetails(linkId));
        assertEquals(linkId.toString(), tree.get("link_id").asText());
        assertEquals(1, tree.size());
    }
}
