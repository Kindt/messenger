/**
 * Shared compact panel for in-chat extras: polls, kanban, whiteboard (one tab, one body).
 * Group chats only — polls, kanban, and whiteboard are hidden in direct (p2p) chats.
 */
(function (global) {
  "use strict";

  function selectedChat(ctx) {
    if (!ctx.state.selectedId) return null;
    return (ctx.state.chats || []).find(function (c) {
      return c.id === ctx.state.selectedId;
    });
  }

  function isGroupChat(ctx) {
    var chat = selectedChat(ctx);
    return !!(chat && chat.type === "group");
  }

  function mountTabChip(ctx, tabId, label, count) {
    var active = ctx.state.threadExtrasTab === tabId;
    var btn = ctx.el(
      "button",
      "thread-extras-tab" + (active ? " active" : "")
    );
    btn.type = "button";
    btn.setAttribute("data-testid", "thread-extras-tab-" + tabId);
    var text = label;
    if (count > 0) {
      text += " · " + count;
    }
    btn.textContent = text;
    btn.onclick = function () {
      ctx.toggleThreadExtrasTab(tabId);
    };
    return btn;
  }

  function mountThreadExtras(ctx) {
    if (!ctx.state.selectedId || ctx.state.selectedId === ctx.state.savedChatId) {
      return null;
    }
    var group = isGroupChat(ctx);
    if (!group) {
      return null;
    }
    var visible = ctx.isPlatformFeatureVisible || function () {
      return true;
    };
    var pollCount = (ctx.state.chatPolls || []).length;
    var shell = ctx.el("div", "thread-extras");
    shell.setAttribute("data-testid", "thread-extras");
    var tabs = ctx.el("div", "thread-extras-tabs");
    var tabCount = 0;
    if (visible("productivity.polls.list")) {
      tabs.appendChild(mountTabChip(ctx, "polls", ctx.L("ui.polls.title"), pollCount));
      tabCount += 1;
    }
    if (visible("collaboration.kanban.list")) {
      tabs.appendChild(mountTabChip(ctx, "kanban", ctx.L("ui.phase5.kanban"), 0));
      tabCount += 1;
    }
    if (visible("collaboration.whiteboard.open")) {
      tabs.appendChild(mountTabChip(ctx, "whiteboard", ctx.L("ui.phase5.whiteboard"), 0));
      tabCount += 1;
    }
    if (!tabCount) {
      return null;
    }
    shell.appendChild(tabs);

    var tab = ctx.state.threadExtrasTab;
    if (tab === "polls" && !visible("productivity.polls.list")) tab = null;
    if (tab === "kanban" && !visible("collaboration.kanban.list")) tab = null;
    if (tab === "whiteboard" && !visible("collaboration.whiteboard.open")) tab = null;
    if (tab === "kanban" || tab === "whiteboard") {
      if (!group) tab = pollCount > 0 && visible("productivity.polls.list") ? "polls" : null;
    }
    if (tab) {
      var body = ctx.el("div", "thread-extras-body");
      body.setAttribute("data-testid", "thread-extras-body-" + tab);
      var panel = null;
      if (tab === "polls" && global.KorusUiPolls) {
        panel = global.KorusUiPolls.mountPollsBody(ctx);
      } else if (tab === "kanban" && group && global.KorusUiPhase5Ext) {
        panel = global.KorusUiPhase5Ext.mountKanbanBody(ctx);
      } else if (tab === "whiteboard" && group && global.KorusUiPhase5Ext) {
        panel = global.KorusUiPhase5Ext.mountWhiteboardBody(ctx);
      }
      if (panel) {
        body.appendChild(panel);
        shell.appendChild(body);
      }
    }
    return shell;
  }

  global.KorusUiThreadExtras = {
    mountThreadExtras: mountThreadExtras,
  };
})(typeof window !== "undefined" ? window : globalThis);
