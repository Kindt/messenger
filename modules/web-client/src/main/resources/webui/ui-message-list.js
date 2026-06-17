/**
 * Virtual message list window (PS-3.2): render only visible rows when thread exceeds threshold.
 */
(function (global) {
  var VIRTUAL_THRESHOLD = 200;
  var DEFAULT_ROW_HEIGHT = 76;
  var BUFFER_ROWS = 40;

  function shouldVirtualize(messageCount) {
    return messageCount > VIRTUAL_THRESHOLD;
  }

  function computeWindow(scrollTop, clientHeight, count, rowHeight, buffer, focusIndex) {
    rowHeight = rowHeight > 0 ? rowHeight : DEFAULT_ROW_HEIGHT;
    buffer = buffer >= 0 ? buffer : BUFFER_ROWS;
    var start = Math.max(0, Math.floor(scrollTop / rowHeight) - buffer);
    var end = Math.min(
      count,
      Math.ceil((scrollTop + Math.max(clientHeight, rowHeight)) / rowHeight) + buffer
    );
    if (end <= start) {
      end = Math.min(count, start + buffer * 2);
    }
    if (focusIndex != null && focusIndex >= 0 && focusIndex < count) {
      start = Math.min(start, Math.max(0, focusIndex - buffer));
      end = Math.max(end, Math.min(count, focusIndex + buffer + 1));
    }
    return {
      start: start,
      end: end,
      topPad: start * rowHeight,
      bottomPad: Math.max(0, (count - end) * rowHeight),
    };
  }

  /**
   * Renders a windowed message list into {@code container}.
   *
   * @returns {boolean} true when virtual mode applied
   */
  function renderVirtualMessages(container, opts) {
    var messages = opts && opts.messages ? opts.messages : [];
    if (!shouldVirtualize(messages.length)) {
      return false;
    }
    var rowHeight = opts.rowHeight || DEFAULT_ROW_HEIGHT;
    var scrollTop = opts.scrollTop != null ? opts.scrollTop : container.scrollTop || 0;
    var win = computeWindow(
      scrollTop,
      container.clientHeight || 480,
      messages.length,
      rowHeight,
      opts.buffer,
      opts.focusIndex
    );

    container.innerHTML = "";
    if (opts.loadMoreEl) {
      container.appendChild(opts.loadMoreEl);
    }

    var topSpacer = document.createElement("div");
    topSpacer.className = "messages-virtual-spacer-top";
    topSpacer.style.height = win.topPad + "px";
    topSpacer.setAttribute("aria-hidden", "true");
    container.appendChild(topSpacer);

    for (var i = win.start; i < win.end; i++) {
      container.appendChild(opts.renderMessage(messages[i], i));
    }

    var bottomSpacer = document.createElement("div");
    bottomSpacer.className = "messages-virtual-spacer-bottom";
    bottomSpacer.style.height = win.bottomPad + "px";
    bottomSpacer.setAttribute("aria-hidden", "true");
    container.appendChild(bottomSpacer);

    container.onscroll = function () {
      if (typeof opts.onScrollNearTop === "function" && container.scrollTop < 64) {
        opts.onScrollNearTop();
      }
      if (typeof opts.onScroll === "function") {
        opts.onScroll();
      }
    };
    return true;
  }

  global.KorusUiMessageList = {
    VIRTUAL_THRESHOLD: VIRTUAL_THRESHOLD,
    DEFAULT_ROW_HEIGHT: DEFAULT_ROW_HEIGHT,
    shouldVirtualize: shouldVirtualize,
    computeWindow: computeWindow,
    renderVirtualMessages: renderVirtualMessages,
  };
})(typeof window !== "undefined" ? window : globalThis);
