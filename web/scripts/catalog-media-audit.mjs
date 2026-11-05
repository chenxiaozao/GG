import { launchAuditBrowser } from "./audit-browser.mjs";
import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";

const output = path.resolve("../tmp/catalog-media-audit");
await mkdir(output, { recursive: true });
const manifest = JSON.parse(
  await readFile("../server/src/main/resources/catalog-media.json", "utf8"),
);
const response = await fetch("http://127.0.0.1:8080/api/products");
assert.equal(response.status, 200);
const catalog = (await response.json()).data;
const assets = [];
for (const entry of manifest) {
  const product = catalog.find((item) => item.name === entry.name);
  assert.ok(product, entry.name);
  assert.equal(product.image, entry.image);
  assert.equal(product.imageKind, "MODEL_PHOTO");
  assert.equal(product.imageCheckedAt, entry.checkedAt);
  assert.equal(product.sourceUrl, entry.sourceUrl);
  const bytes = await readFile(path.resolve("public", entry.image.slice(1)));
  assets.push({
    ...entry,
    id: product.id,
    bytes: bytes.length,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  });
}
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const results = [];
const variants = [
  { name: "desktop", width: 1440, height: 900 },
  { name: "tablet", width: 820, height: 1180 },
  { name: "mobile", width: 390, height: 844 },
];
for (const viewport of variants) {
  for (const theme of ["classic", "midnight"]) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
    });
    await context.addInitScript(
      (theme) =>
        localStorage.setItem(
          "robotmall-theme",
          JSON.stringify({
            theme,
            layout: "standard",
            accentColor: theme === "midnight" ? "#5aa7ff" : "#d9363e",
            backgroundColor: theme === "midnight" ? "#111827" : "#f5f6f8",
          }),
        ),
      theme,
    );
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    async function check(name, run) {
      try {
        await run();
        results.push({ viewport: viewport.name, theme, name, passed: true });
      } catch (error) {
        results.push({
          viewport: viewport.name,
          theme,
          name,
          passed: false,
          error: error.message,
        });
      }
    }
    for (const asset of assets) {
      await check(asset.name, async () => {
        await page.goto(`http://127.0.0.1:5173/product/${asset.id}`, {
          waitUntil: "networkidle",
        });
        await page.locator(".product-main-image img").waitFor();
        assert.equal(
          await page.locator(".product-main-image img").getAttribute("src"),
          asset.image,
        );
        assert.ok(
          await page
            .locator(".product-main-image img")
            .evaluate((image) => image.complete && image.naturalWidth >= 300),
        );
        assert.ok(
          (await page.locator(".product-media").innerText()).includes(
            asset.checkedAt,
          ),
        );
        assert.equal(
          await page.locator(".source-note a").getAttribute("href"),
          asset.sourceUrl,
        );
        assert.ok(
          (await page.locator(".detail-price-box").innerText()).includes(
            "演示售价",
          ),
        );
        assert.ok(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth + 2,
          ),
        );
        assert.deepEqual(errors, []);
        const contrast = await page.evaluate(() => {
          const canvas = document.createElement("canvas");
          canvas.width = canvas.height = 1;
          const context = canvas.getContext("2d");
          // CSS color-mix may serialize as color(srgb ...), not rgb(...).
          const rgb = (color) => {
            context.clearRect(0, 0, 1, 1);
            context.fillStyle = color;
            context.fillRect(0, 0, 1, 1);
            return [...context.getImageData(0, 0, 1, 1).data].slice(0, 3);
          };
          const luminance = (values) =>
            values
              .map((value) => {
                const channel = value / 255;
                return channel <= 0.04045
                  ? channel / 12.92
                  : ((channel + 0.055) / 1.055) ** 2.4;
              })
              .reduce(
                (sum, channel, index) =>
                  sum + channel * [0.2126, 0.7152, 0.0722][index],
                0,
              );
          return [
            ".detail-tags span",
            ".detail-description",
            ".source-note p",
            ".source-note a",
          ].map((selector) => {
            const element = document.querySelector(selector);
            let parent = element;
            let background = "rgb(255,255,255)";
            while (parent) {
              const color = getComputedStyle(parent).backgroundColor;
              if (color !== "rgba(0, 0, 0, 0)" && color !== "transparent") {
                background = color;
                break;
              }
              parent = parent.parentElement;
            }
            const a = luminance(rgb(getComputedStyle(element).color));
            const b = luminance(rgb(background));
            return {
              selector,
              ratio: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
            };
          });
        });
        for (const item of contrast)
          assert.ok(
            item.ratio >= 4.5,
            `${item.selector} 文字对比度 ${item.ratio.toFixed(2)}`,
          );
        if (["石头 Qrevo Curv", "大疆 Mini 4 Pro"].includes(asset.name))
          await page.screenshot({
            path: path.join(
              output,
              `${viewport.name}-${theme}-${asset.id}.png`,
            ),
            fullPage: true,
          });
      });
    }
    await check("reference-and-illustration-not-mislabeled", async () => {
      for (const kind of ["REFERENCE", "ILLUSTRATION"]) {
        const product = catalog.find((item) => item.imageKind === kind);
        assert.ok(product, kind);
        await page.goto(`http://127.0.0.1:5173/product/${product.id}`, {
          waitUntil: "networkidle",
        });
        assert.ok(
          (await page.locator(".product-media").innerText()).includes(
            product.imageNote,
          ),
        );
        assert.equal(await page.locator(".product-image-review").count(), 0);
      }
    });
    await check("broken-image-falls-back-once", async () => {
      const asset = assets[0];
      let attempts = 0;
      const matcher = (url) =>
        url.pathname === asset.image ||
        url.pathname === "/products/placeholder.svg";
      await page.route(matcher, (route) => {
        attempts++;
        return route.abort();
      });
      await page.goto(`http://127.0.0.1:5173/product/${asset.id}`, {
        waitUntil: "networkidle",
      });
      await page.waitForTimeout(200);
      assert.equal(
        await page.locator(".product-main-image img").getAttribute("src"),
        "/products/placeholder.svg",
      );
      assert.ok(
        (await page.locator(".product-media").innerText()).includes(
          "图片暂时无法加载",
        ),
      );
      assert.equal(await page.locator(".product-image-review").count(), 0);
      assert.equal(attempts, 2);
      await page.unroute(matcher);
    });
    await context.close();
  }
}
const report = {
  generatedAt: new Date().toISOString(),
  assets,
  results,
  note: "本地真实商品读取与 Chrome 界面核验；型号图核验不等于全部参数、官方授权、交易或真实手机验收。",
};
await writeFile(
  path.join(output, "report.json"),
  JSON.stringify(report, null, 2),
);
report.browserCleanup = await shutdown();
await writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
const failures = results.filter((item) => !item.passed);
console.log(
  JSON.stringify(
    {
      assets: assets.length,
      cases: results.length,
      passed: results.length - failures.length,
      failures,
    },
    null,
    2,
  ),
);
process.exitCode = failures.length ? 1 : 0;
process.exit(process.exitCode || 0);
