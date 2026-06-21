#!/usr/bin/env node
/** One-shot merge: wave 1+2 UI keys into all locale sources (ru ref). */
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
    shell: { presenceTitle: "Нажмите, чтобы сменить статус" },
    sidebar: {
      folderPersonal: "В папку «Личные»",
      filter: { all: "Все чаты", unread: "Непрочитанные", mentions: "Упоминания" },
      marketplaceSearch: "Поиск интеграций…",
      marketplaceAll: "Все категории",
      noChatsUnread: "Нет непрочитанных чатов.",
      noChatsMentions: "Нет чатов с упоминаниями.",
    },
    integration: { close: "Закрыть панель" },
    thread: { emoji: "Эмодзи" },
    phase5: {
      aiAssist: "AI-помощник",
      aiAssistTitle: "AI-помощник в чате",
      aiAssistPlaceholder: "Задайте вопрос по этому чату…",
      aiAssistRun: "Спросить",
      aiAssistEmpty: "Нет ответа от AI gateway.",
      aiAssistFailed: "Не удалось выполнить AI assist.",
      aiInsert: "Вставить в composer",
      kanbanMoveBack: "В предыдущую колонку",
      kanbanMoveForward: "В следующую колонку",
      kanbanDelete: "Удалить задачу",
      kanbanDeleted: "Задача удалена.",
      passkeysTitle: "Passkeys (scaffold)",
      passkeysEmpty: "Passkeys не зарегистрированы.",
      passkeysRegister: "Зарегистрировать scaffold credential",
      passkeysRegistered: "Passkey scaffold зарегистрирован.",
      passkeysFailed: "Не удалось зарегистрировать passkey.",
      guestExpired: "Гостевая ссылка истекла.",
      guestRedeemed: "Гостевая ссылка принята.",
      guestRedeemFailed: "Не удалось принять гостевую ссылку.",
      recordList: "Список записей",
      recordListEmpty: "Записей пока нет.",
      breakoutList: "Breakout-комнаты",
      breakoutListEmpty: "Breakout-комнат нет.",
      sipTitle: "SIP / H.323 gateway",
      sipEnabled: "Включить SIP",
      sipUri: "Gateway URI",
      sipSave: "Сохранить SIP",
      sipSaved: "SIP gateway сохранён.",
      sipFailed: "Не удалось сохранить SIP.",
      stickerPacks: "Паки стикеров",
      stickerSend: "Отправить стикер",
      captionsLive: "Транскрипт (live)",
    },
    message: { sticker: "Стикер" },
  },
};

const patchEn = {
  ui: {
    shell: { presenceTitle: "Click to change status" },
    sidebar: {
      folderPersonal: "Mark as personal",
      filter: { all: "All chats", unread: "Unread", mentions: "Mentions" },
      marketplaceSearch: "Search integrations…",
      marketplaceAll: "All categories",
      noChatsUnread: "No unread chats.",
      noChatsMentions: "No chats with mentions.",
    },
    integration: { close: "Close panel" },
    thread: { emoji: "Emoji" },
    phase5: {
      aiAssist: "AI assist",
      aiAssistTitle: "AI chat assist",
      aiAssistPlaceholder: "Ask a question about this chat…",
      aiAssistRun: "Run",
      aiAssistEmpty: "No reply from AI gateway.",
      aiAssistFailed: "AI assist failed.",
      aiInsert: "Insert into composer",
      kanbanMoveBack: "Move to previous column",
      kanbanMoveForward: "Move to next column",
      kanbanDelete: "Delete task",
      kanbanDeleted: "Task deleted.",
      passkeysTitle: "Passkeys (scaffold)",
      passkeysEmpty: "No passkeys registered.",
      passkeysRegister: "Register scaffold credential",
      passkeysRegistered: "Passkey scaffold registered.",
      passkeysFailed: "Passkey registration failed.",
      guestExpired: "Guest link expired.",
      guestRedeemed: "Guest link accepted.",
      guestRedeemFailed: "Could not redeem guest link.",
      recordList: "Recordings list",
      recordListEmpty: "No recordings yet.",
      breakoutList: "Breakout rooms",
      breakoutListEmpty: "No breakout rooms.",
      sipTitle: "SIP / H.323 gateway",
      sipEnabled: "Enable SIP",
      sipUri: "Gateway URI",
      sipSave: "Save SIP",
      sipSaved: "SIP gateway saved.",
      sipFailed: "Could not save SIP.",
      stickerPacks: "Sticker packs",
      stickerSend: "Send sticker",
      captionsLive: "Transcript (live)",
    },
    message: { sticker: "Sticker" },
  },
};

const byCode = {
  ru: patchRu,
  en: patchEn,
  be: patchEn,
  kk: patchEn,
  zh: patchEn,
  ko: patchEn,
};

Object.keys(byCode).forEach(function (code) {
  const file = path.join(srcDir, code + ".json");
  const bundle = JSON.parse(fs.readFileSync(file, "utf8"));
  deepMerge(bundle, byCode[code]);
  fs.writeFileSync(file, JSON.stringify(bundle, null, 2) + "\n", "utf8");
  console.log("patched " + code);
});
