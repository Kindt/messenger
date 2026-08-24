/**
 * Smoke tests for ui-deep-link-utils URL building and hash fallback.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-deep-link-utils.js"),
  "utf8"
);

function runWithLocation(location) {
  const sandbox = {
    window: {
      location: location,
      history: location.history || { replaceState: () => {} },
    },
    globalThis: {},
    URLSearchParams: URLSearchParams,
  };
  sandbox.globalThis = sandbox.window;
  vm.runInNewContext(src, sandbox);
  return sandbox.window.KorusUiDeepLinkUtils;
}

const dl = runWithLocation({
  origin: "https://app.example",
  pathname: "/webui/",
  search: "",
  hash: "",
});

if (!dl) {
  throw new Error("KorusUiDeepLinkUtils missing");
}

const chatUrl = dl.buildChatUrl("chat-uuid");
if (chatUrl !== "https://app.example/webui/?chat=chat-uuid") {
  throw new Error("buildChatUrl: " + chatUrl);
}
const msgUrl = dl.buildMessageUrl("chat-uuid", "msg-uuid");
if (msgUrl !== "https://app.example/webui/?chat=chat-uuid&msg=msg-uuid") {
  throw new Error("buildMessageUrl: " + msgUrl);
}

let hashCleared = false;
const dlHash = runWithLocation({
  origin: "https://app.example",
  pathname: "/",
  search: "",
  hash: "#chat=c1&msg=m1",
  history: {
    replaceState: () => {
      hashCleared = true;
    },
  },
});
const parsed = dlHash.stripDeepLinkFromUrl();
if (parsed.chatId !== "c1" || parsed.msgId !== "m1") {
  throw new Error("hash fallback: " + JSON.stringify(parsed));
}
if (!hashCleared) {
  throw new Error("hash should be cleared");
}

const dlCall = runWithLocation({
  origin: "https://app.example",
  pathname: "/",
  search: "?chat=c2&call_session=s1&call_mode=video",
  hash: "",
  history: { replaceState: () => {} },
});
const callParsed = dlCall.stripDeepLinkFromUrl();
if (callParsed.chatId !== "c2" || callParsed.callSession !== "s1" || callParsed.callMode !== "video") {
  throw new Error("call deep link: " + JSON.stringify(callParsed));
}
const callUrl = dl.buildCallUrl("c9", "s9", "audio");
if (
  !callUrl.includes("call_session=s9")
  || !callUrl.includes("call_mode=audio")
  || callUrl.includes("mesh_")
) {
  throw new Error("buildCallUrl: " + callUrl);
}

const dlLegacy = runWithLocation({
  origin: "https://app.example",
  pathname: "/",
  search: "?chat=c3&mesh_session=legacy-neutral-id&mesh_mode=audio",
  hash: "",
  history: { replaceState: () => {} },
});
const legacyParsed = dlLegacy.stripDeepLinkFromUrl();
if (
  legacyParsed.callSession !== "legacy-neutral-id"
  || legacyParsed.callMode !== "audio"
) {
  throw new Error("legacy mesh alias: " + JSON.stringify(legacyParsed));
}

console.log("ui-deep-link-utils smoke OK");
