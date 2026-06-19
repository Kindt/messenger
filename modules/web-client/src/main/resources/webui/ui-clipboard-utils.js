/**
 * Clipboard copy with graceful fallback when navigator.clipboard is unavailable.
 */
(function (global) {
  "use strict";

  function copyText(text, onSuccess, onFallback) {
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard
        .writeText(text)
        .then(function () {
          if (onSuccess) onSuccess();
        })
        .catch(function () {
          if (onFallback) onFallback(text);
        });
    } else if (onFallback) {
      onFallback(text);
    }
  }

  global.KorusUiClipboardUtils = {
    copyText: copyText,
  };
})(typeof window !== "undefined" ? window : globalThis);
