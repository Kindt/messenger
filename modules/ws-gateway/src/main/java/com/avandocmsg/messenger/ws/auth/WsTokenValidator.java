package com.avandocmsg.messenger.ws.auth;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class WsTokenValidator {
    private static final Logger log = LoggerFactory.getLogger(WsTokenValidator.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final String issuer;
    private final String jwksUrl;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, RSAKey> keyCache = new ConcurrentHashMap<>();
    private Instant lastFetch = Instant.EPOCH;

    public WsTokenValidator(String issuer, String jwksUrl) {
        this.issuer = issuer;
        this.jwksUrl = jwksUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public JWTClaimsSet validate(String token) {
        try {
            var signedJWT = SignedJWT.parse(token);
            var kid = signedJWT.getHeader().getKeyID();
            var rsaKey = resolveKey(kid);
            if (rsaKey == null) {
                refreshKeys();
                rsaKey = resolveKey(kid);
            }
            if (rsaKey == null) return null;
            JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!signedJWT.verify(verifier)) return null;
            var claims = signedJWT.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) return null;
            var exp = claims.getExpirationTime();
            if (exp != null && exp.toInstant().isBefore(Instant.now())) return null;
            return claims;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    private RSAKey resolveKey(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        return keyCache.get(kid);
    }

    private void refreshKeys() {
        var now = Instant.now();
        if (now.minus(CACHE_TTL).isBefore(lastFetch) && !keyCache.isEmpty()) return;
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var jwkSet = JWKSet.parse(response.body());
                keyCache.clear();
                for (var jwk : jwkSet.getKeys()) {
                    if (jwk instanceof RSAKey rsaKey) keyCache.put(rsaKey.getKeyID(), rsaKey);
                }
                lastFetch = now;
                log.info("JWKS refreshed: {} keys", keyCache.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to refresh JWKS (interrupted): {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to refresh JWKS: {}", e.getMessage());
        }
    }
}
