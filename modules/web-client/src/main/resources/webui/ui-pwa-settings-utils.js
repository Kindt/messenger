(function (global) {
  "use strict";

  function getVapidPublicKey(win) {
    var cfg = win && win.__WEB_CLIENT__;
    return cfg && cfg.vapidPublicKey ? String(cfg.vapidPublicKey) : null;
  }

  function notificationsAllowed(notifyPref, notificationApi) {
    return (
      !!notifyPref &&
      typeof notificationApi !== "undefined" &&
      notificationApi.permission === "granted"
    );
  }

  function urlBase64ToUint8Array(base64String) {
    var padding = "=".repeat((4 - (base64String.length % 4)) % 4);
    var base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
    var raw = global.atob(base64);
    var out = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) {
      out[i] = raw.charCodeAt(i);
    }
    return out;
  }

  function pushTokenFromSubscription(subscription) {
    if (subscription && typeof subscription.toJSON === "function") {
      return JSON.stringify(subscription.toJSON());
    }
    return JSON.stringify(subscription);
  }

  function canUseServiceWorker(nav) {
    return !!(nav && nav.serviceWorker);
  }

  function canUseWebPush(nav, win) {
    return canUseServiceWorker(nav) && !!(win && win.PushManager);
  }

  function isServiceWorkerDisabled(win) {
    var cfg = win && win.__WEB_CLIENT__;
    return !!(cfg && cfg.disableServiceWorker);
  }

  function nextServiceWorkerUpdatePromise(nav) {
    if (!canUseServiceWorker(nav)) return Promise.resolve();
    return nav.serviceWorker
      .getRegistration("/")
      .then(function (reg) {
        if (reg) return reg.update();
      })
      .catch(function () {});
  }

  function registerServiceWorker(nav, win, callbacks) {
    if (!canUseServiceWorker(nav) || isServiceWorkerDisabled(win)) {
      return Promise.resolve(false);
    }
    callbacks = callbacks || {};
    return nav.serviceWorker
      .register("/sw.js", { scope: "/" })
      .then(function (reg) {
        reg.update().catch(function () {});
        if (reg.waiting && nav.serviceWorker.controller && callbacks.onUpdateReady) {
          callbacks.onUpdateReady();
        }
        reg.addEventListener("updatefound", function () {
          var installing = reg.installing;
          if (!installing) return;
          installing.addEventListener("statechange", function () {
            if (
              installing.state === "installed" &&
              nav.serviceWorker.controller &&
              callbacks.onUpdateReady
            ) {
              callbacks.onUpdateReady();
            }
          });
        });
        if (callbacks.onControllerChange) {
          nav.serviceWorker.addEventListener("controllerchange", callbacks.onControllerChange);
        }
        return true;
      })
      .catch(function () {
        return false;
      });
  }

  function applyServiceWorkerUpdate(nav, onNoWaiting) {
    if (!canUseServiceWorker(nav)) return;
    nav.serviceWorker.getRegistration("/").then(function (reg) {
      if (reg && reg.waiting) {
        reg.waiting.postMessage({ type: "SKIP_WAITING" });
      } else if (onNoWaiting) {
        onNoWaiting();
      }
    });
  }

  global.KorusUiPwaSettingsUtils = {
    getVapidPublicKey: getVapidPublicKey,
    notificationsAllowed: notificationsAllowed,
    urlBase64ToUint8Array: urlBase64ToUint8Array,
    pushTokenFromSubscription: pushTokenFromSubscription,
    canUseServiceWorker: canUseServiceWorker,
    canUseWebPush: canUseWebPush,
    isServiceWorkerDisabled: isServiceWorkerDisabled,
    nextServiceWorkerUpdatePromise: nextServiceWorkerUpdatePromise,
    registerServiceWorker: registerServiceWorker,
    applyServiceWorkerUpdate: applyServiceWorkerUpdate,
  };
})(typeof window !== "undefined" ? window : globalThis);
