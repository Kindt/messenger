package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class StunMessageTest {

    private static final String PASSWORD = "VOkJxbRl1RmTxUk/WvJxBt";
    private static final byte[] REQUEST = hex("""
        000100582112a442b7e7a701bc34d686fa87dfae
        802200105354554e207465737420636c69656e74
        002400046e0001ff
        80290008932ff9b151263b36
        000600096576746a3a68367659202020
        000800149aeaa70cbfd8cb56781ef2b5b2d3f249c1b571a2
        80280004e57a3bcf
        """);
    private static final byte[] IPV4_RESPONSE = hex("""
        0101003c2112a442b7e7a701bc34d686fa87dfae
        8022000b7465737420766563746f7220
        002000080001a147e112a643
        000800142b91f599fd9e90c38c7489f92af9ba53f06be7d7
        80280004c07d4c96
        """);

    @Test
    void validatesOfficialBindingRequestVector() {
        var message = StunMessage.parse(REQUEST);

        assertEquals(StunMessage.BINDING_REQUEST, message.type());
        assertEquals("evtj:h6vY", message.username());
        assertTrue(message.verifyMessageIntegrity(PASSWORD));
        assertTrue(message.verifyFingerprint());
    }

    @Test
    void buildsOfficialIpv4SuccessResponseVector() {
        var request = StunMessage.parse(REQUEST);

        var response = request.bindingSuccess(
            new InetSocketAddress("192.0.2.1", 32853),
            PASSWORD,
            "test vector"
        );

        assertArrayEquals(IPV4_RESPONSE, response);
    }

    @Test
    void detectsIceUseCandidateNomination() {
        var request = StunMessage.parse(hex("""
            000100042112a442000102030405060708090a0b
            00250000
            """));

        assertTrue(request.useCandidate());
    }

    private static byte[] hex(String value) {
        var compact = value.replaceAll("\\s+", "");
        var result = new byte[compact.length() / 2];
        for (var i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
