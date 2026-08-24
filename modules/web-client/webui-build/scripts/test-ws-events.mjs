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
const handlerSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-ws-handler.js"),
  "utf8"
);
const sandbox = { window: {}, globalThis: {} };
vm.runInNewContext(src, sandbox);
vm.runInNewContext(handlerSrc, sandbox);
const ws = sandbox.window.KorusUiWsEvents;
const handler =
  sandbox.KorusUiWsHandler
  || sandbox.globalThis.KorusUiWsHandler
  || sandbox.window.KorusUiWsHandler;

if (!ws || !handler) {
  throw new Error("KorusUiWsEvents or KorusUiWsHandler missing");
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
if (!ws.isCallSessionEvent({
  type: "call.invited",
  chat_id: "c1",
  session_id: "s1",
  caller_user_id: "u1",
  media_intent: "video",
})) {
  throw new Error("isCallSessionEvent accepts provider-neutral invitations");
}
if (ws.isCallSessionEvent({ type: "rtc_signal", chat_id: "c1", session_id: "s1" })) {
  throw new Error("isCallSessionEvent rejects legacy RTC envelopes");
}
let handledCall = null;
handler.handleWsIncoming(
  {
    data: JSON.stringify({
      type: "call.invited",
      chat_id: "c1",
      session_id: "s1",
      caller_user_id: "u1",
      media_intent: "video",
    }),
  },
  {
    isCallSessionEvent: ws.isCallSessionEvent,
    handleCallSessionEvent: function (event) { handledCall = event; },
    sendHeartbeatThrottled: function () {},
  }
);
if (!handledCall || handledCall.session_id !== "s1") {
  throw new Error("WebSocket handler must route provider-neutral call events");
}

console.log("ui-ws-events smoke OK");
