import fs from "node:fs";
import path from "node:path";
import { expect, test } from "../fixtures/test-with-qemu-wait";
import { resetClickCoverage } from "../fixtures/click-crawl";
import { resetConsoleGuardStats } from "../fixtures/console-guard";
import { requireAvatarApi } from "../fixtures/avatar";
import { filterScenariosByProfile, runUiTestScenario } from "../fixtures/ui-tests-runner";
import {
  flushUiTestsLiveProgress,
  initUiTestsSummary,
  recordUiTestResult,
  writeUiTestsSummary,
} from "../fixtures/ui-tests-summary";
import type { UiTestsManifest } from "../fixtures/ui-tests-types";

const manifestPath = path.join(__dirname, "../scenarios/ui-tests-manifest.json");
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8")) as UiTestsManifest;
const profile = process.env.UI_TESTS_PROFILE || "smoke";
const scenarios = filterScenariosByProfile(manifest.scenarios, profile);
const startAfterIndex = Math.max(0, Number(process.env.UI_TESTS_START_AFTER_INDEX || "0"));
const activeScenarios =
  startAfterIndex > 0 && startAfterIndex < scenarios.length
    ? scenarios.slice(startAfterIndex)
    : scenarios;
if (startAfterIndex > 0) {
  // eslint-disable-next-line no-console
  console.log(
    `[ui-tests] resume after index ${startAfterIndex}: ${activeScenarios.length}/${scenarios.length} scenarios remaining`
  );
}
const clkSelectors = manifest.scenarios.filter((s) => s.click?.selector).map((s) => s.click!.selector);

let avatarApiAvailable = true;
const runCounts = { passed: 0, failed: 0, skipped: 0 };
let testIndex = startAfterIndex;

test.describe("тесты UI", () => {
  test.setTimeout(180_000);

  test.beforeAll(async ({ request }) => {
    initUiTestsSummary(profile, scenarios.length);
    resetClickCoverage();
    resetConsoleGuardStats();
    try {
      await requireAvatarApi(request);
    } catch {
      avatarApiAvailable = false;
    }
  });

  for (const scenario of activeScenarios) {
    test(`${scenario.id} — ${scenario.title}`, async ({ browser, page, request }, testInfo) => {
      testIndex++;
      flushUiTestsLiveProgress({
        status: "running",
        index: testIndex,
        total: scenarios.length,
        currentId: scenario.id,
        ...runCounts,
      });

      if (scenario.blockedBy) {
        recordUiTestResult(scenario.layer, "skipped");
        runCounts.skipped++;
        flushUiTestsLiveProgress({
          status: "running",
          index: testIndex,
          total: scenarios.length,
          currentId: scenario.id,
          ...runCounts,
        });
        test.fixme(true, scenario.blockedBy);
        return;
      }
      if (scenario.requiresAvatarApi && !avatarApiAvailable) {
        recordUiTestResult(scenario.layer, "skipped");
        runCounts.skipped++;
        flushUiTestsLiveProgress({
          status: "running",
          index: testIndex,
          total: scenarios.length,
          currentId: scenario.id,
          ...runCounts,
        });
        test.fixme(true, "Avatar API not on server — qemu-sync-api-core required");
        return;
      }
      try {
        await runUiTestScenario(scenario, { browser, page, request, testInfo });
        recordUiTestResult(scenario.layer, "passed");
        runCounts.passed++;
      } catch (err) {
        recordUiTestResult(scenario.layer, "failed");
        runCounts.failed++;
        throw err;
      } finally {
        flushUiTestsLiveProgress({
          status: "running",
          index: testIndex,
          total: scenarios.length,
          currentId: scenario.id,
          ...runCounts,
        });
      }
    });
  }

  test.afterAll(async () => {
    await writeUiTestsSummary(
      {
        passed: runCounts.passed,
        failed: runCounts.failed,
        skipped: runCounts.skipped,
        total: scenarios.length,
      },
      clkSelectors,
      { completed: testIndex }
    );
  });
});
