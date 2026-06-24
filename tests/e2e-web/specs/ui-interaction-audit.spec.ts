import { expect, Page, test } from "@playwright/test";
import { adminBaseUrl, adminNavTo, adminUiLogin } from "../fixtures/admin-ui";
import { apiBase, apiCreateGroup, apiLogin, apiMarkMessageRead, apiMeId, apiSendMessage, ensureSmokeUsers } from "../fixtures/auth";
import { VIEWPORT_PHONE } from "../fixtures/mobile-ui";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";
import {
  attachUiAuditErrorCollector,
  auditInteractiveSurface,
  mockAuditMediaDevices,
  type UiAuditViewport,
} from "../fixtures/ui-audit";

type AdminSection = {
  id: string;
  title?: string;
};

const DESKTOP: UiAuditViewport = { name: "desktop", size: { width: 1280, height: 900 } };
const MOBILE: UiAuditViewport = { name: "mobile", size: VIEWPORT_PHONE };
const VIEWPORTS = [DESKTOP, MOBILE];
const ADMIN_MUTATION_RE =
  /(create|save|apply|patch|post|put|rotate|sync|start|stop|submit|создат|сохран|примен|запуст|останов|синхрон)/i;

async function forceAuditLocale(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("korus_web_locale", "ru");
    localStorage.setItem("admin_console_locale", "ru");
  });
}

async function adminManifest(
  request: import("@playwright/test").APIRequestContext
): Promise<AdminSection[]> {
  const token = await apiLogin(request, "csadmin", "csadmin");
  const res = await request.get(`${apiBase()}/api/v1/admin/ui/manifest`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  return (body.sections || []) as AdminSection[];
}

async function adminOrgId(request: import("@playwright/test").APIRequestContext): Promise<string> {
  const token = await apiLogin(request, "csadmin", "csadmin");
  const orgRes = await request.get(`${apiBase()}/api/v1/admin/organizations`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(orgRes.ok()).toBeTruthy();
  const orgs = await orgRes.json();
  const orgId = orgs[0]?.id || orgs[0]?.org_id;
  expect(orgId).toBeTruthy();
  return orgId as string;
}

async function setAdminOrgContext(page: Page, orgId: string): Promise<void> {
  await page.evaluate((id) => {
    sessionStorage.setItem("admin_console_org_id", id);
    const input = document.getElementById("globalOrgId") as HTMLInputElement | null;
    if (input) input.value = id;
  }, orgId);
}

async function ensureCallPanelOpen(page: Page): Promise<void> {
  if (!(await page.getByTestId("call-panel-title").isVisible().catch(() => false))) {
    await page.getByTestId("call-panel-toggle").click();
  }
  await expect(page.getByTestId("call-panel-title")).toBeVisible({ timeout: 10_000 });
}

async function openAuditedChat(
  page: Page,
  request: import("@playwright/test").APIRequestContext
): Promise<string> {
  await ensureSmokeUsers(request);
  const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
  const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
  const idB = await apiMeId(request, tokenB);
  const title = `ui-audit-${Date.now()}`;
  await apiCreateGroup(request, tokenA, title, [idB]);
  await uiLogin(page, "smoke_user_a", "smokepass123");
  await uiOpenChatByTitle(page, title);
  return title;
}

test.describe("UI interaction audit", () => {
  test.setTimeout(240_000);

  for (const viewport of VIEWPORTS) {
    test(`client ${viewport.name} buttons links fields and layout stay healthy`, async ({
      page,
      request,
    }, testInfo) => {
      await page.setViewportSize(viewport.size);
      await forceAuditLocale(page);
      await mockAuditMediaDevices(page);
      const errors = attachUiAuditErrorCollector(page);

      await page.goto("/");
      await auditInteractiveSurface(page, testInfo, {
        surface: `client-auth-${viewport.name}`,
        rootSelector: "body",
        requiredSelectors: ["[data-testid=auth-submit]"],
        maxActions: 24,
        denyPatterns: [/auth-sso-/i, /locale-/i],
      });

      await page.goto("/");
      await openAuditedChat(page, request);
      await auditInteractiveSurface(page, testInfo, {
        surface: `client-messenger-${viewport.name}`,
        rootSelector: ".messenger-shell",
        requiredSelectors: [".messenger-shell", ".thread", "[data-testid=message-composer]"],
        maxActions: 80,
        denyPatterns: [/thread-back/i, /chat-export-button/i, /file-attach/i],
      });

      if ((await page.locator("article").count()) > 0) {
        await page.locator("article").first().hover().catch(() => {});
      }
      await auditInteractiveSurface(page, testInfo, {
        surface: `client-thread-actions-${viewport.name}`,
        rootSelector: ".thread",
        requiredSelectors: [".thread", "[data-testid=message-composer]"],
        maxActions: 40,
        denyPatterns: [/thread-back/i, /chat-export-button/i, /file-attach/i],
      });

      await page.getByTestId("settings-toggle").click();
      await expect(page.locator(".settings-card")).toBeVisible({ timeout: 10_000 });
      await auditInteractiveSurface(page, testInfo, {
        surface: `client-settings-${viewport.name}`,
        rootSelector: ".settings-card",
        requiredSelectors: [".messenger-shell"],
        maxActions: 60,
      });
      await page.getByTestId("settings-close").click().catch(() => {});

      await ensureCallPanelOpen(page);
      await auditInteractiveSurface(page, testInfo, {
        surface: `client-call-${viewport.name}`,
        rootSelector: ".call-panel",
        requiredSelectors: [".messenger-shell", ".call-panel"],
        maxActions: 48,
        denyPatterns: [/collapse/i, /сверн/i, /закры/i, /✕/i],
      });

      errors.expectNoCollectedErrors(`client ${viewport.name}`);
    });

    test(`admin ${viewport.name} buttons links fields and layout stay healthy`, async ({
      page,
      request,
    }, testInfo) => {
      await page.setViewportSize(viewport.size);
      await forceAuditLocale(page);
      const errors = attachUiAuditErrorCollector(page);
      const sections = await adminManifest(request);
      expect(sections.length).toBeGreaterThan(0);
      const orgId = await adminOrgId(request);

      await page.goto(new URL("/admin/", adminBaseUrl()).href);
      await auditInteractiveSurface(page, testInfo, {
        surface: `admin-auth-${viewport.name}`,
        rootSelector: "body",
        requiredSelectors: ["[data-testid=admin-login-btn]"],
        maxActions: 24,
        denyPatterns: [/admin-locale-select/i],
      });

      await adminUiLogin(page, "csadmin", "csadmin");
      await setAdminOrgContext(page, orgId);

      for (const section of sections) {
        await adminNavTo(page, section.id);
        await expect(page.locator("[data-testid=admin-panel]")).toBeVisible({ timeout: 15_000 });
        await auditInteractiveSurface(page, testInfo, {
          surface: `admin-${viewport.name}-${section.id}`,
          rootSelector: "[data-testid=admin-panel]",
          requiredSelectors: [
            "[data-testid=admin-header]",
            "#sectionList",
            "[data-testid=admin-panel]",
          ],
          maxActions: 36,
          denyPatterns: [ADMIN_MUTATION_RE],
        });
      }

      errors.expectNoCollectedErrors(`admin ${viewport.name}`);
    });
  }

  test("read receipt overlay opens from own message checkmarks", async ({ page, request }) => {
    await page.setViewportSize(DESKTOP.size);
    await forceAuditLocale(page);
    const errors = attachUiAuditErrorCollector(page);

    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `read-receipt-audit-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);
    const messageId = await apiSendMessage(request, tokenA, chatId, "read receipt audit seed");
    await apiMarkMessageRead(request, tokenB, chatId, messageId);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator(".msg-read-receipt-double-check").first()).toBeVisible({
      timeout: 20_000,
    });
    await page.locator(".msg-read-receipt-double-check").first().click();
    await expect(page.getByTestId("read-receipt-overlay")).toBeVisible({ timeout: 10_000 });
    await page.getByTestId("read-receipt-close").click();
    await expect(page.getByTestId("read-receipt-overlay")).toHaveCount(0);

    errors.expectNoCollectedErrors("read receipt overlay");
  });
});
