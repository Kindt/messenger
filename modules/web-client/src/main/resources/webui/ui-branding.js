(function (global) {
  "use strict";

  var CACHE_KEY = "korus_org_branding";
  var STYLE_ID = "korus-org-theme";
  var TOKEN_PREFIX = "--";

  function normalizeConfig(raw) {
    if (!raw || typeof raw !== "object") return null;
    var shellLayout = raw.shell_layout || "default";
    return {
      org_id: raw.org_id || null,
      palette: raw.palette || "korus",
      token_overrides: raw.token_overrides || {},
      custom_css: raw.custom_css || "",
      brand_title: raw.brand_title || null,
      demo_skins_enabled: raw.demo_skins_enabled,
      revision: raw.revision || 0,
      logo_url: raw.logo_url || null,
      shell_layout: shellLayout,
      auth_layout: raw.auth_layout || deriveAuthLayout(shellLayout),
      post_login_layout: raw.post_login_layout || derivePostLoginLayout(shellLayout),
    };
  }

  function deriveAuthLayout(shellLayout) {
    return shellLayout === "auth-split" ? "auth-split" : "default";
  }

  function derivePostLoginLayout(shellLayout) {
    if (shellLayout === "auth-split") return "default";
    if (shellLayout === "compact") return "compact";
    return "default";
  }

  function applyShellLayout(doc, config, options) {
    options = options || {};
    var cfg = config || {};
    var layout = options.postLogin
      ? cfg.post_login_layout || derivePostLoginLayout(cfg.shell_layout)
      : cfg.auth_layout || deriveAuthLayout(cfg.shell_layout);
    doc.documentElement.setAttribute("data-shell-layout", layout || "default");
    if (cfg.shell_layout) {
      doc.documentElement.setAttribute("data-shell-layout-source", cfg.shell_layout);
    }
  }

  function themeColorFromDoc(doc) {
    var rootStyle = getComputedStyle(doc.documentElement);
    var fromVar = rootStyle.getPropertyValue("--theme-color-meta").trim();
    return fromVar || "#7949f4";
  }

  function clearTokenOverrides(doc) {
    var style = doc.documentElement.style;
    var toRemove = [];
    for (var i = 0; i < style.length; i++) {
      var prop = style[i];
      if (prop && prop.indexOf(TOKEN_PREFIX) === 0) {
        toRemove.push(prop);
      }
    }
    toRemove.forEach(function (prop) {
      doc.documentElement.style.removeProperty(prop);
    });
  }

  function applyTokenOverrides(doc, overrides) {
    if (!overrides || typeof overrides !== "object") return;
    Object.keys(overrides).forEach(function (key) {
      if (key.indexOf(TOKEN_PREFIX) !== 0) return;
      var val = overrides[key];
      if (val != null && String(val).trim()) {
        doc.documentElement.style.setProperty(key, String(val).trim());
      }
    });
  }

  function applyFavicon(doc, href) {
    if (!doc || !href) return;
    var links = doc.querySelectorAll('link[rel="icon"], link[rel="apple-touch-icon"]');
    if (links.length) {
      links.forEach(function (link) {
        link.href = href;
      });
      return;
    }
    var link = doc.createElement("link");
    link.rel = "icon";
    link.href = href;
    if (href.indexOf(".svg") >= 0) {
      link.type = "image/svg+xml";
    }
    doc.head.appendChild(link);
  }

  function notifyServiceWorker(config) {
    if (!config || !global.navigator || !navigator.serviceWorker || !navigator.serviceWorker.controller) {
      return;
    }
    try {
      navigator.serviceWorker.controller.postMessage({
        type: "branding",
        revision: config.revision || 0,
        themeColor: themeColorFromDoc(document),
        palette: config.palette || "korus",
        brandTitle: config.brand_title || null,
      });
    } catch (e) {}
  }

  function isPlatformDefaultBranding(cfg) {
    if (!cfg) return true;
    var hasCss = cfg.custom_css && String(cfg.custom_css).trim();
    var hasTokens =
      cfg.token_overrides && Object.keys(cfg.token_overrides).length > 0;
    var hasTitle = cfg.brand_title && String(cfg.brand_title).trim();
    var hasLogo = cfg.logo_url && String(cfg.logo_url).trim();
    var palette = cfg.palette || "korus";
    return palette === "korus" && !hasCss && !hasTokens && !hasTitle && !hasLogo;
  }

  function resolveMergedPalette(cfg, demoPalette) {
    var shell = global.KorusUiShellUtils;
    var normalizedDemo =
      demoPalette && shell && shell.normalizePalette
        ? shell.normalizePalette(demoPalette)
        : demoPalette;
    if (
      normalizedDemo &&
      normalizedDemo !== "korus" &&
      isPlatformDefaultBranding(cfg)
    ) {
      return normalizedDemo;
    }
    var palette = cfg && cfg.palette ? cfg.palette : "korus";
    return shell && shell.normalizePalette ? shell.normalizePalette(palette) : palette;
  }

  function applyOrgBranding(doc, config, options) {
    options = options || {};
    var cfg = normalizeConfig(config);
    if (!cfg) return null;

    if (options.applyPalette !== false && cfg.palette) {
      var shell = global.KorusUiShellUtils;
      var palette = shell && shell.normalizePalette ? shell.normalizePalette(cfg.palette) : cfg.palette;
      doc.documentElement.setAttribute("data-palette", palette);
      cfg.palette = palette;
    }

    clearTokenOverrides(doc);
    applyTokenOverrides(doc, cfg.token_overrides);

    var styleEl = doc.getElementById(STYLE_ID);
    if (!styleEl) {
      styleEl = doc.createElement("style");
      styleEl.id = STYLE_ID;
      doc.head.appendChild(styleEl);
    }
    styleEl.textContent = cfg.custom_css || "";
    styleEl.setAttribute("data-revision", String(cfg.revision || 0));

    var metaTheme = doc.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.setAttribute("content", themeColorFromDoc(doc));
    }

    if (options.cache !== false) {
      try {
        sessionStorage.setItem(CACHE_KEY, JSON.stringify(cfg));
      } catch (e) {}
    }

    if (options.notifySw !== false) {
      notifyServiceWorker(cfg);
    }

    if (options.applyShellLayout !== false) {
      applyShellLayout(doc, cfg, options);
    }

    return cfg;
  }

  function clearOrgBranding(doc) {
    var styleEl = doc.getElementById(STYLE_ID);
    if (styleEl) {
      styleEl.textContent = "";
      styleEl.removeAttribute("data-revision");
    }
    clearTokenOverrides(doc);
    try {
      sessionStorage.removeItem(CACHE_KEY);
    } catch (e) {}
    if (global.navigator && navigator.serviceWorker && navigator.serviceWorker.controller) {
      try {
        navigator.serviceWorker.controller.postMessage({ type: "branding-clear" });
      } catch (e) {}
    }
  }

  function loadCachedBranding() {
    try {
      var raw = sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      return normalizeConfig(JSON.parse(raw));
    } catch (e) {
      return null;
    }
  }

  global.KorusUiBranding = {
    CACHE_KEY: CACHE_KEY,
    STYLE_ID: STYLE_ID,
    normalizeConfig: normalizeConfig,
    deriveAuthLayout: deriveAuthLayout,
    derivePostLoginLayout: derivePostLoginLayout,
    applyShellLayout: applyShellLayout,
    isPlatformDefaultBranding: isPlatformDefaultBranding,
    resolveMergedPalette: resolveMergedPalette,
    applyOrgBranding: applyOrgBranding,
    applyFavicon: applyFavicon,
    clearOrgBranding: clearOrgBranding,
    loadCachedBranding: loadCachedBranding,
    themeColorFromDoc: themeColorFromDoc,
  };
})(typeof window !== "undefined" ? window : globalThis);
