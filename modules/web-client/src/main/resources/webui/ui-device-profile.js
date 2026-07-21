/**
 * Device profile: mobile/tablet/desktop from user agent (not viewport width).
 * Orientation: portrait / landscape (Screen Orientation API or matchMedia).
 * Mobile shell (single-pane): phone always; tablet in portrait only.
 */
(function (global) {
  "use strict";

  var PROFILE = {
    device: "desktop",
    orientation: "landscape",
    mobileShell: false,
  };

  var installed = false;
  var onChangeCb = null;

  function detectDevice(ua, platform, maxTouchPoints, userAgentData) {
    ua = ua || "";
    platform = platform || "";
    maxTouchPoints = maxTouchPoints || 0;

    var isIpad =
      /\biPad\b/.test(ua) || (platform === "MacIntel" && maxTouchPoints > 1);
    var isIphone = /\biPhone\b|\biPod\b/.test(ua);
    var isAndroid = /\bAndroid\b/i.test(ua);
    var isKindle = /\bSilk\b|\bKindle\b/i.test(ua);

    // Chromium Client Hints — надёжнее UA-строки на Chrome/Samsung/Edge Android.
    if (userAgentData && typeof userAgentData.mobile === "boolean") {
      if (userAgentData.mobile) return "phone";
      if (isAndroid || isIpad || isKindle) return "tablet";
    }

    // Android: телефоны почти всегда с «Mobile», планшеты — без него.
    var isAndroidPhone = isAndroid && /\bMobile\b/i.test(ua);
    var isAndroidTablet = isAndroid && !/\bMobile\b/i.test(ua);

    if (isIphone || isAndroidPhone) return "phone";
    if (isIpad || isAndroidTablet || isKindle) return "tablet";
    if (/\bWindows Phone\b/i.test(ua) || /\bIEMobile\b/i.test(ua)) return "phone";
    if (/\bWindows\b/i.test(ua) && /\bTouch\b/i.test(ua)) return "tablet";

    return "desktop";
  }

  function detectOrientation() {
    if (typeof screen !== "undefined" && screen.orientation && screen.orientation.type) {
      return screen.orientation.type.indexOf("portrait") === 0 ? "portrait" : "landscape";
    }
    if (typeof global.matchMedia === "function") {
      return global.matchMedia("(orientation: portrait)").matches ? "portrait" : "landscape";
    }
    return "landscape";
  }

  function computeMobileShell(device, orientation) {
    if (device === "phone") return true;
    if (device === "tablet" && orientation === "portrait") return true;
    return false;
  }

  function readProfile(nav) {
    nav = nav || (typeof global.navigator !== "undefined" ? global.navigator : null);
    var ua = nav && nav.userAgent ? nav.userAgent : "";
    var platform = nav && nav.platform ? nav.platform : "";
    var maxTouchPoints = nav && nav.maxTouchPoints ? nav.maxTouchPoints : 0;
    var userAgentData = nav && nav.userAgentData ? nav.userAgentData : null;
    var device = detectDevice(ua, platform, maxTouchPoints, userAgentData);
    var orientation = detectOrientation();
    return {
      device: device,
      orientation: orientation,
      mobileShell: computeMobileShell(device, orientation),
    };
  }

  function applyProfile(doc, profile) {
    doc = doc || (typeof document !== "undefined" ? document : null);
    if (!doc || !doc.documentElement) return profile;
    var html = doc.documentElement;
    html.setAttribute("data-device", profile.device);
    html.setAttribute("data-orientation", profile.orientation);
    html.setAttribute("data-mobile-shell", profile.mobileShell ? "1" : "0");
    return profile;
  }

  function refresh(doc) {
    var next = readProfile();
    var changed =
      next.device !== PROFILE.device ||
      next.orientation !== PROFILE.orientation ||
      next.mobileShell !== PROFILE.mobileShell;
    PROFILE = next;
    applyProfile(doc, PROFILE);
    if (changed && typeof onChangeCb === "function") {
      onChangeCb(PROFILE);
    }
    return PROFILE;
  }

  function install(doc, opts) {
    opts = opts || {};
    doc = doc || (typeof document !== "undefined" ? document : null);
    onChangeCb = opts.onChange || null;
    PROFILE = refresh(doc);
    if (installed || !doc || !global.addEventListener) {
      return PROFILE;
    }
    installed = true;
    var onOrientation = function () {
      refresh(doc);
    };
    global.addEventListener("orientationchange", onOrientation);
    if (typeof screen !== "undefined" && screen.orientation && screen.orientation.addEventListener) {
      screen.orientation.addEventListener("change", onOrientation);
    }
    return PROFILE;
  }

  function getProfile() {
    return {
      device: PROFILE.device,
      orientation: PROFILE.orientation,
      mobileShell: PROFILE.mobileShell,
    };
  }

  function isMobileShell() {
    return PROFILE.mobileShell;
  }

  function isPhone() {
    return PROFILE.device === "phone";
  }

  function isTablet() {
    return PROFILE.device === "tablet";
  }

  // Apply as early as possible when loaded synchronously from index.html <head>.
  if (typeof document !== "undefined" && document.documentElement) {
    PROFILE = applyProfile(document, readProfile());
  }

  global.KorusUiDeviceProfile = {
    detectDevice: detectDevice,
    detectOrientation: detectOrientation,
    computeMobileShell: computeMobileShell,
    readProfile: readProfile,
    applyProfile: applyProfile,
    refresh: refresh,
    install: install,
    getProfile: getProfile,
    isMobileShell: isMobileShell,
    isPhone: isPhone,
    isTablet: isTablet,
  };
})(typeof window !== "undefined" ? window : globalThis);
