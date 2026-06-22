/**
 * Smoke tests for video call panel desktop sizing.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(
  join(dir, "../../src/main/resources/webui/styles.css"),
  "utf8"
);

function ruleFor(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = css.match(new RegExp(escaped + "\\s*\\{([^}]*)\\}", "m"));
  if (!match) {
    throw new Error("Missing CSS rule: " + selector);
  }
  return match[1];
}

const desktopCallPanel = ruleFor(".messenger-shell.call-open > .call-panel");
const baseMessengerShell = ruleFor(".messenger-shell");
const desktopCallShell = ruleFor(".messenger-shell.call-open");
const desktopCallHeader = ruleFor(".messenger-shell.call-open > .app-header");
const desktopCallMessenger = ruleFor(".messenger-shell.call-open > .messenger");

if (!/height:\s*100vh\s*;/.test(baseMessengerShell) || !/height:\s*100dvh\s*;/.test(baseMessengerShell)) {
  throw new Error("Messenger shell must keep the composer inside the viewport");
}
if (!/overflow:\s*hidden\s*;/.test(baseMessengerShell)) {
  throw new Error("Messenger shell must prevent document-level scroll");
}

if (!/display:\s*grid\s*;/.test(desktopCallShell)) {
  throw new Error("Call-open shell must keep the header in its own grid row");
}
if (!/grid-template-columns:\s*minmax\(\s*0\s*,\s*1fr\s*\)\s+clamp\(\s*520px\s*,\s*42vw\s*,\s*760px\s*\)/.test(desktopCallShell)) {
  throw new Error("Call-open shell must lay out messenger and video panel below the header");
}
if (!/height:\s*100vh\s*;/.test(desktopCallShell) || !/height:\s*100dvh\s*;/.test(desktopCallShell)) {
  throw new Error("Call-open shell must be fixed to the viewport height");
}
if (!/overflow:\s*hidden\s*;/.test(desktopCallShell)) {
  throw new Error("Call-open shell must keep scrolling inside messenger/call panel");
}
if (!/grid-column:\s*1\s*\/\s*-1\s*;/.test(desktopCallHeader) || !/grid-row:\s*1\s*;/.test(desktopCallHeader)) {
  throw new Error("Header must span the call-open layout above messenger and video panel");
}
if (!/grid-column:\s*1\s*;/.test(desktopCallMessenger) || !/grid-row:\s*2\s*;/.test(desktopCallMessenger)) {
  throw new Error("Messenger must stay below the header in call-open layout");
}
if (!/width:\s*clamp\(\s*520px\s*,\s*42vw\s*,\s*760px\s*\)/.test(desktopCallPanel)) {
  throw new Error("Desktop call panel must reserve a usable video width");
}
if (!/flex:\s*0\s+0\s+clamp\(\s*520px\s*,\s*42vw\s*,\s*760px\s*\)/.test(desktopCallPanel)) {
  throw new Error("Desktop call panel flex basis must match its usable width");
}
if (!/grid-column:\s*2\s*;/.test(desktopCallPanel) || !/grid-row:\s*2\s*;/.test(desktopCallPanel)) {
  throw new Error("Call panel must stay beside the messenger below the header");
}

const jitsiWrap = ruleFor(".call-jitsi-wrap");
if (!/min-height:\s*0\s*;/.test(jitsiWrap)) {
  throw new Error("Jitsi wrapper must be allowed to shrink inside the call panel");
}

const jitsiFrame = ruleFor(".call-jitsi-frame");
if (!/min-height:\s*360px\s*;/.test(jitsiFrame)) {
  throw new Error("Jitsi iframe must keep a usable desktop height");
}
if (!/height:\s*100%\s*;/.test(jitsiFrame)) {
  throw new Error("Jitsi iframe must fill the available wrapper height");
}

console.log("call-layout smoke OK");
