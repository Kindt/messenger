/**
 * Spec 020 Phase 0: OpenMLS dev factory stub.
 * When e2ee_openmls_dev=1, app.js prefers this over KorusMlsWasmFactory.
 * Phase 0 delegates encrypt/decrypt to hybrid Web Crypto until real OpenMLS WASM ships.
 */
(function (global) {
  "use strict";

  function createKorusOpenMlsDev(apiJson) {
    var hybrid = global.KorusMlsWasmFactory ? global.KorusMlsWasmFactory(apiJson) : null;
    if (!hybrid) {
      throw new Error("OpenMLS dev stub requires KorusMlsWasmFactory");
    }

    return {
      mode: "openmls-dev-stub",
      cipherSuite: "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
      encrypt: function (plaintext, chatId) {
        return hybrid.encrypt(plaintext, chatId);
      },
      decrypt: function (contentBase64, chatId) {
        return hybrid.decrypt(contentBase64, chatId);
      },
      clearSessionCache: function (chatId) {
        if (hybrid.clearSessionCache) hybrid.clearSessionCache(chatId);
      },
      ping: function () {
        return Promise.resolve({ ok: true, mode: "openmls-dev-stub" });
      },
    };
  }

  global.KorusOpenMlsDevFactory = createKorusOpenMlsDev;
})(typeof window !== "undefined" ? window : globalThis);
