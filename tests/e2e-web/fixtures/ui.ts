import { Page, expect } from "@playwright/test";

/** Locale-agnostic UI login (uses stable ids, not button label text). */
export async function uiLogin(page: Page, username: string, password: string): Promise<void> {
  await page.goto("/");
  await page.locator("#u").fill(username);
  await page.locator("#p").fill(password);
  await page.locator("[data-testid=auth-submit]").click();
  await expect(page.locator("[data-testid=message-composer]")).toBeVisible({ timeout: 30_000 });
}

export async function uiOpenChatByTitle(page: Page, title: string): Promise<void> {
  await page.getByRole("button", { name: new RegExp(title) }).first().click({ timeout: 15_000 });
}

export async function uiSendMessage(page: Page, text: string): Promise<void> {
  const composer = page.locator("[data-testid=message-composer]");
  await composer.fill(text);
  await composer.press("Enter");
  await expect(page.getByText(text)).toBeVisible({ timeout: 20_000 });
}

export const composer = (page: Page) => page.locator("[data-testid=message-composer]");
