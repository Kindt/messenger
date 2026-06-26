/* Korus web-client: tiered static cache; do NOT show login shell when server is down. */
"use strict";

var CACHE_VERSION = "v23";
var SHELL_CACHE = "korus-web-shell-" + CACHE_VERSION;
var LOCALES_CACHE = "korus-web-locales-" + CACHE_VERSION;
var STATIC_CACHE = "korus-web-static-" + CACHE_VERSION;
var ALL_CACHES = [SHELL_CACHE, LOCALES_CACHE, STATIC_CACHE];
var AVATAR_CACHE = "korus-web-avatars-" + CACHE_VERSION;
var AVATAR_CACHE_MAX = 64;
var avatarCacheKeys = [];

function isAvatarResizeRequest(url) {
  return (
    url.pathname.indexOf("/api/v1/files/") === 0 &&
    url.pathname.endsWith("/resize") &&
    url.search.indexOf("avt=") >= 0
  );
}

function rememberAvatarCacheKey(key) {
  var idx = avatarCacheKeys.indexOf(key);
  if (idx >= 0) {
    avatarCacheKeys.splice(idx, 1);
  }
  avatarCacheKeys.push(key);
  while (avatarCacheKeys.length > AVATAR_CACHE_MAX) {
    var evict = avatarCacheKeys.shift();
    caches.open(AVATAR_CACHE).then(function (cache) {
      cache.delete(evict);
    });
  }
}

function avatarCacheFirst(request) {
  return caches.open(AVATAR_CACHE).then(function (cache) {
    return cache.match(request).then(function (cached) {
      if (cached) {
        return cached;
      }
      return fetch(request).then(function (response) {
        if (response && response.ok) {
          cache.put(request, response.clone());
          rememberAvatarCacheKey(request.url);
        }
        return response;
      });
    });
  });
}

var SHELL_PRECACHE = [
  "/fonts.css",
  "/tailwind.css",
  "/styles.css",
  "/themes.css",
  "/themes-palettes.css",
  "/manifest.json",
  "/icon.svg",
];

var cachedBranding = {
  revision: 0,
  themeColor: "#7949f4",
  palette: "korus",
  brandTitle: null,
};

var LOCALES_PRECACHE = ["/locales/manifest.json"];

function pushDefaultsFromCache() {
  return caches
    .open(LOCALES_CACHE)
    .then(function (cache) {
      return cache.match("/locales/ru.json");
    })
    .then(function (cached) {
      if (!cached) {
        return { title: "Korus Messenger", body: "New message" };
      }
      return cached.json().then(function (bundle) {
        var push = bundle && bundle.push ? bundle.push : {};
        return {
          title: push.defaultTitle || "Korus Messenger",
          body: push.defaultBody || "New message",
        };
      });
    })
    .catch(function () {
      return { title: "Korus Messenger", body: "New message" };
    });
}

var OFFLINE_HTML =
  "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\"/>" +
  "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
  "<meta name=\"theme-color\" content=\"#7949f4\"/>" +
  "<title>Korus — нет связи</title>" +
  "<style>body{font-family:\"Source Sans 3\",system-ui,sans-serif;background:#0c0b10;color:#f4f3f7;" +
  "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}" +
  ".box{max-width:28rem;padding:1.5rem;border:1px solid #2e2c38;border-radius:12px}" +
  "h1{font-size:1.1rem;margin:0 0 .75rem;color:#7949f4}p{margin:0;font-size:.9rem;color:#8e8e8e;line-height:1.5}" +
  "code{color:#cebcff}</style></head><body><div class=\"box\">" +
  "<h1>Сервер недоступен</h1>" +
  "<p>Страница входа из кэша не показывается намеренно. Проверьте, что стек запущен " +
  "(<code>qemu-up</code> / Docker), затем обновите страницу.</p>" +
  "<p style=\"margin-top:1rem\">Если остаётся старая версия UI: Настройки → «Сбросить кэш UI» " +
  "или DevTools → Application → Service Workers → Unregister.</p></div></body></html>";

function offlineHtmlResponse() {
  var accent = cachedBranding.themeColor || "#7949f4";
  var title = cachedBranding.brandTitle || "Korus";
  var html =
    "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\"/>" +
    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
    "<meta name=\"theme-color\" content=\"" +
    accent +
    "\"/>" +
    "<title>" +
    title +
    " — нет связи</title>" +
    "<style>body{font-family:\"Source Sans 3\",system-ui,sans-serif;background:#0c0b10;color:#f4f3f7;" +
    "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}" +
    ".box{max-width:28rem;padding:1.5rem;border:1px solid #2e2c38;border-radius:12px}" +
    "h1{font-size:1.1rem;margin:0 0 .75rem;color:" +
    accent +
    "}p{margin:0;font-size:.9rem;color:#8e8e8e;line-height:1.5}" +
    "code{color:" +
    accent +
    "}</style></head><body><div class=\"box\">" +
    "<h1>Сервер недоступен</h1>" +
    "<p>Оформление организации сохранено для offline (revision " +
    String(cachedBranding.revision || 0) +
    ", palette " +
    String(cachedBranding.palette || "korus") +
    ").</p>" +
    "<p style=\"margin-top:1rem\">Проверьте, что стек запущен, затем обновите страницу.</p></div></body></html>";
  return new Response(html, {
    status: 503,
    statusText: "Service Unavailable",
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

function isLocaleJson(pathname) {
  return pathname && pathname.indexOf("/locales/") === 0 && pathname.endsWith(".json");
}

function isShellAsset(pathname) {
  if (!pathname) return false;
  for (var i = 0; i < SHELL_PRECACHE.length; i++) {
    if (SHELL_PRECACHE[i] === pathname) return true;
  }
  return false;
}

function isStaticAsset(pathname) {
  if (!pathname || pathname.indexOf("/api") === 0) return false;
  if (pathname === "/web-client-env.js" || pathname === "/health") return false;
  if (pathname === "/sw.js" || pathname === "/app.js" || pathname === "/app.bundle.js") return false;
  if (pathname === "/ui-lazy-call.mjs" || pathname.endsWith(".mjs")) return false;
  if (pathname === "/" || pathname === "/index.html") return false;
  if (isLocaleJson(pathname)) return true;
  return (
    pathname.endsWith(".css") ||
    pathname.endsWith(".js") ||
    pathname.endsWith(".json") ||
    pathname.endsWith(".svg") ||
    pathname.endsWith(".woff2")
  );
}

function staleWhileRevalidate(cacheName, request) {
  return caches.open(cacheName).then(function (cache) {
    return cache.match(request).then(function (cached) {
      var networkUpdate = fetch(request)
        .then(function (response) {
          if (response && response.ok) {
            cache.put(request, response.clone());
          }
          return response;
        })
        .catch(function () {
          return null;
        });
      if (cached) {
        networkUpdate.catch(function () {});
        return cached;
      }
      return networkUpdate.then(function (response) {
        if (response) return response;
        return new Response("", { status: 503, statusText: "Offline" });
      });
    });
  });
}

function networkFirstLocale(request) {
  return fetch(request)
    .then(function (response) {
      if (response && response.ok) {
        var copy = response.clone();
        caches.open(LOCALES_CACHE).then(function (cache) {
          cache.put(request, copy);
        });
      }
      return response;
    })
    .catch(function () {
      return caches.match(request).then(function (cached) {
        return (
          cached ||
          new Response("{}", {
            status: 503,
            statusText: "Offline",
            headers: { "Content-Type": "application/json; charset=utf-8" },
          })
        );
      });
    });
}

self.addEventListener("install", function (event) {
  event.waitUntil(
    Promise.all([
      caches.open(SHELL_CACHE).then(function (cache) {
        return cache.addAll(SHELL_PRECACHE).catch(function () {});
      }),
      caches.open(LOCALES_CACHE).then(function (cache) {
        return cache.addAll(LOCALES_PRECACHE).catch(function () {});
      }),
    ])
  );
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(
        keys
          .filter(function (k) {
            return ALL_CACHES.indexOf(k) === -1 && k !== AVATAR_CACHE;
          })
          .map(function (k) {
            return caches.delete(k);
          })
      );
    })
  );
  self.clients.claim();
});

self.addEventListener("fetch", function (event) {
  if (event.request.method !== "GET") return;
  var url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  if (isAvatarResizeRequest(url)) {
    event.respondWith(
      avatarCacheFirst(event.request).catch(function () {
        return fetch(event.request);
      })
    );
    return;
  }

  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request).catch(function () {
        return offlineHtmlResponse();
      })
    );
    return;
  }

  if (!isStaticAsset(url.pathname)) return;

  if (isLocaleJson(url.pathname)) {
    event.respondWith(networkFirstLocale(event.request));
    return;
  }

  if (isShellAsset(url.pathname)) {
    event.respondWith(staleWhileRevalidate(SHELL_CACHE, event.request));
    return;
  }

  event.respondWith(staleWhileRevalidate(STATIC_CACHE, event.request));
});

self.addEventListener("message", function (event) {
  if (!event.data) return;
  if (event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
    return;
  }
  if (event.data.type === "branding") {
    cachedBranding.revision = event.data.revision || 0;
    cachedBranding.themeColor = event.data.themeColor || "#7949f4";
    cachedBranding.palette = event.data.palette || "korus";
    cachedBranding.brandTitle = event.data.brandTitle || null;
    return;
  }
  if (event.data.type === "branding-clear") {
    cachedBranding = {
      revision: 0,
      themeColor: "#7949f4",
      palette: "korus",
      brandTitle: null,
    };
  }
});

self.addEventListener("push", function (event) {
  event.waitUntil(
    pushDefaultsFromCache().then(function (defaults) {
      var payload = {
        title: defaults.title,
        body: defaults.body,
        url: "/",
      };
      if (event.data) {
        try {
          var j = event.data.json();
          if (j.title) payload.title = j.title;
          if (j.body) payload.body = j.body;
          if (j.url) payload.url = j.url;
          if (j.icon) payload.icon = j.icon;
        } catch (e) {}
      }
      return Promise.all([
        self.registration.showNotification(payload.title, {
          body: payload.body,
          icon: payload.icon || "/icon.svg",
          tag: "korus-push",
          data: payload,
        }),
        clients.matchAll({ type: "window", includeUncontrolled: true }).then(function (list) {
          list.forEach(function (client) {
            client.postMessage({ type: "korus-push", payload: payload });
          });
        }),
      ]);
    })
  );
});

self.addEventListener("notificationclick", function (event) {
  event.notification.close();
  var target = "/";
  if (event.notification.data) {
    if (event.notification.data.url) target = event.notification.data.url;
    else if (event.notification.data.chatId) {
      target = "/?chat=" + event.notification.data.chatId;
    }
  }
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then(function (list) {
      for (var i = 0; i < list.length; i++) {
        if ("focus" in list[i]) {
          list[i].postMessage({ type: "korus-navigate", url: target });
          return list[i].focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(target);
      }
    })
  );
});
