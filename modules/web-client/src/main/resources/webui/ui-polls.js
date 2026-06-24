/**
 * In-chat polls UI + scheduled send modal (spec 022 Phase 5).
 */
(function (global) {
  "use strict";

  function modalCardHead(ctx, title, closeBtn) {
    var head = ctx.el("div", "settings-head");
    head.appendChild(ctx.el("h2", "settings-title", title));
    head.appendChild(closeBtn);
    return head;
  }

  function totalVotes(counts) {
    if (!counts || !counts.length) return 0;
    return counts.reduce(function (a, b) {
      return a + (b || 0);
    }, 0);
  }

  function mountPollCard(poll, ctx) {
    var card = ctx.el("div", "poll-card");
    card.setAttribute("data-testid", "poll-card");
    card.setAttribute("data-poll-id", poll.id);
    var head = ctx.el("div", "poll-card-head");
    head.appendChild(ctx.el("div", "poll-question", poll.question || ""));
    if (poll.closed) {
      head.appendChild(ctx.el("span", "poll-closed-badge", ctx.L("ui.polls.closed")));
    }
    card.appendChild(head);
    var counts = poll.vote_counts || [];
    var total = totalVotes(counts);
    var selected = {};
    var optsWrap = ctx.el("div", "poll-options");
    (poll.options || []).forEach(function (opt, idx) {
      var row = ctx.el("label", "poll-option");
      var input = document.createElement("input");
      input.type = poll.allow_multiple ? "checkbox" : "radio";
      input.name = "poll-" + poll.id;
      input.value = String(idx);
      input.disabled = !!poll.closed || ctx.state.busy;
      input.setAttribute("data-testid", "poll-option-" + idx);
      row.appendChild(input);
      var labelWrap = ctx.el("div", "poll-option-body");
      labelWrap.appendChild(ctx.el("span", "poll-option-text", opt));
      var c = counts[idx] || 0;
      var pct = total > 0 ? Math.round((c / total) * 100) : 0;
      labelWrap.appendChild(
        ctx.el("span", "poll-option-meta", ctx.L("ui.polls.optionMeta", { count: c, pct: pct }))
      );
      var bar = ctx.el("div", "poll-option-bar");
      bar.style.width = pct + "%";
      labelWrap.appendChild(bar);
      row.appendChild(labelWrap);
      input.onchange = function () {
        if (poll.allow_multiple) {
          selected[idx] = input.checked;
        } else {
          selected = {};
          selected[idx] = true;
        }
      };
      optsWrap.appendChild(row);
    });
    card.appendChild(optsWrap);
    if (!poll.closed) {
      card.appendChild(
        ctx.el("div", "poll-total", ctx.L("ui.polls.totalVotes", { count: total }))
      );
      var voteBtn = ctx.iconBtn("✓", ctx.L("ui.polls.vote"), {
        primary: true,
        cls: "poll-vote-btn",
        testId: "poll-vote-btn",
        disabled: ctx.state.busy,
        onClick: function () {
          var indexes = [];
          Object.keys(selected).forEach(function (k) {
            if (selected[k]) indexes.push(Number(k));
          });
          if (!indexes.length) {
            ctx.state.error = ctx.L("ui.polls.pickOption");
            ctx.render();
            return;
          }
          ctx.votePoll(poll.id, indexes);
        },
      });
      card.appendChild(voteBtn);
      if (ctx.closePoll && poll.created_by && ctx.currentUserId && poll.created_by === ctx.currentUserId()) {
        card.appendChild(
          ctx.iconBtn("⏹", ctx.L("ui.polls.close"), {
            cls: "poll-close-btn",
            testId: "poll-close-" + poll.id,
            disabled: ctx.state.busy,
            onClick: function () {
              ctx.closePoll(poll.id);
            },
          })
        );
      }
    } else {
      card.appendChild(
        ctx.el("div", "poll-total", ctx.L("ui.polls.totalVotes", { count: total }))
      );
    }
    return card;
  }

  function mountPollsSection(ctx) {
    if (!ctx.state.selectedId) return null;
    var wrap = ctx.el("div", "thread-polls");
    wrap.setAttribute("data-testid", "thread-polls");
    var head = ctx.el("div", "thread-polls-head");
    head.appendChild(ctx.el("span", "thread-polls-title", ctx.L("ui.polls.title")));
    head.appendChild(
      ctx.iconBtn("↻", ctx.L("ui.polls.refreshResults"), {
        cls: "thread-polls-refresh",
        testId: "poll-refresh",
        disabled: ctx.state.busy,
        onClick: function () {
          if (ctx.reloadChatPolls) ctx.reloadChatPolls();
        },
      })
    );
    head.appendChild(
      ctx.iconBtn("+", ctx.L("ui.polls.create"), {
        cls: "thread-polls-add",
        testId: "poll-create-open",
        disabled: ctx.state.busy,
        onClick: function () {
          ctx.openPollCreate();
        },
      })
    );
    wrap.appendChild(head);
    if (ctx.state.chatPollsBusy) {
      wrap.appendChild(ctx.el("div", "thread-polls-hint", ctx.L("ui.common.loading")));
      return wrap;
    }
    var polls = ctx.state.chatPolls || [];
    if (!polls.length) {
      wrap.appendChild(ctx.el("div", "thread-polls-hint", ctx.L("ui.polls.empty")));
      return wrap;
    }
    polls.forEach(function (p) {
      wrap.appendChild(mountPollCard(p, ctx));
    });
    return wrap;
  }

  function mountPollCreateOverlay(ctx) {
    if (!ctx.state.pollCreateOpen) return null;
    var ov = ctx.el("div", "forward-overlay poll-create-overlay");
    ov.setAttribute("data-testid", "poll-create-overlay");
    var card = ctx.el("div", "forward-card poll-create-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.cancel"), {
      testId: "poll-create-cancel",
      onClick: function () {
        ctx.closePollCreate();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.polls.create"), closeBtn));
    var content = ctx.el("div", "settings-content forward-card-content");
    var q = document.createElement("input");
    q.type = "text";
    q.className = "poll-create-input";
    q.setAttribute("data-testid", "poll-create-question");
    q.placeholder = ctx.L("ui.polls.question");
    content.appendChild(q);
    var ta = document.createElement("textarea");
    ta.className = "poll-create-options";
    ta.rows = 4;
    ta.setAttribute("data-testid", "poll-create-options");
    ta.placeholder = ctx.L("ui.polls.optionsHint");
    content.appendChild(ta);
    var multiRow = ctx.el("label", "poll-create-multiple");
    var multi = document.createElement("input");
    multi.type = "checkbox";
    multi.setAttribute("data-testid", "poll-create-allow-multiple");
    multiRow.appendChild(multi);
    multiRow.appendChild(document.createTextNode(" " + ctx.L("ui.polls.allowMultiple")));
    content.appendChild(multiRow);
    var actions = ctx.el("div", "poll-create-actions settings-foot");
    actions.appendChild(
      ctx.iconBtn(ctx.L("ui.polls.submit"), ctx.L("ui.polls.submit"), {
        primary: true,
        testId: "poll-create-submit",
        disabled: ctx.state.busy,
        onClick: function () {
          var question = (q.value || "").trim();
          var options = (ta.value || "")
            .split("\n")
            .map(function (s) {
              return s.trim();
            })
            .filter(Boolean);
          ctx.createPoll(question, options, multi.checked);
        },
      })
    );
    content.appendChild(actions);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closePollCreate();
    };
    return ov;
  }

  function mountScheduleOverlay(ctx) {
    if (!ctx.state.scheduleSendOpen) return null;
    var ov = ctx.el("div", "forward-overlay schedule-send-overlay");
    ov.setAttribute("data-testid", "schedule-send-overlay");
    var card = ctx.el("div", "forward-card schedule-send-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.cancel"), {
      testId: "schedule-send-cancel",
      onClick: function () {
        ctx.closeScheduleSend();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.schedule.title"), closeBtn));
    var content = ctx.el("div", "settings-content forward-card-content");
    var draft = "";
    var taEl = document.getElementById("msgdraft");
    if (taEl) draft = (taEl.value || "").trim();
    content.appendChild(ctx.el("p", "schedule-send-preview", draft || ctx.L("ui.schedule.emptyDraft")));
    var when = document.createElement("input");
    when.type = "datetime-local";
    when.className = "schedule-send-when";
    when.setAttribute("data-testid", "schedule-send-when");
    var min = new Date(Date.now() + 60000);
    when.min = min.toISOString().slice(0, 16);
    content.appendChild(ctx.el("label", "schedule-send-label", ctx.L("ui.schedule.when")));
    content.appendChild(when);
    var actions = ctx.el("div", "poll-create-actions settings-foot");
    actions.appendChild(
      ctx.iconBtn(ctx.L("ui.schedule.submit"), ctx.L("ui.schedule.submit"), {
        primary: true,
        testId: "schedule-send-submit",
        disabled: ctx.state.busy || !draft,
        onClick: function () {
          if (!when.value) {
            ctx.state.error = ctx.L("ui.schedule.whenRequired");
            ctx.render();
            return;
          }
          var iso = new Date(when.value).toISOString();
          ctx.scheduleMessage(draft, iso);
        },
      })
    );
    content.appendChild(actions);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeScheduleSend();
    };
    return ov;
  }

  function mountWhenPicker(ctx, testIdPrefix) {
    var wrap = ctx.el("div", "schedule-when-picker");
    var when = document.createElement("input");
    when.type = "datetime-local";
    when.className = "schedule-send-when";
    when.setAttribute("data-testid", testIdPrefix + "-when");
    var min = new Date(Date.now() + 60000);
    when.min = min.toISOString().slice(0, 16);
    wrap.appendChild(ctx.el("label", "schedule-send-label", ctx.L("ui.schedule.when")));
    wrap.appendChild(when);
    var presets = ctx.el("div", "schedule-presets");
    [
      { label: ctx.L("ui.reminders.preset1h"), hours: 1 },
      { label: ctx.L("ui.reminders.preset3h"), hours: 3 },
      { label: ctx.L("ui.reminders.presetTomorrow"), hours: 24 },
    ].forEach(function (p) {
      var b = ctx.el("button", "btn btn-ghost btn-sm schedule-preset-btn", p.label);
      b.type = "button";
      b.onclick = function () {
        var d = new Date(Date.now() + p.hours * 3600_000);
        when.value = d.toISOString().slice(0, 16);
      };
      presets.appendChild(b);
    });
    wrap.appendChild(presets);
    return { wrap: wrap, when: when };
  }

  function mountReminderOverlay(ctx) {
    if (!ctx.state.reminderPick) return null;
    var ov = ctx.el("div", "forward-overlay reminder-overlay");
    ov.setAttribute("data-testid", "message-reminder-overlay");
    var card = ctx.el("div", "forward-card reminder-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.cancel"), {
      testId: "message-reminder-cancel",
      onClick: function () {
        ctx.closeMessageReminder();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.reminders.title"), closeBtn));
    var content = ctx.el("div", "settings-content forward-card-content");
    var picker = mountWhenPicker(ctx, "message-reminder");
    content.appendChild(picker.wrap);
    var actions = ctx.el("div", "poll-create-actions settings-foot");
    actions.appendChild(
      ctx.iconBtn(ctx.L("ui.reminders.submit"), ctx.L("ui.reminders.submit"), {
        primary: true,
        testId: "message-reminder-submit",
        disabled: ctx.state.busy,
        onClick: function () {
          if (!picker.when.value) {
            ctx.state.error = ctx.L("ui.schedule.whenRequired");
            ctx.render();
            return;
          }
          var iso = new Date(picker.when.value).toISOString();
          ctx.createMessageReminder(iso);
        },
      })
    );
    content.appendChild(actions);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeMessageReminder();
    };
    return ov;
  }

  function mountContactShareOverlay(ctx) {
    if (!ctx.state.contactShareOpen) return null;
    var ov = ctx.el("div", "forward-overlay contact-share-overlay");
    ov.setAttribute("data-testid", "contact-share-overlay");
    var card = ctx.el("div", "forward-card contact-share-card");
    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.cancel"), {
      testId: "contact-share-cancel",
      onClick: function () {
        ctx.closeContactShare();
      },
    });
    card.appendChild(modalCardHead(ctx, ctx.L("ui.contactShare.title"), closeBtn));
    var content = ctx.el("div", "settings-content forward-card-content");
    var list = ctx.el("div", "contact-share-list");
    list.setAttribute("data-testid", "contact-share-list");
    var selfBtn = ctx.el("button", "contact-share-item", ctx.L("ui.contactShare.shareSelf"));
    selfBtn.type = "button";
    selfBtn.setAttribute("data-testid", "contact-share-self");
    selfBtn.disabled = ctx.state.busy;
    selfBtn.onclick = function () {
      ctx.shareSelfContact();
    };
    list.appendChild(selfBtn);
    if (ctx.state.contactsBusy) {
      list.appendChild(ctx.el("div", "thread-polls-hint", ctx.L("ui.common.loading")));
    } else if (ctx.state.contacts && ctx.state.contacts.length) {
      ctx.state.contacts.forEach(function (ct) {
        var label = ct.display_name || ct.username || ct.id;
        var b = ctx.el("button", "contact-share-item", label);
        b.type = "button";
        b.onclick = function () {
          ctx.sendContactCard({
            display_name: ct.display_name || ct.username || label,
            username: ct.username || null,
            user_id: ct.id,
          });
        };
        list.appendChild(b);
      });
    } else {
      list.appendChild(ctx.el("div", "thread-polls-hint", ctx.L("ui.contactShare.noContacts")));
    }
    content.appendChild(list);
    card.appendChild(content);
    ov.appendChild(card);
    ov.onclick = function (e) {
      if (e.target === ov) ctx.closeContactShare();
    };
    return ov;
  }

  global.KorusUiPolls = {
    mountPollsSection: mountPollsSection,
    mountPollCreateOverlay: mountPollCreateOverlay,
    mountScheduleOverlay: mountScheduleOverlay,
    mountReminderOverlay: mountReminderOverlay,
    mountContactShareOverlay: mountContactShareOverlay,
    totalVotes: totalVotes,
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
