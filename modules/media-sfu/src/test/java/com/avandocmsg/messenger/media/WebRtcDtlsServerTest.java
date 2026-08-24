package com.avandocmsg.messenger.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Hashtable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.UDPTransport;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.junit.jupiter.api.Test;

class WebRtcDtlsServerTest {

    @Test
    void negotiatesDtls12AndExportsMatchingSrtpKeys() throws Exception {
        var random = new SecureRandom();
        var identity = DtlsIdentity.generate(Clock.systemUTC(), random);
        var clientIdentity = DtlsIdentity.generate(Clock.systemUTC(), random);
        var serverPeer = new WebRtcDtlsServer(
            identity,
            random,
            "sha-256 " + clientIdentity.sha256Fingerprint()
        );
        var clientPeer = new TestDtlsClient(random, clientIdentity);
        var loopback = InetAddress.getLoopbackAddress();
        try (
            var serverSocket = new DatagramSocket(new InetSocketAddress(loopback, 0));
            var clientSocket = new DatagramSocket(new InetSocketAddress(loopback, 0));
            var executor = Executors.newSingleThreadExecutor()
        ) {
            serverSocket.connect(loopback, clientSocket.getLocalPort());
            clientSocket.connect(loopback, serverSocket.getLocalPort());
            serverSocket.setSoTimeout(10_000);
            clientSocket.setSoTimeout(10_000);
            var serverFuture = executor.submit(() ->
                new DTLSServerProtocol().accept(serverPeer, new UDPTransport(serverSocket, 1500))
            );

            var clientTransport = new DTLSClientProtocol()
                .connect(clientPeer, new UDPTransport(clientSocket, 1500));
            var serverTransport = serverFuture.get(10, TimeUnit.SECONDS);
            var serverKeys = serverPeer.exportSrtpKeyMaterial();
            var clientKeys = clientPeer.exportSrtpKeyMaterial();

            assertEquals(
                SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80,
                serverPeer.selectedSrtpProfile()
            );
            assertArrayEquals(clientKeys.clientWriteKey(), serverKeys.clientWriteKey());
            assertArrayEquals(clientKeys.serverWriteKey(), serverKeys.serverWriteKey());
            assertArrayEquals(clientKeys.clientWriteSalt(), serverKeys.clientWriteSalt());
            assertArrayEquals(clientKeys.serverWriteSalt(), serverKeys.serverWriteSalt());
            clientTransport.close();
            serverTransport.close();
        }
    }

    private static final class TestDtlsClient extends DefaultTlsClient {

        private DtlsSrtpKeyMaterial keyMaterial;
        private final DtlsIdentity identity;

        private TestDtlsClient(SecureRandom random, DtlsIdentity identity) {
            super(new BcTlsCrypto(random));
            this.identity = identity;
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] {ProtocolVersion.DTLSv12};
        }

        @Override
        protected int[] getSupportedCipherSuites() {
            return new int[] {
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
            };
        }

        @Override
        public Hashtable getClientExtensions() throws IOException {
            var extensions = super.getClientExtensions();
            if (extensions == null) {
                extensions = new Hashtable();
            }
            TlsSRTPUtils.addUseSRTPExtension(
                extensions,
                new UseSRTPData(
                    new int[] {SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80},
                    new byte[0]
                )
            );
            return extensions;
        }

        @Override
        public TlsAuthentication getAuthentication() {
            return new TlsAuthentication() {
                @Override
                public void notifyServerCertificate(TlsServerCertificate serverCertificate) {}

                @Override
                public TlsCredentials getClientCredentials(CertificateRequest certificateRequest)
                    throws IOException {
                    try {
                        var crypto = (BcTlsCrypto) getCrypto();
                        var chain = new Certificate(new TlsCertificate[] {
                            crypto.createCertificate(identity.certificate().getEncoded())
                        });
                        return new BcDefaultTlsCredentialedSigner(
                            new TlsCryptoParameters(context),
                            crypto,
                            PrivateKeyFactory.createKey(identity.keyPair().getPrivate().getEncoded()),
                            chain,
                            new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa)
                        );
                    } catch (Exception error) {
                        throw new IOException("cannot create client credentials", error);
                    }
                }
            };
        }

        @Override
        public void notifyHandshakeComplete() throws IOException {
            super.notifyHandshakeComplete();
            keyMaterial = DtlsSrtpKeyMaterial.fromExporter(
                context.exportKeyingMaterial(
                    ExporterLabel.dtls_srtp,
                    null,
                    DtlsSrtpKeyMaterial.EXPORTED_BYTES
                )
            );
        }

        private DtlsSrtpKeyMaterial exportSrtpKeyMaterial() {
            return keyMaterial;
        }
    }
}
