(function (global) {
  "use strict";

  /** Emoji → SVG symbol id (see icon-set-policy.md). Unmapped icons stay emoji. */
  var EMOJI_TO_ICON = {
    "\u2715": "close",
    "\u2716": "close",
    "\uD83D\uDCBE": "save",
    "\uD83D\uDDD1": "delete",
    "\u21BB": "refresh",
    "\u2935": "download",
    "\uD83D\uDEAB": "revoke",
    "\uFF0B": "add",
    "\u2795": "add",
    "\u2197": "external",
    "\uD83D\uDCCB": "copy",
    "\uD83D\uDD14": "bell-on",
    "\uD83D\uDD15": "bell-off",
    "\uD83D\uDD0A": "sound-on",
    "\uD83D\uDD07": "sound-off",
    "\uD83C\uDF19": "moon",
    "\u2600\uFE0F": "sun",
    "\u2600": "sun",
    "\uD83C\uDFA4": "mic-on",
    "\uD83D\uDD07": "mic-off",
    "\uD83D\uDCF7": "camera",
    "\uD83D\uDDA5": "screen",
    "\uD83D\uDCF2": "pwa",
    "\uD83D\uDEAA": "logout",
    "\u2699": "settings",
    "\uD83D\uDCE1": "mesh",
    "\uD83C\uDFA5": "video",
    "\u2601": "cloud",
    "\u25B6": "play",
    "\u23F9": "stop",
    "\uD83D\uDD17": "link",
    "\uD83C\uDFAC": "film",
    "\uD83D\uDCF5": "call-off",
    "\uD83D\uDCF9": "video",
  };

  var spriteReady = false;

  var SPRITE_HTML =
    '<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false" style="position:absolute;width:0;height:0;overflow:hidden">' +
    '<symbol id="korus-icon-close" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M6 6l12 12M18 6L6 18"/></symbol>' +
    '<symbol id="korus-icon-save" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M4 4h12l4 4v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"/><path fill="none" stroke="currentColor" stroke-width="2" d="M8 4v6h8V4"/><rect fill="none" stroke="currentColor" stroke-width="2" x="8" y="14" width="8" height="6" rx="1"/></symbol>' +
    '<symbol id="korus-icon-delete" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 7h16M9 7V5h6v2M10 11v6M14 11v6"/><path fill="none" stroke="currentColor" stroke-width="2" d="M6 7l1 12h10l1-12"/></symbol>' +
    '<symbol id="korus-icon-refresh" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 12a8 8 0 0 1 13.5-5.7L20 8"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M20 4v4h-4"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M20 12a8 8 0 0 1-13.5 5.7L4 16"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 20v-4h4"/></symbol>' +
    '<symbol id="korus-icon-download" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M12 4v10"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M8 11l4 4 4-4"/><path fill="none" stroke="currentColor" stroke-width="2" d="M4 20h16"/></symbol>' +
    '<symbol id="korus-icon-revoke" viewBox="0 0 24 24"><circle fill="none" stroke="currentColor" stroke-width="2" cx="12" cy="12" r="9"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M5 5l14 14"/></symbol>' +
    '<symbol id="korus-icon-add" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M12 5v14M5 12h14"/></symbol>' +
    '<symbol id="korus-icon-external" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M14 5h5v5"/><path fill="none" stroke="currentColor" stroke-width="2" d="M10 14L19 5"/><path fill="none" stroke="currentColor" stroke-width="2" d="M19 14v5H5V5h5"/></symbol>' +
    '<symbol id="korus-icon-copy" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="8" y="8" width="12" height="12" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M4 16V6a2 2 0 0 1 2-2h10"/></symbol>' +
    '<symbol id="korus-icon-bell-on" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M6 17h12l-1.5-2V11a5.5 5.5 0 0 0-11 0v4L6 17z"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M10 20a2 2 0 0 0 4 0"/></symbol>' +
    '<symbol id="korus-icon-bell-off" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M6 17h12l-1.5-2V11a5.5 5.5 0 0 0-2.8-4.8"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M10 20a2 2 0 0 0 4 0"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 4l16 16"/></symbol>' +
    '<symbol id="korus-icon-sound-on" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M5 10v4h4l5 4V6L9 10H5z"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M16 9a4 4 0 0 1 0 6"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M18 7a7 7 0 0 1 0 10"/></symbol>' +
    '<symbol id="korus-icon-sound-off" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M5 10v4h4l5 4V6L9 10H5z"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M16 9l5 6M21 9l-5 6"/></symbol>' +
    '<symbol id="korus-icon-moon" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M20 14.5A8.5 8.5 0 0 1 9.5 4 7 7 0 1 0 20 14.5z"/></symbol>' +
    '<symbol id="korus-icon-sun" viewBox="0 0 24 24"><circle fill="none" stroke="currentColor" stroke-width="2" cx="12" cy="12" r="4"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></symbol>' +
    '<symbol id="korus-icon-mic-on" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="9" y="4" width="6" height="10" rx="3"/><path fill="none" stroke="currentColor" stroke-width="2" d="M5 11a7 7 0 0 0 14 0"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M12 18v3"/></symbol>' +
    '<symbol id="korus-icon-mic-off" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 4l16 16"/><rect fill="none" stroke="currentColor" stroke-width="2" x="9" y="4" width="6" height="10" rx="3"/></symbol>' +
    '<symbol id="korus-icon-camera" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M4 8h4l2-2h4l2 2h4v10H4V8z"/><circle fill="none" stroke="currentColor" stroke-width="2" cx="12" cy="13" r="3"/></symbol>' +
    '<symbol id="korus-icon-screen" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="3" y="5" width="18" height="12" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M8 21h8"/></symbol>' +
    '<symbol id="korus-icon-pwa" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="7" y="3" width="10" height="18" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M11 18h2"/></symbol>' +
    '<symbol id="korus-icon-logout" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M14 12H8"/><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M18 9l3 3-3 3"/></symbol>' +
    '<symbol id="korus-icon-settings" viewBox="0 0 24 24"><circle fill="none" stroke="currentColor" stroke-width="2" cx="12" cy="12" r="3"/><path fill="none" stroke="currentColor" stroke-width="2" d="M12 2v2M12 20v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M2 12h2M20 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4"/></symbol>' +
    '<symbol id="korus-icon-mesh" viewBox="0 0 24 24"><circle fill="none" stroke="currentColor" stroke-width="2" cx="12" cy="12" r="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M12 2v3M12 19v3M2 12h3M19 12h3"/><path fill="none" stroke="currentColor" stroke-width="2" d="M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></symbol>' +
    '<symbol id="korus-icon-video" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="3" y="7" width="12" height="10" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M15 10l6-3v10l-6-3"/></symbol>' +
    '<symbol id="korus-icon-cloud" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M7 18h11a4 4 0 0 0 .5-8 5.5 5.5 0 0 0-10.6 1.8A3.5 3.5 0 0 0 7 18z"/></symbol>' +
    '<symbol id="korus-icon-play" viewBox="0 0 24 24"><path fill="currentColor" d="M8 5v14l11-7z"/></symbol>' +
    '<symbol id="korus-icon-stop" viewBox="0 0 24 24"><rect fill="currentColor" x="6" y="6" width="12" height="12" rx="1"/></symbol>' +
    '<symbol id="korus-icon-link" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" d="M10 14a4 4 0 0 1 0-5.7l1.3-1.3a4 4 0 0 1 5.7 5.7l-1.3 1.3"/><path fill="none" stroke="currentColor" stroke-width="2" d="M14 10a4 4 0 0 1 0 5.7l-1.3 1.3a4 4 0 0 1-5.7-5.7l1.3-1.3"/></symbol>' +
    '<symbol id="korus-icon-film" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="3" y="5" width="18" height="14" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M7 5v14M17 5v14M3 10h4M3 14h4M17 10h4M17 14h4"/></symbol>' +
    '<symbol id="korus-icon-call-on" viewBox="0 0 24 24"><rect fill="none" stroke="currentColor" stroke-width="2" x="3" y="7" width="12" height="10" rx="2"/><path fill="none" stroke="currentColor" stroke-width="2" d="M15 10l6-3v10l-6-3"/></symbol>' +
    '<symbol id="korus-icon-call-off" viewBox="0 0 24 24"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" d="M4 4l16 16"/><rect fill="none" stroke="currentColor" stroke-width="2" x="3" y="7" width="12" height="10" rx="2"/></symbol>' +
    "</svg>";

  function ensureSprite() {
    if (spriteReady) return true;
    if (typeof document === "undefined") return false;
    if (document.getElementById("korus-ui-icons-sprite")) {
      spriteReady = true;
      return true;
    }
    var wrap = document.createElement("div");
    wrap.id = "korus-ui-icons-sprite";
    wrap.innerHTML = SPRITE_HTML;
    document.body.appendChild(wrap);
    spriteReady = true;
    return true;
  }

  function resolveIconId(icon, iconId) {
    if (iconId) return String(iconId);
    if (icon == null) return null;
    var key = String(icon);
    if (EMOJI_TO_ICON[key]) return EMOJI_TO_ICON[key];
    if (key.indexOf("icon-") === 0) return key.slice(5);
    return null;
  }

  function appendIcon(btn, icon, iconId) {
    var id = resolveIconId(icon, iconId);
    if (id && ensureSprite()) {
      btn.classList.add("btn-icon-svg");
      var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.setAttribute("class", "ui-icon");
      svg.setAttribute("aria-hidden", "true");
      svg.setAttribute("focusable", "false");
      var use = document.createElementNS("http://www.w3.org/2000/svg", "use");
      use.setAttribute("href", "#korus-icon-" + id);
      svg.appendChild(use);
      btn.appendChild(svg);
      return;
    }
    btn.textContent = icon != null ? String(icon) : "?";
  }

  /** Compact icon buttons with localized tooltips (title + aria-label). */
  function iconButton(opts) {
    opts = opts || {};
    var parts = ["btn", "btn-icon"];
    if (opts.primary) parts.push("btn-primary");
    else parts.push("btn-ghost");
    if (opts.size === "md") {
      /* default icon size */
    } else if (opts.size !== "none") {
      parts.push("btn-sm");
    }
    if (opts.block) parts.push("btn-block");
    if (opts.cls) parts.push(opts.cls);

    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = parts.join(" ");
    appendIcon(btn, opts.icon, opts.iconId);
    if (opts.tip) {
      btn.title = opts.tip;
      btn.setAttribute("aria-label", opts.tip);
    }
    if (opts.testId) btn.setAttribute("data-testid", opts.testId);
    if (opts.disabled) btn.disabled = true;
    if (opts.submit) btn.type = "submit";
    if (opts.onClick) btn.onclick = opts.onClick;
    return btn;
  }

  global.KorusIconButtons = {
    iconButton: iconButton,
    ensureSprite: ensureSprite,
    emojiToIconId: function (emoji) {
      return resolveIconId(emoji, null);
    },
  };
})(typeof window !== "undefined" ? window : globalThis);
