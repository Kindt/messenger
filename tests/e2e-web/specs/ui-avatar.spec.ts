import { expect, test } from "../fixtures/test-with-qemu-wait";
import {
  absoluteApiUrl,
  apiClearAvatar,
  apiEnsureAbGroup,
  apiGetUser,
  apiSetAvatarHidden,
  apiUploadAndSetAvatar,
  apiWaitForChatMessages,
  apiWaitForPeerUserAvatarUrl,
  AVATAR_PNG,
  ensureSmokeUsersInSameOrg,
  expectProfileAvatarAfterReload,
  expectProfileAvatarImgWithAvt,
  expectResizeDeniedWithoutAvt,
  expectSignedAvatarUrlLoads,
  expectAvatarInitialsOnly,
  forceRuLocale,
  openSettingsProfile,
  openPeerBrowserOnChat,
  prepareUserA,
  SMOKE_PASS,
  uiWaitForThreadMessages,
  uiWaitForSenderAvatarImg,
  uploadAvatarViaUiWithCrop,
} from "../fixtures/avatar";
import { apiLogin, apiMeId, apiSendMessage, ensureSmokeUsers } from "../fixtures/auth";
import { uiExpectThreadHasMessage, uiLogin } from "../fixtures/ui";

test.describe("Avatar API contract (spec 068)", () => {
  test.beforeEach(async ({ request }) => {
    await ensureSmokeUsers(request);
  });

  test("upload + PATCH mints signed avatar_url; resize loads without JWT", async ({ request }) => {
    const token = await prepareUserA(request);
    const profile = await apiUploadAndSetAvatar(request, token);
    await expectSignedAvatarUrlLoads(request, profile.avatar_url);
    await expectResizeDeniedWithoutAvt(request, profile.avatar_url!);
  });

  test("invalid avt token is rejected on resize", async ({ request }) => {
    const token = await prepareUserA(request);
    const profile = await apiUploadAndSetAvatar(request, token);
    const bad = absoluteApiUrl(profile.avatar_url!).replace(/avt=[^&]+/, "avt=tampered.invalid.token");
    const res = await request.get(bad);
    expect([401, 403]).toContain(res.status());
  });

  test("remove_avatar clears avatar_url and peer no longer receives image URL", async ({ request }) => {
    const { tokenA, tokenB, idA } = await apiEnsureAbGroup(request, "avt-remove");
    await apiUploadAndSetAvatar(request, tokenA);
    const peerBefore = await apiGetUser(request, tokenB, idA);
    expect(peerBefore.avatar_url).toBeTruthy();

    await apiClearAvatar(request, tokenA);
    const me = await apiGetUser(request, tokenA, idA);
    expect(me.avatar_url ?? null).toBeNull();
    const peerAfter = await apiGetUser(request, tokenB, idA);
    expect(peerAfter.avatar_url ?? null).toBeNull();
  });

  test("avatar_hidden hides avatar_url from peer profile API", async ({ request }) => {
    const { tokenA, tokenB, idA } = await apiEnsureAbGroup(request, "avt-hidden-api");
    await apiUploadAndSetAvatar(request, tokenA);
    await apiSetAvatarHidden(request, tokenA, true);

    const peer = await apiGetUser(request, tokenB, idA);
    expect(peer.avatar_url ?? null).toBeNull();

    await apiSetAvatarHidden(request, tokenA, false);
    const peerVisible = await apiGetUser(request, tokenB, idA);
    expect(peerVisible.avatar_url).toBeTruthy();
    await expectSignedAvatarUrlLoads(request, peerVisible.avatar_url);
  });
});

test.describe("Avatar UI — settings upload, crop, remove", () => {
  test.beforeEach(async ({ page, request }) => {
    await forceRuLocale(page);
    await prepareUserA(request);
  });

  test("settings shows profile avatar controls after login", async ({ page }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await openSettingsProfile(page);
    await expect(page.getByTestId("settings-profile-avatar")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("settings-avatar-change")).toBeVisible();
    await expect(page.getByTestId("settings-avatar-hidden")).toBeVisible();
  });

  test("file picker → crop modal → apply shows img with avt and loads", async ({ page, request }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await uploadAvatarViaUiWithCrop(page);
    const src = await expectProfileAvatarImgWithAvt(page);
    await expectSignedAvatarUrlLoads(request, src);
  });

  test("remove avatar restores initials-only placeholder", async ({ page }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await uploadAvatarViaUiWithCrop(page);
    await expectProfileAvatarImgWithAvt(page);

    await page.getByTestId("settings-avatar-remove").click();
    await expect
      .poll(async () => page.getByTestId("settings-profile-avatar").locator("img.chat-avatar-img").count())
      .toBe(0);
    await expectAvatarInitialsOnly(page, "settings-profile-avatar");
  });

  test("avatar persists after page reload", async ({ page, request }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await uploadAvatarViaUiWithCrop(page);
    await expectProfileAvatarImgWithAvt(page);

    const srcAfter = await expectProfileAvatarAfterReload(page);
    expect(srcAfter).toContain("avt=");
    await expectSignedAvatarUrlLoads(request, srcAfter);

    const token = await apiLogin(request, "smoke_user_a", SMOKE_PASS);
    await apiClearAvatar(request, token);
  });

  test("crop cancel dismisses overlay without applying avatar", async ({ page }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await openSettingsProfile(page);
    await expectAvatarInitialsOnly(page, "settings-profile-avatar");

    const fileInput = page.locator(".settings-avatar-row input[type=file]");
    await fileInput.setInputFiles({
      name: "avatar-e2e.png",
      mimeType: "image/png",
      buffer: AVATAR_PNG,
    });
    await expect(page.getByTestId("avatar-crop-overlay")).toBeVisible({ timeout: 10_000 });
    await page.getByTestId("avatar-crop-cancel").click();
    await expect(page.getByTestId("avatar-crop-overlay")).toHaveCount(0, { timeout: 10_000 });
    await expectAvatarInitialsOnly(page, "settings-profile-avatar");
  });

  test("avatar_hidden checkbox persists; peer profile API returns null", async ({
    page,
    request,
  }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    await uploadAvatarViaUiWithCrop(page);
    await expectProfileAvatarImgWithAvt(page);

    await page.getByTestId("settings-avatar-hidden").check();
    await expect(page.getByTestId("settings-avatar-hidden")).toBeChecked();

    const tokenA = await apiLogin(request, "smoke_user_a", SMOKE_PASS);
    const tokenB = await apiLogin(request, "smoke_user_b", SMOKE_PASS);
    const idA = await apiMeId(request, tokenA);
    await expect
      .poll(async () => (await apiGetUser(request, tokenB, idA)).avatar_url ?? null)
      .toBeNull();

    await page.getByTestId("settings-avatar-hidden").uncheck();
    await expect
      .poll(async () => page.getByTestId("settings-profile-avatar").locator("img.chat-avatar-img").count())
      .toBeGreaterThan(0);
    await expectProfileAvatarImgWithAvt(page);

    await apiClearAvatar(request, tokenA);
  });
});

test.describe("Avatar UI — sidebar and chat list", () => {
  test.beforeEach(async ({ page, request }) => {
    await forceRuLocale(page);
    await prepareUserA(request);
  });

  test("chat list rows expose avatar test ids", async ({ page }) => {
    await uiLogin(page, "smoke_user_a", SMOKE_PASS);
    const avatar = page.locator("[data-testid^='chat-row-avatar']").first();
    await expect(avatar).toBeVisible({ timeout: 20_000 });
  });
});

test.describe("Avatar cross-user visibility (2 browsers)", () => {
  test.setTimeout(180_000);

  test.beforeEach(async ({ request }) => {
    await ensureSmokeUsersInSameOrg(request);
  });

  test("peer sees sender avatar img with avt after A uploads and sends message", async ({
    browser,
    request,
  }) => {
    const { tokenA, tokenB, idA, chatId } = await apiEnsureAbGroup(request, "avt-xuser");
    await apiClearAvatar(request, tokenA);
    await apiUploadAndSetAvatar(request, tokenA);
    const msgText = `avatar-e2e-${Date.now()}`;
    await apiSendMessage(request, tokenA, chatId, msgText);
    await apiWaitForChatMessages(request, tokenB, chatId, 1);
    await apiWaitForPeerUserAvatarUrl(request, tokenB, idA, true);

    const { page: bPage, context } = await openPeerBrowserOnChat(browser, chatId);
    try {
      await uiWaitForThreadMessages(bPage, 1, chatId);
      await uiExpectThreadHasMessage(bPage);
      await uiWaitForSenderAvatarImg(bPage, chatId, true);

      const senderAv = bPage.locator(".messages article .msg-sender-avatar img.chat-avatar-img").last();
      await expect(senderAv).toBeVisible({ timeout: 30_000 });
      const src = await senderAv.getAttribute("src");
      expect(src).toContain("avt=");
      await expect
        .poll(async () =>
          bPage.evaluate(() => {
            const el = document.querySelector(
              ".messages article .msg-sender-avatar img.chat-avatar-img"
            ) as HTMLImageElement | null;
            return el && el.complete && el.naturalWidth > 0;
          })
        )
        .toBe(true);
      await expectSignedAvatarUrlLoads(request, src!);
    } finally {
      await context.close();
      await apiClearAvatar(request, tokenA);
    }
  });

  test("avatar_hidden: peer sees initials, not img, in message sender row", async ({
    browser,
    request,
  }) => {
    const { tokenA, tokenB, idA, chatId } = await apiEnsureAbGroup(request, "avt-hidden-ui");
    await apiUploadAndSetAvatar(request, tokenA);
    await apiSetAvatarHidden(request, tokenA, true);
    const msgText = `avatar-hidden-${Date.now()}`;
    await apiSendMessage(request, tokenA, chatId, msgText);
    await apiWaitForChatMessages(request, tokenB, chatId, 1);
    await apiWaitForPeerUserAvatarUrl(request, tokenB, idA, false);

    const { page: bPage, context } = await openPeerBrowserOnChat(browser, chatId);
    try {
      await uiWaitForThreadMessages(bPage, 1, chatId);
      await uiExpectThreadHasMessage(bPage);
      await uiWaitForSenderAvatarImg(bPage, chatId, false);

      const senderWrap = bPage.locator(".messages article .msg-sender-avatar").last();
      await expect(senderWrap).toBeVisible({ timeout: 30_000 });
      await expect(senderWrap.locator("img.chat-avatar-img")).toHaveCount(0);
    } finally {
      await context.close();
      await apiSetAvatarHidden(request, tokenA, false);
      await apiClearAvatar(request, tokenA);
    }
  });

  test("profile card from sender name shows avatar with avt for peer", async ({
    browser,
    request,
  }) => {
    const { tokenA, tokenB, idA, chatId } = await apiEnsureAbGroup(request, "avt-card");
    await apiClearAvatar(request, tokenA);
    await apiUploadAndSetAvatar(request, tokenA);
    const msgText = `avatar-card-${Date.now()}`;
    await apiSendMessage(request, tokenA, chatId, msgText);
    await apiWaitForChatMessages(request, tokenB, chatId, 1);
    await apiWaitForPeerUserAvatarUrl(request, tokenB, idA, true);

    const { page: bPage, context } = await openPeerBrowserOnChat(browser, chatId);
    try {
      await uiWaitForThreadMessages(bPage, 1, chatId);
      await uiExpectThreadHasMessage(bPage);

      await bPage.getByTestId("message-sender-name").last().click();
      await expect(bPage.getByTestId("profile-card-overlay")).toBeVisible({ timeout: 10_000 });
      const cardImg = bPage.getByTestId("profile-card-avatar").locator("img.chat-avatar-img");
      await expect
        .poll(async () => cardImg.isVisible().catch(() => false), { timeout: 30_000 })
        .toBe(true);
      const src = await cardImg.getAttribute("src");
      expect(src).toContain("avt=");
      await expectSignedAvatarUrlLoads(request, src!);
      await bPage.getByTestId("profile-card-close").click();
      await expect(bPage.getByTestId("profile-card-overlay")).toHaveCount(0);
    } finally {
      await context.close();
      await apiClearAvatar(request, tokenA);
    }
  });
});
