package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class WebRtcSdpTest {

    private static final String OFFER = """
        v=0\r
        o=- 42 2 IN IP4 127.0.0.1\r
        s=-\r
        t=0 0\r
        a=group:BUNDLE 0 1\r
        a=ice-ufrag:clientUfrag\r
        a=ice-pwd:clientPassword1234567890\r
        a=fingerprint:sha-256 AA:BB:CC\r
        m=audio 9 UDP/TLS/RTP/SAVPF 111 0\r
        c=IN IP4 0.0.0.0\r
        a=mid:0\r
        a=sendrecv\r
        a=rtcp-mux\r
        a=rtpmap:111 opus/48000/2\r
        a=fmtp:111 minptime=10;useinbandfec=1\r
        a=rtpmap:0 PCMU/8000\r
        m=video 9 UDP/TLS/RTP/SAVPF 96 97\r
        c=IN IP4 0.0.0.0\r
        a=mid:1\r
        a=recvonly\r
        a=rtcp-mux\r
        a=rtpmap:96 VP8/90000\r
        a=rtcp-fb:96 nack\r
        a=rtpmap:97 rtx/90000\r
        a=fmtp:97 apt=96\r
        """;

    @Test
    void parsesBrowserOfferAndBuildsOwnedIceLiteAnswer() {
        var offer = WebRtcSdpOffer.parse(OFFER);
        assertEquals("clientUfrag", offer.iceUfrag());
        assertEquals("clientPassword1234567890", offer.icePassword());
        assertEquals(2, offer.media().size());
        assertEquals("recvonly", offer.media().get(1).direction());

        var answer = new WebRtcSdpAnswerBuilder(
            "serverUfrag",
            "serverPassword1234567890",
            "11:22:33",
            new InetSocketAddress("192.0.2.10", 40000)
        ).answer(offer);

        assertTrue(answer.contains("a=ice-lite\r\n"));
        assertTrue(answer.contains("a=setup:passive\r\n"));
        assertTrue(answer.contains("a=ice-ufrag:serverUfrag\r\n"));
        assertTrue(answer.contains("a=candidate:korus1 1 udp"));
        assertTrue(answer.contains("a=rtpmap:111 opus/48000/2\r\n"));
        assertTrue(answer.contains("a=rtpmap:96 VP8/90000\r\n"));
        assertTrue(answer.contains("a=group:BUNDLE 0 1\r\n"));
        assertTrue(answer.substring(answer.indexOf("m=video")).contains("a=sendonly\r\n"));
    }

    @Test
    void directNativeAudioPolicyAnswersBrowserOfferWithPcmuOnly() {
        var answer = new WebRtcSdpAnswerBuilder(
            "serverUfrag",
            "serverPassword1234567890",
            "11:22:33",
            new InetSocketAddress("192.0.2.10", 40000)
        ).answer(WebRtcSdpOffer.parse(OFFER), WebRtcSdpAnswerPolicy.DIRECT_PCMU_AUDIO);

        assertTrue(answer.contains("m=audio 9 UDP/TLS/RTP/SAVPF 0\r\n"));
        assertTrue(answer.contains("a=rtpmap:0 PCMU/8000\r\n"));
        assertFalse(answer.contains("a=rtpmap:111 opus/48000/2\r\n"));
        assertFalse(answer.contains("a=fmtp:111 "));
        assertTrue(answer.contains("m=video 0 UDP/TLS/RTP/SAVPF 96 97\r\n"));
        assertFalse(answer.contains("a=rtpmap:96 VP8/90000\r\n"));
    }
}
