/**
 * IndexedDB read-through cache for recent messages per chat (offline scaffold, T02318).
 */
(function (global) {
  "use strict";

  var DB_NAME = "korus-offline";
  var DB_VERSION = 1;
  var STORE = "messages";
  var MAX_PER_CHAT = 50;

  function openDb() {
    return new Promise(function (resolve, reject) {
      var req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onerror = function () {
        reject(req.error);
      };
      req.onupgradeneeded = function (ev) {
        var db = ev.target.result;
        if (!db.objectStoreNames.contains(STORE)) {
          var os = db.createObjectStore(STORE, { keyPath: "chatId" });
          os.createIndex("updatedAt", "updatedAt");
        }
      };
      req.onsuccess = function () {
        resolve(req.result);
      };
    });
  }

  function trimMessages(messages) {
    if (!Array.isArray(messages)) return [];
    var sorted = messages.slice().sort(function (a, b) {
      return (a.created_at || 0) - (b.created_at || 0);
    });
    if (sorted.length <= MAX_PER_CHAT) return sorted;
    return sorted.slice(sorted.length - MAX_PER_CHAT);
  }

  function putMessages(chatId, messages) {
    if (!chatId || !global.indexedDB) return Promise.resolve();
    var trimmed = trimMessages(messages);
    return openDb()
      .then(function (db) {
        return new Promise(function (resolve, reject) {
          var tx = db.transaction(STORE, "readwrite");
          tx.objectStore(STORE).put({
            chatId: chatId,
            messages: trimmed,
            updatedAt: Date.now(),
          });
          tx.oncomplete = function () {
            db.close();
            resolve();
          };
          tx.onerror = function () {
            reject(tx.error);
          };
        });
      })
      .catch(function () {});
  }

  function appendMessage(chatId, message) {
    if (!chatId || !message || !message.id) return Promise.resolve();
    return getMessages(chatId).then(function (existing) {
      var list = Array.isArray(existing) ? existing : [];
      var next = list.filter(function (m) {
        return m.id !== message.id;
      });
      next.push(message);
      return putMessages(chatId, next);
    });
  }

  function getMessages(chatId) {
    if (!chatId || !global.indexedDB) return Promise.resolve(null);
    return openDb()
      .then(function (db) {
        return new Promise(function (resolve, reject) {
          var tx = db.transaction(STORE, "readonly");
          var req = tx.objectStore(STORE).get(chatId);
          req.onsuccess = function () {
            db.close();
            var row = req.result;
            resolve(row && row.messages ? row.messages : null);
          };
          req.onerror = function () {
            reject(req.error);
          };
        });
      })
      .catch(function () {
        return null;
      });
  }

  global.KorusOfflineCache = {
    MAX_PER_CHAT: MAX_PER_CHAT,
    putMessages: putMessages,
    appendMessage: appendMessage,
    getMessages: getMessages,
  };
})(typeof window !== "undefined" ? window : globalThis);
