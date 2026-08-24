package com.avandocmsg.messenger.media;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Hashtable;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

public final class WebRtcDtlsClient extends DefaultTlsClient {

    private final DtlsIdentity identity;
    private DtlsSrtpKeyMaterial keyMaterial;

    public WebRtcDtlsClient(DtlsIdentity identity, SecureRandom random) {
        super(new BcTlsCrypto(random));
        this.identity = identity;
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return new ProtocolVersion[] {ProtocolVersion.DTLSv12};
    }

    @Override
    protected int[] getSupportedCipherSuites() {
        return new int[] {CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256};
    }

    @Override
    public Hashtable getClientExtensions() throws IOException {
        var extensions = super.getClientExtensions();
        if (extensions == null) {
            extensions = new Hashtable();
        }
        TlsSRTPUtils.addUseSRTPExtension(
            extensions,
            new UseSRTPData(new int[] {SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80}, new byte[0])
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

    public DtlsSrtpKeyMaterial exportSrtpKeyMaterial() {
        if (keyMaterial == null) {
            throw new IllegalStateException("DTLS handshake is not complete");
        }
        return keyMaterial;
    }
}
