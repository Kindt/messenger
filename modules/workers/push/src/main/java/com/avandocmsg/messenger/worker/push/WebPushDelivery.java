package com.avandocmsg.messenger.worker.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Optional;

/**
 * Sends Web Push notifications when {@code PUSH_VAPID_* env vars} are set.
 * {@code push_token} must be a JSON PushSubscription (as returned by the browser).
 */
public final class WebPushDelivery {
    private static final Logger log = LoggerFactory.getLogger(WebPushDelivery.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PushService pushService;

    private WebPushDelivery(PushService pushService) {
        this.pushService = pushService;
    }

    public static WebPushDelivery fromEnvironment() {
        var publicKey = readEnv("PUSH_VAPID_PUBLIC_KEY");
        var privateKey = readEnv("PUSH_VAPID_PRIVATE_KEY");
        var subject = readEnv("PUSH_VAPID_SUBJECT");
        if (publicKey.isEmpty() || privateKey.isEmpty()) {
            log.info("Web Push disabled: set PUSH_VAPID_PUBLIC_KEY and PUSH_VAPID_PRIVATE_KEY");
            return disabled();
        }
        if (subject.isEmpty()) {
            subject = "mailto:notify@localhost";
        }
        try {
            var service = new PushService();
            service.setPublicKey(Utils.loadPublicKey(publicKey));
            service.setPrivateKey(Utils.loadPrivateKey(privateKey));
            service.setSubject(subject);
            log.info("Web Push enabled (subject={})", subject);
            return new WebPushDelivery(service);
        } catch (GeneralSecurityException e) {
            log.error("Web Push disabled: invalid VAPID keys", e);
            return disabled();
        }
    }

    public static WebPushDelivery disabled() {
        return new WebPushDelivery(null);
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public static boolean isWebProvider(String provider) {
        return provider != null && "web".equalsIgnoreCase(provider.trim());
    }

    public WebPushSendResult send(String pushToken, PushNotificationPreview preview) {
        if (!isEnabled() || preview == null) {
            return WebPushSendResult.FAILED;
        }
        var subscription = WebPushSubscriptionParser.parse(pushToken);
        if (subscription.isEmpty()) {
            log.debug("Skip web push: token is not a PushSubscription JSON");
            return WebPushSendResult.FAILED;
        }
        try {
            var payload = MAPPER.writeValueAsString(Map.of(
                "title", preview.title(),
                "body", preview.body(),
                "url", preview.url()
            ));
            var notification = new Notification(subscription.get(), payload);
            pushService.send(notification);
            return WebPushSendResult.SENT;
        } catch (Exception e) {
            if (WebPushErrors.isExpiredSubscription(e)) {
                log.info("Web push subscription expired (410)");
                return WebPushSendResult.EXPIRED;
            }
            log.warn("Web push delivery failed: {}", e.getMessage());
            return WebPushSendResult.FAILED;
        }
    }

    private static String readEnv(String name) {
        var v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return "";
        }
        return v.trim();
    }
}
