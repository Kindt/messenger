import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const dir = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(
  join(dir, "../../src/main/resources/webui/ui-device-profile.js"),
  "utf8"
);

function loadProfile(ua, platform, maxTouchPoints, portrait, userAgentData) {
  const sandbox = {
    window: {
      navigator: { userAgent: ua, platform, maxTouchPoints, userAgentData: userAgentData || null },
      matchMedia(q) {
        return { matches: portrait === true && String(q).includes("portrait") };
      },
      addEventListener() {},
    },
    screen: { orientation: { type: portrait ? "portrait-primary" : "landscape-primary" } },
    document: {
      documentElement: {
        attributes: {},
        setAttribute(name, value) {
          this.attributes[name] = value;
        },
      },
    },
  };
  sandbox.globalThis = sandbox.window;
  vm.runInNewContext(src, sandbox);
  return sandbox.window.KorusUiDeviceProfile;
}

const iphone = loadProfile(
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
  "iPhone",
  5,
  true
);
if (iphone.detectDevice("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", "iPhone", 5) !== "phone") {
  throw new Error("iPhone should be phone");
}
if (!iphone.computeMobileShell("phone", "landscape")) {
  throw new Error("phone must use mobile shell in any orientation");
}

const ipadPortrait = loadProfile(
  "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)",
  "iPad",
  5,
  true
);
if (ipadPortrait.detectDevice("Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)", "iPad", 5) !== "tablet") {
  throw new Error("iPad should be tablet");
}
if (!ipadPortrait.computeMobileShell("tablet", "portrait")) {
  throw new Error("tablet portrait must use mobile shell");
}
if (ipadPortrait.computeMobileShell("tablet", "landscape")) {
  throw new Error("tablet landscape must not use mobile shell");
}

const ipadOs = loadProfile(
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
  "MacIntel",
  5,
  true
);
if (ipadOs.detectDevice("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", "MacIntel", 5) !== "tablet") {
  throw new Error("iPadOS MacIntel UA should be tablet");
}

const androidPhoneUa =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
if (loadProfile(androidPhoneUa, "Linux armv8l", 5, true).detectDevice(androidPhoneUa, "Linux armv8l", 5) !== "phone") {
  throw new Error("Android phone UA should be phone");
}

const androidTabletUa =
  "Mozilla/5.0 (Linux; Android 14; SM-X900) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
if (loadProfile(androidTabletUa, "Linux armv8l", 5, true).detectDevice(androidTabletUa, "Linux armv8l", 5) !== "tablet") {
  throw new Error("Android tablet UA should be tablet");
}
if (!loadProfile(androidTabletUa, "Linux armv8l", 5, true).computeMobileShell("tablet", "portrait")) {
  throw new Error("Android tablet portrait must use mobile shell");
}

const androidClientHints = loadProfile(androidPhoneUa, "Linux armv8l", 5, true, { mobile: true });
if (androidClientHints.detectDevice(androidPhoneUa, "Linux armv8l", 5, { mobile: true }) !== "phone") {
  throw new Error("Android Client Hints mobile=true should be phone");
}

const desktop = loadProfile(
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0",
  "Win32",
  0,
  false
);
if (desktop.detectDevice("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "Win32", 0) !== "desktop") {
  throw new Error("desktop Chrome should stay desktop");
}
if (desktop.computeMobileShell("desktop", "portrait")) {
  throw new Error("desktop must never use mobile shell");
}

const applied = desktop.applyProfile(
  { documentElement: { attributes: {}, setAttribute(n, v) { this.attributes[n] = v; } } },
  { device: "phone", orientation: "portrait", mobileShell: true }
);
if (applied.mobileShell !== true) throw new Error("applyProfile failed");

console.log("test-ui-device-profile: OK");
