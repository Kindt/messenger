/**
 * Lazy classic-script loader for call modules (FR-121).
 * app.js uses dynamic import() on this ES module; IIFE scripts stay unchanged.
 */
const pending = Object.create(null);

function loadClassicScript(src, globalName) {
  if (globalThis[globalName]) {
    return Promise.resolve(globalThis[globalName]);
  }
  if (pending[src]) {
    return pending[src];
  }
  pending[src] = new Promise(function (resolve, reject) {
    const s = document.createElement("script");
    s.src = src;
    s.async = true;
    s.onload = function () {
      const mod = globalThis[globalName];
      if (mod) {
        resolve(mod);
        return;
      }
      reject(new Error("Global " + globalName + " missing after " + src));
    };
    s.onerror = function () {
      reject(new Error("Failed to load " + src));
    };
    document.head.appendChild(s);
  });
  return pending[src];
}

export function loadCallMesh() {
  return loadClassicScript("/ui-call-mesh.js", "KorusUiCallMesh");
}

export function loadCallMeshRecord() {
  return loadClassicScript("/ui-call-mesh-record.js", "KorusUiCallMeshRecord");
}

export function loadCallLivekit() {
  return loadClassicScript("/ui-call-livekit.js", "KorusUiCallLivekit");
}

export function loadCallPc() {
  return loadClassicScript("/ui-call-pc.js", "KorusUiCallPc");
}
