/**
 * Conference ADR actions: recording, guest link, breakout, captions (spec 022).
 */
(function (global) {
  "use strict";

  function confPath(conf, suffix) {
    return "/chats/" + conf.chat_id + "/conferences/" + conf.conference_id + suffix;
  }

  function showTokenModal(ctx, title, body) {
    ctx.state.phase5Modal = { title: title, body: body || "" };
    ctx.render();
  }

  function formatList(rows, mapFn) {
    if (!rows || !rows.length) return "";
    return rows
      .map(function (r, idx) {
        return mapFn(r, idx);
      })
      .join("\n");
  }

  function mountConfAdrBar(ctx, conf) {
    if (!conf || !conf.conference_id || !conf.chat_id || !ctx.state.tokens) return null;
    var bar = ctx.el("div", "conf-adr-bar");
    bar.setAttribute("data-testid", "conf-adr-bar");
    bar.appendChild(ctx.el("span", "conf-adr-label", ctx.L("ui.phase5.confTools")));
    bar.appendChild(
      ctx.iconBtn("⏺", ctx.L("ui.phase5.recordStart"), {
        testId: "conf-record-start",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/recordings"), { method: "POST" })
            .then(function () {
              ctx.state.phase5Toast = ctx.L("ui.phase5.recordStarted");
              ctx.render();
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.recordFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("📼", ctx.L("ui.phase5.recordList"), {
        testId: "conf-record-list",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/recordings"), { method: "GET" })
            .then(function (rows) {
              var body =
                rows && rows.length
                  ? formatList(rows, function (r) {
                      return (r.recording_id || r.id || "?") + " · " + (r.status || "");
                    })
                  : ctx.L("ui.phase5.recordListEmpty");
              showTokenModal(ctx, ctx.L("ui.phase5.recordList"), body);
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.recordFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("🔗", ctx.L("ui.phase5.guestLink"), {
        testId: "conf-guest-link",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/guest-links"), {
            method: "POST",
            jsonBody: { waiting_room: true },
          })
            .then(function (data) {
              var token = data.guest_token || "";
              var body = token;
              if (data.waiting_room) {
                body = ctx.L("ui.phase5.guestWaiting") + "\n\n" + token;
              }
              showTokenModal(ctx, ctx.L("ui.phase5.guestLink"), body);
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.guestFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("🚪", ctx.L("ui.phase5.breakout"), {
        testId: "conf-breakout-create",
        disabled: ctx.state.busy,
        onClick: function () {
          var name = window.prompt(ctx.L("ui.phase5.breakoutName"), "Room 1");
          if (!name) return;
          ctx.apiJson(confPath(conf, "/breakout-rooms"), {
            method: "POST",
            jsonBody: { name: name },
          })
            .then(function () {
              ctx.state.phase5Toast = ctx.L("ui.phase5.breakoutCreated");
              ctx.render();
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.breakoutFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("📋", ctx.L("ui.phase5.breakoutList"), {
        testId: "conf-breakout-list",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/breakout-rooms"), { method: "GET" })
            .then(function (rows) {
              var body =
                rows && rows.length
                  ? formatList(rows, function (r) {
                      return (r.name || "?") + " · " + (r.livekit_room || "");
                    })
                  : ctx.L("ui.phase5.breakoutListEmpty");
              showTokenModal(ctx, ctx.L("ui.phase5.breakoutList"), body);
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.breakoutFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("💬", ctx.L("ui.phase5.captions"), {
        testId: "conf-captions-start",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/captions"), {
            method: "POST",
            jsonBody: { language: "ru", sample_text: ctx.L("ui.phase5.captionsSample") },
          })
            .then(function (data) {
              showTokenModal(ctx, ctx.L("ui.phase5.captions"), data.transcript_json || "");
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.captionsFailed");
              ctx.render();
            });
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("📝", ctx.L("ui.phase5.captionsLive"), {
        testId: "conf-captions-get",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.apiJson(confPath(conf, "/captions"), { method: "GET" })
            .then(function (data) {
              showTokenModal(
                ctx,
                ctx.L("ui.phase5.captionsLive"),
                (data && data.transcript_json) || ctx.L("ui.phase5.captionsSample")
              );
            })
            .catch(function (err) {
              ctx.state.error = err.message || ctx.L("ui.phase5.captionsFailed");
              ctx.render();
            });
        },
      })
    );
    return bar;
  }

  function mountPhase5Modal(ctx) {
    if (!ctx.state.phase5Modal) return null;
    var ov = ctx.el("div", "phase5-overlay");
    ov.setAttribute("data-testid", "phase5-info-modal");
    var card = ctx.el("div", "phase5-overlay-card");
    card.appendChild(ctx.el("h3", "phase5-overlay-title", ctx.state.phase5Modal.title || ""));
    card.appendChild(ctx.el("pre", "phase5-modal-body", ctx.state.phase5Modal.body || ""));
    var actions = ctx.el("div", "phase5-modal-actions");
    if (ctx.state.phase5Modal.body && global.KorusUiClipboardUtils) {
      actions.appendChild(
        ctx.iconBtn("📋", ctx.L("ui.phase5.modalCopy"), {
          testId: "phase5-modal-copy",
          onClick: function () {
            global.KorusUiClipboardUtils.copyText(ctx.state.phase5Modal.body, function () {
              ctx.state.phase5Toast = ctx.L("ui.phase5.guestCopied");
              ctx.render();
            });
          },
        })
      );
    }
    actions.appendChild(
      ctx.iconBtn("✕", ctx.L("ui.common.close"), {
        testId: "phase5-modal-close",
        onClick: function () {
          ctx.state.phase5Modal = null;
          ctx.render();
        },
      })
    );
    card.appendChild(actions);
    ov.appendChild(card);
    return ov;
  }

  global.KorusUiCallAdr = {
    mountConfAdrBar: mountConfAdrBar,
    mountPhase5Modal: mountPhase5Modal,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
