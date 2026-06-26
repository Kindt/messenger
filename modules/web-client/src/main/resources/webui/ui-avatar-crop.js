(function (global) {
  "use strict";

  var MIN_OUT = 128;
  var VIEW = 280;

  function clamp(v, min, max) {
    return Math.max(min, Math.min(max, v));
  }

  /**
   * @param {object} opts
   * @param {File} opts.file
   * @param {function(string): string} opts.L
   * @param {function(string, string?, object?): HTMLElement} opts.el
   * @param {function(string, string, object?): HTMLElement} opts.iconBtn
   * @param {function(string, HTMLElement): HTMLElement} opts.modalCardHead
   * @param {function(Blob): void} opts.onApply
   * @param {function(): void} [opts.onCancel]
   */
  function openAvatarCropModal(opts) {
    if (!opts || !opts.file) return;
    var L = opts.L || function (k) {
      return k;
    };
    var el = opts.el;
    var iconBtn = opts.iconBtn;
    var modalCardHead = opts.modalCardHead;
    if (!el || !iconBtn || !modalCardHead) return;

    var overlay = el("div", "forward-overlay avatar-crop-overlay");
    overlay.setAttribute("data-testid", "avatar-crop-overlay");
    overlay.setAttribute("role", "dialog");
    overlay.setAttribute("aria-modal", "true");

    var card = el("div", "forward-card avatar-crop-card");
    var closeBtn = iconBtn("\u2715", L("ui.common.cancel"), {
      testId: "avatar-crop-cancel",
      onClick: close,
    });
    card.appendChild(modalCardHead(L("ui.avatar.cropTitle"), closeBtn));

    var body = el("div", "avatar-crop-body");
    var frame = el("div", "avatar-crop-frame");
    var canvas = document.createElement("canvas");
    canvas.className = "avatar-crop-canvas";
    canvas.width = VIEW;
    canvas.height = VIEW;
    canvas.setAttribute("data-testid", "avatar-crop-canvas");
    frame.appendChild(canvas);
    body.appendChild(frame);
    body.appendChild(el("p", "settings-hint avatar-crop-hint", L("ui.avatar.cropHint")));
    card.appendChild(body);

    var foot = el("div", "modal-footer settings-foot avatar-crop-foot");
    foot.appendChild(
      iconBtn(L("ui.common.cancel"), L("ui.common.cancel"), {
        testId: "avatar-crop-cancel-foot",
        onClick: close,
      })
    );
    foot.appendChild(
      iconBtn(L("ui.avatar.cropApply"), L("ui.avatar.cropApply"), {
        primary: true,
        testId: "avatar-crop-apply",
        onClick: applyCrop,
      })
    );
    card.appendChild(foot);
    overlay.appendChild(card);
    overlay.onclick = function (ev) {
      if (ev.target === overlay) close();
    };
    document.body.appendChild(overlay);

    var img = new Image();
    var scale = 1;
    var offsetX = 0;
    var offsetY = 0;
    var dragging = false;
    var dragStartX = 0;
    var dragStartY = 0;
    var dragBaseX = 0;
    var dragBaseY = 0;

    function close() {
      if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
      if (opts.onCancel) opts.onCancel();
    }

    function draw() {
      var ctx = canvas.getContext("2d");
      if (!ctx || !img.complete) return;
      ctx.clearRect(0, 0, VIEW, VIEW);
      ctx.save();
      ctx.beginPath();
      ctx.arc(VIEW / 2, VIEW / 2, VIEW / 2 - 2, 0, Math.PI * 2);
      ctx.clip();
      var w = img.naturalWidth * scale;
      var h = img.naturalHeight * scale;
      ctx.drawImage(img, offsetX, offsetY, w, h);
      ctx.restore();
    }

    function fitImage() {
      if (!img.naturalWidth || !img.naturalHeight) return;
      scale = Math.max(VIEW / img.naturalWidth, VIEW / img.naturalHeight);
      var w = img.naturalWidth * scale;
      var h = img.naturalHeight * scale;
      offsetX = (VIEW - w) / 2;
      offsetY = (VIEW - h) / 2;
      draw();
    }

    function onPointerDown(ev) {
      dragging = true;
      dragStartX = ev.clientX;
      dragStartY = ev.clientY;
      dragBaseX = offsetX;
      dragBaseY = offsetY;
      canvas.setPointerCapture(ev.pointerId);
    }

    function onPointerMove(ev) {
      if (!dragging) return;
      offsetX = dragBaseX + (ev.clientX - dragStartX);
      offsetY = dragBaseY + (ev.clientY - dragStartY);
      draw();
    }

    function onPointerUp(ev) {
      dragging = false;
      try {
        canvas.releasePointerCapture(ev.pointerId);
      } catch (e) {}
    }

    canvas.addEventListener("pointerdown", onPointerDown);
    canvas.addEventListener("pointermove", onPointerMove);
    canvas.addEventListener("pointerup", onPointerUp);
    canvas.addEventListener("pointercancel", onPointerUp);

    function applyCrop() {
      var out = document.createElement("canvas");
      out.width = MIN_OUT;
      out.height = MIN_OUT;
      var ctx = out.getContext("2d");
      if (!ctx) return;
      ctx.beginPath();
      ctx.arc(MIN_OUT / 2, MIN_OUT / 2, MIN_OUT / 2, 0, Math.PI * 2);
      ctx.clip();
      var ratio = MIN_OUT / VIEW;
      ctx.drawImage(canvas, 0, 0, VIEW, VIEW, 0, 0, MIN_OUT, MIN_OUT);
      out.toBlob(
        function (blob) {
          if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
          if (blob && opts.onApply) opts.onApply(blob);
        },
        "image/jpeg",
        0.92
      );
    }

    img.onload = fitImage;
    img.onerror = close;
    img.src = URL.createObjectURL(opts.file);
  }

  global.KorusUiAvatarCrop = {
    open: openAvatarCropModal,
  };
})(typeof window !== "undefined" ? window : globalThis);
