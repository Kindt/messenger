package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.config.AppConfig;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class TokenValidator {
    private static final Logger log = LoggerFactory.getLogger(TokenValidator.class);

    private final AppConfig appConfig;
    private final Clock clock;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, RSAKey> keyCache = new ConcurrentHashMap<>();
    private Instant lastFetch = Instant.EPOCH;
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public TokenValidator(AppConfig appConfig, Clock clock) {
        this.appConfig = appConfig;
        this.clock = clock;
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
            if (rsaKey == null) {
                log.warn("Key not found: {}", kid);
                return null;
            }
            var publicKey = rsaKey.toRSAPublicKey();
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature invalid");
                return null;
            }
            var claims = signedJWT.getJWTClaimsSet();
            var issuer = claims.getIssuer();
            if (!appConfig.keycloakIssuer().equals(issuer)) {
                log.warn("Unexpected issuer: {}", issuer);
                return null;
            }
            var expectedAud = appConfig.keycloakAudience();
            if (!expectedAud.isEmpty()) {
                var aud = claims.getAudience();
                if (aud == null || aud.stream().noneMatch(expectedAud::equals)) {
                    log.warn("Unexpected audience (expected {})", expectedAud);
                    return null;
                }
            }
            var expiration = claims.getExpirationTime();
            if (expiration != null && expiration.toInstant().isBefore(clock.instant())) {
                log.warn("JWT expired");
                return null;
            }
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
        var now = clock.instant();
        if (now.minus(CACHE_TTL).isBefore(lastFetch) && !keyCache.isEmpty()) {
            return;
        }
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(appConfig.keycloakJwksUrl()))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var jwkSet = JWKSet.parse(response.body());
                keyCache.clear();
                for (var jwk : jwkSet.getKeys()) {
                    if (jwk instanceof RSAKey rsaKey) {
                        keyCache.put(rsaKey.getKeyID(), rsaKey);
                    }
                }
                lastFetch = now;
                log.info("JWKS refreshed: {} keys loaded", keyCache.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to refresh JWKS (interrupted): {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to refresh JWKS: {}", e.getMessage());
        }
    }
}
