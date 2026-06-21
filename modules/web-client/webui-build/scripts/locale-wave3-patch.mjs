#!/usr/bin/env node
/** Wave 3: +66 UI keys into all locale sources (ru ref). */
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
  push: {
    title: "Korus Messenger",
    body: "Новое сообщение",
  },
  ui: {
    shell: {
      updateReady: "Доступна новая версия приложения",
      reloadNow: "Обновить",
      reloadLater: "Позже",
    },
    sidebar: {
      pinChat: "Закрепить чат",
      unpinChat: "Открепить чат",
    },
    search: {
      globalTitle: "Поиск",
      filterAll: "Везде",
      filterChats: "Чаты",
      filterMessages: "Сообщения",
      noResults: "Ничего не найдено.",
    },
    profile: {
      bioHint: "Кратко о себе — видно контактам",
      avatarChange: "Сменить аватар",
    },
    settings: {
      scheduledTitle: "Отложенные сообщения",
      notificationPush: "Push-уведомления",
      dataUsage: "Использование данных",
    },
    mobile: {
      back: "Назад к списку",
      chatsTitle: "Чаты",
    },
    marketplace: {
      open: "Открыть",
      installed: "Подключено",
      categoryBots: "Боты",
      categoryStorage: "Хранилище",
      categoryCalendar: "Календарь",
      categoryHr: "HR / заявки",
      noResults: "Интеграции не найдены.",
    },
    federation: {
      directoryHint: "Доверенные организации для кросс-org чатов (MVP).",
      trustActive: "Активно",
      trustPending: "Ожидает",
      partnerLabel: "Партнёр",
    },
    polls: {
      close: "Закрыть опрос",
      closeFailed: "Не удалось закрыть опрос.",
      closedByYou: "Опрос закрыт.",
      refreshResults: "Обновить результаты",
    },
    reminders: {
      cancel: "Отменить",
      openChat: "Открыть чат",
      emptyList: "Нет ожидающих напоминаний.",
      cancelled: "Напоминание отменено.",
      cancelFailed: "Не удалось отменить напоминание.",
    },
    schedule: {
      listTitle: "Отложенные сообщения",
      cancel: "Отменить отправку",
      cancelFailed: "Не удалось отменить отложенное сообщение.",
      emptyList: "Нет отложенных сообщений.",
      rowPreview: "{chat} · {when}",
    },
    offline: {
      syncPending: "Синхронизация при восстановлении сети…",
      syncDone: "Кэш обновлён",
      clearCache: "Очистить офлайн-кэш",
      clearCacheOk: "Офлайн-кэш очищен.",
      reconnecting: "Переподключение…",
      quotaHint: "Кэш хранит последние сообщения выбранных чатов.",
    },
    whiteboard: {
      toolPen: "Карандаш",
      toolRect: "Прямоугольник",
      toolEraser: "Ластик",
      clearCanvas: "Очистить",
      undo: "Отменить",
      canvasHint: "Рисуйте на полотне — сохранится в snapshot JSON.",
      tabCanvas: "Полотно",
      tabJson: "JSON",
    },
    phase5: {
      guestCopy: "Копировать ссылку",
      guestCopied: "Гостевая ссылка скопирована.",
      guestWaiting: "Комната ожидания включена",
      breakoutJoin: "Войти",
      stickerPackCreate: "Создать пак",
      stickerPackName: "Название пака",
      stickerPackCreated: "Пак стикеров создан.",
      stickerPackFailed: "Не удалось создать пак.",
      recordStop: "Остановить запись",
      modalCopy: "Копировать",
    },
  },
};

const patchEn = {
  push: {
    title: "Korus Messenger",
    body: "New message",
  },
  ui: {
    shell: {
      updateReady: "A new version is available",
      reloadNow: "Reload",
      reloadLater: "Later",
    },
    sidebar: {
      pinChat: "Pin chat",
      unpinChat: "Unpin chat",
    },
    search: {
      globalTitle: "Search",
      filterAll: "All",
      filterChats: "Chats",
      filterMessages: "Messages",
      noResults: "No results.",
    },
    profile: {
      bioHint: "Short bio visible to contacts",
      avatarChange: "Change avatar",
    },
    settings: {
      scheduledTitle: "Scheduled messages",
      notificationPush: "Push notifications",
      dataUsage: "Data usage",
    },
    mobile: {
      back: "Back to list",
      chatsTitle: "Chats",
    },
    marketplace: {
      open: "Open",
      installed: "Connected",
      categoryBots: "Bots",
      categoryStorage: "Storage",
      categoryCalendar: "Calendar",
      categoryHr: "HR / requests",
      noResults: "No integrations found.",
    },
    federation: {
      directoryHint: "Trusted orgs for cross-org chats (MVP).",
      trustActive: "Active",
      trustPending: "Pending",
      partnerLabel: "Partner",
    },
    polls: {
      close: "Close poll",
      closeFailed: "Could not close poll.",
      closedByYou: "Poll closed.",
      refreshResults: "Refresh results",
    },
    reminders: {
      cancel: "Cancel",
      openChat: "Open chat",
      emptyList: "No pending reminders.",
      cancelled: "Reminder cancelled.",
      cancelFailed: "Could not cancel reminder.",
    },
    schedule: {
      listTitle: "Scheduled messages",
      cancel: "Cancel send",
      cancelFailed: "Could not cancel scheduled message.",
      emptyList: "No scheduled messages.",
      rowPreview: "{chat} · {when}",
    },
    offline: {
      syncPending: "Sync when back online…",
      syncDone: "Cache updated",
      clearCache: "Clear offline cache",
      clearCacheOk: "Offline cache cleared.",
      reconnecting: "Reconnecting…",
      quotaHint: "Cache keeps recent messages for selected chats.",
    },
    whiteboard: {
      toolPen: "Pen",
      toolRect: "Rectangle",
      toolEraser: "Eraser",
      clearCanvas: "Clear",
      undo: "Undo",
      canvasHint: "Draw on canvas — saved in snapshot JSON.",
      tabCanvas: "Canvas",
      tabJson: "JSON",
    },
    phase5: {
      guestCopy: "Copy link",
      guestCopied: "Guest link copied.",
      guestWaiting: "Waiting room enabled",
      breakoutJoin: "Join",
      stickerPackCreate: "Create pack",
      stickerPackName: "Pack name",
      stickerPackCreated: "Sticker pack created.",
      stickerPackFailed: "Could not create pack.",
      recordStop: "Stop recording",
      modalCopy: "Copy",
    },
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
