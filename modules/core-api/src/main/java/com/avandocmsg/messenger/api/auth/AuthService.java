package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.LoginRequest;
import com.avandocmsg.messenger.api.auth.dto.LoginResponse;
import com.avandocmsg.messenger.api.auth.dto.RegisterRequest;
import com.avandocmsg.messenger.api.auth.dto.RegisterResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig appConfig;
    private final UserLookupPort userLookupPort;
    private final UserRepositoryPort userRepositoryPort;
    private final SavedChatPort savedChatPort;
    private final HttpClient httpClient;

    public AuthService(
        AppConfig appConfig,
        UserLookupPort userLookupPort,
        UserRepositoryPort userRepositoryPort,
        SavedChatPort savedChatPort
    ) {
        this.appConfig = appConfig;
        this.userLookupPort = userLookupPort;
        this.userRepositoryPort = userRepositoryPort;
        this.savedChatPort = savedChatPort;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public LoginResponse login(LoginRequest request) {
        try {
            var tokenEndpoint = appConfig.keycloakIssuer() + "/protocol/openid-connect/token";
            var username = request.username() != null ? request.username().trim() : "";
            var body = "client_id=messenger-web" +
                "&username=" + urlEncode(username) +
                "&password=" + urlEncode(request.password()) +
                "&grant_type=password";

            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Keycloak login failed: http_status={} (response body not logged)", response.statusCode());
                return null;
            }

            var json = MAPPER.readTree(response.body());
            var accessToken = json.get("access_token").asText();
            syncUserFromAccessToken(accessToken);
            return new LoginResponse(
                accessToken,
                json.get("refresh_token").asText(),
                json.get("expires_in").asInt()
            );
        } catch (Exception e) {
            log.error("Login error", e);
            return null;
        }
    }

    public RegisterOutcome register(RegisterRequest request) {
        final UUID keycloakUserId;
        try {
            keycloakUserId = provisionKeycloakUser(request);
        } catch (UsernameExistsException e) {
            return RegisterOutcome.failure(RegisterOutcome.Status.USERNAME_EXISTS);
        }
        if (keycloakUserId == null) {
            return RegisterOutcome.failure(RegisterOutcome.Status.KEYCLOAK_UNAVAILABLE);
        }
        if (!userRepositoryPort.createLocalUser(UserId.of(keycloakUserId), request.username(), request.displayName())) {
            log.warn("Local user row not created for {} (id={})", request.username(), keycloakUserId);
            if (userLookupPort.findByUsername(request.username()).isPresent()) {
                return RegisterOutcome.failure(RegisterOutcome.Status.USERNAME_EXISTS);
            }
            return RegisterOutcome.failure(RegisterOutcome.Status.PERSISTENCE_FAILED);
        }
        ensureSavedVault(keycloakUserId);
        return RegisterOutcome.success(
            new RegisterResponse(keycloakUserId.toString(), request.username(), request.displayName()));
    }

    /**
     * Creates the user in Keycloak and returns their realm id ({@code sub}).
     *
     * @return user id, {@code null} if Keycloak is unavailable or user id could not be resolved
     * @throws UsernameExistsException if username is already taken in Keycloak
     */
    protected UUID provisionKeycloakUser(RegisterRequest request) throws UsernameExistsException {
        try {
            var adminToken = getAdminToken();
            if (adminToken == null) {
                log.warn("Keycloak admin token unavailable; cannot register {}", request.username());
                return null;
            }
            var usersEndpoint = appConfig.keycloakAdminRealmBase() + "/users";
            var keycloakUser = MAPPER.createObjectNode();
            keycloakUser.put("username", request.username());
            keycloakUser.put("enabled", true);
            var email = request.username() + "@users.korus.local";
            keycloakUser.put("email", email);
            keycloakUser.put("emailVerified", true);
            var display = request.displayName();
            if (display != null && !display.isBlank()) {
                var parts = display.trim().split("\\s+", 2);
                keycloakUser.put("firstName", parts[0]);
                keycloakUser.put("lastName", parts.length > 1 ? parts[1] : parts[0]);
            } else {
                keycloakUser.put("firstName", request.username());
                keycloakUser.put("lastName", request.username());
            }

            var creds = MAPPER.createArrayNode();
            var cred = MAPPER.createObjectNode();
            cred.put("type", "password");
            cred.put("value", request.password());
            cred.put("temporary", false);
            creds.add(cred);
            keycloakUser.set("credentials", creds);

            var kcRequest = HttpRequest.newBuilder()
                .uri(URI.create(usersEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + adminToken)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(keycloakUser.toString()))
                .build();

            var kcResponse = httpClient.send(kcRequest, HttpResponse.BodyHandlers.ofString());
            if (kcResponse.statusCode() == 409) {
                throw new UsernameExistsException(request.username());
            }
            if (kcResponse.statusCode() != 201) {
                log.warn("Keycloak user create failed: http_status={} (response body not logged)",
                    kcResponse.statusCode());
                return null;
            }
            var loc = kcResponse.headers().firstValue("Location").orElse("");
            var userId = parseUserIdFromLocation(loc);
            if (userId == null) {
                userId = lookupKeycloakUserId(adminToken, request.username());
            }
            if (userId == null) {
                log.warn("Keycloak created user but could not resolve id for {}", request.username());
            }
            return userId;
        } catch (UsernameExistsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Keycloak user creation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Отзыв refresh-токена в Keycloak (RFC 7009, endpoint {@code .../openid-connect/revoke}).
     * Идемпотентно для клиента: при уже недействительном токене Keycloak может вернуть 400 — считаем успехом для logout.
     */
    public boolean revokeRefreshToken(String refreshToken) {
        try {
            var revokeUrl = appConfig.keycloakIssuer() + "/protocol/openid-connect/revoke";
            var body = "client_id=messenger-web"
                + "&token=" + urlEncode(refreshToken)
                + "&token_type_hint=refresh_token";
            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(revokeUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var code = response.statusCode();
            if (code >= 200 && code < 300) {
                return true;
            }
            // Keycloak: неверный/истёкший токен часто даёт 400 — для logout это ожидаемо
            if (code == 400) {
                return true;
            }
            log.warn("Keycloak revoke unexpected status: {}", code);
            return false;
        } catch (Exception e) {
            log.warn("Keycloak revoke failed: {}", e.getMessage());
            return false;
        }
    }

    /** Как {@link #login}, но по refresh_token; при отсутствии нового refresh в ответе Keycloak подставляется переданный токен. */
    public LoginResponse refreshAccessToken(String refreshToken) {
        try {
            var tokenEndpoint = appConfig.keycloakIssuer() + "/protocol/openid-connect/token";
            var body = "client_id=messenger-web" +
                "&refresh_token=" + urlEncode(refreshToken) +
                "&grant_type=refresh_token";

            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            var json = MAPPER.readTree(response.body());
            var accessToken = json.get("access_token").asText();
            var newRefresh = json.hasNonNull("refresh_token")
                ? json.get("refresh_token").asText()
                : refreshToken;
            var expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 0;
            syncUserFromAccessToken(accessToken);
            return new LoginResponse(accessToken, newRefresh, expiresIn);
        } catch (Exception e) {
            log.error("Refresh error", e);
            return null;
        }
    }

    private void syncUserFromAccessToken(String accessToken) {
        try {
            var jwt = SignedJWT.parse(accessToken);
            var claims = jwt.getJWTClaimsSet();
            var sub = UUID.fromString(claims.getSubject());
            var username = claims.getStringClaim("preferred_username");
            if (username == null) {
                username = claims.getStringClaim("username");
            }
            var name = claims.getStringClaim("name");
            userRepositoryPort.upsertFromKeycloak(UserId.of(sub), username, name);
            ensureSavedVault(sub);
        } catch (Exception e) {
            log.warn("Could not sync user from access token: {}", e.getMessage());
        }
    }

    private void ensureSavedVault(UUID userId) {
        try {
            savedChatPort.ensureSavedVaultChat(UserId.of(userId));
        } catch (Exception e) {
            log.warn("Saved vault chat not ensured for {}: {}", userId, e.getMessage());
        }
    }

    private UUID lookupKeycloakUserId(String adminToken, String username) {
        try {
            var uri = appConfig.keycloakAdminRealmBase() + "/users?username=" + urlEncode(username) + "&exact=true";
            var req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer " + adminToken)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            var response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            var arr = MAPPER.readTree(response.body());
            if (!arr.isArray() || arr.isEmpty() || arr.get(0).get("id") == null) {
                return null;
            }
            return UUID.fromString(arr.get(0).get("id").asText());
        } catch (Exception e) {
            log.warn("lookupKeycloakUserId failed: {}", e.getMessage());
            return null;
        }
    }

    private static UUID parseUserIdFromLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            var uri = URI.create(location);
            var path = uri.getPath();
            var last = path.substring(path.lastIndexOf('/') + 1);
            return UUID.fromString(last);
        } catch (Exception e) {
            return null;
        }
    }

    private String getAdminToken() {
        try {
            var tokenEndpoint = appConfig.keycloakMasterTokenEndpoint();
            var body = "client_id=admin-cli" +
                "&username=" + urlEncode(appConfig.keycloakMasterUser()) +
                "&password=" + urlEncode(appConfig.keycloakMasterPassword()) +
                "&grant_type=password";

            var request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var json = MAPPER.readTree(response.body());
                return json.get("access_token").asText();
            }
        } catch (Exception e) {
            log.warn("Cannot get admin token: {}", e.getMessage());
        }
        return null;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
