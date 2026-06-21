/* Korus web-client: cache static assets; do NOT show login shell when server is down. */
"use strict";

var CACHE_NAME = "korus-web-static-v9";

function pushDefaultsFromCache() {
  return caches.match("/locales/ru.json").then(function (cached) {
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
  }).catch(function () {
    return { title: "Korus Messenger", body: "New message" };
  });
}

var PRECACHE = [
  "/tailwind.css",
  "/styles.css",
  "/themes.css",
  "/manifest.json",
  "/icon.svg",
  "/locales/manifest.json",
];

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

function isStaticAsset(pathname) {
  if (!pathname || pathname.indexOf("/api") === 0) return false;
  if (pathname === "/web-client-env.js" || pathname === "/health") return false;
  if (pathname === "/sw.js" || pathname === "/app.js") return false;
  if (pathname === "/" || pathname === "/index.html") return false;
  return (
    pathname.endsWith(".css") ||
    pathname.endsWith(".js") ||
    pathname.endsWith(".json") ||
    pathname.endsWith(".svg")
  );
}

self.addEventListener("install", function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return cache.addAll(PRECACHE).catch(function () {});
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(
        keys
          .filter(function (k) {
            return k !== CACHE_NAME;
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

  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request).catch(function () {
        return new Response(OFFLINE_HTML, {
          status: 503,
          statusText: "Service Unavailable",
          headers: { "Content-Type": "text/html; charset=utf-8" },
        });
      })
    );
    return;
  }

  if (!isStaticAsset(url.pathname)) return;

  if (url.pathname.indexOf("/locales/") === 0 && url.pathname.endsWith(".json")) {
    event.respondWith(
      fetch(event.request).then(function (response) {
        if (response && response.ok) {
          var copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) {
            cache.put(event.request, copy);
          });
        }
        return response;
      }).catch(function () {
        return caches.match(event.request);
      })
    );
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then(function (response) {
        if (response && response.ok) {
          var copy = response.clone();
          caches.open(CACHE_NAME).then(function (cache) {
            cache.put(event.request, copy);
          });
        }
        return response;
      })
      .catch(function () {
        return caches.match(event.request).then(function (cached) {
          return cached || new Response("", { status: 503, statusText: "Offline" });
        });
      })
  );
});

self.addEventListener("message", function (event) {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
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
        } catch (e) {}
      }
      return Promise.all([
        self.registration.showNotification(payload.title, {
          body: payload.body,
          icon: "/icon.svg",
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
