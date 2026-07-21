#!/usr/bin/env node
/**
 * Replace viewport-width @media blocks with html[data-*] attribute selectors.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function prefixSelectorList(selector, htmlPrefix) {
  return selector
    .split(",")
    .map((part) => `${htmlPrefix} ${part.trim()}`)
    .join(",\n");
}

function unwrapMediaBlock(css, mediaQuery, htmlPrefix) {
  const marker = `@media ${mediaQuery}`;
  const start = css.indexOf(marker);
  if (start < 0) return css;
  const openBrace = css.indexOf("{", start);
  let depth = 1;
  let i = openBrace + 1;
  while (i < css.length && depth > 0) {
    if (css[i] === "{") depth++;
    else if (css[i] === "}") depth--;
    i++;
  }
  const inner = css.slice(openBrace + 1, i - 1);
  const transformed = inner.replace(/([^{}]+)\{([^{}]*)\}/g, (all, sel, props) => {
    const trimmed = sel.trim();
    if (!trimmed || trimmed.startsWith("@")) return all;
    return `${prefixSelectorList(trimmed, htmlPrefix)} {${props}}\n\n`;
  });
  return css.slice(0, start) + transformed + css.slice(i);
}

function transformFile(filePath, transforms) {
  let css = fs.readFileSync(filePath, "utf8");
  for (const t of transforms) {
    css = unwrapMediaBlock(css, t.media, t.prefix);
  }
  fs.writeFileSync(filePath, css);
  console.log("OK", filePath);
}

const styles = path.resolve(__dirname, "../src/styles.css");
transformFile(styles, [
  { media: "(max-width: 960px)", prefix: 'html[data-mobile-shell="1"]' },
  { media: "(max-width: 520px)", prefix: 'html[data-device="phone"]' },
  { media: "(max-width: 640px)", prefix: 'html[data-device="phone"]' },
]);

let css = fs.readFileSync(styles, "utf8");
css = css.replace(
  /@media\s*\(min-width:\s*961px\)\s*\{([^{}]+)\{([^{}]*)\}\s*\}/,
  (all, sel, props) => `${prefixSelectorList(sel.trim(), 'html[data-mobile-shell="0"]')} {${props}}\n\n`
);
fs.writeFileSync(styles, css);

const shellLayouts = path.resolve(
  __dirname,
  "../../src/main/resources/webui/shell-layouts.css"
);
transformFile(shellLayouts, [
  { media: "(max-width: 960px)", prefix: 'html[data-mobile-shell="1"]' },
]);
