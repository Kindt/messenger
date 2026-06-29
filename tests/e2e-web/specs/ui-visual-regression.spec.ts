/**
 * Visual regression — requires live stack (:19088 / :18080).
 * First baseline: npx playwright test specs/ui-visual-regression.spec.ts --update-snapshots
 * Tier: ui-visual-regression (playwright-tiers.json)
 */
import { Page } from "@playwright/test";
import { expect, test } from "../fixtures/test-with-qemu-wait";
import { apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle } from "../fixtures/ui";

const SETTINGS_TABS = ["general", "profile", "notifications", "links", "security"] as const;

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
    document.documentElement.appendChild(style);
  });
}

async function waitGlobalSearchEmpty(page: Page): Promise<void> {
  await page.locator(".global-search-input").fill("zzzz-no-match-query");
  await expect(page.getByTestId("global-search-empty")).toBeVisible({ timeout: 15_000 });
}

async function waitThreadSearchEmpty(page: Page): Promise<void> {
  await page.locator(".thread-search-input").fill("zzzz-no-thread-hit");
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

test.describe("ui visual regression", () => {
  test.setTimeout(120_000);

  test.beforeEach(async ({ page }) => {
    await forceRuLocale(page);
    await disableMotion(page);
  });

  test("settings tabs match baseline snapshots", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    await uiLogin(page, "smoke_user_a", "smokepass123");
    await openSettings(page);

    for (const tabId of SETTINGS_TABS) {
      await page.getByTestId(`settings-tab-${tabId}`).click();
      const panel = page.locator(`#settings-panel-${tabId}`);
      await expect(panel).toBeVisible({ timeout: 10_000 });
      if (tabId === "profile") {
        await page.getByTestId("settings-presence").selectOption("online");
        await expect(page.getByTestId("settings-dnd-profile-hint")).toHaveCount(0);
      }
      await expect(panel).toHaveScreenshot(`settings-tab-${tabId}.png`, {
        animations: "disabled",
        maxDiffPixelRatio: 0.03,
      });
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
