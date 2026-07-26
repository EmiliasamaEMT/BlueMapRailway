import { expect, test } from "@playwright/test";

test("defaults to browse mode and renders the map", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("body")).not.toHaveClass(/admin-mode/);
  await expect(page.locator("#rail-canvas")).toBeVisible();
  await expect(page.locator("#runtime-pill")).toContainText("已更新");
});

test("valid token opens management mode", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "进入管理" }).click();
  await page.locator("#login-token").fill("test-token");
  await page.getByRole("button", { name: "验证并进入" }).click();
  await expect(page.locator("body")).toHaveClass(/admin-mode/);
  await expect(page.getByRole("tab", { name: "隐藏与裁切" })).toBeVisible();
});

test("route editor posts the stable API contract", async ({ page, request }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "进入管理" }).click();
  await page.locator("#login-token").fill("test-token");
  await page.getByRole("button", { name: "验证并进入" }).click();
  await page.getByRole("button", { name: "编辑" }).first().click();
  await page.locator("#route-name").fill("主线改");
  await page.getByRole("button", { name: "保存线路" }).click();
  const result = await request.get("/__mutations");
  const mutations = (await result.json()).mutations;
  expect(mutations.some((entry) => entry.path === "/api/route" && entry.body.name === "主线改")).toBeTruthy();
});

test("layout remains inside both desktop and narrow viewports", async ({ page }) => {
  await page.goto("/");
  const dimensions = await page.evaluate(() => ({
    bodyWidth: document.body.scrollWidth,
    viewportWidth: window.innerWidth,
    bodyHeight: document.body.scrollHeight,
    viewportHeight: window.innerHeight
  }));
  expect(dimensions.bodyWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
  expect(dimensions.bodyHeight).toBeLessThanOrEqual(dimensions.viewportHeight);
});
