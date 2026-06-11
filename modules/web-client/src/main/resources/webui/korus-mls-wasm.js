/**
 * Client-side MLS encrypt/decrypt (phase 3): Web Crypto AES-GCM + HKDF matching server MlsService.
 * Registers window.KorusMlsWasm when initialized via KorusMlsWasmFactory(apiJson).
 */
(function (global) {
  "use strict";

  var NONCE_SIZE = 12;
  var TAG_BITS = 128;

  function utf8(s) {
    return new TextEncoder().encode(String(s));
  }

  function bytesToBase64(bytes) {
    var bin = "";
    bytes.forEach(function (b) {
      bin += String.fromCharCode(b);
    });
    return btoa(bin);
  }

  function base64ToBytes(b64) {
    var bin = atob(String(b64).trim());
    var out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }

  async function hkdfDerive(seedBytes, infoStr, length) {
    var baseKey = await crypto.subtle.importKey("raw", seedBytes, "HKDF", false, ["deriveBits"]);
    var bits = await crypto.subtle.deriveBits(
      {
        name: "HKDF",
        hash: "SHA-256",
        salt: new Uint8Array(0),
        info: utf8(infoStr),
      },
      baseKey,
      length * 8
    );
    return new Uint8Array(bits);
  }

  async function deriveSessionKey(sessionId, chatId) {
    var seed = utf8(sessionId + ":" + chatId);
    return hkdfDerive(seed, "mls-session-key", 32);
  }

  function createKorusMlsWasm(apiJson) {
    var sessionCache = {};

    async function fetchSession(chatId) {
      if (sessionCache[chatId]) return sessionCache[chatId];
      var row = await apiJson("/e2ee/mls/session/" + encodeURIComponent(chatId), { method: "GET" });
      if (!row || !row.session_id) throw new Error("MLS session unavailable");
      sessionCache[chatId] = {
        sessionId: row.session_id,
        epoch: row.epoch || 0,
      };
      return sessionCache[chatId];
    }

    async function encrypt(plaintext, chatId) {
      var sess = await fetchSession(chatId);
      var keyBytes = await deriveSessionKey(sess.sessionId, chatId);
      var aad = utf8(chatId + ":" + sess.epoch);
      var iv = crypto.getRandomValues(new Uint8Array(NONCE_SIZE));
      var cryptoKey = await crypto.subtle.importKey("raw", keyBytes, "AES-GCM", false, ["encrypt"]);
      var enc = await crypto.subtle.encrypt(
        { name: "AES-GCM", iv: iv, additionalData: aad, tagLength: TAG_BITS },
        cryptoKey,
        utf8(plaintext)
      );
      var ct = new Uint8Array(enc);
      var combined = new Uint8Array(NONCE_SIZE + ct.length);
      combined.set(iv, 0);
      combined.set(ct, NONCE_SIZE);
      return bytesToBase64(combined);
    }

    async function decrypt(contentBase64, chatId) {
      var sess = await fetchSession(chatId);
      var keyBytes = await deriveSessionKey(sess.sessionId, chatId);
      var aad = utf8(chatId + ":" + sess.epoch);
      var combined = base64ToBytes(contentBase64);
      if (combined.length < NONCE_SIZE + 16) throw new Error("invalid ciphertext");
      var iv = combined.subarray(0, NONCE_SIZE);
      var ct = combined.subarray(NONCE_SIZE);
      var cryptoKey = await crypto.subtle.importKey("raw", keyBytes, "AES-GCM", false, ["decrypt"]);
      var plain = await crypto.subtle.decrypt(
        { name: "AES-GCM", iv: iv, additionalData: aad, tagLength: TAG_BITS },
        cryptoKey,
        ct
      );
      return new TextDecoder().decode(plain);
    }

    return {
      encrypt: encrypt,
      decrypt: decrypt,
      clearSessionCache: function (chatId) {
        if (chatId) delete sessionCache[chatId];
        else sessionCache = {};
      },
    };
  }

  global.KorusMlsWasmFactory = createKorusMlsWasm;
})(typeof window !== "undefined" ? window : globalThis);
