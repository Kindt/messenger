/**
 * Smoke tests for layout zone structure across client + admin UI.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));
const appJs = readFileSync(
  join(dir, "../../src/main/resources/webui/app.js"),
  "utf8"
);
const styles = readFileSync(
  join(dir, "../../src/main/resources/webui/styles.css"),
  "utf8"
);
const adminHtml = readFileSync(
  join(
    dir,
    "../../../core-api/src/main/resources/admin-ui/index.html"
  ),
  "utf8"
);
const adminStyles = readFileSync(
  join(
    dir,
    "../../../core-api/src/main/resources/admin-ui/styles.css"
  ),
  "utf8"
);

const clientMarkers = [
  { file: "app.js", src: appJs, needle: '"sidebar-content"' },
  { file: "app.js", src: appJs, needle: '"thread-body"' },
  { file: "app.js", src: appJs, needle: '"thread-foot"' },
  { file: "app.js", src: appJs, needle: '"settings-head"' },
  { file: "app.js", src: appJs, needle: '"settings-content"' },
  { file: "app.js", src: appJs, needle: '"auth-card-content"' },
  { file: "app.js", src: appJs, needle: '"integration-panel-content"' },
  { file: "app.js", src: appJs, needle: "function sidebarTabButton" },
  { file: "app.js", src: appJs, needle: "function uiLabelFallback" },
  { file: "app.js", src: appJs, needle: '"sidebar-tab-label"' },
  { file: "styles.css", src: styles, needle: ".sidebar-content" },
  { file: "styles.css", src: styles, needle: ".thread-body" },
  { file: "styles.css", src: styles, needle: ".thread-foot" },
  { file: "styles.css", src: styles, needle: ".settings-head" },
  { file: "styles.css", src: styles, needle: ".settings-content" },
  { file: "styles.css", src: styles, needle: ".auth-card-content" },
  { file: "styles.css", src: styles, needle: ".integration-panel-content" },
  {
    file: "styles.css",
    src: styles,
    needle: ".messenger-shell.call-open.integration-open",
  },
];

for (const marker of clientMarkers) {
  if (!marker.src.includes(marker.needle)) {
    throw new Error(`${marker.file} missing layout zone marker: ${marker.needle}`);
  }
}

if (!adminHtml.includes('class="admin-panel-content"')) {
  throw new Error("admin index.html missing admin-panel-content wrapper");
}

if (!adminStyles.includes(".admin-panel-content")) {
  throw new Error("admin styles.css missing .admin-panel-content rule");
}

console.log("layout-zones smoke OK");
