#!/usr/bin/env node
/** Spec 068: avatar + profile card i18n keys across locales. */
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
    profile: {
      avatarChange: "Сменить аватар",
      avatarRemove: "Удалить аватар",
      avatarUploadFailed: "Не удалось загрузить аватар",
      avatarHidden: "Скрыть мой аватар",
      avatarHiddenHint: "Другие пользователи увидят инициалы вместо фото",
    },
    avatar: {
      altUser: "Аватар пользователя {name}",
      altChat: "Аватар чата {name}",
      cropTitle: "Обрезка аватара",
      cropHint: "Перетащите изображение. Минимум 128×128 px.",
      cropApply: "Применить",
    },
    profileCard: {
      title: "Профиль",
      message: "Написать",
    },
  },
};

const patchEn = {
  ui: {
    profile: {
      avatarChange: "Change avatar",
      avatarRemove: "Remove avatar",
      avatarUploadFailed: "Could not upload avatar",
      avatarHidden: "Hide my avatar",
      avatarHiddenHint: "Others will see initials instead of your photo",
    },
    avatar: {
      altUser: "Avatar of {name}",
      altChat: "Chat avatar for {name}",
      cropTitle: "Crop avatar",
      cropHint: "Drag to reposition. Minimum 128×128 px.",
      cropApply: "Apply",
    },
    profileCard: {
      title: "Profile",
      message: "Message",
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
