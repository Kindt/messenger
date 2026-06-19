/**
 * Smoke tests for ui-ws-events guards.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-ws-events.js"),
  "utf8"
);
const sandbox = { window: {}, globalThis: {} };
vm.runInNewContext(src, sandbox);
const ws = sandbox.window.KorusUiWsEvents;

if (!ws) {
  throw new Error("KorusUiWsEvents missing");
}

if (!ws.isMessageSendEvent({ messageId: "m1", chatId: "c1" })) {
  throw new Error("isMessageSendEvent");
}
if (ws.isMessageSendEvent({ messageId: "m1", chatId: "c1", change: "update" })) {
  throw new Error("isMessageSendEvent rejects change");
}
if (!ws.isMessageChangeEvent({ change: "update", messageId: "m1", chatId: "c1" })) {
  throw new Error("isMessageChangeEvent");
}
if (!ws.isReactionChangeEvent({ change: "add", messageId: "m1", chatId: "c1", reaction: "👍" })) {
  throw new Error("isReactionChangeEvent");
}
if (!ws.isTypingEvent({ chat_id: "c1", user_id: "u1", ts: 1 })) {
  throw new Error("isTypingEvent");
}
if (ws.isTypingEvent({ chat_id: "c1", user_id: "u1", ts: 1, type: "read_receipt" })) {
  throw new Error("isTypingEvent rejects read_receipt");
}

console.log("ui-ws-events smoke OK");
