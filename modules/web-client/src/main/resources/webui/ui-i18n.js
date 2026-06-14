(function (global) {
  "use strict";

  var LOCALE_KEY = "korus_web_locale";
  var DEFAULT_LOCALE = "ru";
  var HTML_LANG = {
    ru: "ru",
    en: "en",
    be: "be",
    kk: "kk",
    zh: "zh-Hans",
    ko: "ko",
  };

  var current = DEFAULT_LOCALE;
  var localeCodes = [DEFAULT_LOCALE];
  var loaded = {};
  var loading = {};

  var ERROR_EXACT = {
    "Request failed": "errors.requestFailed",
    "Internal Server Error": "errors.serverError",
    "Bad Request": "errors.badRequest",
    "Unauthorized": "errors.unauthorized",
    "Forbidden": "errors.forbidden",
    "Not Found": "errors.notFound",
    "Service Unavailable": "errors.serverUnavailable",
    "Invalid credentials": "auth.invalidCredentials",
    "Invalid username or password": "auth.invalidCredentials",
    "Сессия истекла — войдите снова.": "errors.sessionExpired",
    "Session expired — please sign in again.": "errors.sessionExpired",
  };

  function fetchJson(url) {
    return fetch(url, { credentials: "same-origin" }).then(function (res) {
      if (!res.ok) throw new Error("Locale fetch failed: " + url);
      return res.json();
    });
  }

  function loadManifest() {
    return fetchJson("/locales/manifest.json").then(function (manifest) {
      if (manifest && manifest.codes && manifest.codes.length) {
        localeCodes = manifest.codes.slice();
      }
      if (manifest && manifest.default) {
        DEFAULT_LOCALE = manifest.default;
      }
      return manifest;
    });
  }

  function loadLocale(code) {
    if (loaded[code]) return Promise.resolve(loaded[code]);
    if (loading[code]) return loading[code];
    loading[code] = fetchJson("/locales/" + encodeURIComponent(code) + ".json")
      .then(function (bundle) {
        loaded[code] = bundle;
        delete loading[code];
        return bundle;
      })
      .catch(function (err) {
        delete loading[code];
        throw err;
      });
    return loading[code];
  }

  function resolveBundle(code) {
    if (loaded[code]) return loaded[code];
    if (loaded[DEFAULT_LOCALE]) return loaded[DEFAULT_LOCALE];
    return {};
  }

  function lookup(bundle, key) {
    if (!key) return undefined;
    var node = bundle;
    var parts = String(key).split(".");
    for (var i = 0; i < parts.length; i++) {
      if (!node || typeof node !== "object") return undefined;
      node = node[parts[i]];
    }
    return typeof node === "string" ? node : undefined;
  }

  function interpolate(text, params) {
    if (!params || !text) return text;
    return String(text).replace(/\{(\w+)\}/g, function (_m, name) {
      return params[name] != null ? String(params[name]) : "{" + name + "}";
    });
  }

  function pickRegionalFromNavigator() {
    var nav = global.navigator;
    if (!nav) return null;
    var list =
      nav.languages && nav.languages.length ? nav.languages.slice() : [nav.language];
    for (var i = 0; i < list.length; i++) {
      var tag = String(list[i] || "").toLowerCase();
      if (!tag) continue;
      if (tag.indexOf("be") === 0 && localeCodes.indexOf("be") >= 0) return "be";
      if (tag.indexOf("kk") === 0 && localeCodes.indexOf("kk") >= 0) return "kk";
      if (tag.indexOf("ko") === 0 && localeCodes.indexOf("ko") >= 0) return "ko";
      if (tag.indexOf("zh") === 0 && localeCodes.indexOf("zh") >= 0) return "zh";
    }
    return null;
  }

  function detectLocale() {
    try {
      var stored = localStorage.getItem(LOCALE_KEY);
      if (stored && localeCodes.indexOf(stored) >= 0) return stored;
    } catch (e) {}
    var regional = pickRegionalFromNavigator();
    if (regional) return regional;
    return DEFAULT_LOCALE;
  }

  function applyHtmlLang(code) {
    if (global.document && global.document.documentElement) {
      global.document.documentElement.setAttribute("lang", HTML_LANG[code] || DEFAULT_LOCALE);
    }
  }

  function t(key, params) {
    var text =
      lookup(resolveBundle(current), key) ||
      lookup(resolveBundle(DEFAULT_LOCALE), key) ||
      key;
    return interpolate(text, params);
  }

  function hasCyrillic(text) {
    return /[\u0400-\u04FF]/.test(text);
  }

  function translateError(raw) {
    if (raw == null || raw === "") return t("errors.requestFailed");
    var text = String(raw).trim();
    if (!text) return t("errors.requestFailed");

    if (ERROR_EXACT[text]) return t(ERROR_EXACT[text]);

    if (/connect timed out|connection timed out|HTTP connect timed out/i.test(text)) {
      return t("errors.connectTimeout");
    }
    if (/HTTP Status 5\d\d|Internal Server Error/i.test(text)) {
      return t("errors.serverUnavailable");
    }
    if (/HTTP Status 400|Bad Request/i.test(text)) {
      return t("errors.badRequest");
    }
    if (/HTTP Status 401|Unauthorized|Invalid credentials/i.test(text)) {
      return t("auth.invalidCredentials");
    }
    if (/HTTP Status 403|Forbidden/i.test(text)) {
      return t("errors.forbidden");
    }
    if (/HTTP Status 404|Not Found/i.test(text)) {
      return t("errors.notFound");
    }
    if (/Unexpected character|StreamReadFeature|JSON parse/i.test(text)) {
      return t("errors.serverUnavailable");
    }
    if (/<!doctype html|<html/i.test(text)) {
      return t("errors.serverUnavailable");
    }

    if (hasCyrillic(text)) return text;
    return t("errors.genericWithDetail", { detail: text });
  }

  function translateMediaError(raw) {
    if (raw == null || raw === "") {
      return t("media.message", { detail: t("media.denied") });
    }
    var text = String(raw).trim();
    var detail;
    if (/not found|NotFoundError|Requested device not found/i.test(text)) {
      detail = t("media.deviceNotFound");
    } else if (/denied|NotAllowed|Permission/i.test(text)) {
      detail = t("media.denied");
    } else if (/not readable|NotReadable|Could not start/i.test(text)) {
      detail = t("media.notReadable");
    } else if (hasCyrillic(text)) {
      detail = text;
    } else {
      detail = translateError(text);
      if (detail.indexOf("errors.") === 0 || detail === text) {
        detail = text;
      }
    }
    return t("media.message", { detail: detail });
  }

  function getLocale() {
    return current;
  }

  function setLocale(code) {
    var next = code && localeCodes.indexOf(code) >= 0 ? code : DEFAULT_LOCALE;
    return loadLocale(DEFAULT_LOCALE)
      .then(function () {
        if (next !== DEFAULT_LOCALE) return loadLocale(next);
      })
      .then(function () {
        current = next;
        try {
          localStorage.setItem(LOCALE_KEY, next);
        } catch (e) {}
        applyHtmlLang(next);
      });
  }

  function init() {
    return loadManifest()
      .catch(function () {
        return null;
      })
      .then(function () {
        current = detectLocale();
        applyHtmlLang(current);
        return loadLocale(DEFAULT_LOCALE).then(function () {
          if (current !== DEFAULT_LOCALE) return loadLocale(current);
        });
      });
  }

  function supportedLocales() {
    return localeCodes.slice();
  }

  global.KorusI18n = {
    t: t,
    translateError: translateError,
    translateMediaError: translateMediaError,
    getLocale: getLocale,
    setLocale: setLocale,
    init: init,
    supportedLocales: supportedLocales,
  };
})(typeof window !== "undefined" ? window : globalThis);
