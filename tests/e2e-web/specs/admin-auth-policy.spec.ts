import { test, expect } from "@playwright/test";
import { apiBase, apiLogin } from "../fixtures/auth";

test.describe("admin auth policy", () => {
  test("admin GET auth-policy returns JSON", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");

    const orgRes = await request.get(`${apiBase()}/api/v1/admin/organizations`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(orgRes.ok()).toBeTruthy();
    const orgs = await orgRes.json();
    const orgId = orgs[0]?.id || orgs[0]?.org_id;
    expect(orgId).toBeTruthy();

    const res = await request.get(`${apiBase()}/api/v1/admin/orgs/${orgId}/auth-policy`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.org_id || body.orgId).toBeTruthy();
    expect(typeof (body.allow_local_password ?? body.allowLocalPassword)).toBe("boolean");
    expect(typeof (body.allow_self_registration ?? body.allowSelfRegistration)).toBe("boolean");
    expect(Array.isArray(body.providers)).toBeTruthy();
  });
});
