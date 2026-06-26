import { APIRequestContext, expect, Page } from "@playwright/test";
import {
  apiBase,
  apiCreateGroup,
  apiLogin,
  apiMeId,
  ensureSmokeUsers,
} from "./auth";
import { installPlaywrightWebClientEnv } from "./group-users";
import { uiLogin, uiOpenChatById, uiOpenChatByTitle } from "./ui";

const SMOKE_PASS = "smokepass123";
let sameOrgReady = false;
let cachedOrgId: string | undefined;

    /** Valid 32×32 PNG — generated via ImageResizeServiceTest.exportAvatarFixtureBase64. */
export const AVATAR_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAASklEQVR4XrXHsQ0AMBADIe+/9Ke/mkg0bLe/eq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neq7neu0BNof8Lr1YpLkAAAAASUVORK5CYII=",
  "base64"
);

export { SMOKE_PASS };

export type UserProfile = {
  id?: string;
  avatar_url?: string | null;
  avatar_file_id?: string | null;
  avatar_hidden?: boolean;
  display_name?: string;
  username?: string;
};

export async function apiUploadFile(
  request: APIRequestContext,
  token: string,
  fileName: string,
  body: Buffer,
  mimeType = "application/octet-stream"
): Promise<string> {
  const res = await request.post(`${apiBase()}/api/v1/files/upload`, {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": mimeType,
      "X-Filename": fileName,
    },
    data: body,
  });
  if (!res.ok()) throw new Error(`upload failed: ${res.status()} ${await res.text()}`);
  const json = await res.json();
  const id = json.id || json.file_id;
  if (!id) throw new Error("upload missing file id");
  return id as string;
}

export async function apiGetMe(
  request: APIRequestContext,
  token: string
): Promise<UserProfile> {
  const res = await request.get(`${apiBase()}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) throw new Error(`users/me failed: ${res.status()}`);
  return (await res.json()) as UserProfile;
}

export async function apiGetUser(
  request: APIRequestContext,
  token: string,
  userId: string
): Promise<UserProfile> {
  const res = await request.get(`${apiBase()}/api/v1/users/${userId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) throw new Error(`users/${userId} failed: ${res.status()}`);
  return (await res.json()) as UserProfile;
}

export async function apiClearAvatar(
  request: APIRequestContext,
  token: string
): Promise<UserProfile> {
  const me = await apiGetMe(request, token);
  const patch: Record<string, unknown> = {};
  if (me.avatar_file_id || me.avatar_url) {
    patch.remove_avatar = true;
  }
  if (me.avatar_hidden) {
    patch.avatar_hidden = false;
  }
  if (Object.keys(patch).length === 0) {
    return me;
  }
  const res = await request.patch(`${apiBase()}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
    data: patch,
  });
  if (!res.ok()) throw new Error(`clear avatar failed: ${res.status()} ${await res.text()}`);
  return (await res.json()) as UserProfile;
}

export async function apiSetAvatarFile(
  request: APIRequestContext,
  token: string,
  fileId: string
): Promise<UserProfile> {
  const res = await request.patch(`${apiBase()}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { avatar_file_id: fileId },
  });
  if (!res.ok()) throw new Error(`set avatar failed: ${res.status()}`);
  return (await res.json()) as UserProfile;
}

export async function apiSetAvatarHidden(
  request: APIRequestContext,
  token: string,
  hidden: boolean
): Promise<UserProfile> {
  const res = await request.patch(`${apiBase()}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { avatar_hidden: hidden },
  });
  if (!res.ok()) throw new Error(`avatar_hidden failed: ${res.status()}`);
  return (await res.json()) as UserProfile;
}

export async function apiUploadAndSetAvatar(
  request: APIRequestContext,
  token: string,
  body: Buffer = AVATAR_PNG
): Promise<UserProfile> {
  const fileId = await apiUploadFile(request, token, `e2e-avatar-${Date.now()}.png`, body);
  return apiSetAvatarFile(request, token, fileId);
}

export function absoluteApiUrl(relativeOrAbsolute: string): string {
  if (relativeOrAbsolute.startsWith("http")) return relativeOrAbsolute;
  const base = apiBase().replace(/\/$/, "");
  const pathPart = relativeOrAbsolute.startsWith("/") ? relativeOrAbsolute : `/${relativeOrAbsolute}`;
  return `${base}${pathPart}`;
}

/** Signed avt resize URL must load without Bearer (img tag model). */
export async function expectSignedAvatarUrlLoads(
  request: APIRequestContext,
  avatarUrl: string | null | undefined
): Promise<void> {
  expect(avatarUrl, "avatar_url should be set").toBeTruthy();
  expect(avatarUrl!).toContain("avt=");
  expect(avatarUrl!).toMatch(/\/resize\?/);
  const res = await request.get(absoluteApiUrl(avatarUrl!));
  expect(res.status(), `resize should return 200, got ${res.status()}`).toBe(200);
  const ct = res.headers()["content-type"] || "";
  expect(ct).toMatch(/image\//);
  expect((await res.body()).byteLength).toBeGreaterThan(0);
}

export async function expectResizeDeniedWithoutAvt(
  request: APIRequestContext,
  avatarUrl: string
): Promise<void> {
  const u = new URL(absoluteApiUrl(avatarUrl));
  u.searchParams.delete("avt");
  const res = await request.get(u.toString());
  expect([401, 403, 404]).toContain(res.status());
}

/** Spec 068: peer avatar_url requires same org (JdbcAvatarAccessRepository). */
export async function ensureSmokeUsersInSameOrg(
  request: APIRequestContext
): Promise<string> {
  await ensureSmokeUsers(request);
  if (sameOrgReady && cachedOrgId) {
    return cachedOrgId;
  }
  const adminToken = await apiLogin(request, "csadmin", "csadmin");
  const orgRes = await request.get(`${apiBase()}/api/v1/admin/organizations`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (!orgRes.ok()) {
    throw new Error(`admin organizations failed: ${orgRes.status()} ${await orgRes.text()}`);
  }
  const orgs = await orgRes.json();
  const orgId = (orgs[0]?.id || orgs[0]?.org_id) as string | undefined;
  if (!orgId) throw new Error("no organization for avatar E2E");
  for (const username of ["smoke_user_a", "smoke_user_b"] as const) {
    const token = await apiLogin(request, username, SMOKE_PASS);
    const userId = await apiMeId(request, token);
    const res = await request.patch(`${apiBase()}/api/v1/admin/users/${userId}/organization`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { org_id: orgId },
    });
    if (!res.ok()) {
      throw new Error(`set org for ${username} failed: ${res.status()} ${await res.text()}`);
    }
  }
  sameOrgReady = true;
  cachedOrgId = orgId;
  return orgId;
}

export async function apiEnsureAbGroup(
  request: APIRequestContext,
  titlePrefix: string
): Promise<{
  tokenA: string;
  tokenB: string;
  idA: string;
  idB: string;
  chatId: string;
  title: string;
}> {
  await ensureSmokeUsersInSameOrg(request);
  const tokenA = await apiLogin(request, "smoke_user_a", SMOKE_PASS);
  const tokenB = await apiLogin(request, "smoke_user_b", SMOKE_PASS);
  const idA = await apiMeId(request, tokenA);
  const idB = await apiMeId(request, tokenB);
  const title = `${titlePrefix}-${Date.now()}`;
  const chatId = await apiCreateGroup(request, tokenA, title, [idB]);
  return { tokenA, tokenB, idA, idB, chatId, title };
}

export async function openSettingsProfile(page: Page): Promise<void> {
  await page.getByTestId("settings-toggle").click();
  await expect(page.locator(".settings-card")).toBeVisible({ timeout: 15_000 });
  await page.getByTestId("settings-tab-profile").click();
  await expect(page.locator("#settings-panel-profile")).toBeVisible({ timeout: 10_000 });
}

export async function uploadAvatarViaUiWithCrop(page: Page): Promise<void> {
  await openSettingsProfile(page);
  const fileInput = page.locator(".settings-avatar-row input[type=file]");
  await fileInput.setInputFiles({
    name: "avatar-e2e.png",
    mimeType: "image/png",
    buffer: AVATAR_PNG,
  });
  await expect(page.getByTestId("avatar-crop-overlay")).toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId("avatar-crop-canvas")).toBeVisible();
  await page.getByTestId("avatar-crop-apply").click();
  await expect(page.getByTestId("avatar-crop-overlay")).toHaveCount(0, { timeout: 30_000 });
}

export async function expectProfileAvatarImgWithAvt(page: Page): Promise<string> {
  const img = page.getByTestId("settings-profile-avatar").locator("img.chat-avatar-img");
  await expect(img).toBeVisible({ timeout: 30_000 });
  const src = await img.getAttribute("src");
  expect(src).toBeTruthy();
  expect(src!).toContain("avt=");
  expect(src!).toMatch(/\/resize\?/);
  await expect
    .poll(async () => {
      return page.evaluate((sel) => {
        const el = document.querySelector(sel) as HTMLImageElement | null;
        return el && el.complete && el.naturalWidth > 0;
      }, "[data-testid=settings-profile-avatar] img.chat-avatar-img");
    })
    .toBe(true);
  return src!;
}

export async function expectAvatarInitialsOnly(page: Page, testId: string): Promise<void> {
  const wrap = page.getByTestId(testId);
  await expect(wrap).toBeVisible();
  await expect(wrap.locator("img.chat-avatar-img")).toHaveCount(0);
  await expect(wrap).not.toHaveText("?");
}

export async function forceRuLocale(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem("korus_web_locale", "ru");
  });
}

/** Open smoke_user_b on a group chat via deep link (stable vs sidebar search). */
export async function openPeerBrowserOnChat(
  browser: import("@playwright/test").Browser,
  chatId: string
): Promise<{ page: import("@playwright/test").Page; context: import("@playwright/test").BrowserContext }> {
  const context = await browser.newContext();
  await installPlaywrightWebClientEnv(context);
  const page = await context.newPage();
  await forceRuLocale(page);
  await uiLogin(page, "smoke_user_b", SMOKE_PASS);
  await uiOpenChatById(page, chatId);
  return { page, context };
}

export async function apiWaitForPeerUserAvatarUrl(
  request: APIRequestContext,
  tokenB: string,
  userIdA: string,
  expectVisible: boolean
): Promise<void> {
  await expect
    .poll(
      async () => {
        const peer = await apiGetUser(request, tokenB, userIdA);
        return peer.avatar_url ?? null;
      },
      { timeout: 30_000, intervals: [400, 800, 1500] }
    )
    .toEqual(expectVisible ? expect.stringMatching(/\/resize\?/) : null);
}

export async function uiWaitForSenderAvatarImg(
  page: Page,
  chatId: string,
  expectImg: boolean
): Promise<void> {
  const senderAv = page.locator(".messages article .msg-sender-avatar img.chat-avatar-img").last();
  const senderWrap = page.locator(".messages article .msg-sender-avatar").last();
  await expect
    .poll(
      async () => {
        if (expectImg) {
          if (await senderAv.isVisible().catch(() => false)) return true;
        } else if ((await senderWrap.locator("img.chat-avatar-img").count()) === 0) {
          return true;
        }
        await uiOpenChatById(page, chatId);
        return false;
      },
      { timeout: 60_000, intervals: [500, 1000, 2000] }
    )
    .toBe(true);
}

export async function apiWaitForChatMessages(
  request: APIRequestContext,
  token: string,
  chatId: string,
  minCount: number
): Promise<void> {
  await expect
    .poll(
      async () => {
        const res = await request.get(`${apiBase()}/api/v1/chats/${chatId}/messages?limit=20`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok()) return 0;
        const rows = await res.json();
        return Array.isArray(rows) ? rows.length : 0;
      },
      { timeout: 30_000, intervals: [400, 800, 1500] }
    )
    .toBeGreaterThanOrEqual(minCount);
}

export async function uiWaitForThreadMessages(
  page: import("@playwright/test").Page,
  minCount: number,
  chatId: string
): Promise<void> {
  const articles = page.locator(".messages article");
  await expect
    .poll(
      async () => {
        const n = await articles.count();
        if (n >= minCount) return n;
        await uiOpenChatById(page, chatId);
        return await articles.count();
      },
      { timeout: 90_000, intervals: [500, 1000, 2000, 3000] }
    )
    .toBeGreaterThanOrEqual(minCount);
}

export async function expectProfileAvatarAfterReload(page: Page): Promise<string> {
  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.locator("[data-testid=logout]")).toBeVisible({ timeout: 30_000 });
  await expect
    .poll(
      async () => {
        const settingsOpen = await page.locator(".settings-card").isVisible().catch(() => false);
        if (!settingsOpen) {
          await openSettingsProfile(page).catch(() => {});
        }
        return page.getByTestId("settings-profile-avatar").locator("img.chat-avatar-img").count();
      },
      { timeout: 60_000, intervals: [500, 1000, 2000] }
    )
    .toBeGreaterThan(0);
  return expectProfileAvatarImgWithAvt(page);
}

export async function prepareUserA(request: APIRequestContext): Promise<string> {
  await ensureSmokeUsersInSameOrg(request);
  const token = await apiLogin(request, "smoke_user_a", SMOKE_PASS);
  await apiClearAvatar(request, token);
  return token;
}

/** Skip avatar E2E when guest core-api lacks spec 068 profile fields. */
export async function requireAvatarApi(
  request: APIRequestContext
): Promise<void> {
  await ensureSmokeUsersInSameOrg(request);
  const token = await apiLogin(request, "smoke_user_a", SMOKE_PASS);
  const me = await apiGetMe(request, token);
  if (!("avatar_url" in me) && !("avatar_file_id" in me)) {
    throw new Error(
      "Avatar API not available on server — run .\\scripts\\qemu-sync-api-core.ps1 -NoCache -Wait on QEMU guest"
    );
  }
}
