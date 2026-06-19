/**
 * Smoke tests for ui-message-reply.js.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-message-reply.js"),
  "utf8"
);
const sandbox = {
  window: {},
  document: { getElementById: () => null },
};
sandbox.globalThis = sandbox;
vm.runInNewContext(src, sandbox);
const reply = sandbox.KorusUiMessageReply;

if (!reply) {
  throw new Error("KorusUiMessageReply missing");
}

const ctx = {
  L: function (k) {
    return k;
  },
  el: function (tag, cls, text) {
    const n = {
      tagName: tag,
      className: cls || "",
      textContent: text || "",
      children: [],
      attributes: {},
    };
    n.appendChild = function (c) {
      n.children.push(c);
    };
    n.setAttribute = function (k, v) {
      n.attributes[k] = v;
    };
    return n;
  },
  findMessageInThread: function () {
    return null;
  },
  replySnippetForId: function () {
    return "snippet";
  },
  senderLabelForUserId: function (id) {
    return id.slice(0, 4);
  },
  isE2eeType: function () {
    return false;
  },
  scrollToMessageId: function () {},
};

const preview = reply.replyPreviewData(
  {
    reply_to_msg_id: "abc",
    reply_preview: { message_id: "mid", sender_id: "user1", snippet: "hi" },
  },
  ctx
);
if (!preview || preview.messageId !== "mid" || preview.snippet !== "hi") {
  throw new Error("replyPreviewData from API preview failed");
}

const art = ctx.el("article");
reply.appendReplyQuoteBlock(art, {
  reply_preview: { message_id: "mid", sender_id: "user1", snippet: "hi" },
}, ctx);
if (!art.children.length) {
  throw new Error("appendReplyQuoteBlock did not append");
}

console.log("ui-message-reply smoke OK");
