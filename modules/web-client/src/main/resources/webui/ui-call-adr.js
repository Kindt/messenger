/**
 * Conference ADR actions: recording, guest link, breakout, captions (spec 022).
 */
(function (global) {
  "use strict";

  function modalCardHead(ctx, title, closeBtn) {
    var head = ctx.el("div", "settings-head");
    head.appendChild(ctx.el("h2", "settings-title", title));
    head.appendChild(closeBtn);
    return head;
  }

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
            .then(function (data) {
              ctx.state.phase5ActiveRecordingId =
                (data && (data.recording_id || data.id)) || null;
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
      ctx.iconBtn("⏹", ctx.L("ui.phase5.recordStop"), {
        testId: "conf-record-stop",
        disabled: ctx.state.busy,
        onClick: function () {
          function completeId(recId) {
            if (!recId) {
              ctx.state.error = ctx.L("ui.phase5.recordListEmpty");
              ctx.render();
              return;
            }
            ctx.apiJson(confPath(conf, "/recordings/" + recId + "/complete"), { method: "POST" })
              .then(function () {
                ctx.state.phase5ActiveRecordingId = null;
                ctx.state.phase5Toast = ctx.L("ui.phase5.recordCompleted");
                ctx.render();
              })
              .catch(function (err) {
                ctx.state.error = err.message || ctx.L("ui.phase5.recordFailed");
                ctx.render();
              });
          }
          if (ctx.state.phase5ActiveRecordingId) {
            completeId(ctx.state.phase5ActiveRecordingId);
            return;
          }
          ctx.apiJson(confPath(conf, "/recordings"), { method: "GET" })
            .then(function (rows) {
              var pending = (rows || []).find(function (r) {
                return (r.status || "") === "recording" || (r.status || "") === "pending";
              });
              completeId(pending && (pending.recording_id || pending.id));
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
      ctx.iconBtn("👥", ctx.L("ui.phase5.guestAdmitBtn"), {
        testId: "conf-guest-admit",
        disabled: ctx.state.busy,
        onClick: function () {
          if (!conf || !conf.conference_id || !conf.chat_id) {
            showTokenModal(ctx, ctx.L("ui.phase5.guestAdmitBtn"), ctx.L("ui.phase5.guestAdmitHint"));
            return;
          }
          ctx.apiJson(
            "/chats/" + conf.chat_id + "/conferences/" + conf.conference_id + "/guest-links/waiting",
            { method: "GET" }
          )
            .then(function (rows) {
              var body =
                rows && rows.length
                  ? formatList(rows, function (r) {
                      return (r.link_id || "?") + " · " + (r.created_at || "");
                    })
                  : ctx.L("ui.phase5.guestWaitingEmpty");
              ctx.state.phase5Modal = {
                title: ctx.L("ui.phase5.guestAdmitBtn"),
                body: body,
                waitingLinks: rows || [],
                conference: conf,
              };
              ctx.render();
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
                      var room = r.livekit_room || r.room_id || "";
                      return (r.name || "?") + " · " + room + " — " + ctx.L("ui.phase5.breakoutJoin");
                    })
                  : ctx.L("ui.phase5.breakoutListEmpty");
              showTokenModal(ctx, ctx.L("ui.phase5.breakoutList"), body);
              if (rows && rows.length && rows[0].livekit_room && global.KorusUiClipboardUtils) {
                ctx.state.phase5ModalJoinRoom = rows[0].livekit_room;
              }
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
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.close"), {
      testId: "phase5-modal-close",
      onClick: function () {
        ctx.state.phase5Modal = null;
        ctx.state.phase5ModalJoinRoom = null;
        ctx.render();
      },
    });
    card.appendChild(
      modalCardHead(ctx, ctx.state.phase5Modal.title || "", closeBtn)
    );
    var content = ctx.el("div", "settings-content phase5-overlay-content");
    if (ctx.state.phase5Modal.mode === "edit") {
      var inp = document.createElement("textarea");
      inp.className = "phase5-modal-edit-input";
      inp.setAttribute("data-testid", "message-edit-input");
      inp.value = ctx.state.phase5Modal.body || "";
      inp.oninput = function () {
        ctx.state.phase5Modal.body = inp.value;
      };
      content.appendChild(inp);
    } else {
      content.appendChild(ctx.el("pre", "phase5-modal-body", ctx.state.phase5Modal.body || ""));
    }
    var actions = ctx.el("div", "phase5-modal-actions settings-foot");
    if (ctx.state.phase5Modal.mode === "edit" && ctx.saveEditedMessage) {
      actions.appendChild(
        ctx.iconBtn("✓", ctx.L("ui.edit.save"), {
          testId: "message-edit-save",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.saveEditedMessage(ctx.state.phase5Modal);
          },
        })
      );
    } else if (
      ctx.state.phase5Modal.waitingLinks &&
      ctx.state.phase5Modal.waitingLinks.length &&
      ctx.state.phase5Modal.conference
    ) {
      actions.appendChild(
        ctx.iconBtn("✓", ctx.L("ui.phase5.guestAdmitDo"), {
          testId: "guest-admit-first",
          disabled: ctx.state.busy,
          onClick: function () {
            var conf = ctx.state.phase5Modal.conference;
            var linkId = ctx.state.phase5Modal.waitingLinks[0].link_id;
            ctx.apiJson(
              "/chats/" +
                conf.chat_id +
                "/conferences/" +
                conf.conference_id +
                "/guest-links/" +
                linkId +
                "/admit",
              { method: "POST" }
            )
              .then(function () {
                ctx.state.phase5Modal = null;
                ctx.state.phase5Toast = ctx.L("ui.phase5.guestAdmitDone");
                ctx.render();
              })
              .catch(function (err) {
                ctx.state.error = err.message || ctx.L("ui.phase5.guestFailed");
                ctx.render();
              });
          },
        })
      );
    } else if (ctx.state.phase5Modal.body && global.KorusUiClipboardUtils) {
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
      if (ctx.state.phase5ModalJoinRoom) {
        actions.appendChild(
          ctx.iconBtn("🚪", ctx.L("ui.phase5.breakoutJoin"), {
            testId: "phase5-modal-breakout-join",
            onClick: function () {
              global.KorusUiClipboardUtils.copyText(ctx.state.phase5ModalJoinRoom, function () {
                ctx.state.phase5Toast = ctx.L("ui.federation.joinCopied");
                ctx.render();
              });
            },
          })
        );
      }
    }
    if (actions.childNodes.length) {
      content.appendChild(actions);
    }
    card.appendChild(content);
    ov.appendChild(card);
    return ov;
  }

  global.KorusUiCallAdr = {
    mountConfAdrBar: mountConfAdrBar,
    mountPhase5Modal: mountPhase5Modal,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
