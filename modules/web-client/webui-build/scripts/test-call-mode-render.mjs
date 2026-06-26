/**
 * Smoke tests for call panel (mesh) vs meetings workspace (Jitsi/LiveKit).
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const app = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);
const meetings = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-meetings.js"),
  "utf8"
);

const renderCallPanelIndex = app.indexOf("function renderCallPanel(shell) {");
if (renderCallPanelIndex < 0) {
  throw new Error("renderCallPanel missing");
}

const callBody = app.slice(
  renderCallPanelIndex,
  app.indexOf("function normalizeSettingsTab", renderCallPanelIndex)
);
const meshStageIndex = callBody.indexOf("call-stage-mesh");
const screenStatusIndex = callBody.indexOf('"call-screen-status"');
const largeLocalScreenPreviewIndex = callBody.indexOf('sv.id = "callScreenVideo"');
const jitsiInCallPanel = callBody.indexOf('state.callMode === "jitsi"');

if (meshStageIndex < 0) {
  throw new Error("renderCallPanel must keep the mesh WebRTC stage");
}
if (jitsiInCallPanel >= 0) {
  throw new Error("renderCallPanel must not render Jitsi — use ui-meetings.js");
}
if (screenStatusIndex < 0) {
  throw new Error("local screen share must render a compact status card");
}
if (largeLocalScreenPreviewIndex >= 0) {
  throw new Error("local screen share must not render a large recursive video preview");
}
if (app.indexOf("function startChatCall(") < 0) {
  throw new Error("startChatCall missing — chat calls must launch from thread header");
}
if (app.indexOf("beginRtcMesh(true)") < 0) {
  throw new Error("outgoing mesh calls must offer all chat peers");
}
if (app.indexOf("call-hangup") < 0) {
  throw new Error("call-hangup testid missing");
}
if (app.indexOf("mesh-record-start") < 0) {
  throw new Error("mesh-record-start testid missing");
}
if (app.indexOf("KorusUiCallMeshRecord") < 0 && app.indexOf("mesh-calls/sessions") < 0) {
  throw new Error("mesh call recording API integration missing");
}
if (app.indexOf("mesh-record-list") < 0) {
  throw new Error("mesh-record-list testid missing");
}
if (app.indexOf("joinMeshCallSession") < 0) {
  throw new Error("joinMeshCallSession missing");
}
if (app.indexOf("mesh_session") < 0) {
  throw new Error("mesh_session rtc signal missing");
}
if (meetings.indexOf("mountJitsiStage") < 0) {
  throw new Error("ui-meetings.js must render Jitsi stage");
}
if (meetings.indexOf("renderWorkspace") < 0) {
  throw new Error("ui-meetings.js must expose renderWorkspace");
}

console.log("call-mode-render smoke OK");
