(function (global) {
  "use strict";

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
    btn.textContent = opts.icon != null ? String(opts.icon) : "?";
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

  global.KorusIconButtons = { iconButton: iconButton };
})(typeof window !== "undefined" ? window : globalThis);
