/**
 * Smoke tests for call panel mode-specific rendering.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const app = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);

const fn = app.match(/function renderCallPanel\(shell\) \{[\s\S]*?\n  \}/);
if (!fn) {
  throw new Error("renderCallPanel missing");
}

const body = fn[0];
const jitsiActiveIndex = body.indexOf('state.callMode === "jitsi" && state.activeConference');
const meshStageIndex = body.indexOf("WebRTC mesh через NATS");
const jitsiIdleGuardIndex = body.indexOf('state.callMode !== "mesh"');

if (jitsiActiveIndex < 0) {
  throw new Error("renderCallPanel must keep the active Jitsi branch");
}
if (meshStageIndex < 0) {
  throw new Error("renderCallPanel must keep the mesh WebRTC stage");
}
if (jitsiIdleGuardIndex < 0 || jitsiIdleGuardIndex > meshStageIndex) {
  throw new Error(
    "Jitsi idle panel must return before rendering mesh WebRTC controls"
  );
}

console.log("call-mode-render smoke OK");
