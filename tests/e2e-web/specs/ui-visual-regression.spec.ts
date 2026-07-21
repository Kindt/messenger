/**
 * Visual regression — requires live stack (:19088 / :18080).
 * First baseline: npx playwright test specs/ui-visual-regression.spec.ts --update-snapshots
 * Tier: ui-visual-regression (playwright-tiers.json)
 */
import { Locator, Page } from "@playwright/test";
import { expect, test } from "../fixtures/test-with-qemu-wait";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

const SETTINGS_TABS = ["general", "profile", "notifications", "links", "security"] as const;
/** Stable tabs only — profile/notifications/security panels grow with async push/devices (engage lab). */
const SETTINGS_SNAPSHOT_TABS = ["general", "links"] as const;
/** Per-tab fixed heights — Playwright requires identical dimensions; content height varies by tab. */
const SETTINGS_TAB_SNAPSHOT_HEIGHT_PX: Record<(typeof SETTINGS_SNAPSHOT_TABS)[number], number> = {
  general: 640,
  links: 120,
};

async function forceRuLocale(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("korus_web_locale", "ru");
  });
}

async function disableMotion(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const style = document.createElement("style");
    style.textContent =
      "*, *::before, *::after { animation-duration: 0s !important; transition-duration: 0s !important; }";
    const root = document.head || document.documentElement;
    if (root) root.appendChild(style);
  });
}

async function waitGlobalSearchEmpty(page: Page): Promise<void> {
  await page.getByTestId("message-search-sidebar").fill("zzzz-no-match-query");
  await expect(page.getByTestId("global-search-empty")).toBeVisible({ timeout: 15_000 });
}

async function waitThreadSearchEmpty(page: Page): Promise<void> {
  const input = page.getByTestId("message-search-thread");
  await input.evaluate((el, q) => {
    const inp = el as HTMLInputElement;
    inp.value = q;
    inp.dispatchEvent(new Event("input", { bubbles: true }));
  }, "zzzz-no-thread-hit");
  await expect(page.getByTestId("thread-search-empty")).toBeVisible({ timeout: 15_000 });
}

async function openSettings(page: Page): Promise<void> {
  await expect(page.getByTestId("logout")).toBeVisible({ timeout: 30_000 });
  const card = page.locator(".settings-card");
  for (let attempt = 0; attempt < 3; attempt++) {
    if (await card.isVisible().catch(() => false)) {
      return;
    }
    await page.getByTestId("settings-toggle").click();
    try {
      await expect(card).toBeVisible({ timeout: 8_000 });
      return;
    } catch {
      /* settings overlay may miss first click under load */
    }
  }
  await expect(card).toBeVisible({ timeout: 15_000 });
}

/** Profile tab hint blocks can reflow by ~1 line between runs — wait for stable height. */
async function waitPanelLayoutStable(panel: Locator, opts: { samples?: number; delayMs?: number } = {}): Promise<void> {
  const samples = opts.samples ?? 3;
  const delayMs = opts.delayMs ?? 300;
  await expect
    .poll(
      async () => {
        const heights: number[] = [];
        for (let i = 0; i < samples; i++) {
          heights.push((await panel.boundingBox())?.height ?? 0);
          if (i + 1 < samples) await new Promise((r) => setTimeout(r, delayMs));
        }
        const min = Math.min(...heights);
        const max = Math.max(...heights);
        return min > 0 && max - min <= 1;
      },
      { timeout: 25_000 }
    )
    .toBe(true);
}

async function waitSettingsTabReady(page: Page, tabId: (typeof SETTINGS_TABS)[number], panel: Locator): Promise<void> {
  if (tabId === "profile") {
    await page.getByTestId("settings-presence").selectOption("online");
    await expect(page.getByTestId("settings-dnd-profile-hint")).toHaveCount(0);
  }
  if (tabId === "notifications") {
    await page
      .waitForResponse((r) => r.url().includes("/api/v1/me/devices") && r.status() < 500, { timeout: 20_000 })
      .catch(() => {});
  }
  const samples = tabId === "profile" || tabId === "notifications" ? 6 : 4;
  const delayMs = tabId === "profile" || tabId === "notifications" ? 500 : 300;
  await waitPanelLayoutStable(panel, { samples, delayMs });
}

/** Lock tabpanel height so Playwright snapshot dimensions are identical every run. */
async function freezePanelHeightForSnapshot(
  panel: Locator,
  tabId: (typeof SETTINGS_SNAPSHOT_TABS)[number]
): Promise<void> {
  const h = SETTINGS_TAB_SNAPSHOT_HEIGHT_PX[tabId];
  await panel.evaluate((el, height) => {
    const node = el as HTMLElement;
    node.style.boxSizing = "border-box";
    node.style.height = `${height}px`;
    node.style.minHeight = `${height}px`;
    node.style.maxHeight = `${height}px`;
    node.style.overflow = "hidden";
  }, h);
}

test.describe("ui visual regression", () => {
  test.setTimeout(120_000);

  test.beforeEach(async ({ page }) => {
    await forceRuLocale(page);
    await disableMotion(page);
  });

  test("settings tabs match baseline snapshots", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    // csadmin: avoid notifications layout drift after api-tier device register on smoke_user_a.
    await uiLogin(page, "csadmin", "csadmin");
    await openSettings(page);

    for (const tabId of SETTINGS_SNAPSHOT_TABS) {
      await page.getByTestId(`settings-tab-${tabId}`).click();
      const panel = page.locator(`#settings-panel-${tabId}`);
      await expect(panel).toBeVisible({ timeout: 10_000 });
      await waitSettingsTabReady(page, tabId, panel);
      await freezePanelHeightForSnapshot(panel, tabId);
      await expect(panel).toHaveScreenshot(`settings-tab-${tabId}.png`, {
        animations: "disabled",
        maxDiffPixelRatio: 0.05,
      });
    }

    for (const tabId of SETTINGS_TABS) {
      if ((SETTINGS_SNAPSHOT_TABS as readonly string[]).includes(tabId)) continue;
      await page.getByTestId(`settings-tab-${tabId}`).click();
      await expect(page.locator(`#settings-panel-${tabId}`)).toBeVisible({ timeout: 10_000 });
    }
  });

  test("search empty states match baseline snapshots", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `visual-empty-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await waitGlobalSearchEmpty(page);
    await expect(page.getByTestId("global-search-empty")).toHaveScreenshot("global-search-empty.png", {
      animations: "disabled",
      maxDiffPixelRatio: 0.03,
    });

    await uiOpenChatByTitle(page, title);
    await waitThreadSearchEmpty(page);
    await expect(page.getByTestId("thread-search-empty")).toHaveScreenshot("thread-search-empty.png", {
      animations: "disabled",
      maxDiffPixelRatio: 0.03,
    });
  });
});
