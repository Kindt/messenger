package com.avandocmsg.messenger.media;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.ExporterLabel;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

public final class WebRtcDtlsServer extends DefaultTlsServer {

    private final DtlsIdentity identity;
    private final String expectedClientFingerprint;
    private UseSRTPData offeredSrtp;
    private int selectedSrtpProfile;
    private DtlsSrtpKeyMaterial keyMaterial;

    public WebRtcDtlsServer(DtlsIdentity identity, SecureRandom random) {
        this(identity, random, null);
    }

    public WebRtcDtlsServer(
        DtlsIdentity identity,
        SecureRandom random,
        String expectedClientFingerprint
    ) {
        super(new BcTlsCrypto(random));
        this.identity = identity;
        this.expectedClientFingerprint = normalizeFingerprint(expectedClientFingerprint);
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return new ProtocolVersion[] {ProtocolVersion.DTLSv12};
    }

    @Override
    protected int[] getSupportedCipherSuites() {
        return new int[] {
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
        };
    }

    @Override
    public void processClientExtensions(Hashtable clientExtensions) throws IOException {
        super.processClientExtensions(clientExtensions);
        offeredSrtp = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);
        if (offeredSrtp == null) {
            throw new IOException("WebRTC client did not offer use_srtp");
        }
        selectedSrtpProfile = selectProfile(offeredSrtp.getProtectionProfiles());
    }

    @Override
    public Hashtable getServerExtensions() throws IOException {
        var extensions = super.getServerExtensions();
        if (extensions == null) {
            extensions = new Hashtable();
        }
        TlsSRTPUtils.addUseSRTPExtension(
            extensions,
            new UseSRTPData(new int[] {selectedSrtpProfile}, offeredSrtp.getMki())
        );
        return extensions;
    }

    @Override
    protected TlsCredentialedSigner getECDSASignerCredentials() throws IOException {
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
            throw new IOException("cannot create DTLS credentials", error);
        }
    }

    @Override
    public CertificateRequest getCertificateRequest() {
        if (expectedClientFingerprint == null) {
            return null;
        }
        var algorithms = new Vector<SignatureAndHashAlgorithm>();
        algorithms.add(new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
        return new CertificateRequest(
            new short[] {ClientCertificateType.ecdsa_sign},
            algorithms,
            new Vector<>()
        );
    }

    @Override
    public void notifyClientCertificate(Certificate clientCertificate) throws IOException {
        if (expectedClientFingerprint == null) {
            return;
        }
        if (clientCertificate == null || clientCertificate.isEmpty()) {
            throw new TlsFatalAlert(AlertDescription.bad_certificate);
        }
        try {
            var actual = DtlsIdentity.fingerprint(clientCertificate.getCertificateAt(0).getEncoded());
            if (!expectedClientFingerprint.equals(actual)) {
                throw new TlsFatalAlert(AlertDescription.bad_certificate);
            }
        } catch (GeneralSecurityException error) {
            throw new IOException("cannot verify DTLS client fingerprint", error);
        }
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

    public int selectedSrtpProfile() {
        return selectedSrtpProfile;
    }

    private static int selectProfile(int[] offered) throws IOException {
        for (var profile : offered) {
            if (profile == SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80) {
                return profile;
            }
        }
        throw new IOException("SRTP_AES128_CM_HMAC_SHA1_80 not offered");
    }

    private static String normalizeFingerprint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        var separator = normalized.indexOf(' ');
        if (separator >= 0) {
            var algorithm = normalized.substring(0, separator).toLowerCase(Locale.ROOT);
            if (!algorithm.equals("sha-256")) {
                throw new IllegalArgumentException("only sha-256 DTLS fingerprints are supported");
            }
            normalized = normalized.substring(separator + 1).trim();
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
