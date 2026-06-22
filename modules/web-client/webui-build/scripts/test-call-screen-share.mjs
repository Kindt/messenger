/**
 * Smoke tests for screen sharing feature detection.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const app = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);

const fn = app.match(/async function toggleScreenShare\(\) \{[\s\S]*?\n  \}/);
if (!fn) {
  throw new Error("toggleScreenShare missing");
}

const body = fn[0];
const guardIndex = body.indexOf("typeof mediaDevices.getDisplayMedia");
const callIndex = body.indexOf("mediaDevices.getDisplayMedia");
if (guardIndex < 0 || callIndex < 0 || guardIndex > callIndex) {
  throw new Error("toggleScreenShare must guard getDisplayMedia before calling it");
}
if (!body.includes('L("rtc.screenShareUnsupported")')) {
  throw new Error("Unsupported screen sharing must use an i18n message");
}

console.log("call-screen-share smoke OK");
