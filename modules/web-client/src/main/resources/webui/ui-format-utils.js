(function (global) {
  "use strict";

  function formatInstantLabel(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString();
    } catch (e) {
      return String(iso);
    }
  }

  function formatChatListTime(ms) {
    if (!ms) return "";
    var d = new Date(ms);
    if (isNaN(d.getTime())) return "";
    var now = Date.now();
    var diff = now - ms;
    if (diff < 60000) return "сейчас";
    if (diff < 3600000) {
      var mins = Math.floor(diff / 60000);
      return mins + " мин";
    }
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var day = new Date(d);
    day.setHours(0, 0, 0, 0);
    var yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    if (day.getTime() === today.getTime()) {
      return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }
    if (day.getTime() === yesterday.getTime()) return "вчера";
    if (now - ms < 7 * 86400000) {
      var wd = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"];
      return wd[d.getDay()];
    }
    return d.toLocaleDateString([], { day: "numeric", month: "short" });
  }

  function formatTtlLabel(seconds) {
    if (!seconds || seconds < 1) return "";
    if (seconds >= 86400) return "⏱ " + Math.round(seconds / 86400) + " д";
    if (seconds >= 3600) return "⏱ " + Math.round(seconds / 3600) + " ч";
    if (seconds >= 60) return "⏱ " + Math.round(seconds / 60) + " мин";
    return "⏱ " + seconds + " с";
  }

  global.KorusUiFormatUtils = {
    formatInstantLabel: formatInstantLabel,
    formatChatListTime: formatChatListTime,
    formatTtlLabel: formatTtlLabel,
  };
})(typeof window !== "undefined" ? window : globalThis);
