import { expect, Page, test } from "@playwright/test";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { adminBaseUrl, adminNavTo, adminUiLogin } from "../fixtures/admin-ui";
import { uiLogin, uiOpenChatByTitle, uiSendMessage } from "../fixtures/ui";

type UiArtifact = {
  source: string;
  value: string;
  reason: string;
};

const SUPPORTED_LOCALES = ["ru", "en", "be", "kk", "zh", "ko"];
const ADMIN_LOCALE_EXPECTATIONS: Record<string, { lang: string; login: string }> = {
  ru: { lang: "ru", login: "Войти" },
  en: { lang: "en", login: "Sign in" },
  be: { lang: "be", login: "Увайсці" },
  kk: { lang: "kk", login: "Кіру" },
  zh: { lang: "zh-Hans", login: "登录" },
  ko: { lang: "ko", login: "로그인" },
};

const RAW_KEY_RE =
  /\b(?:auth|settings|ws|conference|chat|messages|message|ui|errors|media|rtc|files|export|profile|contacts|search|readReceipts|notifications|saved|admin|plugins|retention|audit|live|e2ee)(?:\.[A-Za-z0-9_-]+){1,}\b/;
const MOJIBAKE_RE = /(?:�|Ð|Ñ|Рџ|Рђ|Рµ|Рё|Рѕ|РЅ|Р°|Р»|Рє|РЎ|СЃ|С‚|СЂ|СЊ)/;
const UNINTERPOLATED_RE = /\{[A-Za-z_][A-Za-z0-9_]*\}/;

async function collectArtifacts(page: Page): Promise<UiArtifact[]> {
  return page.evaluate(
    ({ rawKey, mojibake, uninterpolated }) => {
      const rawKeyRe = new RegExp(rawKey);
      const mojibakeRe = new RegExp(mojibake);
      const uninterpolatedRe = new RegExp(uninterpolated);
      const out: UiArtifact[] = [];
      const hidden = (el: Element) => {
        const html = el as HTMLElement;
        const style = window.getComputedStyle(html);
        const box = html.getBoundingClientRect();
        return (
          style.display === "none" ||
          style.visibility === "hidden" ||
          html.hidden ||
          box.width === 0 ||
          box.height === 0
        );
      };
      const check = (source: string, value: unknown) => {
        const text = String(value || "").replace(/\s+/g, " ").trim();
        if (!text) return;
        let reason = "";
        if (rawKeyRe.test(text)) reason = "raw-i18n-key";
        else if (mojibakeRe.test(text)) reason = "mojibake";
        else if (uninterpolatedRe.test(text)) reason = "uninterpolated-placeholder";
        if (reason === "uninterpolated-placeholder" && /\/[^\s]*\{[A-Za-z_][A-Za-z0-9_]*\}/.test(text)) {
          return;
        }
        if (reason) out.push({ source, value: text.slice(0, 220), reason });
      };

      document
        .querySelectorAll("body *")
        .forEach((el) => {
          if (hidden(el)) return;
          const html = el as HTMLElement;
          if (html.childElementCount === 0) check(describe(el, "text"), html.innerText);
          ["aria-label", "title", "placeholder", "alt"].forEach((attr) => {
            check(describe(el, attr), el.getAttribute(attr));
          });
          if (
            el instanceof HTMLInputElement ||
            el instanceof HTMLTextAreaElement ||
            el instanceof HTMLButtonElement
          ) {
            check(describe(el, "value"), el.value);
          }
        });

      function describe(el: Element, part: string): string {
        const testId = el.getAttribute("data-testid");
        const id = el.getAttribute("id");
        const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 3).join(".");
        return [
          el.tagName.toLowerCase(),
          testId ? `[data-testid=${testId}]` : "",
          id ? `#${id}` : "",
          cls ? `.${cls}` : "",
          `:${part}`,
        ].join("");
      }

      return out;
    },
    {
      rawKey: RAW_KEY_RE.source,
      mojibake: MOJIBAKE_RE.source,
      uninterpolated: UNINTERPOLATED_RE.source,
    }
  );
}

async function expectNoI18nArtifacts(page: Page, surface: string): Promise<void> {
  const artifacts = await collectArtifacts(page);
  expect(
    artifacts,
    `${surface} has visible i18n artifacts:\n${artifacts
      .slice(0, 12)
      .map((a) => `- ${a.reason} ${a.source}: ${a.value}`)
      .join("\n")}`
  ).toEqual([]);
}

test.describe("UI translation artifact audit", () => {
  test("web auth shell has no visible translation artifacts in every supported locale", async ({
    browser,
  }) => {
    for (const locale of SUPPORTED_LOCALES) {
      const context = await browser.newContext();
      await context.addInitScript((code) => {
        localStorage.setItem("korus_web_locale", code);
      }, locale);
      const page = await context.newPage();
      try {
        await page.goto("/");
        await expect(page.locator("[data-testid=auth-submit]")).toBeVisible({ timeout: 15_000 });
        await expectNoI18nArtifacts(page, `web auth locale=${locale}`);
      } finally {
        await context.close();
      }
    }
  });

  test("web authenticated messaging surfaces have no visible translation artifacts", async ({
    page,
    request,
  }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `i18n-artifacts-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await expectNoI18nArtifacts(page, "web after login");
    await uiOpenChatByTitle(page, title);
    await uiSendMessage(page, `i18n-smoke-${Date.now()}`);
    await expectNoI18nArtifacts(page, "web thread after send");
    await page.getByTestId("settings-toggle").click();
    await expect(page.locator(".settings-card")).toBeVisible({ timeout: 10_000 });
    await expectNoI18nArtifacts(page, "web settings modal");
  });

  test("admin login and key panels have no visible translation artifacts", async ({ page }) => {
    await page.goto(new URL("/admin/", adminBaseUrl()).href);
    await expect(page.locator("[data-testid=admin-login-btn]")).toBeVisible({ timeout: 10_000 });
    await expectNoI18nArtifacts(page, "admin login");

    await adminUiLogin(page, "csadmin", "csadmin");
    for (const sectionId of ["core-fleet-stats", "core-retention", "core-legal-hold", "core-directory-sync"]) {
      await adminNavTo(page, sectionId);
      await expect(page.locator("[data-testid=admin-panel]")).toBeVisible({ timeout: 15_000 });
      await expectNoI18nArtifacts(page, `admin section ${sectionId}`);
    }
  });

  test("admin login shell supports every configured locale", async ({ browser }) => {
    for (const locale of SUPPORTED_LOCALES) {
      const context = await browser.newContext();
      await context.addInitScript((code) => {
        localStorage.setItem("admin_console_locale", code);
      }, locale);
      const page = await context.newPage();
      try {
        await page.goto(new URL("/admin/", adminBaseUrl()).href);
        await expect(page.locator("[data-testid=admin-login-btn]")).toBeVisible({ timeout: 10_000 });
        await expect(page.locator("html")).toHaveAttribute("lang", ADMIN_LOCALE_EXPECTATIONS[locale].lang);
        await expect(page.getByTestId("admin-login-btn")).toHaveText(ADMIN_LOCALE_EXPECTATIONS[locale].login);
        await expectNoI18nArtifacts(page, `admin login locale=${locale}`);
      } finally {
        await context.close();
      }
    }
  });
});
