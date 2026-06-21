/**
 * Spec 022 Phase 5 extended UI: stickers, kanban, whiteboard (ADR scaffolds).
 */
(function (global) {
  "use strict";

  function mountThreadTools(ctx) {
    if (!ctx.state.selectedId || ctx.state.selectedId === ctx.state.savedChatId) return null;
    var bar = ctx.el("div", "thread-phase5-tools");
    bar.setAttribute("data-testid", "thread-phase5-tools");
    bar.appendChild(
      ctx.iconBtn("🎨", ctx.L("ui.phase5.stickers"), {
        testId: "phase5-stickers-open",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.openStickersPanel();
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("📋", ctx.L("ui.phase5.kanban"), {
        testId: "phase5-kanban-toggle",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.toggleKanbanPanel();
        },
      })
    );
    bar.appendChild(
      ctx.iconBtn("🖊", ctx.L("ui.phase5.whiteboard"), {
        testId: "phase5-whiteboard-toggle",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.toggleWhiteboardPanel();
        },
      })
    );
    return bar;
  }

  function mountKanbanSection(ctx) {
    if (!ctx.state.phase5KanbanOpen || !ctx.state.selectedId) return null;
    var wrap = ctx.el("div", "thread-kanban");
    wrap.setAttribute("data-testid", "thread-kanban");
    wrap.appendChild(ctx.el("div", "thread-kanban-title", ctx.L("ui.phase5.kanbanTitle")));
    var cols = ["todo", "doing", "done"];
    var labels = {
      todo: ctx.L("ui.phase5.colTodo"),
      doing: ctx.L("ui.phase5.colDoing"),
      done: ctx.L("ui.phase5.colDone"),
    };
    var grid = ctx.el("div", "kanban-grid");
    cols.forEach(function (col) {
      var colEl = ctx.el("div", "kanban-col");
      colEl.setAttribute("data-column", col);
      colEl.appendChild(ctx.el("div", "kanban-col-head", labels[col] || col));
      (ctx.state.chatKanbanTasks || [])
        .filter(function (t) {
          return (t.column_key || "todo") === col;
        })
        .forEach(function (t) {
          colEl.appendChild(ctx.el("div", "kanban-task", t.title || ""));
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

  function mountWhiteboardSection(ctx) {
    if (!ctx.state.phase5WhiteboardOpen || !ctx.state.selectedId) return null;
    var wrap = ctx.el("div", "thread-whiteboard");
    wrap.setAttribute("data-testid", "thread-whiteboard");
    wrap.appendChild(ctx.el("div", "thread-whiteboard-title", ctx.L("ui.phase5.whiteboardTitle")));
    var ta = document.createElement("textarea");
    ta.className = "whiteboard-editor";
    ta.setAttribute("data-testid", "whiteboard-editor");
    ta.rows = 6;
    ta.placeholder = ctx.L("ui.phase5.whiteboardPlaceholder");
    ta.value =
      (ctx.state.chatWhiteboard && ctx.state.chatWhiteboard.snapshot_json) ||
      '{"version":1,"strokes":[]}';
    var saveBtn = ctx.iconBtn("💾", ctx.L("ui.phase5.whiteboardSave"), {
      testId: "whiteboard-save",
      primary: true,
      disabled: ctx.state.busy,
      onClick: function () {
        ctx.saveWhiteboard(ta.value);
      },
    });
    wrap.appendChild(ta);
    wrap.appendChild(saveBtn);
    return wrap;
  }

  function mountStickersOverlay(ctx) {
    if (!ctx.state.phase5StickersOpen) return null;
    var ov = ctx.el("div", "phase5-overlay");
    ov.setAttribute("data-testid", "stickers-overlay");
    var card = ctx.el("div", "phase5-overlay-card");
    card.appendChild(ctx.el("h3", "phase5-overlay-title", ctx.L("ui.phase5.stickersTitle")));
    var grid = ctx.el("div", "stickers-gif-grid");
    (ctx.state.stickerGifs || []).forEach(function (g, idx) {
      var btn = ctx.el("button", "stickers-gif-item");
      btn.type = "button";
      btn.setAttribute("data-testid", "gif-item-" + idx);
      btn.textContent = g.query_key || g.gif_url || "?";
      btn.onclick = function () {
        ctx.insertGifMessage(g);
      };
      grid.appendChild(btn);
    });
    if (!(ctx.state.stickerGifs || []).length) {
      grid.appendChild(ctx.el("p", "phase5-hint", ctx.L("ui.phase5.stickersEmpty")));
    }
    card.appendChild(grid);
    card.appendChild(
      ctx.iconBtn("✕", ctx.L("ui.common.close"), {
        testId: "stickers-overlay-close",
        onClick: function () {
          ctx.closeStickersPanel();
        },
      })
    );
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeStickersPanel();
    };
    return ov;
  }

  function mountFederationDirectory(ctx, panel) {
    if (!panel) return;
    panel.appendChild(ctx.el("h3", "settings-subtitle", ctx.L("ui.phase5.federationTitle")));
    var list = ctx.el("div", "federation-directory-list");
    list.setAttribute("data-testid", "federation-directory-list");
    (ctx.state.federationDirectory || []).forEach(function (p) {
      var row = ctx.el("div", "federation-directory-row");
      row.textContent = (p.name || p.org_id) + (p.slug ? " (" + p.slug + ")" : "");
      list.appendChild(row);
    });
    if (!(ctx.state.federationDirectory || []).length) {
      list.appendChild(ctx.el("p", "phase5-hint", ctx.L("ui.phase5.federationEmpty")));
    }
    panel.appendChild(list);
  }

  global.KorusUiPhase5Ext = {
    mountThreadTools: mountThreadTools,
    mountKanbanSection: mountKanbanSection,
    mountWhiteboardSection: mountWhiteboardSection,
    mountStickersOverlay: mountStickersOverlay,
    mountFederationDirectory: mountFederationDirectory,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
