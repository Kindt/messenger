#!/usr/bin/env node
"use strict";

var fs = require("fs");
var path = require("path");

var srcDir = path.join(
  __dirname,
  "..",
  "modules",
  "core-api",
  "src",
  "main",
  "resources",
  "admin-ui",
  "locales"
);

var REF = "ru";
var LOCALES = ["ru", "en", "be", "kk", "zh", "ko"];

function leafKeys(obj, prefix) {
  prefix = prefix || "";
  var keys = [];
  if (!obj || typeof obj !== "object") return keys;
  Object.keys(obj).forEach(function (k) {
    var full = prefix ? prefix + "." + k : k;
    if (typeof obj[k] === "string") keys.push(full);
    else keys = keys.concat(leafKeys(obj[k], full));
  });
  return keys;
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

var manifest = readJson(path.join(srcDir, "manifest.json"));
var manifestCodes = (manifest.codes || []).slice().sort();
var expectedCodes = LOCALES.slice().sort();
var failed = false;

console.log("Source: " + srcDir);
console.log("Reference: " + REF);

if (JSON.stringify(manifestCodes) !== JSON.stringify(expectedCodes)) {
  failed = true;
  console.log("manifest: FAIL codes=" + manifestCodes.join(","));
}

var refKeys = leafKeys(readJson(path.join(srcDir, REF + ".json"))).sort();
console.log("Reference keys: " + refKeys.length);

LOCALES.forEach(function (code) {
  var file = path.join(srcDir, code + ".json");
  if (!fs.existsSync(file)) {
    failed = true;
    console.log(code + ": FAIL missing file");
    return;
  }
  var keys = new Set(leafKeys(readJson(file)));
  var missing = refKeys.filter(function (k) {
    return !keys.has(k);
  });
  if (missing.length) {
    failed = true;
    console.log("\n" + code + ": FAIL missing " + missing.length);
    missing.slice(0, 15).forEach(function (k) {
      console.log("  - " + k);
    });
  } else {
    console.log(code + ": OK");
  }
});

process.exit(failed ? 1 : 0);
