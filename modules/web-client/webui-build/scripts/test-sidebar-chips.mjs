/**
 * Smoke tests for sidebar folder/filter icon chips (tooltips, compact bar).
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

if (!appJs.includes("function sidebarIconChip")) {
  throw new Error("app.js must define sidebarIconChip");
}
if (!appJs.includes("mountSidebarChipsBar")) {
  throw new Error("app.js must define mountSidebarChipsBar");
}
if (!appJs.includes("sidebar-chips-bar")) {
  throw new Error("sidebar must use top sidebar-chips-bar for folders/actions");
}
if (!appJs.includes("mountSidebarFiltersPanel")) {
  throw new Error("app.js must define mountSidebarFiltersPanel");
}
if (!appJs.includes("sidebar-filters-panel")) {
  throw new Error("sidebar must use bottom sidebar-filters-panel for show filters");
}
if (!appJs.includes("sidebarIconChip(")) {
  throw new Error("top chips bar must use sidebarIconChip");
}
if (!appJs.includes("sidebarChipButton(")) {
  throw new Error("bottom filter panel must use sidebarChipButton");
}
if (!css.includes(".sidebar-chips-bar")) {
  throw new Error("styles.css must style sidebar-chips-bar");
}
if (!css.includes(".sidebar-filters-panel")) {
  throw new Error("styles.css must style sidebar-filters-panel");
}
if (!css.includes(".sidebar-icon-chip")) {
  throw new Error("styles.css must style sidebar icon chips");
}

console.log("sidebar-chips smoke OK");
