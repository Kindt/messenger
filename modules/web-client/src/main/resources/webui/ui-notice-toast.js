/**
 * Floating notice toast: errors, success toasts — auto-dismiss, click-outside to close.
 */
(function (global) {
  "use strict";

  var AUTO_MS = 30000;
  var timer = null;
  var lastSig = null;

  function activeNotice(state) {
    if (state.error) return { text: state.error, kind: "error", sig: "e:" + state.error };
    if (state.phase5Toast) {
      return { text: state.phase5Toast, kind: "success", sig: "t:" + state.phase5Toast };
    }
    if (state.statusMessage) {
      return { text: state.statusMessage, kind: "success", sig: "s:" + state.statusMessage };
    }
    return null;
  }

  function clearNoticeState(state) {
    state.error = null;
    state.phase5Toast = null;
    state.statusMessage = null;
  }

  function dismiss(ctx) {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    lastSig = null;
    if (ctx && ctx.state) clearNoticeState(ctx.state);
    if (ctx && ctx.render) ctx.render();
  }

  function arm(notice, ctx) {
    if (!notice) {
      if (timer) clearTimeout(timer);
      timer = null;
      lastSig = null;
      return;
    }
    if (notice.sig === lastSig && timer) return;
    lastSig = notice.sig;
    if (timer) clearTimeout(timer);
    timer = setTimeout(function () {
      dismiss(ctx);
    }, AUTO_MS);
  }

  function reset() {
    if (timer) clearTimeout(timer);
    timer = null;
    lastSig = null;
  }

  function mount(host, ctx) {
    var notice = activeNotice(ctx.state);
    if (!notice || !host) return null;
    arm(notice, ctx);

    var backdrop = ctx.el("div", "notice-backdrop");
    backdrop.setAttribute("data-testid", "notice-backdrop");
    var card = ctx.el("div", "notice-float notice-float-" + notice.kind);
    card.setAttribute("role", "alert");
    card.setAttribute("data-testid", "notice-toast");
    card.textContent = notice.text;
    backdrop.appendChild(card);
    backdrop.addEventListener("click", function (e) {
      if (e.target === backdrop && ctx.dismissNotice) {
        ctx.dismissNotice();
      }
    });
    host.appendChild(backdrop);
    return backdrop;
  }

  global.KorusUiNoticeToast = {
    mount: mount,
    dismiss: dismiss,
    reset: reset,
    activeNotice: activeNotice,
  };
})(typeof window !== "undefined" ? window : globalThis);
