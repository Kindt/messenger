package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.auth.policy.AuthPolicyRepository;
import com.avandocmsg.messenger.api.auth.policy.AuthPolicyService;
import com.avandocmsg.messenger.api.auth.policy.KeycloakAuthSyncClient;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.testsupport.EmptyOrganizationLookupPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthResourceTest {

    @Test
    void logout_rateLimited_returns429() {
        var stub = new StubAuthService();
        var res = new AuthResource(stub, noopAuthPolicy(), AuthRateLimiter.testingDenyLogout(), I18nTestFixtures.messagesEn());
        var resp = res.logout(new AuthResource.RefreshTokenRequest("rt"), null);
        assertEquals(429, resp.getStatus());
        assertNull(stub.lastRevoked);
    }

    @Test
    void logout_missingRefresh_returns400() {
        var stub = new StubAuthService();
        var res = new AuthResource(stub, noopAuthPolicy(), AuthRateLimiter.noop(), I18nTestFixtures.messagesEn());
        var resp = res.logout(new AuthResource.RefreshTokenRequest(null), null);
        assertEquals(400, resp.getStatus());
        assertNull(stub.lastRevoked);
    }

    @Test
    void logout_revokeFails_returns502() {
        var stub = new StubAuthService();
        stub.revokeReturns = false;
        var res = new AuthResource(stub, noopAuthPolicy(), AuthRateLimiter.noop(), I18nTestFixtures.messagesEn());
        var resp = res.logout(new AuthResource.RefreshTokenRequest("rt"), null);
        assertEquals(502, resp.getStatus());
        assertInstanceOf(ApiError.class, resp.getEntity());
    }

    @Test
    void logout_revokeOk_returns204() {
        var stub = new StubAuthService();
        var res = new AuthResource(stub, noopAuthPolicy(), AuthRateLimiter.noop(), I18nTestFixtures.messagesEn());
        var resp = res.logout(new AuthResource.RefreshTokenRequest("rt"), null);
        assertEquals(204, resp.getStatus());
        assertEquals("rt", stub.lastRevoked);
    }

    private static AuthPolicyService noopAuthPolicy() {
        var appConfig = new AppConfig();
        return new AuthPolicyService(
            appConfig,
            new AuthPolicyRepository(null),
            new EmptyOrganizationLookupPort(),
            new KeycloakAuthSyncClient(appConfig));
    }

    private static final class StubAuthService extends AuthService {
        boolean revokeReturns = true;
        String lastRevoked;

        StubAuthService() {
            super(new AppConfig(), null, null, null);
        }

        @Override
        public boolean revokeRefreshToken(String refreshToken) {
            lastRevoked = refreshToken;
            return revokeReturns;
        }
    }
}
