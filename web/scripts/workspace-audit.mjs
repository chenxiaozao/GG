import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { launchAuditBrowser } from "./audit-browser.mjs";
const output = "../tmp/workspace-audit";
await mkdir(output, { recursive: true });
const login = await fetch("http://127.0.0.1:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "user", password: "123456" }),
}).then((r) => r.json());
assert.equal(login.code, 200);
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const results = [];
try {
  for (const width of [1440, 1024, 390])
    for (const theme of ["warm", "midnight"])
      for (const layout of ["standard", "editorial", "compact"]) {
        const context = await browser.newContext({
          viewport: { width, height: 1000 },
          reducedMotion: "reduce",
        });
        await context.addInitScript(
          ({ user, theme, layout }) => {
            localStorage.setItem("token", user.token);
            localStorage.setItem(
              "user",
              JSON.stringify({
                ...user,
                theme,
                interfaceLayout: layout,
                accentColor: theme === "warm" ? "#c65d32" : "#5aa7ff",
                backgroundColor: theme === "warm" ? "#f7f1e7" : "#111827",
              }),
            );
          },
          { user: login.data, theme, layout },
        );
        const page = await context.newPage();
        page.setDefaultTimeout(8000);
        const errors = [];
        page.on("pageerror", (e) => errors.push(e.message));
        try {
          for (const path of [
            "/",
            "/products",
            "/compare",
            "/profile",
            "/cart",
            "/orders",
            "/after-sales",
            "/favorites",
            "/history",
            "/compatibility",
            "/advisor",
            "/trade-in",
            "/profile#appearance",
          ]) {
            await page.goto("http://127.0.0.1:5173" + path, { waitUntil: 'networkidle' });
            await page.locator(".workspace-content > *").first().waitFor();
            assert.equal(
              await page.locator(".top-strip, .profile-nav").count(),
              0,
            );
            if (width <= 900)
              await page.locator(".account-menu-toggle").click();
            await page
              .locator("#account-navigation")
              .waitFor({ state: "visible" });
            assert.equal(
              await page.locator("#account-navigation nav a").count(),
              11,
            );
            const overflow = await page.evaluate(
              () => document.documentElement.scrollWidth > innerWidth + 2,
            );
            assert.equal(overflow, false, path + ": horizontal overflow");
            if (path.startsWith("/profile"))
              assert.equal(
                await page.locator("#account-navigation .is-active").count(),
                1,
              );
            if (width <= 900) await page.locator(".account-nav-close").click();
            else {
              await page.evaluate(() => window.scrollTo(0, 500));
              const bounds = await page
                .locator("#account-navigation")
                .boundingBox();
              assert.ok(
                bounds.y >= 78 && bounds.y <= 112,
                path + ": sidebar must stay visible",
              );
              await page.evaluate(() => window.scrollTo(0, 0));
            }
            if (path === "/products") {
              await page.locator(".catalog-search input").focus();
              const border = await page
                .locator(".catalog-search input")
                .evaluate((el) => ({
                  border: getComputedStyle(el).borderTopWidth,
                  shadow: getComputedStyle(el).boxShadow,
                }));
              assert.equal(border.border, "0px");
              assert.equal(border.shadow, "none");
            }
            if (path === "/cart") {
              for (const stepper of await page
                .locator(".quantity-stepper")
                .all()) {
                const box = await stepper.boundingBox();
                const plus = await stepper
                  .locator("button")
                  .last()
                  .boundingBox();
                assert.ok(
                  plus.x + plus.width <= box.x + box.width + 1,
                  "quantity button clipped",
                );
              }
            }
            if (
              width === 1440 &&
              theme === "warm" &&
              layout === "standard" &&
              ["/profile", "/cart", "/products"].includes(path)
            )
              await page.screenshot({
                path: `${output}/${path.slice(1)}.png`,
                fullPage: true,
              });
          }
          // Route changes must preserve the same shared navigation node.
          if (width <= 900) await page.locator(".account-menu-toggle").click();
          await page
            .locator("#account-navigation")
            .evaluate((el) => (el.dataset.testIdentity = "persistent"));
          await page.locator('#account-navigation a[href="/cart"]').click();
          await page.waitForURL("**/cart");
          assert.equal(
            await page
              .locator("#account-navigation")
              .getAttribute("data-test-identity"),
            "persistent",
          );
          if (width <= 900)
            assert.equal(
              await page.locator("#account-navigation").isVisible(),
              false,
            );
          assert.deepEqual(errors, []);
          results.push({ width, theme, layout, passed: true });
        } catch (error) {
          results.push({
            width,
            theme,
            layout,
            passed: false,
            error: error.message,
          });
        }
        console.log(JSON.stringify(results.at(-1)));
        await writeFile(
          `${output}/report.json`,
          JSON.stringify(
            { generatedAt: new Date().toISOString(), results },
            null,
            2,
          ),
        );
        await context.close();
      }
} finally {
  await writeFile(
    `${output}/report.json`,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        note: "Shared sidebar, navigation persistence, focus and quantity geometry; read-only, no order, profile save or external AI call.",
        results,
      },
      null,
      2,
    ),
  );
  await shutdown();
}
console.log(
  `${results.filter((r) => r.passed).length}/${results.length} passed`,
);
process.exit(results.every((r) => r.passed) ? 0 : 1);
