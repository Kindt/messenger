#!/usr/bin/env node
/** Wave 5–8 closure: guest admit, marketplace connect, recording complete keys. */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const srcDir = path.resolve(__dirname, "../locales/messages");

function deepMerge(target, patch) {
  Object.keys(patch).forEach(function (k) {
    if (patch[k] && typeof patch[k] === "object" && !Array.isArray(patch[k])) {
      if (!target[k] || typeof target[k] !== "object") target[k] = {};
      deepMerge(target[k], patch[k]);
    } else {
      target[k] = patch[k];
    }
  });
  return target;
}

const patchRu = {
  ui: {
    marketplace: {
      connect: "Подключить",
      disconnect: "Отключить",
      connectFailed: "Не удалось подключить интеграцию.",
      disconnectFailed: "Не удалось отключить интеграцию.",
    },
    phase5: {
      guestWaitingEmpty: "Нет гостей в зале ожидания.",
      guestAdmitDo: "Допустить гостя",
      guestAdmitDone: "Гость допущен.",
      recordCompleted: "Запись завершена.",
    },
  },
};

const patchEn = {
  ui: {
    marketplace: {
      connect: "Connect",
      disconnect: "Disconnect",
      connectFailed: "Could not connect integration.",
      disconnectFailed: "Could not disconnect integration.",
    },
    phase5: {
      guestWaitingEmpty: "No guests in the waiting room.",
      guestAdmitDo: "Admit guest",
      guestAdmitDone: "Guest admitted.",
      recordCompleted: "Recording completed.",
    },
  },
};

const byCode = { ru: patchRu, en: patchEn, be: patchEn, kk: patchEn, zh: patchEn, ko: patchEn };
Object.keys(byCode).forEach(function (code) {
  const file = path.join(srcDir, code + ".json");
  const bundle = JSON.parse(fs.readFileSync(file, "utf8"));
  deepMerge(bundle, byCode[code]);
  fs.writeFileSync(file, JSON.stringify(bundle, null, 2) + "\n", "utf8");
  console.log("patched " + code);
});
