import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";
import { launchAuditBrowser } from "./audit-browser.mjs";
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const results = [];
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  page.setDefaultTimeout(8000);
  await page.goto("http://127.0.0.1:5173/login", { waitUntil: "networkidle" });
  async function check(name, fn) {
    try {
      await fn();
      results.push({ name, passed: true });
    } catch (error) {
      results.push({ name, passed: false, error: error.message });
    }
  }
  await check("Escape restores entry focus", async () => {
    await page.locator(".account-menu-toggle").click();
    await page.locator(".account-nav-close").waitFor({ state: "visible" });
    await page.keyboard.press("Escape");
    await page.locator("#account-navigation").waitFor({ state: "hidden" });
    assert.equal(
      await page
        .locator(".account-menu-toggle")
        .evaluate((el) => el === document.activeElement),
      true,
    );
  });
  await check("Tab stays inside drawer", async () => {
    await page.locator(".account-menu-toggle").click();
    await page.locator(".account-nav-close").waitFor({ state: "visible" });
    await page.locator(".account-admin-link").focus();
    await page.keyboard.press("Tab");
    assert.equal(
      await page
        .locator(".account-nav-close")
        .evaluate((el) => el === document.activeElement),
      true,
    );
  });
  await check("Guest orders redirect to login", async () => {
    if (!(await page.locator("#account-navigation").isVisible()))
      await page.locator(".account-menu-toggle").click();
    await page.locator('#account-navigation a[href="/orders"]').click();
    await page.waitForURL(
      (url) =>
        url.pathname === "/login" &&
        url.searchParams.get("redirect") === "/orders",
    );
    await page.locator("#account-navigation").waitFor({ state: "hidden" });
    await page.locator(".account-menu-toggle").click();
    await page.screenshot({
      path: "../tmp/workspace-audit/mobile-navigation.png",
      fullPage: true,
    });
  });
} finally {
  await writeFile(
    "../tmp/workspace-audit/keyboard-report.json",
    JSON.stringify({ results }, null, 2),
  );
  await shutdown();
}
console.log(JSON.stringify(results));
process.exit(results.every((r) => r.passed) ? 0 : 1);
