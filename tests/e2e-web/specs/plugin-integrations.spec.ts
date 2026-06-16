import { test, expect } from "@playwright/test";
import { apiBase, apiLogin } from "../fixtures/auth";

const integrationsBase = () =>
  process.env.KORUS_INTEGRATIONS_GATE_URL || "http://127.0.0.1:18190";

test.describe("plugin integrations", () => {
  test("admin lists plugin presets", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const res = await request.get(`${apiBase()}/api/v1/admin/plugins/presets`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body.presets)).toBeTruthy();
    expect(body.presets.some((p: { id: string }) => p.id === "1c-bridge")).toBeTruthy();
  });

  test("admin org plugin policy roundtrip", async ({ request }) => {
    const token = await apiLogin(request, "csadmin", "csadmin");
    const orgRes = await request.get(`${apiBase()}/api/v1/admin/organizations`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(orgRes.ok()).toBeTruthy();
    const orgs = await orgRes.json();
    const orgId = orgs[0]?.id || orgs[0]?.org_id;
    expect(orgId).toBeTruthy();

    const put = await request.post(
      `${apiBase()}/api/v1/admin/plugins/policies?org_id=${orgId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        data: {
          allowed_preset_ids: ["echo-sidecar", "1c-bridge"],
          llm_mode: "hybrid",
          ocr_on_prem_only: true,
        },
      }
    );
    expect(put.ok()).toBeTruthy();
    const policy = await put.json();
    expect(policy.llm_mode).toBe("hybrid");

    const get = await request.get(
      `${apiBase()}/api/v1/admin/plugins/policies?org_id=${orgId}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    expect(get.ok()).toBeTruthy();
    const loaded = await get.json();
    expect(loaded.allowed_preset_ids).toContain("1c-bridge");
  });

  test("integrations gateway health when stack up", async ({ request }) => {
    test.skip(!process.env.KORUS_INTEGRATIONS_GATE_URL, "set KORUS_INTEGRATIONS_GATE_URL for live gate");
    const res = await request.get(`${integrationsBase()}/health`);
    expect(res.ok()).toBeTruthy();
  });
});
