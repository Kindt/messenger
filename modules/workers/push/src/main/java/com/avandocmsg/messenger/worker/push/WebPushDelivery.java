package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * Sends Web Push notifications when {@code PUSH_VAPID_* env vars} are set.
 * {@code push_token} must be a JSON PushSubscription (as returned by the browser).
 */
public final class WebPushDelivery {
    private static final Logger log = LoggerFactory.getLogger(WebPushDelivery.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final PushService pushService;
    private final UserMessageSource workerMessages;

    private WebPushDelivery(PushService pushService, UserMessageSource workerMessages) {
        this.pushService = pushService;
        this.workerMessages = workerMessages;
    }

    public static WebPushDelivery fromEnvironment(UserMessageSource workerMessages) {
        var publicKey = readEnv("PUSH_VAPID_PUBLIC_KEY");
        var privateKey = readEnv("PUSH_VAPID_PRIVATE_KEY");
        var subject = readEnv("PUSH_VAPID_SUBJECT");
        if (publicKey.isEmpty() || privateKey.isEmpty()) {
            log.info(workerMessages.get("worker.push.web_disabled"));
            return disabled(workerMessages);
        }
        if (subject.isEmpty()) {
            subject = "mailto:notify@localhost";
        }
        try {
            var service = new PushService();
            service.setPublicKey(Utils.loadPublicKey(publicKey));
            service.setPrivateKey(Utils.loadPrivateKey(privateKey));
            service.setSubject(subject);
            log.info(workerMessages.format("worker.push.web_enabled", subject));
            return new WebPushDelivery(service, workerMessages);
        } catch (GeneralSecurityException e) {
            log.error(workerMessages.get("worker.push.web_invalid_keys"), e);
            return disabled(workerMessages);
        }
    }

    public static WebPushDelivery disabled(UserMessageSource workerMessages) {
        return new WebPushDelivery(null, workerMessages);
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
            log.debug(workerMessages.get("worker.push.skip_not_subscription"));
            return WebPushSendResult.FAILED;
        }
        try {
            var payload = MAPPER.writeValueAsString(Map.of(
                "title", preview.title(),
                "body", preview.body(),
                "url", preview.url(),
                "icon", preview.iconUrl() != null ? preview.iconUrl() : "/icon.svg"
            ));
            var notification = new Notification(subscription.get(), payload);
            pushService.send(notification);
            return WebPushSendResult.SENT;
        } catch (Exception e) {
            if (WebPushErrors.isExpiredSubscription(e)) {
                log.info(workerMessages.get("worker.push.subscription_expired"));
                return WebPushSendResult.EXPIRED;
            }
            log.warn(workerMessages.format("worker.push.delivery_failed", e.getMessage()));
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
