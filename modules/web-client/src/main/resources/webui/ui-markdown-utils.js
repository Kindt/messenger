/**
 * Safe Markdown subset for message bodies (bold, italic, code, links).
 */
(function (global) {
  "use strict";

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function inlineMarkdown(escaped) {
    var s = escaped.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
    s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    s = s.replace(/(^|\W)\*([^*\n]+)\*(?=\W|$)/g, "$1<em>$2</em>");
    s = s.replace(
      /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    );
    s = s.replace(/\n/g, "<br>");
    return s;
  }

  function safeMarkdown(src) {
    if (!src) return "";
    var text = String(src);
    var parts = text.split(/```/);
    var out = [];
    for (var i = 0; i < parts.length; i++) {
      var seg = parts[i];
      if (i % 2 === 1) {
        out.push("<pre><code>" + escapeHtml(seg.replace(/^\w*\r?\n/, "")) + "</code></pre>");
      } else {
        out.push(inlineMarkdown(escapeHtml(seg)));
      }
    }
    return out.join("");
  }

  global.KorusUiMarkdownUtils = {
    escapeHtml: escapeHtml,
    inlineMarkdown: inlineMarkdown,
    safeMarkdown: safeMarkdown,
  };
})(typeof window !== "undefined" ? window : globalThis);
