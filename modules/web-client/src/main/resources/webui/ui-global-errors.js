/**
 * Global error hooks (spec 025 FR-119).
 */
(function (global) {
  "use strict";

  var installed = false;

  function installGlobalErrorHandlers(options) {
    options = options || {};
    if (installed) {
      return;
    }
    installed = true;
    var onReport = options.onReport || function () {};

    global.addEventListener("error", function (ev) {
      if (!ev) {
        return;
      }
      var msg = ev.message || "error";
      if (ev.error && ev.error.message) {
        msg = ev.error.message;
      }
      onReport("error", msg);
    });

    global.addEventListener("unhandledrejection", function (ev) {
      var reason = ev && ev.reason;
      var msg =
        reason && reason.message
          ? reason.message
          : typeof reason === "string"
            ? reason
            : "unhandled rejection";
      onReport("rejection", msg);
    });
  }

  global.KorusUiGlobalErrors = {
    installGlobalErrorHandlers: installGlobalErrorHandlers,
  };
})(typeof window !== "undefined" ? window : globalThis);
