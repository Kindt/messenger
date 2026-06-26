(function (global) {
  "use strict";

  var GRADIENT_PAIRS = [
    ["#7949F4", "#7c5cff"],
    ["#6366f1", "#8b5cf6"],
    ["#0ea5e9", "#6366f1"],
    ["#10b981", "#0ea5e9"],
    ["#f59e0b", "#ef4444"],
    ["#ec4899", "#8b5cf6"],
    ["#14b8a6", "#3b82f6"],
    ["#a855f7", "#6366f1"],
  ];

  function hashUserId(userId) {
    var s = String(userId || "");
    var h = 0;
    for (var i = 0; i < s.length; i++) {
      h = ((h << 5) - h + s.charCodeAt(i)) | 0;
    }
    return Math.abs(h);
  }

  function gradientForUser(userId) {
    var pair = GRADIENT_PAIRS[hashUserId(userId) % GRADIENT_PAIRS.length];
    return "linear-gradient(135deg, " + pair[0] + ", " + pair[1] + ")";
  }

  function initialsFromTitle(title) {
    var s = (title || "?").trim();
    if (!s) return "?";
    var parts = s.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }
    if (s.length >= 2) return s.slice(0, 2).toUpperCase();
    return s.charAt(0).toUpperCase();
  }

  function sizeClass(size) {
    if (size === "sm") return "chat-avatar-sm";
    if (size === "lg") return "chat-avatar-lg";
    return "chat-avatar-md";
  }

  function renderAvatar(opts) {
    opts = opts || {};
    var url = opts.url || "";
    var title = opts.title || "?";
    var userId = opts.userId != null ? opts.userId : title;
    var size = opts.size || "md";
    var testId = opts.testId || null;
    var alt = opts.alt != null ? opts.alt : title;

    var wrap = document.createElement("div");
    wrap.className = "chat-avatar " + sizeClass(size);
    if (testId) wrap.setAttribute("data-testid", testId);

    function showInitials() {
      wrap.textContent = initialsFromTitle(title);
      wrap.style.background = gradientForUser(userId);
      var img = wrap.querySelector("img.chat-avatar-img");
      if (img) img.remove();
    }

    if (url) {
      var img = document.createElement("img");
      img.className = "chat-avatar-img";
      img.src = url;
      img.alt = alt;
      img.loading = "lazy";
      img.decoding = "async";
      img.onerror = function () {
        showInitials();
      };
      wrap.appendChild(img);
    } else {
      showInitials();
    }
    return wrap;
  }

  global.KorusUiAvatar = {
    renderAvatar: renderAvatar,
    initialsFromTitle: initialsFromTitle,
    gradientForUser: gradientForUser,
    hashUserId: hashUserId,
  };
})(typeof window !== "undefined" ? window : globalThis);
