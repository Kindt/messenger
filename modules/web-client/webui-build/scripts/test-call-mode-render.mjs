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

const renderCallPanelIndex = app.indexOf("function renderCallPanel(shell) {");
if (renderCallPanelIndex < 0) {
  throw new Error("renderCallPanel missing");
}

const body = app.slice(renderCallPanelIndex, app.indexOf("function normalizeSettingsTab", renderCallPanelIndex));
const jitsiActiveIndex = body.indexOf('state.callMode === "jitsi" && state.activeConference');
const meshStageIndex = body.indexOf("call-stage");
const jitsiIdleGuardIndex = body.indexOf('state.callMode !== "mesh"');
const callLobbyIndex = body.indexOf('"call-lobby"');
const callLiveStageIndex = body.indexOf('"call-live-stage"');
const screenStatusIndex = body.indexOf('"call-screen-status"');
const largeLocalScreenPreviewIndex = body.indexOf('sv.id = "callScreenVideo"');
const callModeFallbackIndex = body.indexOf("function callModeLabel");

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
if (callLobbyIndex < 0 || callLiveStageIndex < 0) {
  throw new Error("call panel must split lobby controls from the live stage");
}
if (callLobbyIndex > callLiveStageIndex) {
  throw new Error("call lobby must render before the live stage as a separate zone");
}
if (screenStatusIndex < 0) {
  throw new Error("local screen share must render a compact status card");
}
if (largeLocalScreenPreviewIndex >= 0) {
  throw new Error("local screen share must not render a large recursive video preview");
}
if (callModeFallbackIndex < 0) {
  throw new Error("call mode labels must have fallback text for stale locale caches");
}

console.log("call-mode-render smoke OK");
