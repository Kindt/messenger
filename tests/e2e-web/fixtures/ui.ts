import { APIRequestContext, Page, expect } from "@playwright/test";
import { apiBase } from "./auth";

/** Locale-agnostic UI login (uses stable ids, not button label text). */
export async function uiLogin(page: Page, username: string, password: string): Promise<void> {
  await page.goto("/");
  await page.locator("#u").fill(username);
  await page.locator("#p").fill(password);
  await page.locator("[data-testid=auth-submit]").click();
  await expect(page.locator("[data-testid=logout]")).toBeVisible({ timeout: 30_000 });
}

export async function uiOpenChatByTitle(page: Page, title: string): Promise<void> {
  const btn = page.getByRole("button", { name: new RegExp(title) }).first();
  await expect(btn).toBeVisible({ timeout: 30_000 });
  await btn.click();
  await expect(page.locator("[data-testid=message-composer]")).toBeVisible({ timeout: 15_000 });
}

/** Assert message text when client decrypt/plaintext is visible. */
export async function uiExpectMessageText(page: Page, text: string): Promise<void> {
  const locator = page
    .locator(".msg-body, .msg-e2ee-body.msg-e2ee-decrypted")
    .getByText(text, { exact: true })
    .first();
  await expect(locator).toBeVisible({ timeout: 30_000 });
}

/** Send via composer; under MLS accept encrypted bubble when decrypt preview is deferred. */
export async function uiSendMessage(page: Page, text: string): Promise<void> {
  const composer = page.locator("[data-testid=message-composer]");
  const articles = page.locator("article");
  const before = await articles.count();
  await composer.fill(text);
  await composer.press("Enter");
  await expect(composer).toHaveValue("", { timeout: 10_000 });
  await expect(articles).toHaveCount(before + 1, { timeout: 30_000 });
  const plaintext = page
    .locator(".msg-body, .msg-e2ee-body.msg-e2ee-decrypted")
    .getByText(text, { exact: true });
  if (await plaintext.first().isVisible({ timeout: 5_000 }).catch(() => false)) {
    return;
  }
  await expect(articles.last().locator(".msg-body, .msg-e2ee-body")).toBeVisible({ timeout: 10_000 });
}

/** True when server reports MLS active (encrypted bubbles; plaintext may be deferred). */
export async function apiMlsActive(request: APIRequestContext): Promise<boolean> {
  const capsRes = await request.get(`${apiBase()}/api/v1/media/capabilities`);
  const caps = await capsRes.json();
  return (caps.mls_status || caps.mlsStatus) === "active";
}

/** Assert at least one message bubble is visible (MLS-safe). */
export async function uiExpectThreadHasMessage(page: Page): Promise<void> {
  const article = page.locator("article").first();
  await expect(article).toBeVisible({ timeout: 30_000 });
  await expect(article.locator(".msg-body, .msg-e2ee-body")).toBeVisible({ timeout: 15_000 });
}

/** Plaintext when available; otherwise accept encrypted bubble under MLS. */
export async function uiExpectMessageTextOrBubble(
  page: Page,
  request: APIRequestContext,
  text: string
): Promise<void> {
  if (await apiMlsActive(request)) {
    await uiExpectThreadHasMessage(page);
    return;
  }
  await uiExpectMessageText(page, text);
}

/** Hover message row so action buttons become visible. */
export async function uiHoverMessage(page: Page, articleIndex = 0): Promise<void> {
  await page.locator("article").nth(articleIndex).hover();
}

/** Click a message action button (reply, forward, delete, …) by data-testid. */
export async function uiClickMessageAction(
  page: Page,
  testId: string,
  articleIndex = 0
): Promise<void> {
  const article = page.locator("article").nth(articleIndex);
  await article.hover();
  await article.locator(`[data-testid=${testId}]`).click();
}

export const composer = (page: Page) => page.locator("[data-testid=message-composer]");
