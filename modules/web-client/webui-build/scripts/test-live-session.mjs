/**
 * Smoke tests for live-session panel visibility.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-live-session.js"),
  "utf8"
);

function node(tag, cls, text) {
  return {
    tag,
    cls,
    text: text || "",
    children: [],
    attrs: {},
    appendChild(child) {
      this.children.push(child);
      return child;
    },
    setAttribute(name, value) {
      this.attrs[name] = value;
    },
  };
}

const sandbox = {
  window: {},
  document: {
    createElement: (tag) => node(tag),
    head: { appendChild: () => {} },
  },
};
sandbox.globalThis = sandbox;
vm.runInNewContext(src, sandbox);

const live = sandbox.window.KorusUiLiveSession;
if (!live) {
  throw new Error("KorusUiLiveSession missing");
}

const panel = node("div", "call-panel");
live.renderLiveSection(panel, {
  tokens: { access_token: "token" },
  mediaCaps: { live_streaming_enabled: false },
  platformCaps: { modules: {} },
}, {
  el: node,
  iconBtn: (label) => node("button", "btn", label),
  L: (key) => key,
  apiJson: () => Promise.resolve([]),
  render: () => {},
});

if (panel.children.length !== 0) {
  throw new Error("Disabled LiveKit must not render live-stream noise in the call panel");
}

console.log("ui-live-session smoke OK");
