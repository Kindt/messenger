/**
 * Sender profile card overlay (spec 068 W6).
 */
(function (global) {
  "use strict";

  function mountProfileCardOverlay(ctx) {
    if (!ctx.state.profileCardUserId) {
      return null;
    }
    var userId = ctx.state.profileCardUserId;
    var myId = ctx.myId;
    if (myId && userId === myId) {
      return null;
    }
    var title = ctx.avatarTitleForUser(userId);
    var url = ctx.avatarUrlForUser(userId);

    var ov = ctx.el("div", "profile-card-overlay");
    ov.setAttribute("data-testid", "profile-card-overlay");
    ov.setAttribute("role", "dialog");
    ov.setAttribute("aria-modal", "true");
    ov.setAttribute("aria-label", ctx.L("ui.profileCard.title"));

    var card = ctx.el("div", "profile-card");
    card.setAttribute("data-testid", "profile-card");

    var closeBtn = ctx.iconBtn("✕", ctx.L("ui.common.close"), {
      testId: "profile-card-close",
      onClick: function () {
        ctx.closeProfileCard();
      },
    });
    var head = ctx.modalCardHead(ctx.L("ui.profileCard.title"), closeBtn);
    card.appendChild(head);

    var body = ctx.el("div", "profile-card-body");
    body.appendChild(
      ctx.renderAvatar({
        url: url,
        title: title,
        userId: userId,
        size: "lg",
        testId: "profile-card-avatar",
      })
    );
    body.appendChild(ctx.el("div", "profile-card-name", title));
    body.appendChild(ctx.el("div", "profile-card-id text-muted text-sm", userId.slice(0, 8) + "…"));

    var actions = ctx.el("div", "profile-card-actions");
    actions.appendChild(
      ctx.iconBtn("💬", ctx.L("ui.profileCard.message"), {
        testId: "profile-card-message",
        primary: true,
        onClick: function () {
          ctx.openP2pChat(userId);
          ctx.closeProfileCard();
        },
      })
    );
    actions.appendChild(
      ctx.iconBtn("🚫", ctx.L("ui.sidebar.blockUser"), {
        testId: "profile-card-block",
        onClick: function () {
          ctx.blockUser(userId);
          ctx.closeProfileCard();
        },
      })
    );
    body.appendChild(actions);
    card.appendChild(body);
    ov.appendChild(card);

    ov.onclick = function (e) {
      if (e.target === ov) {
        ctx.closeProfileCard();
      }
    };

    var onKey = function (ev) {
      if (ev.key === "Escape") {
        ctx.closeProfileCard();
      }
    };
    document.addEventListener("keydown", onKey, { once: true });

    return ov;
  }

  global.KorusUiProfileCard = {
    mountProfileCardOverlay: mountProfileCardOverlay,
  };
})(typeof window !== "undefined" ? window : globalThis);
