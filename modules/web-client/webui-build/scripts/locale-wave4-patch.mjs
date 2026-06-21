#!/usr/bin/env node
/** Wave 4: acceptance UX + federation/marketplace keys. */
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
    federation: {
      badgeThread: "Федерация",
      joinCopied: "Комната скопирована в буфер обмена.",
    },
    edit: {
      title: "Изменить сообщение",
      save: "Сохранить",
      failed: "Не удалось изменить сообщение.",
    },
    phase5: {
      guestAdmitHint: "Ожидание в зале: отправьте гостю ссылку с токеном после проверки.",
      guestAdmitBtn: "Зал ожидания",
    },
    mobile: {
      settingsTitle: "Настройки",
    },
  },
  push: {
    defaultTitle: "Korus Messenger",
    defaultBody: "Новое сообщение",
  },
};

const patchEn = {
  ui: {
    federation: {
      badgeThread: "Federation",
      joinCopied: "Room copied to clipboard.",
    },
    edit: {
      title: "Edit message",
      save: "Save",
      failed: "Could not edit message.",
    },
    phase5: {
      guestAdmitHint: "Waiting room: share the guest link after host approval.",
      guestAdmitBtn: "Waiting room",
    },
    mobile: {
      settingsTitle: "Settings",
    },
  },
  push: {
    defaultTitle: "Korus Messenger",
    defaultBody: "New message",
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
