import { test, expect } from "@playwright/test";
import type { RiskSummary, StressResult } from "../src/riskTypes";
const money = (v: string) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    Number(v),
  );
const percent = (v: string) => `${Number(v).toFixed(2)}%`;

test("Risk Lab uses real historical risk, compares read-only scenarios and explains incomplete states", async ({
  page,
}, testInfo) => {
  test.setTimeout(90000);
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page
    .getByLabel("Email address")
    .fill(`risk-${testInfo.project.name}-${Date.now()}@example.test`);
  await page
    .getByLabel("Password", { exact: true })
    .fill("phase-three-test-password");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Risk laboratory");
  await page.getByLabel("Initial simulated cash (USD)").fill("10000");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await page.getByRole("button", { name: "Risk Lab", exact: true }).click();
  const lab = page.getByRole("region", { name: "Risk Lab", exact: true });
  await expect(page.getByTestId("risk-var99")).toHaveText("$0.00");
  await expect(
    lab.getByText(/Cash only · zero asset-price exposure/).first(),
  ).toBeVisible();
  await expect(page.getByTestId("stress-value")).toHaveText("$10,000.00");
  await page.getByLabel("Execution date").fill("2024-01-02");
  await page.getByLabel("Quantity", { exact: true }).fill("10");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Bought 10 ALTA" }),
  ).toBeVisible();
  await page.getByLabel("Fictional asset").selectOption("BRIM");
  await page.getByLabel("Quantity", { exact: true }).fill("5");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Bought 5 BRIM" }),
  ).toBeVisible();
  const list = await (await page.request.get("/api/portfolios")).json();
  const id = list.items.find(
    (p: { name: string }) => p.name === "Risk laboratory",
  ).id;
  const summary = (await (
    await page.request.get(`/api/portfolios/${id}/risk/summary`)
  ).json()) as RiskSummary;
  expect(summary.analysis.returns.points.length).toBeGreaterThanOrEqual(500);
  await expect(page.getByTestId("risk-daily-vol")).toHaveText(
    percent(summary.analysis.dailyVolatility.value!),
  );
  await expect(page.getByTestId("risk-annual-vol")).toHaveText(
    percent(summary.analysis.annualVolatility.value!),
  );
  await expect(page.getByTestId("risk-var95")).toHaveText(
    money(summary.analysis.var95.usd!),
  );
  await expect(page.getByTestId("risk-var99")).toHaveText(
    money(summary.analysis.var99.usd!),
  );
  await expect(page.getByTestId("risk-drawdown")).toHaveText(
    percent(summary.drawdown.percent!),
  );
  expect(Number(summary.analysis.var99.usd)).toBeGreaterThanOrEqual(
    Number(summary.analysis.var95.usd),
  );
  await expect(
    page.getByRole("figure", { name: "Historical shock return distribution" }),
  ).toBeVisible();
  const term = lab.getByRole("button", { name: "Explain VaR 99%" });
  await term.focus();
  await expect(
    lab.getByRole("tooltip").filter({ hasText: "Nearest-rank 99th" }),
  ).toBeVisible();
  await term.press("Tab");
  const historyBefore = await (
    await page.request.get(`/api/portfolios/${id}/orders`)
  ).json();
  await lab
    .getByRole("slider", { name: "Common asset shock", exact: true })
    .fill("-10");
  await lab.getByLabel("Override BRIM").check();
  const override = lab.getByRole("slider", { name: "BRIM shock", exact: true });
  await override.focus();
  await override.press("ArrowRight");
  await override.fill("20");
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const stress = (await (
    await page.request.post(`/api/portfolios/${id}/risk/stress`, {
      headers: { "X-XSRF-TOKEN": csrf.token },
      data: {
        date: summary.date,
        scenarios: [
          {
            name: "Manual",
            commonShockPercent: "-10",
            overrides: { BRIM: "20" },
          },
        ],
      },
    })
  ).json()) as StressResult[];
  await expect(page.getByTestId("stress-value")).toHaveText(
    money(stress[0].stressedValue),
  );
  const contributions = lab.getByRole("table", {
    name: "Stress asset assumptions and contributions",
  });
  await expect(
    contributions.getByRole("row").filter({ hasText: "BRIM" }),
  ).toContainText("20.00%");
  await lab.getByLabel("Scenario name").fill("Mixed view");
  await lab
    .getByRole("button", { name: "Save comparison", exact: true })
    .click();
  await expect(
    lab.getByRole("button", { name: "Inspect Mixed view", exact: true }),
  ).toBeVisible();
  await lab.getByRole("button", { name: "Reset to zero", exact: true }).click();
  await expect(page.getByTestId("stress-value")).toHaveText(
    money(summary.valuation.totalValue!),
  );
  await expect(page.getByTestId("stress-change")).toContainText(
    "$0.00 / 0.00%",
  );
  expect(
    await (await page.request.get(`/api/portfolios/${id}/orders`)).json(),
  ).toEqual(historyBefore);
  async function analyze(date: string, window: string) {
    await lab.getByLabel("Risk valuation date", { exact: true }).fill(date);
    await lab.getByLabel("Risk lookback window").selectOption(window);
    await lab
      .getByRole("button", { name: "Analyze risk", exact: true })
      .click();
    await expect(page.getByTestId("risk-window")).toContainText(date);
  }
  await analyze("2024-01-05", "30");
  await expect(page.getByTestId("risk-daily-vol")).toHaveText("Unavailable");
  await expect(page.getByTestId("risk-var95")).toHaveText("Unavailable");
  await expect(
    lab.getByText(/Insufficient observations/).first(),
  ).toBeVisible();
  await analyze("2024-01-06", "30");
  await expect(lab.getByRole("alert")).toContainText(
    "Exact selected-date prices are missing",
  );
  await expect(lab.getByText(/Stress simulation unavailable/)).toBeVisible();
  await analyze("2024-01-01", "30");
  await expect(page.getByTestId("risk-var99")).toHaveText("$0.00");
  await expect(page.getByTestId("stress-value")).toHaveText("$10,000.00");
  const route = "**/api/portfolios/*/risk/summary?*";
  await page.route(route, (r) =>
    r.fulfill({
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({
        code: "TEST_OUTAGE",
        detail: "Temporary risk test outage.",
      }),
    }),
  );
  await lab.getByRole("button", { name: "Analyze risk", exact: true }).click();
  await expect(lab.getByRole("alert")).toContainText(
    "Temporary risk test outage",
  );
  await page.unroute(route);
  await lab.getByRole("button", { name: "Retry risk analytics" }).click();
  await expect(page.getByTestId("risk-var99")).toHaveText("$0.00");
  await analyze("2025-12-31", "730");
  await expect(page.getByTestId("stress-value")).toHaveText(
    money(summary.valuation.totalValue!),
  );
  await lab
    .getByRole("button", { name: "Severe selloff -25%", exact: true })
    .click();
  await expect(page.getByTestId("stress-change")).not.toContainText(
    "$0.00 / 0.00%",
  );
  await page
    .getByRole("heading", { name: "Understand the exposure." })
    .scrollIntoViewIfNeeded();
  await page.screenshot({
    path: testInfo.outputPath("phase3-risk.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(errors).toEqual([]);
});
