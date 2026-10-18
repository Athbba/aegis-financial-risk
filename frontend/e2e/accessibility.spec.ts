import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("keyboard, contrast and mobile review use real synthetic dashboard", async ({
  page,
}, testInfo) => {
  test.setTimeout(90000);
  await page.goto("/");
  await page.getByLabel("Email address").waitFor();
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("link", { name: "Skip to content" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await page
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  const email = `presentation-${testInfo.project.name}-${Date.now()}@example.test`;
  await page.getByLabel("Email address").fill(email);
  await page
    .getByLabel("Password", { exact: true })
    .fill("synthetic-presentation-password");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create your first portfolio" })
    .click();
  await page.getByLabel("Portfolio name").fill("Synthetic teaching portfolio");
  await page.getByLabel("Initial simulated cash (USD)").fill("10000");
  await page
    .getByRole("button", { name: "Create portfolio", exact: true })
    .click();
  await page.getByLabel("Execution date").fill("2024-01-02");
  await page.getByLabel("Quantity", { exact: true }).fill("10");
  await page.getByRole("button", { name: "Simulate buy", exact: true }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Bought 10 ALTA" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Risk Lab", exact: true }).click();
  await expect(page.getByTestId("risk-var99")).not.toHaveText("—");
  const slider = page.getByRole("slider", {
    name: "Common asset shock",
    exact: true,
  });
  await slider.focus();
  await slider.press("ArrowRight");
  await expect(slider).toBeFocused();
  await page.getByRole("button", { name: "Explain VaR 99%" }).focus();
  await expect(
    page.getByRole("tooltip").filter({ hasText: "Nearest-rank 99th" }),
  ).toBeVisible();
  await page.keyboard.press("Tab");
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("heading", { name: "Understand the exposure." }).click();
  await page.screenshot({
    path: testInfo.outputPath("phase5-synthetic-demo.png"),
    fullPage: true,
    maskColor: "#111824",
    mask: [page.getByText(email, { exact: true })],
  });
});
