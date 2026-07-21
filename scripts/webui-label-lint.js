#!/usr/bin/env node
"use strict";

/**
 * WebUI label lint — locale parity + iconBtn/i18n heuristics (spec 026 L4).
 * CI gate via ./gradlew checkWebuiLabelLint (also part of buildIntegrity).
 */
var fs = require("fs");
var path = require("path");
var childProcess = require("child_process");

var repoRoot = path.join(__dirname, "..");
var webuiDir = path.join(
  repoRoot,
  "modules",
  "web-client",
  "src",
  "main",
  "resources",
  "webui"
);

var WEBUI_JS = [
  "app.js",
  "ui-composer.js",
  "ui-export-utils.js",
  "ui-i18n.js",
  "ui-icon-buttons.js",
  "ui-phase5-ext.js",
  "ui-polls.js",
  "ui-transport-utils.js",
];

var CYRILLIC = /[\u0400-\u04FF]/;
/** Hardcoded string tooltip (must be L() or callModeLabel()). */
var ICON_BTN_STRING_TIP =
  /iconBtn\s*\(\s*[^,]+,\s*"(?:[^"\\]|\\.)*"/;
var ICON_BTN_EN_LITERAL =
  /iconBtn\s*\(\s*[^,]+,\s*"[A-Za-z][^"]*"/;

var CYRILLIC_ALLOW = [
  /^\s*\/\//,
  /^\s*\/\*/,
  /^\s*\*/,
  /ERROR_EXACT/,
  /KorusI18n/,
  /global\.KorusI18n/,
  /callModeLabel\s*\(/,
  /errors\.sessionExpired/,
  /^\s*:\s*"/,
  /indexOf\s*\(\s*"/,
  /new Error\s*\(\s*"/,
  /reject\s*\(\s*new Error/,
  /setState\s*\(\s*\{/,
  /statusMessage\s*:/,
  /"Чаты"/,
  /"Контакты"/,
  /"Интеграции"/,
  /return\s+" · /,
  /уч\./,
];

function stripComments(line) {
  var idx = line.indexOf("//");
  if (idx >= 0) return line.slice(0, idx);
  return line;
}

function readLines(rel) {
  var file = path.join(webuiDir, rel);
  if (!fs.existsSync(file)) return [];
  return fs.readFileSync(file, "utf8").split(/\r?\n/);
}

function runParity() {
  var script = path.join(repoRoot, "scripts", "webui-locale-parity-audit.js");
  if (!fs.existsSync(script)) {
    console.error("FAIL: missing " + script);
    return false;
  }
  console.log("--- locale parity ---");
  var out = childProcess.spawnSync(process.execPath, [script], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  if (out.stdout) process.stdout.write(out.stdout);
  if (out.stderr) process.stderr.write(out.stderr);
  if (out.status !== 0) {
    console.error("FAIL: locale parity");
    return false;
  }
  console.log("PASS: locale parity");
  return true;
}

function lintIconBtnTips() {
  console.log("\n--- iconBtn tooltips (no hardcoded strings) ---");
  var findings = [];
  WEBUI_JS.forEach(function (rel) {
    readLines(rel).forEach(function (line, i) {
      if (/function\s+iconBtn\s*\(/.test(line)) return;
      var n = i + 1;
      if (ICON_BTN_STRING_TIP.test(line)) {
        findings.push(rel + ":" + n + " iconBtn tip must use L() or callModeLabel(), not a string literal");
      } else if (ICON_BTN_EN_LITERAL.test(line)) {
        findings.push(rel + ":" + n + " iconBtn hardcoded EN tooltip");
      }
    });
  });
  if (findings.length) {
    findings.forEach(function (f) {
      console.log("  - " + f);
    });
    console.log("FAIL: iconBtn lint (" + findings.length + ")");
    return false;
  }
  console.log("PASS: iconBtn lint");
  return true;
}

function lintCyrillicDrift() {
  console.log("\n--- Cyrillic in webui JS (heuristic) ---");
  var findings = [];
  WEBUI_JS.forEach(function (rel) {
    readLines(rel).forEach(function (line, i) {
      var code = stripComments(line);
      if (!CYRILLIC.test(code)) return;
      if (CYRILLIC_ALLOW.some(function (re) {
        return re.test(code);
      })) {
        return;
      }
      findings.push(rel + ":" + (i + 1) + " " + code.trim().slice(0, 100));
    });
  });
  if (findings.length) {
    console.log("WARN: Cyrillic outside allowlist (" + findings.length + ") — review:");
    findings.slice(0, 20).forEach(function (f) {
      console.log("  - " + f);
    });
    if (findings.length > 20) {
      console.log("  ... +" + (findings.length - 20) + " more");
    }
  } else {
    console.log("PASS: Cyrillic heuristic");
  }
  return true;
}

function main() {
  console.log("webui-label-lint");
  var ok = runParity() && lintIconBtnTips() && lintCyrillicDrift();
  process.exit(ok ? 0 : 1);
}

main();
