#!/usr/bin/env node
"use strict";

var fs = require("fs");
var path = require("path");

var srcDir = path.join(
  __dirname,
  "..",
  "modules",
  "web-client",
  "webui-build",
  "locales",
  "messages"
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

function readJson(code) {
  var file = path.join(srcDir, code + ".json");
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

var refKeys = leafKeys(readJson(REF)).sort();
var failed = false;

console.log("Source: " + srcDir);
console.log("Reference: " + REF + " (" + refKeys.length + " keys)");

LOCALES.forEach(function (code) {
  if (code === REF) return;
  var keys = new Set(leafKeys(readJson(code)));
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
