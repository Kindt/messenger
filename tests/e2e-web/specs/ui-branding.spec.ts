import { expect, test } from "@playwright/test";
import { adminBaseUrl, adminNavTo, adminUiLogin } from "../fixtures/admin-ui";
import { apiBase, apiLogin } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";
test.describe("UI branding API (spec 027)", () => {
  test("GET /api/v1/branding is public", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(typeof body.palette).toBe("string");
    expect(body).toHaveProperty("demo_skins_enabled");
    expect(body).toHaveProperty("revision");
  });

  test("GET /api/v1/branding/me requires auth", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding/me`);
    expect(res.status()).toBe(401);
  });

  test("GET /api/v1/branding/me returns merged branding when authenticated", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const res = await request.get(`${apiBase()}/api/v1/branding/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(typeof body.palette).toBe("string");
  });

  test("GET /api/v1/branding/manifest.webmanifest is public PWA manifest", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding/manifest.webmanifest`);
    expect(res.ok()).toBeTruthy();
    expect(res.headers()["content-type"] || "").toMatch(/manifest\+json/);
    const body = await res.json();
    expect(typeof body.theme_color).toBe("string");
    expect(body.theme_color).toMatch(/^#/);
    expect(body.name).toBeTruthy();
    expect(body.icons?.length).toBeGreaterThan(0);
  });

  test("GET /api/v1/branding/me/manifest.webmanifest requires auth", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding/me/manifest.webmanifest`);
    expect(res.status()).toBe(401);
  });
});

test.describe("UI branding demo skins (login)", () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.removeItem("korus_web_style");
      localStorage.setItem("korus_web_locale", "ru");
    });
  });

  test("demo skin buttons switch data-palette on html", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("#u")).toBeVisible({ timeout: 15_000 });

    const demo = page.getByTestId("auth-demo-skins");
    const visible = await demo.isVisible({ timeout: 8_000 }).catch(() => false);
    if (!visible) {
      test.skip(true, "demo skins disabled (WEB_CLIENT_DEMO_SKINS or demo_skins_enabled=false)");
      return;
    }

    await page.getByTestId("auth-skin-vtb").click();
    await expect(page.locator("html")).toHaveAttribute("data-palette", "vtb");

    await page.getByTestId("auth-skin-sberbank").click();
    await expect(page.locator("html")).toHaveAttribute("data-palette", "sberbank");
  });

  test("after login demo palette persists only on platform-default branding", async ({
    page,
    request,
  }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const brandingRes = await request.get(`${apiBase()}/api/v1/branding`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(brandingRes.ok()).toBeTruthy();
    const branding = await brandingRes.json();
    const platformDefault =
      (branding.palette || "korus") === "korus" &&
      !branding.brand_title &&
      !branding.logo_url &&
      !(branding.custom_css && String(branding.custom_css).trim()) &&
      !(branding.token_overrides && Object.keys(branding.token_overrides).length);

    await page.goto("/");
    await expect(page.locator("#u")).toBeVisible({ timeout: 15_000 });

    const demo = page.getByTestId("auth-demo-skins");
    const demoVisible = await demo.isVisible({ timeout: 8_000 }).catch(() => false);
    if (!demoVisible) {
      test.skip(true, "demo skins disabled");
      return;
    }

    await page.getByTestId("auth-skin-vtb").click();
    await expect(page.locator("html")).toHaveAttribute("data-palette", "vtb");

    await uiLogin(page, "csadmin", "csadmin");
    await expect(page.getByTestId("auth-demo-skins")).toHaveCount(0);

    if (platformDefault) {
      await expect(page.locator("html")).toHaveAttribute("data-palette", "vtb");
    } else {
      const expected = branding.palette || "korus";
      await expect(page.locator("html")).toHaveAttribute("data-palette", expected);
    }
  });
});

test.describe("Admin UI branding (spec 027)", () => {
  test.use({ baseURL: adminBaseUrl() });

  test("core-ui-branding panel loads and preview applies palette", async ({ page }) => {
    await adminUiLogin(page, "csadmin", "csadmin");
    await adminNavTo(page, "core-ui-branding");
    await expect(page.getByTestId("admin-branding-toolbar")).toBeVisible({ timeout: 15_000 });
    await page.locator("#brandingPalette").selectOption("vtb");
    await page.getByTestId("admin-branding-preview").click();
    await expect(page.locator("html")).toHaveAttribute("data-palette", "vtb");
  });

  test("admin branding API roundtrip via save", async ({ page, request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const before = await request.get(`${apiBase()}/api/v1/admin/branding/platform`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(before.ok()).toBeTruthy();
    const prev = await before.json();

    await adminUiLogin(page, "csadmin", "csadmin");
    await adminNavTo(page, "core-ui-branding");
    await page.locator("#brandingPalette").selectOption("alfa");
    await page.getByTestId("admin-branding-save").click();
    await expect(page.locator("#brandingMsg")).toContainText(/.+/, { timeout: 15_000 });

    const after = await request.get(`${apiBase()}/api/v1/admin/branding/platform`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(after.ok()).toBeTruthy();
    const saved = await after.json();
    expect(saved.palette).toBe("alfa");
    expect(saved.revision).toBeGreaterThan(prev.revision ?? 0);

    await request.put(`${apiBase()}/api/v1/admin/branding/platform`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { palette: prev.palette ?? "korus", demo_skins_enabled: prev.demo_skins_enabled ?? true },
    });
  });
});
