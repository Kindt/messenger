/**
 * Smoke tests for ui-ux-perception.js (FR-165–168).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-ux-perception.js"),
  "utf8"
);

const sandbox = {
  document: {
    head: { appendChild: () => {} },
    getElementById: () => null,
    createElement: (tag) => ({ tagName: tag, textContent: "", style: {}, setAttribute: () => {} }),
  },
};
sandbox.globalThis = sandbox;
vm.runInNewContext(src, sandbox);
const ux = sandbox.KorusUiUxPerception;

if (!ux) {
  throw new Error("KorusUiUxPerception missing");
}

const id1 = ux.newClientMsgId();
const id2 = ux.newClientMsgId();
if (!id1 || id1 === id2 || id1.indexOf("c_") !== 0) {
  throw new Error("newClientMsgId invalid: " + id1 + " / " + id2);
}

const opt = ux.buildOptimisticMessage({
  clientMsgId: "test_client",
  chatId: "chat-1",
  senderId: "user-1",
  type: "text",
  content: "hello",
});
if (opt.id !== "pending-test_client" || !opt._pending || opt.content !== "hello") {
  throw new Error("buildOptimisticMessage failed: " + JSON.stringify(opt));
}

function sortAsc(rows) {
  return rows.slice().sort(function (a, b) {
    return new Date(a.created_at) - new Date(b.created_at);
  });
}

var reconciled = ux.reconcileOptimisticSend(
  [opt],
  "test_client",
  { id: "real-1", client_msg_id: "test_client", created_at: opt.created_at },
  sortAsc
);
if (reconciled.length !== 1 || reconciled[0].id !== "real-1") {
  throw new Error("reconcile success failed: " + JSON.stringify(reconciled));
}

var failed = ux.reconcileOptimisticSend([opt], "test_client", null, sortAsc);
if (!failed[0]._failed || failed[0]._pending) {
  throw new Error("reconcile failure failed: " + JSON.stringify(failed[0]));
}

const elFn = (tag, cls, text) => ({
  tagName: tag,
  className: cls || "",
  textContent: text || "",
  style: { cssText: "" },
  setAttribute: () => {},
  appendChild: function (c) {
    this.children = this.children || [];
    this.children.push(c);
  },
  children: [],
});
const list = { innerHTML: "", appendChild(c) { this.children = this.children || []; this.children.push(c); }, setAttribute: () => {} };
ux.mountChatListSkeleton(list, elFn, 3);
if (!list.children || list.children.length !== 3) {
  throw new Error("mountChatListSkeleton expected 3 rows");
}

const bar = ux.mountUploadProgressBar(elFn, 42, (k) => k);
if (!bar || String(bar.title).indexOf("42") < 0) {
  throw new Error("mountUploadProgressBar missing pct");
}

console.log("ui-ux-perception smoke OK");
