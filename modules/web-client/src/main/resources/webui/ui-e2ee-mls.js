(function (global) {
  "use strict";

  function createUiE2eeMls(deps) {
    var apiJson = deps.apiJson;
    var getState = deps.getState;
    var getMlsWasm = deps.getMlsWasm;

    function mlsBytesToBase64(bytes) {
      var bin = "";
      bytes.forEach(function (b) {
        bin += String.fromCharCode(b);
      });
      return btoa(bin);
    }

    function isMlsCapabilitiesActive() {
      var state = getState();
      var caps = state && state.mediaCaps;
      return !!(caps && caps.mls_status === "active");
    }

    function mlsGenerateKeyPackage() {
      var pk = new Uint8Array(32);
      var sk = new Uint8Array(32);
      crypto.getRandomValues(pk);
      crypto.getRandomValues(sk);
      return {
        cipher_suite: "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
        protocol_version: "mls10",
        uploadPayload: {
          public_key_base64: mlsBytesToBase64(pk),
          signature_key_base64: mlsBytesToBase64(sk),
          cipher_suite: "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
          protocol_version: "mls10",
        },
      };
    }

    var mlsClientKeyPackage = null;

    async function mlsEnsureKeyPackage() {
      if (mlsClientKeyPackage) return mlsClientKeyPackage;
      mlsClientKeyPackage = mlsGenerateKeyPackage();
      var state = getState();
      if (!state || !state.tokens) return mlsClientKeyPackage;
      try {
        await apiJson("/e2ee/key-packages", {
          method: "POST",
          jsonBody: mlsClientKeyPackage.uploadPayload,
        });
      } catch (e) {}
      return mlsClientKeyPackage;
    }

    async function mlsClientEncrypt(plaintext, chatId) {
      if (!isMlsCapabilitiesActive() || plaintext == null) return null;
      var wasm = getMlsWasm();
      if (wasm && typeof wasm.encrypt === "function") {
        try {
          return await wasm.encrypt(String(plaintext), chatId);
        } catch (e) {
          throw new Error("MLS client encrypt failed");
        }
      }
      return null;
    }

    async function mlsClientDecrypt(contentBase64, chatId) {
      if (!isMlsCapabilitiesActive() || !contentBase64) return null;
      var wasm = getMlsWasm();
      if (wasm && typeof wasm.decrypt === "function") {
        try {
          return await wasm.decrypt(String(contentBase64), chatId);
        } catch (e) {
          return null;
        }
      }
      return null;
    }

    return {
      isMlsCapabilitiesActive: isMlsCapabilitiesActive,
      mlsEnsureKeyPackage: mlsEnsureKeyPackage,
      mlsClientEncrypt: mlsClientEncrypt,
      mlsClientDecrypt: mlsClientDecrypt,
      mlsBytesToBase64: mlsBytesToBase64,
    };
  }

  global.KorusUiE2eeMls = createUiE2eeMls;
})(typeof window !== "undefined" ? window : globalThis);
