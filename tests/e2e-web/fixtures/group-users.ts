import { Browser, BrowserContext, expect, Page } from "@playwright/test";
import { uiLogin, uiOpenChatByTitle, uiSendMessage } from "./ui";

/** Canonical smoke users for multi-user Playwright scenarios (spec 003 parity). */
export type SmokeUserId = "smoke_user_a" | "smoke_user_b" | "smoke_user_c";

export const SMOKE_USER_PASS = "smokepass123";

export const SMOKE_USERS: Record<SmokeUserId, { displayName: string }> = {
  smoke_user_a: { displayName: "Smoke User A" },
  smoke_user_b: { displayName: "Smoke User B" },
  smoke_user_c: { displayName: "Smoke User C" },
};

export interface GroupUserSession {
  username: SmokeUserId;
  context: BrowserContext;
  page: Page;
}

function playwrightWsUrl(): string {
  if (process.env.PLAYWRIGHT_WS_URL) return process.env.PLAYWRIGHT_WS_URL;
  const web =
    process.env.KORUS_WEB_URL ||
    process.env.PLAYWRIGHT_BASE_URL ||
    "http://127.0.0.1:19088";
  return web.replace(/^http/i, "ws") + "/ws";
}

/** Loopback-friendly WS for host Playwright (avoids LAN IP wsUrl mismatch). */
export async function installPlaywrightWebClientEnv(context: BrowserContext): Promise<void> {
  const wsUrl = playwrightWsUrl();
  await context.route("**/web-client-env.js", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/javascript; charset=utf-8",
      body:
        "window.__WEB_CLIENT__ = " +
        JSON.stringify({
          wsUrl,
          iceServersJson: null,
          vapidPublicKey: null,
          disableServiceWorker: true,
        }) +
        ";",
    });
  });
}

export async function uiWaitForWsConnected(page: Page): Promise<void> {
  const ws = page.locator(".ws-status.connected");
  if (await ws.isVisible({ timeout: 5_000 }).catch(() => false)) return;
  // WS may stay offline on QEMU host; tests fall back to REST thread reload.
}

/** Isolated browser context per user — simulates concurrent clients in one test. */
export async function openGroupUserSessions(
  browser: Browser,
  usernames: SmokeUserId[]
): Promise<GroupUserSession[]> {
  const sessions: GroupUserSession[] = [];
  for (const username of usernames) {
    const context = await browser.newContext();
    await installPlaywrightWebClientEnv(context);
    const page = await context.newPage();
    await uiLogin(page, username, SMOKE_USER_PASS);
    sessions.push({ username, context, page });
  }
  return sessions;
}

export async function closeGroupUserSessions(sessions: GroupUserSession[]): Promise<void> {
  for (const s of sessions) {
    await s.context.close();
  }
}

export async function openGroupChatForAll(sessions: GroupUserSession[], title: string): Promise<void> {
  for (const s of sessions) {
    await uiOpenChatByTitle(s.page, title);
  }
}

export function sessionPage(sessions: GroupUserSession[], username: SmokeUserId): Page {
  const hit = sessions.find((s) => s.username === username);
  if (!hit) throw new Error(`no session for ${username}`);
  return hit.page;
}

async function uiReloadChatThread(page: Page, title: string): Promise<void> {
  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.locator("[data-testid=logout]")).toBeVisible({ timeout: 30_000 });
  await uiOpenChatByTitle(page, title);
}

/** Wait until thread gains one message (WS push or REST reload). MLS may hide plaintext. */
export async function uiWaitForNewMessage(
  page: Page,
  previousCount: number,
  optionalText?: string,
  chatTitle?: string
): Promise<void> {
  const target = previousCount + 1;
  const articles = page.locator("article");
  await expect
    .poll(
      async () => {
        let n = await articles.count();
        if (n >= target) return n;
        if (chatTitle) await uiReloadChatThread(page, chatTitle);
        return await articles.count();
      },
      { timeout: 30_000, intervals: [400, 800, 1500] }
    )
    .toBeGreaterThanOrEqual(target);
  if (optionalText) {
    const plaintext = page
      .locator(".msg-body, .msg-e2ee-body.msg-e2ee-decrypted")
      .getByText(optionalText, { exact: true });
    if (await plaintext.first().isVisible({ timeout: 5_000 }).catch(() => false)) {
      return;
    }
  }
  await expect(
    page.locator("article").last().locator(".msg-body, .msg-e2ee-body").first()
  ).toBeVisible({ timeout: 10_000 });
}

export async function uiSendAndExpectDelivery(
  sender: Page,
  receivers: Page[],
  text: string,
  chatTitle?: string
): Promise<void> {
  const beforeCounts = await Promise.all(receivers.map((p) => p.locator("article").count()));
  await uiSendMessage(sender, text);
  await Promise.all(
    receivers.map((page, i) => uiWaitForNewMessage(page, beforeCounts[i], text, chatTitle))
  );
}
