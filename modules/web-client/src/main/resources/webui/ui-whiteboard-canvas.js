/**
 * Simple canvas whiteboard scaffold — strokes serialized into snapshot JSON.
 */
(function (global) {
  "use strict";

  function parseSnapshot(raw) {
    try {
      var j = JSON.parse(raw || "{}");
      if (j && Array.isArray(j.strokes)) return j;
    } catch (e) {
      /* ignore */
    }
    return { version: 1, strokes: [] };
  }

  function mount(ctx, host, initialJson) {
    var data = parseSnapshot(initialJson);
    var mode = "pen";
    var drawing = false;
    var start = null;
    var currentStroke = null;

    var tabs = ctx.el("div", "whiteboard-tabs");
    var canvasPanel = ctx.el("div", "whiteboard-canvas-panel");
    var jsonPanel = ctx.el("div", "whiteboard-json-panel hidden");
    var ta = document.createElement("textarea");
    ta.className = "whiteboard-editor";
    ta.setAttribute("data-testid", "whiteboard-editor");
    ta.rows = 6;
    ta.placeholder = ctx.L("ui.phase5.whiteboardPlaceholder");

    var canvas = document.createElement("canvas");
    canvas.className = "whiteboard-canvas";
    canvas.setAttribute("data-testid", "whiteboard-canvas");
    canvas.width = 640;
    canvas.height = 320;
    var g = canvas.getContext("2d");
    g.lineCap = "round";
    g.lineJoin = "round";

    function redraw() {
      g.clearRect(0, 0, canvas.width, canvas.height);
      g.fillStyle = "#fff";
      g.fillRect(0, 0, canvas.width, canvas.height);
      (data.strokes || []).forEach(function (s) {
        if (s.type === "rect" && s.x != null) {
          g.strokeStyle = s.color || "#111";
          g.lineWidth = s.width || 2;
          g.strokeRect(s.x, s.y, s.w, s.h);
        } else if (s.points && s.points.length) {
          g.strokeStyle = s.color || "#111";
          g.lineWidth = s.width || 2;
          g.beginPath();
          s.points.forEach(function (p, i) {
            if (i === 0) g.moveTo(p.x, p.y);
            else g.lineTo(p.x, p.y);
          });
          g.stroke();
        }
      });
      ta.value = JSON.stringify(data, null, 2);
    }

    function pos(ev) {
      var r = canvas.getBoundingClientRect();
      var sx = canvas.width / r.width;
      var sy = canvas.height / r.height;
      return {
        x: (ev.clientX - r.left) * sx,
        y: (ev.clientY - r.top) * sy,
      };
    }

    canvas.onpointerdown = function (ev) {
      ev.preventDefault();
      drawing = true;
      var p = pos(ev);
      if (mode === "rect") {
        start = p;
        currentStroke = { type: "rect", x: p.x, y: p.y, w: 0, h: 0, color: "#2563eb", width: 2 };
      } else {
        currentStroke = { type: "pen", color: "#111", width: 3, points: [p] };
      }
      canvas.setPointerCapture(ev.pointerId);
    };
    canvas.onpointermove = function (ev) {
      if (!drawing || !currentStroke) return;
      var p = pos(ev);
      if (currentStroke.type === "rect" && start) {
        currentStroke.w = p.x - start.x;
        currentStroke.h = p.y - start.y;
        data.strokes = (data.strokes || []).slice(0, -1);
        data.strokes.push(currentStroke);
        redraw();
        data.strokes.pop();
        g.strokeStyle = currentStroke.color;
        g.lineWidth = currentStroke.width;
        g.strokeRect(currentStroke.x, currentStroke.y, currentStroke.w, currentStroke.h);
      } else if (currentStroke.points) {
        currentStroke.points.push(p);
        redraw();
        g.strokeStyle = currentStroke.color;
        g.lineWidth = currentStroke.width;
        g.beginPath();
        var pts = currentStroke.points;
        g.moveTo(pts[pts.length - 2].x, pts[pts.length - 2].y);
        g.lineTo(p.x, p.y);
        g.stroke();
      }
    };
    function finishStroke() {
      if (!drawing || !currentStroke) return;
      drawing = false;
      if (currentStroke.type === "rect") {
        if (Math.abs(currentStroke.w) > 2 && Math.abs(currentStroke.h) > 2) {
          data.strokes.push(currentStroke);
        }
      } else if (currentStroke.points && currentStroke.points.length > 1) {
        data.strokes.push(currentStroke);
      }
      currentStroke = null;
      start = null;
      redraw();
    }
    canvas.onpointerup = finishStroke;
    canvas.onpointerleave = finishStroke;

    var toolbar = ctx.el("div", "whiteboard-toolbar");
    [
      { id: "pen", label: ctx.L("ui.whiteboard.toolPen") },
      { id: "rect", label: ctx.L("ui.whiteboard.toolRect") },
    ].forEach(function (t) {
      var b = ctx.el("button", "whiteboard-tool-btn", t.label);
      b.type = "button";
      b.setAttribute("data-testid", "whiteboard-tool-" + t.id);
      b.onclick = function () {
        mode = t.id;
      };
      toolbar.appendChild(b);
    });
    var clearBtn = ctx.el("button", "whiteboard-tool-btn", ctx.L("ui.whiteboard.clearCanvas"));
    clearBtn.type = "button";
    clearBtn.onclick = function () {
      data.strokes = [];
      redraw();
    };
    toolbar.appendChild(clearBtn);
    var undoBtn = ctx.el("button", "whiteboard-tool-btn", ctx.L("ui.whiteboard.undo"));
    undoBtn.type = "button";
    undoBtn.onclick = function () {
      data.strokes.pop();
      redraw();
    };
    toolbar.appendChild(undoBtn);

    canvasPanel.appendChild(ctx.el("p", "phase5-hint", ctx.L("ui.whiteboard.canvasHint")));
    canvasPanel.appendChild(toolbar);
    canvasPanel.appendChild(canvas);

    function showCanvas() {
      canvasPanel.classList.remove("hidden");
      jsonPanel.classList.add("hidden");
    }
    function showJson() {
      jsonPanel.classList.remove("hidden");
      canvasPanel.classList.add("hidden");
    }
    var tabCanvas = ctx.el("button", "whiteboard-tab active", ctx.L("ui.whiteboard.tabCanvas"));
    tabCanvas.type = "button";
    tabCanvas.onclick = showCanvas;
    var tabJson = ctx.el("button", "whiteboard-tab", ctx.L("ui.whiteboard.tabJson"));
    tabJson.type = "button";
    tabJson.onclick = showJson;
    tabs.appendChild(tabCanvas);
    tabs.appendChild(tabJson);

    jsonPanel.appendChild(ta);
    host.appendChild(tabs);
    host.appendChild(canvasPanel);
    host.appendChild(jsonPanel);
    redraw();

    return {
      getSnapshotJson: function () {
        if (!jsonPanel.classList.contains("hidden")) {
          data = parseSnapshot(ta.value);
        }
        return JSON.stringify(data);
      },
    };
  }

  global.KorusUiWhiteboardCanvas = { mount: mount };
})(typeof globalThis !== "undefined" ? globalThis : this);
