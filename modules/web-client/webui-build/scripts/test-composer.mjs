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

const ta = {
  value: "hello",
  selectionStart: 0,
  selectionEnd: 5,
  focus: () => {},
};
const sandbox = {
  window: { MediaRecorder: function () {} },
  document: {
    getElementById: (id) => (id === "msgdraft" ? ta : null),
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

console.log("ui-composer smoke OK");
