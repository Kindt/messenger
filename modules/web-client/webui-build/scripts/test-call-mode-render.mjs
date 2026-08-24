/**
 * Smoke tests for the provider-neutral Korus Calls panel.
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
const screenStatusIndex = callBody.indexOf('"call-screen-status"');
const largeLocalScreenPreviewIndex = callBody.indexOf('sv.id = "callScreenVideo"');
const jitsiInCallPanel = callBody.indexOf('state.callMode === "jitsi"');

if (callBody.indexOf("renderKorusCallPanelContent") < 0) {
  throw new Error("renderCallPanel must use the provider-neutral Korus Calls renderer");
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
const startIndex = app.indexOf("async function startChatCall(");
const startBody = app.slice(startIndex, app.indexOf("async function joinCallFromDeepLink", startIndex));
if (startBody.indexOf("controller.start") < 0 || startBody.indexOf("beginRtcMesh") >= 0) {
  throw new Error("outgoing calls must negotiate once with the Korus media node");
}
if (
  startBody.indexOf("currentChat()") < 0
  || startBody.indexOf('type === "p2p" ? "direct" : "group"') < 0
) {
  throw new Error("outgoing call kind must match direct/group chat topology");
}
if (app.indexOf("call-hangup") < 0) {
  throw new Error("call-hangup testid missing");
}
if (app.indexOf('"data-call-state"') < 0) {
  throw new Error("call panel FSM state attribute missing");
}
if (app.indexOf('"call-start-audio"') < 0 || app.indexOf('"call-start-video"') < 0) {
  throw new Error("neutral start/join actions missing");
}
if (app.indexOf("handleCallSessionEvent") < 0 || app.indexOf('event.type === "call.invited"') < 0) {
  throw new Error("provider-neutral server call invitation event missing");
}
if (app.indexOf('kind: "call_invite"') >= 0 || app.indexOf('kind: "call_decline"') >= 0) {
  throw new Error("call invitations must not reuse legacy peer RTC envelopes");
}
const declineIndex = app.indexOf("function declineIncomingRtcCall()");
const declineBody = app.slice(declineIndex, app.indexOf("function formatInstantLabel", declineIndex));
if (declineIndex < 0 || declineBody.indexOf(".decline(") < 0) {
  throw new Error("decline action must use the provider-neutral Calls API");
}
const toggleIndex = app.indexOf("async function toggleCallPanel()");
const toggleBody = app.slice(toggleIndex, app.indexOf("function attachLocalVideo", toggleIndex));
if (toggleBody.indexOf("endChatCall") >= 0) {
  throw new Error("collapsing the call panel must not leave the call");
}
const terminateIndex = app.indexOf("function terminateActiveCall(");
if (terminateIndex < 0) {
  throw new Error("shared active-call termination helper missing");
}
const expiredIndex = app.indexOf("function sessionExpired()");
const expiredBody = app.slice(expiredIndex, app.indexOf("function isUuidString", expiredIndex));
if (
  expiredIndex < 0
  || expiredBody.indexOf("terminateActiveCall") < 0
  || expiredBody.indexOf("terminateActiveCall") > expiredBody.indexOf("clearTokens")
) {
  throw new Error("session expiry must stop active capture before clearing authentication");
}
const connectivityIndex = app.indexOf("function setupConnectivityHandlers()");
const connectivityBody = app.slice(
  connectivityIndex,
  app.indexOf("async function createAndUploadKeyPackage", connectivityIndex)
);
if (
  connectivityBody.indexOf('"pagehide"') < 0
  || connectivityBody.indexOf("terminateActiveCall") < 0
) {
  throw new Error("pagehide must synchronously terminate active call media");
}
const pendingIndex = app.indexOf("function consumePendingDeepLink()");
const pendingBody = app.slice(
  pendingIndex,
  app.indexOf("function consumePendingGuestLink", pendingIndex)
);
if (pendingBody.indexOf("stashPendingCallDeepLink(fromUrl)") < 0) {
  throw new Error("the first URL parse must preserve call deep links for post-login join");
}
const pendingCallIndex = app.indexOf("async function consumePendingCallDeepLink()");
const pendingCallBody = app.slice(
  pendingCallIndex,
  app.indexOf("async function consumePendingMeetingDeepLink", pendingCallIndex)
);
if (pendingCallBody.indexOf("stripDeepLinkFromUrl") >= 0) {
  throw new Error("call deep-link consumption must read the value stashed by the first URL parse");
}
if (meetings.indexOf("renderWorkspace") < 0) {
  throw new Error("ui-meetings.js must expose renderWorkspace");
}

console.log("call-mode-render smoke OK");
