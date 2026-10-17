import { test, expect } from "@playwright/test";

test("register, fund, buy, sell, inspect records, and sign in again", async ({
  page,
  context,
}, testInfo) => {
  const email = `journey-${testInfo.project.name}-${Date.now()}@example.test`;
  const password = "a-strong-test-password";
  const consoleErrors: string[] = [];
  page.on("pageerror", (e) => consoleErrors.push(e.message));
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Portfolio desk." }),
  ).toBeVisible();
  await expect(
    page.getByText("All assets and prices are deterministic synthetic data.", {
      exact: false,
    }),
  ).toBeVisible();
  const session = (await context.cookies()).find(
    (c) => c.name === "JSESSIONID",
  );
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Strict");
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Core conviction");
  await page.getByLabel("Initial simulated cash (USD)").fill("10000.00");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await expect(
    page.getByText("$10,000.00", { exact: true }).first(),
  ).toBeVisible();
  await page.getByLabel("Execution date").fill("2024-01-02");
  await page.getByLabel("Quantity", { exact: true }).fill("10");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Bought 10 ALTA" }),
  ).toBeVisible();
  await expect(
    page.getByRole("table", {
      name: "Open holdings and FIFO cost basis in USD",
    }),
  ).toContainText("ALTA");
  await page.getByRole("button", { name: "Sell", exact: true }).click();
  await page.getByLabel("Quantity", { exact: true }).fill("50");
  await page
    .getByRole("button", { name: "Simulate sell", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "cannot sell more than you own",
  );
  await page.getByLabel("Quantity", { exact: true }).fill("4");
  await page
    .getByRole("button", { name: "Simulate sell", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Sold 4 ALTA" }),
  ).toContainText("Realized P/L");
  const holdings = page.getByRole("table", {
    name: "Open holdings and FIFO cost basis in USD",
  });
  await expect(
    holdings.getByRole("cell", { name: "6", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Filter this page").fill("SELL");
  await expect(
    page
      .getByRole("table", { name: "Simulated transactions" })
      .getByRole("row"),
  ).toHaveCount(2);
  await page.getByLabel("Filter this page").fill("");
  await page.getByRole("button", { name: "Cash ledger", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Cash ledger in USD" }),
  ).toContainText("FUNDING");
  await page.getByRole("button", { name: "Audit events", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Portfolio audit events" }),
  ).toContainText("SIMULATED_SELL");
  await page.getByRole("button", { name: "Transactions", exact: true }).click();
  await expect(
    page.getByRole("table", { name: "Simulated transactions" }),
  ).toContainText("SELL");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: testInfo.outputPath("portfolio-desk.png"),
    fullPage: true,
  });
  // No page-level horizontal overflow at desktop or mobile widths; tables scroll within their own region.
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("table", {
      name: "Open holdings and FIFO cost basis in USD",
    }),
  ).toContainText("ALTA");
  await expect(
    holdings.getByRole("cell", { name: "6", exact: true }),
  ).toBeVisible();
  expect(consoleErrors).toEqual([]);
});

test("missing synthetic price disables execution", async ({
  page,
}, testInfo) => {
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page
    .getByLabel("Email address")
    .fill(`gap-${testInfo.project.name}-${Date.now()}@example.test`);
  await page
    .getByLabel("Password", { exact: true })
    .fill("a-strong-test-password");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Gap check");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await page.getByLabel("Execution date").fill("2024-01-06");
  await expect(page.getByText("Unavailable", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Simulate buy", exact: true }),
  ).toBeDisabled();
});
