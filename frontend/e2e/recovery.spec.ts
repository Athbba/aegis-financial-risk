import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import path from "node:path";

test("pending order survives absent confirmation, refresh and a server restart without duplicate execution", async ({
  page,
  context,
}, testInfo) => {
  test.setTimeout(120000);
  const email = `recovery-${testInfo.project.name}-${Date.now()}@example.test`,
    password = "recovery-test-password";
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Recovery fixture");
  await page.getByLabel("Initial simulated cash (USD)").fill("10000");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await expect(page.getByTestId("total-value")).toHaveText("$10,000.00");
  const list = await (await page.request.get("/api/portfolios")).json();
  const id = list.items.find(
    (p: { name: string }) => p.name === "Recovery fixture",
  ).id;
  const route = "**/api/portfolios/*/orders";
  const keys: string[] = [];
  // First attempt never reaches the server: a missing lookup is not a reason to change keys.
  await page.route(route, async (r) => {
    if (r.request().method() === "POST") {
      keys.push(r.request().headers()["idempotency-key"]);
      await r.abort("failed");
    } else await r.continue();
  });
  await page.getByLabel("Execution date").fill("2024-01-02");
  await page.getByLabel("Quantity", { exact: true }).fill("10");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Retry saved order", exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Retry saved order", exact: true }),
  ).toBeEnabled();
  await expect(page.getByLabel("Quantity", { exact: true })).toBeDisabled();
  const before = await (
    await page.request.get(`/api/portfolios/${id}/orders`)
  ).json();
  expect(before.total).toBe(0);
  await page.unroute(route);
  // This time PostgreSQL commits, but the browser loses the response.
  await page.route(route, async (r) => {
    if (r.request().method() === "POST") {
      keys.push(r.request().headers()["idempotency-key"]);
      const response = await r.fetch();
      expect(response.status()).toBe(200);
      await r.abort("failed");
    } else await r.continue();
  });
  await page
    .getByRole("button", { name: "Retry saved order", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Confirmation is uncertain",
  );
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  await page.unroute(route);
  if (process.env.AEGIS_E2E_RESTART_BACKEND === "true") {
    execFileSync("docker", ["compose", "restart", "-t", "15", "backend"], {
      cwd: path.resolve(import.meta.dirname, "../.."),
      timeout: 45000,
      stdio: "pipe",
    });
    let ready = false;
    for (let i = 0; i < 60; i++) {
      try {
        const res = await page.request.get(
          "http://localhost:8080/actuator/health/readiness",
        );
        if (res.ok()) {
          ready = true;
          break;
        }
      } catch {}
      await new Promise((r) => setTimeout(r, 500));
    }
    expect(ready).toBe(true);
  }
  await page.reload();
  if (process.env.AEGIS_E2E_RESTART_BACKEND === "true") {
    await page.getByLabel("Email address").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
  }
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: "Recovered confirmation: Bought 10 ALTA" }),
  ).toBeVisible();
  await expect(page.getByLabel("Quantity", { exact: true })).toBeEnabled();
  expect(
    await page.evaluate(
      (key) => sessionStorage.getItem(`aegis.pending-order.${key}`),
      id,
    ),
  ).toBeNull();
  const after = await (
    await page.request.get(`/api/portfolios/${id}/orders`)
  ).json();
  expect(after.total).toBe(1);
  expect(after.items[0].idempotencyKey).toBe(keys[0]);
  const ledger = await (
    await page.request.get(`/api/portfolios/${id}/ledger`)
  ).json();
  expect(ledger.total).toBe(2);
  const audit = await (
    await page.request.get(`/api/portfolios/${id}/audit`)
  ).json();
  expect(audit.total).toBe(2);
  const reconciliation = await (
    await page.request.get(`/api/portfolios/${id}/reconciliation`)
  ).json();
  expect(reconciliation.status).toBe("CONSISTENT");
  // Login intentionally clears the old CSRF token; fetch the replacement before inspecting it.
  await page.request.get("/api/auth/csrf");
  const cookies = await context.cookies();
  expect(cookies.find((c) => c.name === "XSRF-TOKEN")?.sameSite).toBe("Strict");
  expect(errors).toEqual([]);
});
