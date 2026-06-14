(function (global) {
  "use strict";

  function apiRoot() {
    return "/api/v1";
  }

  function wsBaseUrl(win, loc) {
    var cfg = win && win.__WEB_CLIENT__;
    var pageHost = loc && loc.host ? loc.host : "127.0.0.1:8081";
    var sameOrigin =
      (loc && loc.protocol === "https:" ? "wss:" : "ws:") + "//" + pageHost + "/ws";
    if (cfg && cfg.wsUrl) {
      var configured = String(cfg.wsUrl).replace(/\/$/, "");
      try {
        var cfgUrl = new URL(configured);
        var pageHost = loc && loc.host ? loc.host : "";
        var pageHostname = loc && loc.hostname ? loc.hostname : "";
        var pageIsLoopback = pageHostname === "127.0.0.1" || pageHostname === "localhost";
        if (pageIsLoopback && pageHost && cfgUrl.host !== pageHost) {
          return sameOrigin;
        }
      } catch (e) {
        /* keep configured */
      }
      return configured;
    }
    return sameOrigin;
  }

  function buildWsUrl(baseUrl, accessToken) {
    return baseUrl + "?token=" + encodeURIComponent(accessToken);
  }

  function nextWsReconnectDelay(attempt) {
    return Math.min(30000, 1000 * Math.pow(2, attempt));
  }

  function createApiClient(options) {
    var fetchImpl = options.fetchImpl || global.fetch.bind(global);
    var getAccessToken = options.getAccessToken || function () { return null; };
    var getRefreshToken = options.getRefreshToken || function () { return null; };
    var isPublicAuthPath = options.isPublicAuthPath || function () { return false; };
    var tryRefreshTokens = options.tryRefreshTokens || (async function () { return false; });
    var onSessionExpired = options.onSessionExpired || function () {};
    var root = options.apiRoot || apiRoot();

    async function apiFetch(path, opts) {
      opts = opts || {};
      var headers = Object.assign({}, opts.headers || {});
      if (!(opts.body instanceof FormData) && !headers.Accept) {
        headers.Accept = opts.accept || "application/json";
      }
      var body = opts.body;
      if (opts.jsonBody !== undefined) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(opts.jsonBody);
      }
      var accessToken = getAccessToken();
      if (accessToken && !opts.noAuth) {
        headers.Authorization = "Bearer " + accessToken;
      }
      var url = root + (path.startsWith("/") ? path : "/" + path);
      var res = await fetchImpl(url, {
        method: opts.method || "GET",
        headers: headers,
        body: body,
      });
      if (
        res.status === 401 &&
        !opts.noRefresh &&
        getRefreshToken() &&
        !isPublicAuthPath(path)
      ) {
        var refreshed = await tryRefreshTokens();
        if (refreshed) {
          return apiFetch(path, Object.assign({}, opts, { noRefresh: true }));
        }
        onSessionExpired();
        throw new Error(
          global.KorusI18n
            ? global.KorusI18n.t("errors.sessionExpired")
            : "Сессия истекла — войдите снова."
        );
      }
      return res;
    }

    async function apiJson(path, opts) {
      var res = await apiFetch(path, opts || {});
      var text = await res.text();
      var parsed = null;
      if (text) {
        try {
          parsed = JSON.parse(text);
        } catch (e) {
          parsed = text;
        }
      }
      if (!res.ok) {
        var msg =
          parsed && typeof parsed === "object" && parsed.message
            ? String(parsed.message)
            : res.statusText;
        throw new Error(
          global.KorusI18n
            ? global.KorusI18n.translateError(msg)
            : msg || "Request failed"
        );
      }
      return parsed;
    }

    return { apiFetch: apiFetch, apiJson: apiJson };
  }

  global.KorusUiTransportUtils = {
    apiRoot: apiRoot,
    wsBaseUrl: wsBaseUrl,
    buildWsUrl: buildWsUrl,
    nextWsReconnectDelay: nextWsReconnectDelay,
    createApiClient: createApiClient,
  };
})(typeof window !== "undefined" ? window : globalThis);
