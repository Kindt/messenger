/**
 * Smoke tests for sidebar folder/filter chips with text labels.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const appJs = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);
const css = readFileSync(
  join(dir, "../../src/main/resources/webui/styles.css"),
  "utf8"
);

if (!appJs.includes("function sidebarChipButton")) {
  throw new Error("app.js must define sidebarChipButton");
}
if (!appJs.includes('"sidebar-chip-label"')) {
  throw new Error("sidebar chips must render visible text labels");
}
if (!appJs.includes("sidebarChipButton(")) {
  throw new Error("folder/filter bars must use sidebarChipButton");
}
if (!css.includes(".sidebar-chip-label")) {
  throw new Error("styles.css must style sidebar chip labels");
}

console.log("sidebar-chips smoke OK");
