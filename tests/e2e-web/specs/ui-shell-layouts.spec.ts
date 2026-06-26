import { expect, test } from "@playwright/test";
import { adminBaseUrl, adminNavTo, adminUiLogin } from "../fixtures/admin-ui";
import { apiBase, apiLogin, uiBase } from "../fixtures/auth";
import { uiLogin } from "../fixtures/ui";

const DEV_ORG_ID = "11111111-1111-4111-8111-111111111111";

type BrandingSnapshot = {
  palette?: string;
  demo_skins_enabled?: boolean;
  shell_layout?: string;
  token_overrides?: Record<string, string>;
  custom_css?: string | null;
  brand_title?: string | null;
};

async function getPlatformBranding(
  request: import("@playwright/test").APIRequestContext,
  token: string
): Promise<BrandingSnapshot> {
  const res = await request.get(`${apiBase()}/api/v1/admin/branding/platform`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  return res.json();
}

async function putPlatformBranding(
  request: import("@playwright/test").APIRequestContext,
  token: string,
  body: BrandingSnapshot
): Promise<void> {
  const res = await request.put(`${apiBase()}/api/v1/admin/branding/platform`, {
    headers: { Authorization: `Bearer ${token}` },
    data: body,
  });
  expect(res.ok()).toBeTruthy();
}

async function putOrgBranding(
  request: import("@playwright/test").APIRequestContext,
  token: string,
  orgId: string,
  body: BrandingSnapshot
): Promise<void> {
  const res = await request.put(`${apiBase()}/api/v1/admin/branding/orgs/${orgId}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: body,
  });
  expect(res.ok()).toBeTruthy();
}

async function deleteOrgBranding(
  request: import("@playwright/test").APIRequestContext,
  token: string,
  orgId: string
): Promise<void> {
  await request.delete(`${apiBase()}/api/v1/admin/branding/orgs/${orgId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

function platformRestoreBody(prev: BrandingSnapshot): BrandingSnapshot {
  return {
    palette: prev.palette ?? "korus",
    demo_skins_enabled: prev.demo_skins_enabled ?? true,
    shell_layout: prev.shell_layout ?? "default",
    token_overrides: prev.token_overrides ?? {},
    custom_css: prev.custom_css ?? null,
    brand_title: prev.brand_title ?? null,
  };
}

test.describe("UI shell layouts API (spec 028)", () => {
  test("GET /api/v1/branding includes shell_layout fields", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.shell_layout).toBe("default");
    expect(body.auth_layout).toBe("default");
    expect(body.post_login_layout).toBe("default");
  });

  test("GET /api/v1/branding accepts org_slug query", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/branding?org_slug=missing-org-slug-028`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(typeof body.shell_layout).toBe("string");
  });

  test("GET /api/v1/branding?org_slug=dev merges org shell_layout override", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    try {
      await putOrgBranding(request, token, DEV_ORG_ID, { shell_layout: "auth-split" });
      const res = await request.get(`${apiBase()}/api/v1/branding?org_slug=dev`);
      expect(res.ok()).toBeTruthy();
      const body = await res.json();
      expect(body.shell_layout).toBe("auth-split");
      expect(body.auth_layout).toBe("auth-split");
      expect(body.post_login_layout).toBe("default");
    } finally {
      await deleteOrgBranding(request, token, DEV_ORG_ID);
    }
  });
});

test.describe("Admin UI shell layout (OSL-031)", () => {
  test.use({ baseURL: adminBaseUrl() });

  test("admin saves platform auth-split and client login shows split hero", async ({
    page,
    request,
    browser,
  }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const prev = await getPlatformBranding(request, token);
    try {
      await adminUiLogin(page, "csadmin", "csadmin");
      await adminNavTo(page, "core-ui-branding");
      await expect(page.getByTestId("admin-branding-toolbar")).toBeVisible({ timeout: 15_000 });
      await page.locator("#brandingShellLayout").selectOption("auth-split");
      await page.getByTestId("admin-branding-save").click();
      await expect(page.locator("#brandingMsg")).toContainText(/.+/, { timeout: 15_000 });

      const saved = await getPlatformBranding(request, token);
      expect(saved.shell_layout).toBe("auth-split");

      const client = await browser.newPage();
      await client.goto(uiBase());
      await expect(client.getByTestId("auth-split-hero")).toBeVisible({ timeout: 15_000 });
      await expect(client.locator("html")).toHaveAttribute("data-shell-layout", "auth-split");
      await client.close();
    } finally {
      await putPlatformBranding(request, token, platformRestoreBody(prev));
    }
  });
});

test.describe("UI shell layouts (login + post-login)", () => {
  test("auth-split desktop shows hero column", async ({ page, request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const prev = await getPlatformBranding(request, token);
    try {
      await putPlatformBranding(request, token, {
        ...platformRestoreBody(prev),
        shell_layout: "auth-split",
      });
      await page.goto("/");
      await expect(page.getByTestId("auth-split-hero")).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId("auth-shell")).toHaveClass(/auth-shell-split/);
      await expect(page.locator("html")).toHaveAttribute("data-shell-layout", "auth-split");
    } finally {
      await putPlatformBranding(request, token, platformRestoreBody(prev));
    }
  });

  test("auth-split mobile stacks hero", async ({ page, request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const prev = await getPlatformBranding(request, token);
    try {
      await putPlatformBranding(request, token, {
        ...platformRestoreBody(prev),
        shell_layout: "auth-split",
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto("/");
      await expect(page.getByTestId("auth-split-hero")).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId("auth-submit")).toBeVisible();
    } finally {
      await putPlatformBranding(request, token, platformRestoreBody(prev));
    }
  });

  test("compact applies on post-login shell", async ({ page, request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const prev = await getPlatformBranding(request, token);
    try {
      await putPlatformBranding(request, token, {
        ...platformRestoreBody(prev),
        shell_layout: "compact",
      });
      await uiLogin(page, "csadmin", "csadmin");
      await expect(page.locator("html")).toHaveAttribute("data-shell-layout", "compact");
      await expect(page.getByTestId("logout")).toBeVisible();
    } finally {
      await putPlatformBranding(request, token, platformRestoreBody(prev));
    }
  });

  test("org_slug=dev on login applies org auth-split", async ({ page, request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    try {
      await putOrgBranding(request, token, DEV_ORG_ID, { shell_layout: "auth-split" });
      await page.goto("/?org_slug=dev");
      await expect(page.getByTestId("auth-split-hero")).toBeVisible({ timeout: 15_000 });
      await expect(page.locator("html")).toHaveAttribute("data-shell-layout-source", "auth-split");
    } finally {
      await deleteOrgBranding(request, token, DEV_ORG_ID);
    }
  });
});
