/**
 * Smoke tests for ui-composer.js wrapComposerSelection.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-composer.js"),
  "utf8"
);

let activeElement = null;
let currentTextarea = null;
const ta = {
  value: "hello",
  selectionStart: 0,
  selectionEnd: 5,
  focus: () => {
    activeElement = ta;
  },
};
currentTextarea = ta;
const sandbox = {
  window: { MediaRecorder: function () {} },
  document: {
    get activeElement() {
      return activeElement;
    },
    getElementById: (id) => (id === "msgdraft" ? currentTextarea : null),
    createElement: (tag) => ({ tagName: tag, children: [] }),
  },
  navigator: { mediaDevices: null },
};
sandbox.globalThis = sandbox;
vm.runInNewContext(src, sandbox);
const composer = sandbox.KorusUiComposer;

if (!composer) {
  throw new Error("KorusUiComposer missing");
}

composer.wrapComposerSelection("**", "**");
if (ta.value !== "**hello**") {
  throw new Error("wrapComposerSelection failed: " + ta.value);
}

ta.value = "draft while refresh waits";
ta.selectionStart = 6;
ta.selectionEnd = 11;
ta.focus();
const snapshot = composer.captureComposerState(sandbox.document, "chat-1");
currentTextarea = {
  value: "old saved draft",
  selectionStart: 0,
  selectionEnd: 0,
  focus: () => {
    activeElement = currentTextarea;
  },
};
let savedDraft = null;
composer.restoreComposerState(sandbox.document, snapshot, "chat-1", (_chatId, text) => {
  savedDraft = text;
});
if (currentTextarea.value !== "draft while refresh waits") {
  throw new Error("restoreComposerState did not restore live value: " + currentTextarea.value);
}
if (currentTextarea.selectionStart !== 6 || currentTextarea.selectionEnd !== 11) {
  throw new Error(
    "restoreComposerState did not restore selection: " +
      currentTextarea.selectionStart +
      "/" +
      currentTextarea.selectionEnd
  );
}
if (activeElement !== currentTextarea) {
  throw new Error("restoreComposerState did not restore focus");
}
if (savedDraft !== "draft while refresh waits") {
  throw new Error("restoreComposerState did not persist restored draft: " + savedDraft);
}

console.log("ui-composer smoke OK");
