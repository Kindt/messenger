/**
 * Video meetings workspace: Jitsi conferences, LiveKit SFU, planning & admin (separate from chat calls).
 */
(function (global) {
  "use strict";

  function modeLabel(ctx, key, ruFallback, fallback) {
    return ctx.uiLabelFallback(key, ruFallback, fallback);
  }

  function modeTabButton(ctx, btn, label) {
    btn.className += " call-mode-tab";
    btn.appendChild(ctx.el("span", "call-mode-label", label));
    return btn;
  }

  function mountConferenceLobby(ctx, host) {
    var state = ctx.state;
    if (!state.tokens) return;
    var confSec = ctx.el("div", "call-conferences");
    var confHead = ctx.el("div", "call-conferences-head");
    var confTitle = ctx.el("div", "call-conferences-title");
    confTitle.textContent = ctx.L("conference.sectionTitle");
    confHead.appendChild(confTitle);
    confHead.appendChild(
      ctx.iconBtn("↻", ctx.L("conference.refreshList"), {
        disabled: state.conferenceBusy || state.busy,
        onClick: function () {
          ctx.loadActiveConferences()
            .then(function () {
              return ctx.loadChatConferences();
            })
            .then(ctx.render)
            .catch(ctx.render);
        },
      })
    );
    confSec.appendChild(confHead);
    confSec.appendChild(ctx.el("p", "call-hint", ctx.L("conference.inviteHint")));
    var confActions = ctx.el("div", "call-conferences-actions");
    confActions.appendChild(
      ctx.iconBtn("＋", state.conferenceBusy ? ctx.L("conference.creating") : ctx.L("conference.create"), {
        primary: true,
        testId: "meetings-create",
        disabled: state.conferenceBusy || state.busy,
        onClick: function () {
          ctx.createConference();
        },
      })
    );
    if (state.selectedId && state.selectedId !== state.savedChatId) {
      confActions.appendChild(
        ctx.iconBtn("🎬", ctx.L("conference.startInChat"), {
          testId: "meetings-start-in-chat",
          disabled: state.conferenceBusy || state.busy,
          onClick: function () {
            ctx.createConferenceInChat();
          },
        })
      );
    }
    confActions.appendChild(
      ctx.iconBtn("🔗", ctx.L("conference.joinByLink"), {
        testId: "meetings-join-link",
        disabled: state.conferenceBusy || state.busy,
        onClick: function () {
          ctx.joinConferenceByLink();
        },
      })
    );
    confSec.appendChild(confActions);
    var confList = ctx.listUserActiveConferences();
    if (confList.length) {
      confList.forEach(function (c) {
        var row = ctx.el("div", "call-conf-row");
        var chatLabel = c.chat_id ? ctx.chatTitleById(c.chat_id) : "";
        var baseTitle =
          ctx.safeConferenceDisplayTitle(c) +
          (chatLabel ? " · " + chatLabel : "") +
          ctx.conferenceParticipantsLabel(c.participant_count) +
          (state.activeConference && state.activeConference.conference_id === c.conference_id
            ? " · " + ctx.L("conference.live")
            : "");
        row.appendChild(ctx.el("span", "call-conf-label", baseTitle));
        row.appendChild(
          ctx.iconBtn("▶", ctx.L("conference.join"), {
            testId: "meetings-join-" + (c.conference_id || "guest"),
            disabled: state.conferenceBusy,
            onClick: function () {
              ctx.joinJitsiConference(c);
            },
          })
        );
        confSec.appendChild(row);
      });
    } else {
      confSec.appendChild(ctx.el("p", "call-conf-empty", ctx.L("conference.noneActive")));
    }
    host.appendChild(confSec);
  }

  function mountJitsiStage(ctx, callLiveStage) {
    var state = ctx.state;
    if (state.meetingsMode !== "jitsi" || !state.activeConference || !state.activeConference.join_url) {
      return null;
    }
    var jHint = ctx.el("p", "call-hint");
    jHint.textContent = ctx.L("conference.jitsiHint", {
      host:
        state.mediaCaps && state.mediaCaps.jitsi_base_url
          ? state.mediaCaps.jitsi_base_url
          : "meet.jit.si",
    });
    callLiveStage.appendChild(jHint);
    if (
      ctx.conferenceIsTracked(state.activeConference) &&
      state.activeConference.conference_id &&
      state.conferenceParticipantsConfId !== state.activeConference.conference_id
    ) {
      ctx.loadConferenceParticipants(state.activeConference.conference_id)
        .then(function () {
          if (
            state.activeConference &&
            state.activeConference.conference_id === state.conferenceParticipantsConfId
          ) {
            ctx.scheduleRender();
          }
        })
        .catch(function () {});
    }
    if (ctx.conferenceIsTracked(state.activeConference)) {
      var partSec = ctx.el("div", "call-participants");
      var partHead = ctx.el("div", "call-participants-head");
      partHead.appendChild(ctx.el("span", "call-participants-title", ctx.L("conference.participantsTitle")));
      partHead.appendChild(
        ctx.iconBtn("↻", ctx.L("conference.refreshParticipants"), {
          onClick: function () {
            ctx.loadConferenceParticipants(state.activeConference.conference_id)
              .then(ctx.render)
              .catch(ctx.render);
          },
        })
      );
      partSec.appendChild(partHead);
      var partList = ctx.el("ul", "call-participants-list");
      var participants = state.conferenceParticipantsList;
      if (participants && participants.length) {
        participants.forEach(function (p) {
          partList.appendChild(ctx.el("li", "call-participant-row", ctx.conferenceParticipantLabel(p)));
        });
      } else if (participants && !participants.length) {
        partList.appendChild(
          ctx.el("li", "call-participant-row call-participant-empty", ctx.L("conference.noParticipants"))
        );
      } else {
        partList.appendChild(ctx.el("li", "call-participant-row call-participant-empty", "…"));
      }
      partSec.appendChild(partList);
      callLiveStage.appendChild(partSec);
    }
    var jWrap = ctx.el("div", "call-jitsi-wrap");
    var iframe = ctx.getOrCreateJitsiIframe();
    if (iframe.src !== state.activeConference.join_url) {
      iframe.src = state.activeConference.join_url;
    }
    jWrap.appendChild(iframe);
    callLiveStage.appendChild(jWrap);
    return mountJitsiToolbar(ctx);
  }

  function mountJitsiToolbar(ctx) {
    var state = ctx.state;
    var jBar = ctx.el("div", "call-toolbar meetings-toolbar");
    jBar.appendChild(
      ctx.iconBtn("📋", ctx.L("conference.copyLinkHint"), {
        onClick: function () {
          ctx.copyConferenceLink();
        },
      })
    );
    jBar.appendChild(
      ctx.iconBtn("↻", ctx.L("conference.reloadJitsiHint"), {
        onClick: function () {
          ctx.reloadJitsiIframe();
        },
      })
    );
    if (ctx.conferenceIsTracked(state.activeConference)) {
      jBar.appendChild(
        ctx.iconBtn("➕", ctx.L("conference.inviteMembers"), {
          disabled: state.busy,
          onClick: function () {
            ctx.inviteMembersToMeetingChat(state.activeConference);
          },
        })
      );
      jBar.appendChild(
        ctx.iconBtn("📢", ctx.L("conference.repostInvite"), {
          disabled: state.busy,
          onClick: function () {
            ctx.postMeetingInviteMessage(state.activeConference.chat_id, state.activeConference)
              .then(function () {
                state.statusMessage = ctx.L("conference.invitePosted");
                ctx.render();
              })
              .catch(function (e) {
                state.error = ctx.localErr(e.message) || ctx.L("conference.invitePostFailed");
                ctx.render();
              });
          },
        })
      );
    }
    jBar.appendChild(
      ctx.iconBtn("🚪", ctx.L("conference.leave"), {
        onClick: function () {
          ctx.leaveActiveConference();
        },
      })
    );
    var endBtn = ctx.iconBtn("⏹", ctx.L("conference.endAll"), {
      disabled: state.busy || !ctx.conferenceIsTracked(state.activeConference),
      onClick: function () {
        ctx.endActiveConference();
      },
    });
    if (!ctx.conferenceIsTracked(state.activeConference)) {
      endBtn.title = ctx.L("conference.endGuestHint");
      endBtn.setAttribute("aria-label", ctx.L("conference.endGuestHint"));
    }
    jBar.appendChild(endBtn);
    return jBar;
  }

  function renderSidebarList(sidebarContent, ctx) {
    var state = ctx.state;
    var wrap = ctx.el("div", "meetings-sidebar-list");
    wrap.setAttribute("data-testid", "meetings-sidebar-list");
    var confList = ctx.listUserActiveConferences();
    if (!confList.length) {
      wrap.appendChild(ctx.el("div", "chat-list-empty", ctx.L("conference.noneActive")));
      sidebarContent.appendChild(wrap);
      return;
    }
    confList.forEach(function (c) {
      var row = ctx.el("button", "chat-item meetings-sidebar-item");
      row.type = "button";
      row.setAttribute("data-testid", "meetings-sidebar-item-" + (c.conference_id || "guest"));
      var chatLabel = c.chat_id ? ctx.chatTitleById(c.chat_id) : "";
      row.textContent =
        ctx.safeConferenceDisplayTitle(c) +
        (chatLabel ? " · " + chatLabel : "") +
        ctx.conferenceParticipantsLabel(c.participant_count);
      row.onclick = function () {
        ctx.joinJitsiConference(c);
      };
      wrap.appendChild(row);
    });
    sidebarContent.appendChild(wrap);
  }

  function renderWorkspace(container, ctx) {
    var state = ctx.state;
    var root = ctx.el("div", "meetings-workspace");
    root.setAttribute("data-testid", "meetings-workspace");
    var head = ctx.el("div", "meetings-workspace-head");
    head.appendChild(
      ctx.el(
        "h2",
        "meetings-workspace-title",
        ctx.uiLabelFallback("ui.meetings.title", "Видеовстречи", "Video meetings")
      )
    );
    head.appendChild(
      ctx.el(
        "p",
        "meetings-workspace-sub",
        ctx.uiLabelFallback(
          "ui.meetings.subtitle",
          "Планирование, ссылки для гостей и администрирование — отдельно от быстрых звонков в чатах.",
          "Planning, guest links, and admin tools — separate from quick in-chat calls."
        )
      )
    );
    root.appendChild(head);

    var modeBar = ctx.el("div", "call-mode-bar meetings-mode-bar");
    modeBar.appendChild(
      modeTabButton(
        ctx,
        ctx.iconBtn("🎥", modeLabel(ctx, "ui.call.modeJitsi", "Встреча", "Meeting"), {
          primary: state.meetingsMode === "jitsi",
          testId: "meetings-mode-jitsi",
          disabled: state.conferenceBusy || state.busy,
          onClick: function () {
            ctx.switchMeetingsMode("jitsi");
          },
        }),
        modeLabel(ctx, "ui.call.modeJitsi", "Встреча", "Meeting")
      )
    );
    if (global.KorusUiCallLivekit && KorusUiCallLivekit.groupCallSfuEnabled(state)) {
      modeBar.appendChild(
        modeTabButton(
          ctx,
          ctx.iconBtn("☁", modeLabel(ctx, "ui.call.modeLivekit", "Эфир", "Live"), {
            primary: state.meetingsMode === "livekit",
            testId: "livekit-sfu-button",
            disabled: state.conferenceBusy || state.callPanelToggleBusy,
            onClick: function () {
              ctx.switchMeetingsMode("livekit");
            },
          }),
          modeLabel(ctx, "ui.call.modeLivekit", "Эфир", "Live")
        )
      );
    }
    root.appendChild(modeBar);

    var body = ctx.el("div", "meetings-workspace-body");
    var lobby = ctx.el("section", "call-lobby meetings-lobby");
    var liveStage = ctx.el("section", "call-live-stage meetings-live-stage");

    mountConferenceLobby(ctx, lobby);
    if (global.KorusUiLiveSession) {
      KorusUiLiveSession.renderLiveSection(lobby, state, {
        el: ctx.el,
        iconBtn: ctx.iconBtn,
        L: ctx.L,
        apiJson: ctx.apiJson,
        render: ctx.render,
      });
    }

    if (state.meetingsMode === "livekit" && global.KorusUiCallLivekit) {
      KorusUiCallLivekit.renderLiveKitSection(liveStage, state, {
        el: ctx.el,
        iconBtn: ctx.iconBtn,
        L: ctx.L,
        switchCallMode: ctx.switchMeetingsMode,
        render: ctx.render,
      });
    }

    if (state.activeConference && ctx.conferenceIsTracked(state.activeConference)) {
      var confAdrBar = ctx.uiCallAdr.mountConfAdrBar(ctx.getPhase5UiCtx(), state.activeConference);
      if (confAdrBar) liveStage.appendChild(confAdrBar);
    }

    var toolbar = mountJitsiStage(ctx, liveStage);

    if (state.meetingsMode !== "jitsi" || !state.activeConference) {
      body.appendChild(lobby);
    }
    if (liveStage.childNodes.length) {
      body.appendChild(liveStage);
    }
    root.appendChild(body);
    if (toolbar) root.appendChild(toolbar);
    container.appendChild(root);
  }

  global.KorusUiMeetings = {
    renderWorkspace: renderWorkspace,
    renderSidebarList: renderSidebarList,
  };
})(typeof window !== "undefined" ? window : globalThis);
