import { test, expect } from "@playwright/test";
import type { Performance } from "../src/analyticsTypes";
const money = (value: string) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(
    Number(value),
  );

test("historical performance uses real dated records, shows gaps and excludes future trades", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (entry) => {
    if (
      entry.type() === "error" &&
      /Content Security Policy|Refused to apply|Refused to execute/i.test(
        entry.text(),
      )
    )
      errors.push(entry.text());
  });
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page
    .getByLabel("Email address")
    .fill(`performance-${testInfo.project.name}-${Date.now()}@example.test`);
  await page
    .getByLabel("Password", { exact: true })
    .fill("phase-two-test-password");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Historical lens");
  await page.getByLabel("Initial simulated cash (USD)").fill("1000.00");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await expect(page.getByTestId("total-value")).toHaveText("$1,000.00");
  await expect(page.getByTestId("total-pnl")).toHaveText("$0.00");
  await expect(page.getByTestId("initial-return")).toHaveText("0.00%");
  await expect(page.getByLabel("Market-value allocation")).toContainText(
    "100.00%",
  );
  const analytics = page.getByRole("region", {
    name: "Portfolio valuation and performance",
  });
  async function range(from: string, to: string) {
    await page.getByLabel("Performance start date").fill(from);
    await page.getByLabel("Valuation / end date").fill(to);
    await page
      .getByRole("button", { name: "Apply dates", exact: true })
      .click();
    await expect(analytics.locator(".valuation-date")).toContainText(
      `Displayed range: ${from} — ${to}`,
    );
  }
  await range("2024-01-01", "2024-01-08");
  await page.getByLabel("Execution date").fill("2024-01-02");
  await page.getByLabel("Quantity", { exact: true }).fill("10");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Bought 10 ALTA" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sell", exact: true }).click();
  await page.getByLabel("Execution date").fill("2024-01-03");
  await page.getByLabel("Quantity", { exact: true }).fill("4");
  await page
    .getByRole("button", { name: "Simulate sell", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Sold 4 ALTA" }),
  ).toBeVisible();
  const list = (await (await page.request.get("/api/portfolios")).json()) as {
    items: { id: string; name: string }[];
  };
  const id = list.items.find((p) => p.name === "Historical lens")!.id;
  const response = await page.request.get(
    `/api/portfolios/${id}/performance?from=2024-01-01&to=2024-01-08`,
  );
  expect(response.ok()).toBe(true);
  const data = (await response.json()) as Performance;
  expect(data.series.missingDays).toBe(2);
  for (const [testId, value] of [
    ["total-value", data.summary.totalValue],
    ["dated-cash", data.summary.cash],
    ["market-value", data.summary.marketValue],
    ["total-pnl", data.summary.totalPnl],
    ["realized-pnl", data.summary.realizedPnl],
    ["unrealized-pnl", data.summary.unrealizedPnl],
  ] as const) {
    expect(value).not.toBeNull();
    await expect(page.getByTestId(testId)).toHaveText(money(value!));
  }
  await expect(page.getByTestId("initial-capital")).toHaveText("$1,000.00");
  await expect(
    page
      .getByRole("table", {
        name: "Marked holdings and position performance in USD",
      })
      .getByRole("cell", { name: "6", exact: true }),
  ).toBeVisible();
  await analytics
    .getByText("View dated values and price gaps", { exact: true })
    .click();
  const history = page.getByRole("table", {
    name: "Historical portfolio values in USD",
  });
  await expect(
    history.getByRole("row").filter({ hasText: "2024-01-01" }),
  ).toContainText("$1,000.00");
  await expect(
    history.getByRole("row").filter({ hasText: "2024-01-06" }),
  ).toContainText("Missing: ALTA");
  await expect(
    history.getByRole("row").filter({ hasText: "2024-01-06" }),
  ).toContainText("Unavailable");
  await analytics
    .getByText("View dated values and price gaps", { exact: true })
    .click();

  await range("2024-01-01", "2024-01-01");
  await expect(page.getByTestId("total-value")).toHaveText("$1,000.00");
  await expect(page.getByTestId("realized-pnl")).toHaveText("$0.00");
  await expect(
    page.getByRole("heading", { name: "No positions at this date" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "One valued observation" }),
  ).toBeVisible();
  await range("2024-01-06", "2024-01-06");
  await expect(page.getByTestId("total-value")).toHaveText("Unavailable");
  await expect(
    analytics.getByText(
      /Valuation incomplete: no exact closing price for ALTA/,
    ),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "No valued observations in this range" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Allocation unavailable" }),
  ).toBeVisible();
  await expect(page.getByTestId("dated-cash")).toHaveText(
    money(data.summary.cash),
  );

  await page.getByLabel("Performance start date").fill("2024-01-08");
  await page.getByLabel("Valuation / end date").fill("2024-01-02");
  await page.getByRole("button", { name: "Apply dates", exact: true }).click();
  await expect(analytics.getByRole("alert")).toContainText(
    "Choose an ordered date range",
  );
  await range("2024-01-02", "2024-01-05");
  const figure = page.getByRole("figure", {
    name: "Historical portfolio value chart",
  });
  await expect(figure).toBeVisible();
  const plot = figure.locator(".recharts-wrapper");
  const box = await plot.boundingBox();
  expect(box).not.toBeNull();
  await plot.hover({ position: { x: box!.width * 0.65, y: 120 } });
  await expect(page.locator(".chart-tooltip")).toContainText("synthetic close");
  // Exercise the chart's keyboard layer as well as mouse/touch emulation.
  const keyboardChart = figure.locator("[role='application']");
  await keyboardChart.focus();
  await keyboardChart.press("ArrowRight");
  await expect(page.locator(".chart-tooltip")).toBeVisible();

  // Failure injection contains no fabricated financial values; retry must fetch real backend data.
  const route = "**/api/portfolios/*/performance?*";
  await page.route(route, (request) =>
    request.fulfill({
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({
        code: "TEST_OUTAGE",
        detail: "Temporary analytics test outage.",
      }),
    }),
  );
  await page.getByRole("button", { name: "1M", exact: true }).click();
  await expect(analytics.getByRole("alert")).toContainText(
    "Temporary analytics test outage",
  );
  await page.unroute(route);
  await page
    .getByRole("button", { name: "Retry analytics", exact: true })
    .click();
  await expect(page.getByTestId("total-value")).not.toHaveText("Unavailable");
  await range("2024-01-01", "2024-01-08");
  await page.getByRole("heading", { name: "Portfolio desk." }).hover();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("phase2-performance.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(errors).toEqual([]);
});
