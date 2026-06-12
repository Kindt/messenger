import { test, expect } from "@playwright/test";
import { apiBase, apiCreateGroup, apiLogin, apiMeId, ensureSmokeUsers } from "../fixtures/auth";
import { uiLogin, uiOpenChatByTitle, uiSendMessage } from "../fixtures/ui";

test.describe("files and export parity", () => {
  test("upload file via API; export job requested", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-files-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);

    const upload = await request.post(`${apiBase()}/api/v1/files/upload`, {
      headers: {
        Authorization: `Bearer ${tokenA}`,
        "Content-Type": "application/octet-stream",
        "X-Filename": "parity.txt",
      },
      data: "playwright parity file",
    });
    expect(upload.ok()).toBeTruthy();

    const exportRes = await request.post(`${apiBase()}/api/v1/chats/${chatId}/export`, {
      headers: { Authorization: `Bearer ${tokenA}` },
      data: {},
    });
    expect([200, 202, 409]).toContain(exportRes.status());

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await expect(page.locator("[data-testid=message-composer]")).toBeVisible();
  });

  test("export via UI triggers download when job completes", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-export-ui-${Date.now()}`;
    const chatId = await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    await uiSendMessage(page, `export-seed-${Date.now()}`);

    const exportBtn = page.locator("[data-testid=chat-export-button]");
    await expect(exportBtn).toBeVisible({ timeout: 10_000 });

    const downloadPromise = page.waitForEvent("download", { timeout: 180_000 });
    await exportBtn.click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/korus-export|\.zip/i);
    expect(chatId).toBeTruthy();
  });

  test("upload file via DOM composer attach", async ({ page, request }) => {
    await ensureSmokeUsers(request);
    const tokenA = await apiLogin(request, "smoke_user_a", "smokepass123");
    const tokenB = await apiLogin(request, "smoke_user_b", "smokepass123");
    const idB = await apiMeId(request, tokenB);
    const title = `e2e-dom-upload-${Date.now()}`;
    await apiCreateGroup(request, tokenA, title, [idB]);

    await uiLogin(page, "smoke_user_a", "smokepass123");
    await uiOpenChatByTitle(page, title);
    const fileName = `dom-upload-${Date.now()}.txt`;
    const fileBody = "playwright DOM file upload";
    await page.locator("[data-testid=file-attach-input]").setInputFiles({
      name: fileName,
      mimeType: "text/plain",
      buffer: Buffer.from(fileBody),
    });
    await expect(page.getByRole("button", { name: /Скачать файл|Download file/i })).toBeVisible({
      timeout: 30_000,
    });
  });
});
