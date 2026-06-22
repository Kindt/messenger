(function (global) {
  "use strict";

  var STORAGE_KEY = "admin_console_locale";
  var DEFAULT_LOCALE = "ru";
  var HTML_LANG = {
    ru: "ru",
    en: "en",
    be: "be",
    kk: "kk",
    zh: "zh-Hans",
    ko: "ko",
  };
  var localeCodes = [DEFAULT_LOCALE];
  var loaded = {};
  var current = DEFAULT_LOCALE;
  var applying = false;
  var originalConfirm = global.confirm ? global.confirm.bind(global) : null;
  var originalAlert = global.alert ? global.alert.bind(global) : null;

  function fetchJson(url) {
    return fetch(url, { credentials: "same-origin" }).then(function (res) {
      if (!res.ok) throw new Error("Admin locale fetch failed: " + url);
      return res.json();
    });
  }

  function loadManifest() {
    return fetchJson("locales/manifest.json").then(function (manifest) {
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
    var next = localeCodes.indexOf(code) >= 0 ? code : DEFAULT_LOCALE;
    if (loaded[next]) return Promise.resolve(loaded[next]);
    return fetchJson("locales/" + encodeURIComponent(next) + ".json").then(function (bundle) {
      loaded[next] = bundle || {};
      return loaded[next];
    });
  }

  function lookup(bundle, key) {
    var node = bundle;
    String(key || "")
      .split(".")
      .forEach(function (part) {
        if (node && typeof node === "object") node = node[part];
        else node = undefined;
      });
    return typeof node === "string" ? node : undefined;
  }

  function interpolate(text, params) {
    if (!params) return text;
    return String(text).replace(/\{(\w+)\}/g, function (_m, name) {
      return params[name] != null ? String(params[name]) : "{" + name + "}";
    });
  }

  function bundleFor(code) {
    return loaded[code] || loaded[DEFAULT_LOCALE] || {};
  }

  function t(key, params) {
    var text = lookup(bundleFor(current), key) || lookup(bundleFor(DEFAULT_LOCALE), key) || key;
    return interpolate(text, params);
  }

  function text(value) {
    var raw = String(value == null ? "" : value);
    var trimmed = raw.trim();
    if (!trimmed) return raw;
    var table = (bundleFor(current).text || {});
    var translated = table[trimmed];
    if (!translated && current !== DEFAULT_LOCALE) {
      translated = (bundleFor(DEFAULT_LOCALE).text || {})[trimmed];
    }
    if (!translated || translated === trimmed) return raw;
    return raw.replace(trimmed, translated);
  }

  function applyLang(code) {
    if (document.documentElement) {
      document.documentElement.setAttribute("lang", HTML_LANG[code] || DEFAULT_LOCALE);
    }
  }

  function translateAttrs(el) {
    ["placeholder", "title", "aria-label", "alt", "value"].forEach(function (attr) {
      if (!el.getAttribute || !el.hasAttribute(attr)) return;
      var next = text(el.getAttribute(attr));
      if (next !== el.getAttribute(attr)) el.setAttribute(attr, next);
    });
  }

  function translateNode(node) {
    if (!node) return;
    if (node.nodeType === Node.TEXT_NODE) {
      var next = text(node.nodeValue);
      if (next !== node.nodeValue) node.nodeValue = next;
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    var el = node;
    if (el.hasAttribute && el.hasAttribute("data-i18n")) {
      el.textContent = t(el.getAttribute("data-i18n"));
    }
    translateAttrs(el);
    Array.prototype.forEach.call(el.childNodes || [], translateNode);
  }

  function applyDom() {
    if (!document.body) return;
    applying = true;
    try {
      document.title = t("admin.documentTitle");
      setupLocaleSelect();
      translateNode(document.body);
    } finally {
      applying = false;
    }
  }

  function setupLocaleSelect() {
    var select = document.querySelector("[data-testid=admin-locale-select]");
    if (!select || select.dataset.adminI18nReady === "true") return;
    select.dataset.adminI18nReady = "true";
    select.innerHTML = "";
    localeCodes.forEach(function (code) {
      var option = document.createElement("option");
      option.value = code;
      option.textContent = code;
      select.appendChild(option);
    });
    select.value = current;
    select.addEventListener("change", function () {
      setLocale(select.value).catch(function (err) {
        console.error("admin locale switch failed", err);
      });
    });
  }

  function observeDom() {
    if (!global.MutationObserver || !document.body) return;
    var obs = new MutationObserver(function (mutations) {
      if (applying) return;
      applying = true;
      try {
        mutations.forEach(function (m) {
          Array.prototype.forEach.call(m.addedNodes || [], translateNode);
          if (m.type === "characterData") translateNode(m.target);
          if (m.type === "attributes") translateAttrs(m.target);
        });
      } finally {
        applying = false;
      }
    });
    obs.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ["placeholder", "title", "aria-label", "alt", "value"],
    });
  }

  function patchDialogs() {
    if (originalConfirm) {
      global.confirm = function (message) {
        return originalConfirm(text(message));
      };
    }
    if (originalAlert) {
      global.alert = function (message) {
        return originalAlert(text(message));
      };
    }
  }

  function detectLocale() {
    try {
      var stored = localStorage.getItem(STORAGE_KEY);
      if (stored && localeCodes.indexOf(stored) >= 0) return stored;
    } catch (e) {}
    return DEFAULT_LOCALE;
  }

  function setLocale(code) {
    var next = localeCodes.indexOf(code) >= 0 ? code : DEFAULT_LOCALE;
    return loadLocale(DEFAULT_LOCALE)
      .then(function () {
        return next === DEFAULT_LOCALE ? null : loadLocale(next);
      })
      .then(function () {
        current = next;
        try {
          localStorage.setItem(STORAGE_KEY, next);
        } catch (e) {}
        applyLang(next);
        applyDom();
        return next;
      });
  }

  function init() {
    return loadManifest()
      .then(function () {
        return setLocale(detectLocale());
      })
      .then(function () {
        observeDom();
        patchDialogs();
      });
  }

  global.AdminI18n = {
    init: init,
    t: t,
    text: text,
    setLocale: setLocale,
    getLocale: function () {
      return current;
    },
    supportedLocales: function () {
      return localeCodes.slice();
    },
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      init().catch(function (err) {
        console.error("admin i18n init failed", err);
      });
    });
  } else {
    init().catch(function (err) {
      console.error("admin i18n init failed", err);
    });
  }
})(window);
