/**
 * Smoke tests for ui-message-list virtual window math (PS-3.2).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-message-list.js"),
  "utf8"
);
const sandbox = { window: {}, globalThis: {} };
vm.runInNewContext(src, sandbox);
const list = sandbox.window.KorusUiMessageList;

if (!list) {
  throw new Error("KorusUiMessageList missing");
}

const win = list.computeWindow(1000, 400, 500, 80, 10, 250);
if (win.start > 250 || win.end < 251) {
  throw new Error("focusIndex window failed: " + JSON.stringify(win));
}
if (!list.shouldVirtualize(201)) {
  throw new Error("shouldVirtualize threshold");
}
if (list.shouldVirtualize(200)) {
  throw new Error("shouldVirtualize boundary");
}

const contentSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-message-content.js"),
  "utf8"
);
if (!contentSrc.includes('loading = "lazy"')) {
  throw new Error("ui-message-content missing lazy image loading (FR-083)");
}

const attachSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-file-attach.js"),
  "utf8"
);
if (!attachSrc.includes("IntersectionObserver")) {
  throw new Error("ui-file-attach missing viewport-deferred image load");
}

const appSrc = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);
if (!appSrc.includes("stopTtlRenderTicker") || !appSrc.includes("clearDeferredUiTimers")) {
  throw new Error("app.js missing deferred timer cleanup (FR-085)");
}

console.log("ui-message-list smoke OK");
