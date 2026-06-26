/**
 * Script order for app.bundle.js (T073 / FR-082).
 * Kept here because index.html loads the bundle instead of listing each module.
 */
export const DEFERRED_HEAVY_SCRIPTS = [
  "korus-mls-wasm.js",
  "e2ee/openmls/korus-openmls-dev.js",
];

/** Loaded on demand via import("/ui-lazy-call.mjs") in app.js (FR-121). */
export const DEFERRED_CALL_SCRIPTS = ["ui-call-mesh.js", "ui-call-livekit.js"];

/** @type {string[]} */
export const BUNDLE_SCRIPTS = [
  "ui-global-errors.js",
  "ui-i18n.js",
  "ui-shell-utils.js",
  "ui-transport-utils.js",
  "ui-ws-client.js",
  "ui-ws-events.js",
  "ui-ws-handler.js",
  "ui-format-utils.js",
  "ui-avatar.js",
  "ui-avatar-crop.js",
  "ui-profile-card.js",
  "ui-messages-utils.js",
  "ui-deep-link-utils.js",
  "ui-clipboard-utils.js",
  "ui-markdown-utils.js",
  "ui-ux-perception.js",
  "ui-notice-toast.js",
  "ui-file-attach.js",
  "ui-message-content.js",
  "ui-message-reply.js",
  "ui-message-article.js",
  "ui-message-list.js",
  "ui-composer.js",
  "ui-polls.js",
  "ui-phase5-ext.js",
  "ui-thread-extras.js",
  "ui-whiteboard-canvas.js",
  "ui-call-adr.js",
  "ui-rtc-utils.js",
  "ui-live-session.js",
  "ui-pwa-settings-utils.js",
  "ui-export-utils.js",
  "ui-icon-buttons.js",
  "ui-e2ee-mls.js",
  "ui-e2ee-utils.js",
  "ui-offline-cache.js",
  "app.js",
];
