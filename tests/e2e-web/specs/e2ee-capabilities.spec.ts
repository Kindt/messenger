import { test, expect } from "@playwright/test";
import { apiBase, apiLogin } from "../fixtures/auth";

test.describe("e2ee capabilities", () => {
  test("capabilities advertise e2ee schemes", async ({ request }) => {
    const res = await request.get(`${apiBase()}/api/v1/media/capabilities`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    const schemes = body.e2ee_schemes || body.e2eeSchemes || [];
    expect(schemes.length).toBeGreaterThan(0);
    expect(body.mls_status || body.mlsStatus).toBeTruthy();
  });

  test("legacy e2ee key package endpoint requires auth", async ({ request }) => {
    const anon = await request.post(`${apiBase()}/api/v1/e2ee/key-packages`, { data: {} });
    expect([401, 403, 404, 405]).toContain(anon.status());
    const token = await apiLogin(request, "csadmin", "csadmin");
    const authed = await request.get(`${apiBase()}/api/v1/e2ee/key-packages/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 404]).toContain(authed.status());
  });
});
