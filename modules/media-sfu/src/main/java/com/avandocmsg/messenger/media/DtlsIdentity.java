package com.avandocmsg.messenger.media;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public record DtlsIdentity(
    KeyPair keyPair,
    X509Certificate certificate,
    String sha256Fingerprint
) {
    public DtlsIdentity {
        Objects.requireNonNull(keyPair, "keyPair");
        Objects.requireNonNull(certificate, "certificate");
        Objects.requireNonNull(sha256Fingerprint, "sha256Fingerprint");
    }

    public static DtlsIdentity generate(Clock clock, SecureRandom random) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(random, "random");
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), random);
            var keyPair = generator.generateKeyPair();
            var subject = new X500Name("CN=Korus Media");
            var now = clock.instant();
            var builder = new JcaX509v3CertificateBuilder(
                subject,
                new BigInteger(128, random).abs().add(BigInteger.ONE),
                Date.from(now.minus(Duration.ofMinutes(1))),
                Date.from(now.plus(Duration.ofDays(1))),
                subject,
                keyPair.getPublic()
            );
            var signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
            var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
            certificate.verify(keyPair.getPublic());
            return new DtlsIdentity(keyPair, certificate, fingerprint(certificate));
        } catch (GeneralSecurityException | OperatorCreationException error) {
            throw new IllegalStateException("cannot generate DTLS identity", error);
        }
    }

    private static String fingerprint(X509Certificate certificate)
        throws GeneralSecurityException, CertificateException {
        return fingerprint(certificate.getEncoded());
    }

    public static String fingerprint(byte[] encodedCertificate) throws GeneralSecurityException {
        var digest = MessageDigest.getInstance("SHA-256").digest(encodedCertificate);
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
    }
}
