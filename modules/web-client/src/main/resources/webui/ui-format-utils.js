(function (global) {
  "use strict";

  function t(key, params) {
    var i18n = global.KorusI18n;
    if (i18n && i18n.t) return i18n.t(key, params);
    return key;
  }

  function localeTag() {
    var i18n = global.KorusI18n;
    var code = i18n && i18n.getLocale ? i18n.getLocale() : "ru";
    if (code === "en") return "en-US";
    if (code === "be") return "be-BY";
    if (code === "kk") return "kk-KZ";
    if (code === "zh") return "zh-CN";
    if (code === "ko") return "ko-KR";
    return "ru-RU";
  }

  function formatInstantLabel(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString(localeTag());
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
    if (diff < 60000) return t("time.now");
    if (diff < 3600000) {
      var mins = Math.floor(diff / 60000);
      return t("time.minutesAgo", { n: mins });
    }
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var day = new Date(d);
    day.setHours(0, 0, 0, 0);
    var yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    if (day.getTime() === today.getTime()) {
      return d.toLocaleTimeString(localeTag(), { hour: "2-digit", minute: "2-digit" });
    }
    if (day.getTime() === yesterday.getTime()) return t("time.yesterday");
    if (now - ms < 7 * 86400000) {
      return d.toLocaleDateString(localeTag(), { weekday: "short" });
    }
    return d.toLocaleDateString(localeTag(), { day: "numeric", month: "short" });
  }

  function formatTtlLabel(seconds) {
    if (!seconds || seconds < 1) return "";
    var prefix = t("time.ttlPrefix");
    if (seconds >= 86400) return prefix + t("time.daysShort", { n: Math.round(seconds / 86400) });
    if (seconds >= 3600) return prefix + t("time.hoursShort", { n: Math.round(seconds / 3600) });
    if (seconds >= 60) return prefix + t("time.minutesShort", { n: Math.round(seconds / 60) });
    return prefix + t("time.secondsShort", { n: seconds });
  }

  function formatTimeLeft(secondsLeft) {
    if (secondsLeft == null || secondsLeft <= 0) return t("time.expired");
    var s = Math.floor(secondsLeft);
    if (s >= 86400) return t("time.daysShort", { n: Math.ceil(s / 86400) });
    if (s >= 3600) return t("time.hoursShort", { n: Math.ceil(s / 3600) });
    if (s >= 60) return t("time.minutesShort", { n: Math.ceil(s / 60) });
    return t("time.secondsShort", { n: s });
  }

  global.KorusUiFormatUtils = {
    formatInstantLabel: formatInstantLabel,
    formatChatListTime: formatChatListTime,
    formatTtlLabel: formatTtlLabel,
    formatTimeLeft: formatTimeLeft,
  };
})(typeof window !== "undefined" ? window : globalThis);
