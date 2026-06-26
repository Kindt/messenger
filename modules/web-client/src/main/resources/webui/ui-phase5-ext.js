/**
 * Spec 022 Phase 5 extended UI: stickers, kanban, whiteboard, AI, passkeys (ADR scaffolds).
 */
(function (global) {
  "use strict";

  function modalCardHead(ctx, title, closeBtn) {
    var head = ctx.el("div", "settings-head");
    head.appendChild(ctx.el("h2", "settings-title", title));
    head.appendChild(closeBtn);
    return head;
  }

  var KANBAN_COLS = ["todo", "doing", "done"];

  function mountThreadTools(ctx) {
    if (!ctx.state.selectedId || ctx.state.selectedId === ctx.state.savedChatId) return null;
    var visible = ctx.isPlatformFeatureVisible || function () {
      return true;
    };
    var bar = ctx.el("div", "thread-phase5-tools");
    bar.setAttribute("data-testid", "thread-phase5-tools");
    if (visible("productivity.stickers.use")) {
      bar.appendChild(
        ctx.iconBtn("🎨", ctx.L("ui.phase5.stickers"), {
          testId: "phase5-stickers-open",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.openStickersPanel();
          },
        })
      );
    }
    if (visible("ai.assist.request")) {
      bar.appendChild(
        ctx.iconBtn("✨", ctx.L("ui.phase5.aiAssist"), {
          testId: "phase5-ai-assist-open",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.toggleAiAssistPanel();
          },
        })
      );
    }
    if (!bar.firstChild) return null;
    return bar;
  }

  function appendKanbanMoveBtns(ctx, taskEl, task) {
    var taskId = task.task_id || task.id;
    if (!taskId) return;
    var col = task.column_key || "todo";
    var idx = KANBAN_COLS.indexOf(col);
    if (idx > 0) {
      var prev = KANBAN_COLS[idx - 1];
      var backBtn = ctx.el("button", "kanban-move-btn");
      backBtn.type = "button";
      backBtn.setAttribute("data-testid", "kanban-move-" + taskId + "-" + prev);
      backBtn.textContent = "←";
      backBtn.title = ctx.L("ui.phase5.kanbanMoveBack");
      backBtn.onclick = function () {
        ctx.moveKanbanTask(taskId, prev);
      };
      taskEl.appendChild(backBtn);
    }
    if (idx >= 0 && idx < KANBAN_COLS.length - 1) {
      var next = KANBAN_COLS[idx + 1];
      var fwdBtn = ctx.el("button", "kanban-move-btn");
      fwdBtn.type = "button";
      fwdBtn.setAttribute("data-testid", "kanban-move-" + taskId + "-" + next);
      fwdBtn.textContent = "→";
      fwdBtn.title = ctx.L("ui.phase5.kanbanMoveForward");
      fwdBtn.onclick = function () {
        ctx.moveKanbanTask(taskId, next);
      };
      taskEl.appendChild(fwdBtn);
    }
    var delBtn = ctx.el("button", "kanban-move-btn kanban-del-btn");
    delBtn.type = "button";
    delBtn.setAttribute("data-testid", "kanban-delete-" + taskId);
    delBtn.textContent = "×";
    delBtn.title = ctx.L("ui.phase5.kanbanDelete");
    delBtn.onclick = function () {
      ctx.deleteKanbanTask(taskId);
    };
    taskEl.appendChild(delBtn);
  }

  function mountKanbanBody(ctx) {
    if (!ctx.state.selectedId) return null;
    var wrap = ctx.el("div", "thread-extras-kanban");
    wrap.setAttribute("data-testid", "thread-kanban");
    var labels = {
      todo: ctx.L("ui.phase5.colTodo"),
      doing: ctx.L("ui.phase5.colDoing"),
      done: ctx.L("ui.phase5.colDone"),
    };
    var grid = ctx.el("div", "kanban-grid");
    KANBAN_COLS.forEach(function (col) {
      var colEl = ctx.el("div", "kanban-col");
      colEl.setAttribute("data-column", col);
      colEl.appendChild(ctx.el("div", "kanban-col-head", labels[col] || col));
      (ctx.state.chatKanbanTasks || [])
        .filter(function (t) {
          return (t.column_key || "todo") === col;
        })
        .forEach(function (t) {
          var taskEl = ctx.el("div", "kanban-task");
          taskEl.appendChild(ctx.el("span", "kanban-task-title", t.title || ""));
          appendKanbanMoveBtns(ctx, taskEl, t);
          colEl.appendChild(taskEl);
        });
      grid.appendChild(colEl);
    });
    wrap.appendChild(grid);
    var addRow = ctx.el("div", "kanban-add-row");
    var inp = document.createElement("input");
    inp.type = "text";
    inp.className = "kanban-add-input";
    inp.setAttribute("data-testid", "kanban-task-input");
    inp.placeholder = ctx.L("ui.phase5.kanbanNewTask");
    var addBtn = ctx.iconBtn("+", ctx.L("ui.phase5.kanbanAdd"), {
      testId: "kanban-task-add",
      primary: true,
      disabled: ctx.state.busy,
      onClick: function () {
        var title = (inp.value || "").trim();
        if (!title) return;
        ctx.addKanbanTask(title);
        inp.value = "";
      },
    });
    addRow.appendChild(inp);
    addRow.appendChild(addBtn);
    wrap.appendChild(addRow);
    return wrap;
  }

  function mountWhiteboardBody(ctx) {
    if (!ctx.state.selectedId) return null;
    var wrap = ctx.el("div", "thread-extras-whiteboard");
    wrap.setAttribute("data-testid", "thread-whiteboard");
    var editorHost = ctx.el("div", "whiteboard-editor-host");
    var initial =
      (ctx.state.chatWhiteboard && ctx.state.chatWhiteboard.snapshot_json) ||
      '{"version":1,"strokes":[]}';
    var editor = null;
    if (global.KorusUiWhiteboardCanvas && global.KorusUiWhiteboardCanvas.mount) {
      editor = global.KorusUiWhiteboardCanvas.mount(ctx, editorHost, initial);
    } else {
      var ta = document.createElement("textarea");
      ta.className = "whiteboard-editor";
      ta.setAttribute("data-testid", "whiteboard-editor");
      ta.rows = 6;
      ta.placeholder = ctx.L("ui.phase5.whiteboardPlaceholder");
      ta.value = initial;
      editorHost.appendChild(ta);
      editor = {
        getSnapshotJson: function () {
          return ta.value;
        },
      };
    }
    var saveBtn = ctx.iconBtn("💾", ctx.L("ui.phase5.whiteboardSave"), {
      testId: "whiteboard-save",
      primary: true,
      disabled: ctx.state.busy,
      onClick: function () {
        ctx.saveWhiteboard(editor.getSnapshotJson());
      },
    });
    wrap.appendChild(editorHost);
    wrap.appendChild(saveBtn);
    return wrap;
  }

  function mountStickersOverlay(ctx) {
    if (!ctx.state.phase5StickersOpen) return null;
    var ov = ctx.el("div", "phase5-overlay");
    ov.setAttribute("data-testid", "stickers-overlay");
    var card = ctx.el("div", "phase5-overlay-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.close"), {
      testId: "stickers-overlay-close",
      onClick: function () {
        ctx.closeStickersPanel();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.phase5.stickersTitle"), closeBtn));
    var content = ctx.el("div", "settings-content phase5-overlay-content");
    content.appendChild(ctx.el("h4", "phase5-overlay-sub", ctx.L("ui.phase5.stickerPacks")));
    var packGrid = ctx.el("div", "stickers-pack-grid");
    (ctx.state.stickerPacks || []).forEach(function (p, idx) {
      var btn = ctx.el("button", "stickers-pack-item");
      btn.type = "button";
      btn.setAttribute("data-testid", "sticker-pack-" + idx);
      var preview = p.preview_url || p.cover_url || p.icon_url;
      if (preview) {
        var img = document.createElement("img");
        img.className = "stickers-pack-preview";
        img.src = preview;
        img.alt = p.name || p.pack_id || "";
        btn.appendChild(img);
      }
      btn.appendChild(ctx.el("span", "stickers-pack-label", p.name || p.pack_id || "?"));
      btn.onclick = function () {
        ctx.insertStickerMessage(p);
      };
      packGrid.appendChild(btn);
    });
    if (!(ctx.state.stickerPacks || []).length) {
      packGrid.appendChild(ctx.el("p", "phase5-hint", ctx.L("ui.phase5.stickersEmpty")));
    }
    content.appendChild(packGrid);
    var packCreateRow = ctx.el("div", "stickers-pack-create");
    var packInp = document.createElement("input");
    packInp.type = "text";
    packInp.className = "stickers-pack-create-input";
    packInp.setAttribute("data-testid", "sticker-pack-create-input");
    packInp.placeholder = ctx.L("ui.phase5.stickerPackName");
    var packCreateBtn = ctx.iconBtn("+", ctx.L("ui.phase5.stickerPackCreate"), {
      testId: "sticker-pack-create",
      disabled: ctx.state.busy,
      onClick: function () {
        ctx.createStickerPack((packInp.value || "").trim());
      },
    });
    packCreateRow.appendChild(packInp);
    packCreateRow.appendChild(packCreateBtn);
    content.appendChild(packCreateRow);
    var grid = ctx.el("div", "stickers-gif-grid");
    (ctx.state.stickerGifs || []).forEach(function (g, idx) {
      var btn = ctx.el("button", "stickers-gif-item");
      btn.type = "button";
      btn.setAttribute("data-testid", "gif-item-" + idx);
      var gifUrl = g.preview_url || g.gif_url || g.thumbnail_url;
      if (gifUrl) {
        var img = document.createElement("img");
        img.className = "stickers-gif-preview";
        img.src = gifUrl;
        img.alt = g.query_key || "";
        btn.appendChild(img);
      } else {
        btn.textContent = g.query_key || g.gif_url || "?";
      }
      btn.onclick = function () {
        ctx.insertGifMessage(g);
      };
      grid.appendChild(btn);
    });
    if (!(ctx.state.stickerGifs || []).length) {
      grid.appendChild(ctx.el("p", "phase5-hint", ctx.L("ui.phase5.stickersEmpty")));
    }
    content.appendChild(grid);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeStickersPanel();
    };
    return ov;
  }

  function mountAiAssistOverlay(ctx) {
    if (!ctx.state.phase5AiOpen) return null;
    var ov = ctx.el("div", "phase5-overlay");
    ov.setAttribute("data-testid", "ai-assist-overlay");
    var card = ctx.el("div", "phase5-overlay-card ai-assist-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.close"), {
      testId: "ai-assist-close",
      onClick: function () {
        ctx.closeAiAssistPanel();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.phase5.aiAssistTitle"), closeBtn));
    var content = ctx.el("div", "settings-content phase5-overlay-content");
    var inp = document.createElement("textarea");
    inp.className = "ai-assist-input";
    inp.setAttribute("data-testid", "ai-assist-input");
    inp.rows = 3;
    inp.placeholder = ctx.L("ui.phase5.aiAssistPlaceholder");
    content.appendChild(inp);
    var foot = ctx.el("div", "settings-foot phase5-modal-actions");
    foot.appendChild(
      ctx.iconBtn("▶", ctx.L("ui.phase5.aiAssistRun"), {
        testId: "ai-assist-run",
        primary: true,
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.runAiAssist((inp.value || "").trim());
        },
      })
    );
    if (ctx.state.phase5AiReply) {
      content.appendChild(ctx.el("pre", "ai-assist-reply", ctx.state.phase5AiReply));
      foot.appendChild(
        ctx.iconBtn("↩", ctx.L("ui.phase5.aiInsert"), {
          testId: "ai-assist-insert",
          disabled: ctx.state.busy,
          onClick: function () {
            ctx.insertAiReplyToComposer();
          },
        })
      );
    }
    content.appendChild(foot);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeAiAssistPanel();
    };
    return ov;
  }

  function federationTrustLevelLabel(ctx, level) {
    if (level == null || level === "") return "";
    var norm = String(level).toLowerCase().replace(/-/g, "_");
    var aliases = { active: "trusted" };
    var id = aliases[norm] || norm;
    var key = "ui.federation.trustLevel." + id;
    var out = ctx.L(key);
    if (!out || out === key) {
      return ctx.L("ui.federation.trustLevel.unknown", { value: String(level) });
    }
    return out;
  }

  function mountFederationDirectory(ctx, panel) {
    if (!panel) return;
    var wrap = ctx.el("div", "federation-settings-panel");
    wrap.setAttribute("data-testid", "federation-settings-panel");
    wrap.appendChild(ctx.el("h3", "settings-subtitle", ctx.L("ui.phase5.federationTitle")));
    var head = ctx.el("div", "federation-settings-head");
    if ((ctx.state.federationDirectory || []).length) {
      head.appendChild(
        ctx.el("span", "federation-trust-badge", ctx.L("ui.federation.trustActive"))
      );
    }
    wrap.appendChild(head);
    wrap.appendChild(ctx.el("p", "settings-hint", ctx.L("ui.federation.directoryHint")));
    var list = ctx.el("div", "federation-directory-list");
    list.setAttribute("data-testid", "federation-directory-list");
    (ctx.state.federationDirectory || []).forEach(function (p) {
      var row = ctx.el("div", "federation-directory-row");
      var label = (p.name || p.org_id) + (p.slug ? " (" + p.slug + ")" : "");
      if (p.trust_level) {
        label += " · " + federationTrustLevelLabel(ctx, p.trust_level);
      }
      row.textContent = label;
      list.appendChild(row);
    });
    if (!(ctx.state.federationDirectory || []).length) {
      list.appendChild(ctx.el("p", "settings-hint", ctx.L("ui.phase5.federationEmpty")));
    }
    wrap.appendChild(list);
    panel.appendChild(wrap);
  }

  function mountPasskeysSection(ctx, panel) {
    if (!panel) return;
    panel.appendChild(ctx.el("h3", "settings-subtitle", ctx.L("ui.phase5.passkeysTitle")));
    var list = ctx.el("div", "passkeys-list");
    list.setAttribute("data-testid", "passkeys-list");
    (ctx.state.myPasskeys || []).forEach(function (pk) {
      var row = ctx.el("div", "passkeys-row settings-value-mono");
      row.textContent = (pk.credential_id || pk.id || "").slice(0, 24);
      list.appendChild(row);
    });
    if (!(ctx.state.myPasskeys || []).length) {
      list.appendChild(ctx.el("p", "settings-hint", ctx.L("ui.phase5.passkeysEmpty")));
    }
    panel.appendChild(list);
    var row = ctx.el("div", "settings-row");
    row.appendChild(ctx.el("span", null, ctx.L("ui.phase5.passkeysRegister")));
    row.appendChild(
      ctx.iconBtn("＋", ctx.L("ui.phase5.passkeysRegister"), {
        testId: "passkey-register-scaffold",
        disabled: ctx.state.busy || ctx.state.passkeysBusy,
        onClick: function () {
          ctx.registerPasskeyScaffold();
        },
      })
    );
    panel.appendChild(row);
  }

  function mountSipGatewaySection(ctx, panel) {
    if (!panel) return;
    panel.appendChild(ctx.el("h3", "settings-subtitle", ctx.L("ui.phase5.sipTitle")));
    var enableRow = ctx.el("div", "settings-row sip-gateway-row");
    var enabled = ctx.el("input");
    enabled.type = "checkbox";
    enabled.id = "sip-enabled";
    enabled.setAttribute("data-testid", "sip-enabled");
    enabled.checked = !!(ctx.state.sipGateway && ctx.state.sipGateway.enabled);
    enableRow.appendChild(enabled);
    var enableLabel = ctx.el("label", "sip-label");
    enableLabel.setAttribute("for", "sip-enabled");
    enableLabel.textContent = ctx.L("ui.phase5.sipEnabled");
    enableRow.appendChild(enableLabel);
    panel.appendChild(enableRow);
    var uriBlock = ctx.el("div", "settings-row settings-row-stack");
    uriBlock.appendChild(ctx.el("span", "settings-row-label", ctx.L("ui.phase5.sipUri")));
    var uriWrap = ctx.el("div", "settings-inline-controls");
    var uriInp = document.createElement("input");
    uriInp.type = "text";
    uriInp.className = "settings-text-input";
    uriInp.id = "sip-uri-input";
    uriInp.setAttribute("data-testid", "sip-uri-input");
    uriInp.placeholder = "sip:gw.example.local";
    uriInp.value = (ctx.state.sipGateway && ctx.state.sipGateway.gateway_uri) || "";
    uriWrap.appendChild(uriInp);
    uriWrap.appendChild(
      ctx.iconBtn("💾", ctx.L("ui.phase5.sipSave"), {
        testId: "sip-save",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.saveSipGateway(enabled.checked, uriInp.value);
        },
      })
    );
    uriBlock.appendChild(uriWrap);
    panel.appendChild(uriBlock);
  }

  global.KorusUiPhase5Ext = {
    mountThreadTools: mountThreadTools,
    mountKanbanBody: mountKanbanBody,
    mountWhiteboardBody: mountWhiteboardBody,
    mountKanbanSection: mountKanbanBody,
    mountWhiteboardSection: mountWhiteboardBody,
    mountStickersOverlay: mountStickersOverlay,
    mountAiAssistOverlay: mountAiAssistOverlay,
    mountFederationDirectory: mountFederationDirectory,
    mountPasskeysSection: mountPasskeysSection,
    mountSipGatewaySection: mountSipGatewaySection,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
