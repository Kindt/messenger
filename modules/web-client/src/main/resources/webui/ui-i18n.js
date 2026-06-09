(function (global) {
  "use strict";

  var LOCALE_KEY = "korus_web_locale";
  var DEFAULT_LOCALE = "ru";
  var current = DEFAULT_LOCALE;

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

  function bundles() {
    return global.KorusLocales || {};
  }

  function resolveBundle(code) {
    var all = bundles();
    if (all[code]) return all[code];
    if (all[DEFAULT_LOCALE]) return all[DEFAULT_LOCALE];
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

  function detectLocale() {
    try {
      var stored = localStorage.getItem(LOCALE_KEY);
      if (stored && resolveBundle(stored)) return stored;
    } catch (e) {}
    var nav = (global.navigator && global.navigator.language) || DEFAULT_LOCALE;
    nav = String(nav).toLowerCase();
    if (nav.indexOf("en") === 0 && resolveBundle("en")) return "en";
    return DEFAULT_LOCALE;
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
    var next = code && resolveBundle(code) ? code : DEFAULT_LOCALE;
    current = next;
    try {
      localStorage.setItem(LOCALE_KEY, next);
    } catch (e) {}
    if (global.document && global.document.documentElement) {
      global.document.documentElement.setAttribute("lang", next === "en" ? "en" : "ru");
    }
  }

  function init() {
    setLocale(detectLocale());
  }

  function supportedLocales() {
    return Object.keys(bundles()).filter(function (code) {
      return !!resolveBundle(code);
    });
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
