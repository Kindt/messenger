/**
 * Own browser call client: RTCPeerConnection only (no LiveKit, no Jitsi).
 */
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const srcPath = join(dir, "../../src/main/resources/webui/ui-call-pc.js");

if (!existsSync(srcPath)) {
  throw new Error("ui-call-pc.js missing — own RTCPeerConnection client required");
}

const src = readFileSync(srcPath, "utf8");
if (/livekit-client|LivekitClient|meet\.jit\.si|JitsiMeetExternalAPI/i.test(src)) {
  throw new Error("ui-call-pc.js must not load LiveKit or Jitsi");
}
if (/cdn\.jsdelivr\.net|unpkg\.com/i.test(src)) {
  throw new Error("ui-call-pc.js must not load third-party CDN scripts");
}
if (!src.includes("RTCPeerConnection")) {
  throw new Error("ui-call-pc.js must use RTCPeerConnection");
}

function FakePC(config) {
  this.config = config;
  this.localDescription = null;
  this.remoteDescription = null;
  this.onicecandidate = null;
  this.ontrack = null;
  this.closed = false;
  this.tracks = [];
  this.ice = [];
  this.transceivers = [];
}
FakePC.prototype.addTrack = function (track, stream) {
  this.tracks.push({ track, stream });
};
FakePC.prototype.createOffer = async function () {
  return {
    type: "offer",
    sdp: "v=0\r\no=korus 1 1 IN IP4 0.0.0.0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111 0\r\n",
  };
};
FakePC.prototype.addTransceiver = function (kind, init) {
  const transceiver = { kind, direction: init && init.direction };
  this.transceivers.push(transceiver);
  return transceiver;
};
FakePC.prototype.createAnswer = async function () {
  return { type: "answer", sdp: "v=0\r\no=korus 2 2 IN IP4 0.0.0.0\r\n" };
};
FakePC.prototype.setLocalDescription = async function (desc) {
  this.localDescription = desc;
};
FakePC.prototype.setRemoteDescription = async function (desc) {
  this.remoteDescription = desc;
};
FakePC.prototype.addIceCandidate = async function (c) {
  this.ice.push(c);
};
FakePC.prototype.close = function () {
  this.closed = true;
};

const sandbox = {
  RTCPeerConnection: FakePC,
  window: {},
  globalThis: {},
};
sandbox.window = sandbox;
sandbox.globalThis = sandbox;
vm.runInNewContext(src, sandbox);

const api = sandbox.KorusUiCallPc;
if (!api || typeof api.createPeerConnection !== "function") {
  throw new Error("KorusUiCallPc.createPeerConnection missing");
}

const pc = api.createPeerConnection([{ urls: "stun:stun.example.test:3478" }]);
if (!(pc instanceof FakePC)) {
  throw new Error("createPeerConnection must construct RTCPeerConnection");
}

const signals = [];
await api.startAsOfferer(pc, function (msg) {
  signals.push(msg);
});
if (!signals.some(function (m) { return m.type === "offer" && m.sdp; })) {
  throw new Error("startAsOfferer must signal local offer SDP");
}
if (!signals.some(function (m) { return m.sdp && m.sdp.includes("m=audio 9 UDP/TLS/RTP/SAVPF 0 111"); })) {
  throw new Error("startAsOfferer must prefer PCMU (PT 0) for native desktop interop");
}
if (typeof api.preferPcmuInSdp !== "function" || api.preferPcmuInSdp(
  "m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8"
) !== "m=audio 9 UDP/TLS/RTP/SAVPF 0 111 8") {
  throw new Error("preferPcmuInSdp must put payload type 0 first");
}

await api.applyRemoteSignal(pc, { type: "answer", sdp: "v=0\r\nanswer" });
if (!pc.remoteDescription || pc.remoteDescription.type !== "answer") {
  throw new Error("applyRemoteSignal answer must set remote description");
}

await api.applyRemoteSignal(pc, { type: "ice", candidate: { candidate: "cand1" } });
if (pc.ice.length !== 1) {
  throw new Error("applyRemoteSignal ice must addIceCandidate");
}
const lifecyclePc = new FakePC({});
if (
  !await api.applyRemoteSignal(lifecyclePc, { type: "participant_left", participant_id: "p2" })
  || lifecyclePc.closed
) {
  throw new Error("participant_left must not close the shared SFU connection");
}
if (
  !await api.applyRemoteSignal(lifecyclePc, { type: "session_ended" })
  || !lifecyclePc.closed
) {
  throw new Error("session_ended must close the shared SFU connection");
}

const stream = { getTracks: function () { return [{ kind: "audio" }]; } };
api.attachLocalStream(pc, stream);
if (pc.tracks.length !== 1) {
  throw new Error("attachLocalStream must addTrack");
}

api.prepareReceiveSlots(pc, { video: 2, audio: 1 });
if (pc.transceivers.length !== 3 || pc.transceivers[0].direction !== "recvonly") {
  throw new Error("prepareReceiveSlots must preallocate recvonly SFU transceivers");
}

const requests = [];
const calls = api.createApi(async function (path, options) {
  requests.push({ path, options });
  return {};
});
const callJoin = { chat_id: "c 1", session_id: "s/1", participant_id: "p:1" };
await calls.create(callJoin.chat_id, "group", "video");
await calls.join(callJoin.chat_id, callJoin.session_id);
await calls.decline(callJoin.chat_id, callJoin.session_id);
await calls.leave(callJoin);
if (
  requests[0].path !== "/chats/c%201/calls"
  || requests[0].options.jsonBody.kind !== "group"
  || requests[0].options.jsonBody.media_intent !== "video"
) {
  throw new Error("createApi.create must send call kind and media intent");
}
if (requests[1].path !== "/chats/c%201/calls/s%2F1/join") {
  throw new Error("createApi.join path mismatch");
}
if (requests[2].path !== "/chats/c%201/calls/s%2F1/decline") {
  throw new Error("createApi.decline path mismatch");
}
if (requests[3].path !== "/chats/c%201/calls/s%2F1/participants/p%3A1/leave") {
  throw new Error("createApi.leave path mismatch");
}

const localTrack = {
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const localVideoTrack = {
  kind: "video",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const localTracks = [localTrack];
const localStream = {
  getTracks: function () { return localTracks; },
  getAudioTracks: function () { return localTracks.filter((track) => track.kind === "audio"); },
  getVideoTracks: function () { return localTracks.filter((track) => track.kind === "video"); },
  addTrack: function (track) { localTracks.push(track); },
};
const localVideoStream = {
  getTracks: function () { return [localVideoTrack]; },
  getAudioTracks: function () { return []; },
  getVideoTracks: function () { return [localVideoTrack]; },
};
const controllerPc = new FakePC({});
controllerPc.connectionState = "new";
const controllerStates = [];
const controllerTimers = [];
let pollCount = 0;
let leaveCount = 0;
let createdKind = null;
const controller = api.createSessionController({
  api: {
    create: async function (_chatId, kind) {
      createdKind = kind;
      return {
        chat_id: "chat-1",
        session_id: "session-1",
        participant_id: "participant-1",
        ice_servers: [],
      };
    },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: async function () {
      pollCount += 1;
      return pollCount <= 2
        ? [{ type: "answer", sdp: "v=0\r\nanswer-" + pollCount }]
        : [];
    },
    leave: async function () { leaveCount += 1; },
  },
  mediaDevices: {
    getUserMedia: async function (constraints) {
      return constraints.video && !constraints.audio ? localVideoStream : localStream;
    },
  },
  createPeerConnection: function () { return controllerPc; },
  setTimeout: function (callback, delay) {
    controllerTimers.push({ callback, delay });
    return controllerTimers.length;
  },
  clearTimeout: function () {},
  onState: function (state) { controllerStates.push(state.status); },
});

await controller.start("chat-1", "audio", null, "direct");
if (createdKind !== "direct") {
  throw new Error("session controller must preserve direct/group topology");
}
if (!controllerPc.remoteDescription || controllerPc.remoteDescription.type !== "answer") {
  throw new Error("session controller must poll and apply media-node answer");
}
controllerPc.connectionState = "connected";
controllerPc.onconnectionstatechange();
if (controller.snapshot().status !== "connected") {
  throw new Error("session controller must expose connected state");
}
await controller.toggleCamera();
if (controller.snapshot().status !== "reconnecting") {
  throw new Error("media renegotiation must expose reconnecting state");
}
controllerTimers.find((timer) => timer.delay === 250).callback();
await new Promise((resolve) => setImmediate(resolve));
if (controller.snapshot().status !== "connected") {
  throw new Error("renegotiation answer on a connected transport must restore connected state");
}
await controller.leave();
if (leaveCount !== 1 || localTrack.readyState !== "ended" || !controllerPc.closed) {
  throw new Error("session controller leave must stop media, close PC, and notify API");
}
if (!controllerStates.includes("acquiring") || !controllerStates.includes("connecting")) {
  throw new Error("session controller must expose acquiring and connecting states");
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
}

async function nextTurn() {
  await new Promise((resolve) => setImmediate(resolve));
}

const staleJoin = {
  chat_id: "chat-race",
  session_id: "session-race",
  participant_id: "participant-race",
  ice_servers: [],
};
const createGate = deferred();
const staleTrack = {
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const staleStream = {
  getTracks: function () { return [staleTrack]; },
  getAudioTracks: function () { return [staleTrack]; },
  getVideoTracks: function () { return []; },
};
let staleLeaveCount = 0;
const staleController = api.createSessionController({
  api: {
    create: function () { return createGate.promise; },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: async function () { return []; },
    leave: async function () { staleLeaveCount += 1; },
  },
  mediaDevices: {
    getUserMedia: async function () { return staleStream; },
  },
  createPeerConnection: function () { return new FakePC({}); },
  setTimeout: function () { return 1; },
  clearTimeout: function () {},
});
const staleStart = staleController.start("chat-race", "audio");
await nextTurn();
await staleController.cancel();
createGate.resolve(staleJoin);
await staleStart;
if (
  staleLeaveCount !== 1
  || staleTrack.readyState !== "ended"
  || staleController.snapshot().join !== null
) {
  throw new Error("cancel during create must release the late server participant and local media");
}

const cameraGate = deferred();
const cameraTrack = {
  kind: "video",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const cameraStream = {
  getTracks: function () { return [cameraTrack]; },
  getAudioTracks: function () { return []; },
  getVideoTracks: function () { return [cameraTrack]; },
};
const cameraLocalTracks = [{
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
}];
const cameraLocalStream = {
  getTracks: function () { return cameraLocalTracks; },
  getAudioTracks: function () { return cameraLocalTracks; },
  getVideoTracks: function () { return []; },
  addTrack: function (track) { cameraLocalTracks.push(track); },
};
const cameraPc = new FakePC({});
const cameraController = api.createSessionController({
  api: {
    create: async function () {
      return {
        chat_id: "chat-camera",
        session_id: "session-camera",
        participant_id: "participant-camera",
        ice_servers: [],
      };
    },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: async function () { return [{ type: "answer", sdp: "v=0\r\nanswer" }]; },
    leave: async function () {},
  },
  mediaDevices: {
    getUserMedia: function (constraints) {
      return constraints.video && !constraints.audio
        ? cameraGate.promise
        : Promise.resolve(cameraLocalStream);
    },
  },
  createPeerConnection: function () { return cameraPc; },
  setTimeout: function () { return 1; },
  clearTimeout: function () {},
});
await cameraController.start("chat-camera", "audio");
const cameraToggle = cameraController.toggleCamera();
await nextTurn();
await cameraController.leave();
cameraGate.resolve(cameraStream);
await cameraToggle;
if (cameraTrack.readyState !== "ended" || cameraLocalTracks.includes(cameraTrack)) {
  throw new Error("camera chooser completion after leave must stop and discard the late track");
}

const pollGate = deferred();
const deadlineCallbacks = [];
const timeoutTrack = {
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const timeoutStream = {
  getTracks: function () { return [timeoutTrack]; },
  getAudioTracks: function () { return [timeoutTrack]; },
  getVideoTracks: function () { return []; },
};
const timeoutController = api.createSessionController({
  api: {
    create: async function () {
      return {
        chat_id: "chat-timeout",
        session_id: "session-timeout",
        participant_id: "participant-timeout",
        ice_servers: [],
      };
    },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: function () { return pollGate.promise; },
    leave: async function () {},
  },
  mediaDevices: {
    getUserMedia: async function () { return timeoutStream; },
  },
  createPeerConnection: function () { return new FakePC({}); },
  setTimeout: function (callback) {
    deadlineCallbacks.push(callback);
    return deadlineCallbacks.length;
  },
  clearTimeout: function () {},
});
const timeoutStart = timeoutController.start("chat-timeout", "audio");
await nextTurn();
if (deadlineCallbacks.length === 0) {
  throw new Error("connection deadline must start before the first signaling poll");
}
deadlineCallbacks[0]();
pollGate.resolve([]);
await timeoutStart;
if (timeoutTrack.readyState !== "ended" || timeoutController.snapshot().status !== "error") {
  throw new Error("connection deadline must stop capture while signaling is stalled");
}

const endedTrack = {
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const endedStream = {
  getTracks: function () { return [endedTrack]; },
  getAudioTracks: function () { return [endedTrack]; },
  getVideoTracks: function () { return []; },
};
const endedPc = new FakePC({});
const endedController = api.createSessionController({
  api: {
    create: async function () {
      return {
        chat_id: "chat-ended",
        session_id: "session-ended",
        participant_id: "participant-ended",
        ice_servers: [],
      };
    },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: async function () {
      return [
        { type: "answer", sdp: "v=0\r\nanswer" },
        { type: "session_ended" },
      ];
    },
    leave: async function () {},
  },
  mediaDevices: {
    getUserMedia: async function () { return endedStream; },
  },
  createPeerConnection: function () { return endedPc; },
  setTimeout: function () { return 1; },
  clearTimeout: function () {},
});
await endedController.start("chat-ended", "audio");
if (
  endedController.snapshot().status !== "idle"
  || endedController.snapshot().join !== null
  || endedTrack.readyState !== "ended"
  || !endedPc.closed
) {
  throw new Error("session_ended must release controller media and return it to idle");
}

const rejectedTrack = {
  kind: "audio",
  enabled: true,
  readyState: "live",
  stop: function () { this.readyState = "ended"; },
};
const rejectedStream = {
  getTracks: function () { return [rejectedTrack]; },
  getAudioTracks: function () { return [rejectedTrack]; },
  getVideoTracks: function () { return []; },
};
const rejectedPc = new FakePC({});
let rejectedLeaveCount = 0;
const rejectedController = api.createSessionController({
  api: {
    create: async function () {
      return {
        chat_id: "chat-rejected",
        session_id: "session-rejected",
        participant_id: "participant-rejected",
        ice_servers: [],
      };
    },
    join: async function () { throw new Error("unexpected join"); },
    send: async function () {},
    poll: async function () {
      return [{ type: "error", error_code: "NO_COMMON_AUDIO_CODEC" }];
    },
    leave: async function () { rejectedLeaveCount += 1; },
  },
  mediaDevices: {
    getUserMedia: async function () { return rejectedStream; },
  },
  createPeerConnection: function () { return rejectedPc; },
  setTimeout: function () { return 1; },
  clearTimeout: function () {},
});
await rejectedController.start("chat-rejected", "audio", null, "direct");
const rejectedSnapshot = rejectedController.snapshot();
if (
  rejectedSnapshot.status !== "error"
  || !rejectedSnapshot.error
  || rejectedSnapshot.error.code !== "NO_COMMON_AUDIO_CODEC"
  || rejectedSnapshot.join !== null
  || rejectedTrack.readyState !== "ended"
  || !rejectedPc.closed
  || rejectedLeaveCount !== 1
) {
  throw new Error("typed media-node errors must fail and tear down the browser call");
}

api.closePeer(pc);
if (!pc.closed) {
  throw new Error("closePeer must close RTCPeerConnection");
}

console.log("call-pc-client smoke OK");
