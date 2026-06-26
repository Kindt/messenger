import fs from "node:fs/promises";
import path from "node:path";
import { expect, Page, TestInfo } from "@playwright/test";
import { expectNoHorizontalScroll } from "./mobile-ui";

export type UiAuditViewport = {
  name: "desktop" | "mobile" | string;
  size: { width: number; height: number };
};

type UiAuditCandidate = {
  auditId: string;
  action: "click" | "fill" | "select" | "skip";
  description: string;
  testId: string;
  inputType: string;
  text: string;
  dangerous: boolean;
  reason?: string;
};

type UiAuditReportEntry = {
  surface: string;
  action: string;
  description: string;
  status: "ok" | "skipped" | "stale" | "failed";
  reason?: string;
};

type UiAuditDiskArtifact = {
  surface: string;
  screenshot: string;
  report: string;
  actions: number;
  failed: number;
  skipped: number;
  stale: number;
};

export type UiAuditSurfaceOptions = {
  surface: string;
  rootSelector: string;
  requiredSelectors?: string[];
  maxActions?: number;
  /** Stop auditing this surface after N ms even if maxActions not reached (avoids WebRTC hangs). */
  maxWallClockMs?: number;
  denyPatterns?: RegExp[];
};

export type UiAuditErrorCollector = {
  errors: string[];
  expectNoCollectedErrors: (surface: string) => void;
};

const DEFAULT_DANGEROUS_RE =
  /(delete|remove|purge|revoke|destroy|wipe|drop|ban|block|unblock|logout|sign\s*out|end\s*call|retention|legal\s*hold|cancel\s+(?:scheduled|reminder)|удал|очист|отозв|заблок|выйти|заверш|ретенц|legal hold)/i;

const HARMLESS_CONSOLE_RE =
  /Failed to load resource: the server responded with a status of \d+|favicon|ResizeObserver loop limit exceeded/i;
const ARTIFACT_RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACT_ROOT = process.env.UI_AUDIT_SCREENSHOT_DIR
  ? path.resolve(process.env.UI_AUDIT_SCREENSHOT_DIR)
  : path.join(process.cwd(), "artifacts", "ui-interaction-audit", ARTIFACT_RUN_ID);
const diskArtifacts: UiAuditDiskArtifact[] = [];

export function attachUiAuditErrorCollector(page: Page): UiAuditErrorCollector {
  const errors: string[] = [];
  page.on("pageerror", (err) => {
    errors.push(`pageerror: ${err.message}`);
  });
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    if (HARMLESS_CONSOLE_RE.test(text)) return;
    errors.push(`console.error: ${text}`);
  });
  page.on("dialog", (dialog) => {
    dialog.dismiss().catch(() => {});
  });
  return {
    errors,
    expectNoCollectedErrors(surface: string) {
      expect(errors, `${surface} collected console/page errors:\n${errors.join("\n")}`).toEqual([]);
    },
  };
}

export async function mockAuditMediaDevices(page: Page): Promise<void> {
  await page.addInitScript(() => {
    if (!navigator.mediaDevices) {
      Object.defineProperty(navigator, "mediaDevices", {
        configurable: true,
        value: {},
      });
    }
    const audioOnlyStream = () => {
      const ctx = new AudioContext();
      const dest = ctx.createMediaStreamDestination();
      const osc = ctx.createOscillator();
      osc.frequency.value = 440;
      osc.connect(dest);
      osc.start();
      return dest.stream;
    };
    const videoStream = () => {
      const canvas = document.createElement("canvas");
      canvas.width = 32;
      canvas.height = 24;
      const cx = canvas.getContext("2d");
      if (cx) {
        cx.fillStyle = "#5b2ee5";
        cx.fillRect(0, 0, canvas.width, canvas.height);
      }
      return canvas.captureStream(5);
    };
    const displayStream = () => {
      const stream = videoStream();
      const track = stream.getVideoTracks()[0];
      Object.defineProperty(track, "label", { configurable: true, value: "ui-audit-screen-share" });
      const original = track.getSettings ? track.getSettings.bind(track) : () => ({});
      track.getSettings = () => ({ ...original(), displaySurface: "monitor" });
      return stream;
    };
    Object.defineProperty(navigator.mediaDevices, "getUserMedia", {
      configurable: true,
      value: async (constraints: MediaStreamConstraints) => {
        const wantVideo = !!constraints.video;
        const wantAudio = !!constraints.audio;
        if (wantVideo && !wantAudio) return videoStream();
        if (wantAudio && !wantVideo) return audioOnlyStream();
        const v = videoStream();
        audioOnlyStream().getAudioTracks().forEach((t) => v.addTrack(t));
        return v;
      },
    });
    Object.defineProperty(navigator.mediaDevices, "getDisplayMedia", {
      configurable: true,
      value: async () => displayStream(),
    });
  });
}

export async function auditInteractiveSurface(
  page: Page,
  testInfo: TestInfo,
  options: UiAuditSurfaceOptions
): Promise<void> {
  await assertSurfaceHealthy(page, options);
  const report: UiAuditReportEntry[] = [];
  const candidates = await collectInteractiveCandidates(page, options);
  const maxActions = options.maxActions ?? 80;
  const deadline = Date.now() + (options.maxWallClockMs ?? 120_000);

  for (const candidate of candidates.slice(0, maxActions)) {
    if (Date.now() > deadline) {
      report.push({
        surface: options.surface,
        action: candidate.action,
        description: candidate.description,
        status: "skipped",
        reason: "wall-clock budget exceeded",
      });
      break;
    }
    if (candidate.dangerous || candidate.action === "skip") {
      report.push({
        surface: options.surface,
        action: candidate.action,
        description: candidate.description,
        status: "skipped",
        reason: candidate.reason || "dangerous-or-unsupported",
      });
      continue;
    }

    const locator = page.locator(`[data-ui-audit-id="${candidate.auditId}"]`).first();
    if (!(await locator.isVisible().catch(() => false))) {
      report.push({
        surface: options.surface,
        action: candidate.action,
        description: candidate.description,
        status: "stale",
        reason: "element disappeared before action",
      });
      continue;
    }

    try {
      await locator.scrollIntoViewIfNeeded({ timeout: 2_000 });
      if (candidate.action === "fill") {
        await locator.focus({ timeout: 5_000 });
        await locator.fill(auditFillValue(candidate), { timeout: 5_000 });
      } else if (candidate.action === "select") {
        await selectFirstNonCurrentOption(page, candidate.auditId);
      } else {
        await locator.click({ timeout: 5_000 });
      }
      await page.waitForTimeout(50);
      await assertSurfaceHealthy(page, options);
      report.push({
        surface: options.surface,
        action: candidate.action,
        description: candidate.description,
        status: "ok",
      });
    } catch (err) {
      if (isStaleElementError(err)) {
        report.push({
          surface: options.surface,
          action: candidate.action,
          description: candidate.description,
          status: "stale",
          reason: err instanceof Error ? err.message : String(err),
        });
        continue;
      }
      report.push({
        surface: options.surface,
        action: candidate.action,
        description: candidate.description,
        status: "failed",
        reason: err instanceof Error ? err.message : String(err),
      });
      await attachAuditArtifacts(page, testInfo, options.surface, report);
      throw err;
    }
  }

  await attachAuditArtifacts(page, testInfo, options.surface, report);
}

async function collectInteractiveCandidates(
  page: Page,
  options: UiAuditSurfaceOptions
): Promise<UiAuditCandidate[]> {
  return page.evaluate(
    ({ rootSelector, denySources, defaultDangerous }) => {
      const root = document.querySelector(rootSelector) || document.body;
      const deny = denySources.map((src) => new RegExp(src, "i"));
      const dangerousRe = new RegExp(defaultDangerous, "i");
      const nodes = Array.from(
        root.querySelectorAll(
          [
            "button",
            "a[href]",
            "input",
            "textarea",
            "select",
            "[role=button]",
            "[role=link]",
            "[contenteditable=true]",
            "[data-testid]",
          ].join(",")
        )
      );
      let seq = 0;
      return nodes
        .filter((el) => isCandidateVisible(el))
        .filter((el) => !el.classList.contains("chat-item"))
        .filter((el) => !el.closest("aside, [role=complementary], .sidebar, .chat-list"))
        .map((el) => {
          const html = el as HTMLElement;
          const auditId = `ui-audit-${Date.now()}-${seq++}`;
          html.setAttribute("data-ui-audit-id", auditId);
          const tag = el.tagName.toLowerCase();
          const testId = html.getAttribute("data-testid") || "";
          const role = html.getAttribute("role") || "";
          const type = (html.getAttribute("type") || "").toLowerCase();
          const href = html instanceof HTMLAnchorElement ? html.href : "";
          const label = [
            testId,
            html.getAttribute("aria-label") || "",
            html.getAttribute("title") || "",
            html.innerText || "",
            html.getAttribute("placeholder") || "",
            href,
          ]
            .join(" ")
            .replace(/\s+/g, " ")
            .trim();
          const description = describeElement(html, tag, role, type, label);
          let action: UiAuditCandidate["action"] = "click";
          let reason = "";
          if (
            html instanceof HTMLInputElement ||
            html instanceof HTMLTextAreaElement ||
            html.isContentEditable
          ) {
            if (
              type === "file" ||
              type === "hidden" ||
              type === "submit" ||
              type === "button" ||
              type === "checkbox" ||
              type === "radio" ||
              (html as HTMLInputElement | HTMLTextAreaElement).readOnly
            ) {
              action = type === "checkbox" || type === "radio" ? "click" : "skip";
              reason = action === "skip" ? `unsupported input type=${type || tag}` : "";
            } else {
              action = "fill";
            }
          } else if (html instanceof HTMLSelectElement) {
            action = html.options.length > 1 ? "select" : "skip";
            reason = action === "skip" ? "select has fewer than two options" : "";
          }
          const externalLink =
            html instanceof HTMLAnchorElement &&
            (html.target === "_blank" ||
              html.hasAttribute("download") ||
              /^(mailto:|tel:|blob:|data:)/i.test(html.getAttribute("href") || "") ||
              (href && new URL(href).origin !== location.origin));
          if (externalLink) {
            action = "skip";
            reason = "external or download link";
          }
          const dangerous = dangerousRe.test(label) || deny.some((re) => re.test(label));
          return {
            auditId,
            action,
            description,
            testId,
            inputType: type,
            text: label.slice(0, 180),
            dangerous,
            reason: dangerous ? "dangerous action pattern" : reason,
          };
        });

      function isCandidateVisible(el: Element): boolean {
        const html = el as HTMLElement;
        if (html.hidden || html.hasAttribute("disabled") || html.getAttribute("aria-disabled") === "true") {
          return false;
        }
        const style = window.getComputedStyle(html);
        const box = html.getBoundingClientRect();
        return (
          style.display !== "none" &&
          style.visibility !== "hidden" &&
          style.pointerEvents !== "none" &&
          box.width > 0 &&
          box.height > 0
        );
      }

      function describeElement(
        html: HTMLElement,
        tag: string,
        role: string,
        type: string,
        label: string
      ): string {
        const id = html.id ? `#${html.id}` : "";
        const testId = html.getAttribute("data-testid")
          ? `[data-testid=${html.getAttribute("data-testid")}]`
          : "";
        const rolePart = role ? `[role=${role}]` : "";
        const typePart = type ? `[type=${type}]` : "";
        return `${tag}${id}${testId}${rolePart}${typePart} ${label.slice(0, 120)}`.trim();
      }
    },
    {
      rootSelector: options.rootSelector,
      denySources: (options.denyPatterns || []).map((re) => re.source),
      defaultDangerous: DEFAULT_DANGEROUS_RE.source,
    }
  );
}

function auditFillValue(candidate: UiAuditCandidate): string {
  switch (candidate.inputType) {
    case "number":
    case "range":
      return "7";
    case "date":
      return "2026-06-22";
    case "datetime-local":
      return "2026-06-22T12:00";
    case "month":
      return "2026-06";
    case "time":
      return "12:00";
    case "week":
      return "2026-W26";
    case "email":
      return `ui-audit-${Date.now()}@example.test`;
    case "url":
      return "https://example.test/ui-audit";
    case "tel":
      return "+10000000000";
    default:
      return `ui-audit-${Date.now()}`;
  }
}

function isStaleElementError(err: unknown): boolean {
  const message = err instanceof Error ? err.message : String(err);
  return /not attached to the DOM|Element is not attached|Execution context was destroyed|detached from the DOM/i.test(
    message
  );
}

async function selectFirstNonCurrentOption(page: Page, auditId: string): Promise<void> {
  const value = await page.evaluate((id) => {
    const select = document.querySelector(`[data-ui-audit-id="${id}"]`) as HTMLSelectElement | null;
    if (!select) return null;
    const option = Array.from(select.options).find((o) => !o.disabled && o.value !== select.value);
    return option ? option.value : null;
  }, auditId);
  if (value == null) return;
  await page.locator(`[data-ui-audit-id="${auditId}"]`).selectOption(value, { timeout: 5_000 });
}

async function assertSurfaceHealthy(page: Page, options: UiAuditSurfaceOptions): Promise<void> {
  await expect(page.locator("body")).toBeVisible({ timeout: 10_000 });
  const healthRoot = options.rootSelector === "body" ? "body" : options.rootSelector;
  await expect
    .poll(
      async () =>
        page.evaluate((sel) => {
          const root = document.querySelector(sel) || document.body;
          if (!root) return false;
          const visibleText = (root as HTMLElement).innerText.replace(/\s+/g, " ").trim();
          return (
            visibleText.length > 0 ||
            !!root.querySelector("button,input,textarea,select,a[href]")
          );
        }, healthRoot),
      { timeout: 10_000 }
    )
    .toBe(true);
  await expectNoHorizontalScroll(page);
  for (const selector of options.requiredSelectors || []) {
    const loc = page.locator(selector).first();
    await expect(loc, `${options.surface} required selector ${selector}`).toBeVisible({
      timeout: 10_000,
    });
    await expect
      .poll(
        async () => {
          const box = await loc.boundingBox();
          return !!box && box.width > 0 && box.height > 0;
        },
        { timeout: 10_000 }
      )
      .toBe(true);
  }
}

async function attachAuditArtifacts(
  page: Page,
  testInfo: TestInfo,
  surface: string,
  report: UiAuditReportEntry[]
): Promise<void> {
  const safeName = surface.replace(/[^a-z0-9_-]+/gi, "-").slice(0, 80);
  const diskArtifact = await writeAuditDiskArtifacts(page, safeName, surface, report);
  await testInfo.attach(`${safeName}-audit-report.json`, {
    body: JSON.stringify(report, null, 2),
    contentType: "application/json",
  });
  await testInfo.attach(`${safeName}-screenshot-path.txt`, {
    body: diskArtifact.screenshot,
    contentType: "text/plain",
  });
  if (report.some((entry) => entry.status === "failed")) {
    await testInfo.attach(`${safeName}-failure.png`, {
      body: await page.screenshot({ fullPage: false }),
      contentType: "image/png",
    });
  }
}

async function writeAuditDiskArtifacts(
  page: Page,
  safeName: string,
  surface: string,
  report: UiAuditReportEntry[]
): Promise<UiAuditDiskArtifact> {
  await fs.mkdir(ARTIFACT_ROOT, { recursive: true });
  const screenshot = path.join(ARTIFACT_ROOT, `${safeName}.png`);
  const reportPath = path.join(ARTIFACT_ROOT, `${safeName}.json`);
  // Viewport capture — fullPage on long chat lists exceeds tier timeout (240s).
  await page.screenshot({ path: screenshot, fullPage: false });
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  const artifact: UiAuditDiskArtifact = {
    surface,
    screenshot,
    report: reportPath,
    actions: report.length,
    failed: report.filter((entry) => entry.status === "failed").length,
    skipped: report.filter((entry) => entry.status === "skipped").length,
    stale: report.filter((entry) => entry.status === "stale").length,
  };
  diskArtifacts.push(artifact);
  await fs.writeFile(
    path.join(ARTIFACT_ROOT, "index.json"),
    JSON.stringify(
      {
        artifactRoot: ARTIFACT_ROOT,
        runId: ARTIFACT_RUN_ID,
        generatedAt: new Date().toISOString(),
        artifacts: diskArtifacts,
      },
      null,
      2
    ),
    "utf8"
  );
  return artifact;
}
