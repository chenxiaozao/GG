import { launchAuditBrowser } from "./audit-browser.mjs";
import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

// All API traffic is fulfilled in memory. This suite never alters the user's database.
const output = path.resolve("../tmp/service-ui-audit");
await mkdir(output, { recursive: true });
const { browser, shutdown } = await launchAuditBrowser({
  headless: true,
  executablePath:
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});
const report = [];
const viewports = [
  { name: "desktop", width: 1440, height: 900 },
  { name: "tablet", width: 820, height: 1180 },
  { name: "mobile", width: 390, height: 844 },
];

function fixture() {
  const products = Array.from({ length: 25 }, (_, i) => ({
    id: i + 1,
    name: i === 0 ? "石头 G20S Ultra" : `家用清洁机器人 ${i + 1}`,
    subtitle: "适合养宠家庭与日常清洁，长文本用于检验商品布局是否自然换行",
    categoryId: 1,
    brandId: 1,
    categoryName: "地面清洁",
    brandName: "石头科技",
    type: "ROBOT",
    price: 5999,
    marketPrice: 6999,
    stock: 30,
    enabled: i !== 24,
    image: "/products/placeholder.svg",
    rating: 4.8,
    sales: 100,
    parametersJson: '{"运行噪音":"60dB","耗材余量":0}',
    enduranceMinutes: 120,
    payloadKg: 0,
    intelligenceLevel: "HIGH",
    skuOptions: "标准版,高配版",
    warranty: "整机保修一年",
    shippingNote: "演示配送说明",
    description: "商品资料说明",
  }));
  const orders = ["PENDING", "PAID", "SHIPPED", "COMPLETED", "CANCELLED"].map(
    (status, i) => ({
      order: {
        id: 101 + i,
        orderNo: `UI-ORDER-${101 + i}`,
        userId: 2,
        status,
        totalAmount: 5999,
        createdAt: "2026-09-16T10:00:00",
        receiverInfo:
          "测试收货人，13800138000，江苏省南京市鼓楼区测试地址（仅浏览器测试数据）",
        logisticsCompany: status === "SHIPPED" ? "演示物流" : "",
        logisticsNo: status === "SHIPPED" ? "UITEST103" : "",
      },
      items: [
        {
          id: 201 + i,
          productId: i + 1,
          productName: products[i].name,
          price: 5999,
          quantity: 1,
          selectedSku: "标准版",
        },
      ],
    }),
  );
  return {
    products,
    orders,
    cart: [
      {
        id: 1,
        productId: 1,
        quantity: 1,
        selectedSku: "标准版 · 含基站、清洁刷与演示耗材套装",
        product: products[0],
      },
    ],
    addresses: [
      {
        id: 11,
        receiver: "测试收货人",
        phone: "13800138000",
        province: "江苏省",
        city: "南京市",
        district: "鼓楼区",
        detail: "测试路 1 号（仅浏览器测试数据）",
        defaultAddress: true,
      },
    ],
    favorites: products.slice(0, 4),
    history: products
      .slice(0, 4)
      .map((p) => ({ product: p, viewTime: "2026-09-16T10:20:00" })),
    reviews: [],
    services: [
      {
        id: 301,
        serviceNo: "UI-AS-301",
        orderId: 102,
        orderItemId: 202,
        productName: products[1].name,
        type: "RETURN",
        reason: "商品故障",
        description: "演示故障描述，用于检查售后记录与物流表单布局。".repeat(5),
        status: "WAITING_RETURN",
        requestedAmount: 5999,
        createdAt: "2026-09-16T10:00:00",
        adminReply: "请寄回商品并填写物流信息，收到后进行检测。",
      },
      {
        id: 302,
        serviceNo: "UI-AS-302",
        orderId: 104,
        orderItemId: 204,
        productName: products[3].name,
        type: "REPAIR",
        reason: "商品故障",
        description: "设备无法正常启动，已检查充电器。",
        status: "SUBMITTED",
        requestedAmount: 0,
        createdAt: "2026-09-16T11:00:00",
      },
    ],
    writes: [],
    failGet: "",
    failSave: false,
    failDelete: false,
  };
}
async function mockApi(context, state) {
  await context.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url()).pathname.replace(/^\/api/, "");
    const method = request.method();
    const body = request.postDataJSON() || {};
    let data;
    let code = 200;
    let message = "success";
    if (method !== "GET") state.writes.push({ url, method, body });
    if (method === "GET" && state.failGet === url) {
      state.failGet = "";
      code = 400;
      message = "测试：网络异常，请重新加载";
    } else if (method === "GET") {
      const map = {
        "/products": state.products,
        "/categories": [{ id: 1, name: "地面清洁" }],
        "/brands": [{ id: 1, name: "石头科技" }],
        "/cart": state.cart,
        "/addresses": state.addresses,
        "/favorites": state.favorites,
        "/history": state.history,
        "/orders": state.orders,
        "/reviews/mine": state.reviews,
        "/after-sales": state.services,
        "/admin/products": state.products,
        "/admin/users": [
          {
            id: 1,
            username: "admin",
            nickname: "管理员",
            role: "ADMIN",
            enabled: true,
          },
          {
            id: 2,
            username: "test-user",
            nickname: "测试用户",
            role: "USER",
            enabled: true,
          },
        ],
        "/admin/orders": state.orders.map((e) => e.order),
        "/admin/after-sales": state.services,
        "/admin/dashboard": {
          totalUsers: 2,
          totalProducts: 25,
          totalOrders: 5,
          afterSalePending: 2,
          paidConversionRate: 60,
          totalSales: 5999,
          dailySales: Array.from({ length: 7 }, (_, i) => ({
            date: `2026-09-${10 + i}`,
            amount: i === 6 ? 5999 : 0,
            orders: i === 6 ? 1 : 0,
          })),
          statusDistribution: {
            PENDING: 1,
            PAID: 1,
            SHIPPED: 1,
            COMPLETED: 1,
            CANCELLED: 1,
          },
          categorySales: { 地面清洁: 1 },
          lowStockProducts: [],
        },
      };
      data = map[url];
      if (data === undefined && /^\/products\/\d+$/.test(url))
        data = state.products.find(
          (p) => p.id === Number(url.split("/").at(-1)),
        );
      if (data === undefined) {
        code = 400;
        message = `Unhandled GET ${url}`;
      }
    } else if (url === "/products/compare")
      data = state.products.filter((p) => body.includes(p.id));
    else if (url === "/orders") {
      data = {
        id: 110,
        orderNo: "UI-ORDER-110",
        userId: 2,
        status: "PENDING",
        totalAmount: 5999,
        receiverInfo: body.addressId
          ? "测试收货人，测试地址"
          : body.receiverInfo,
      };
      state.orders.push({
        order: data,
        items: [
          {
            id: 210,
            productId: 1,
            productName: state.products[0].name,
            quantity: 1,
            price: 5999,
          },
        ],
      });
      state.cart = [];
    } else if (/^\/cart\/\d+$/.test(url)) {
      if (method === "PUT") state.cart[0].quantity = body.quantity;
      else state.cart = [];
      data = true;
    } else if (/^\/favorites\/\d+$/.test(url)) {
      const id = Number(url.split("/").at(-1));
      if (state.failDelete && id === 2) {
        state.failDelete = false;
        code = 400;
        message = "测试：移除失败";
      } else state.favorites = state.favorites.filter((p) => p.id !== id);
      data = true;
    } else if (/^\/history\/\d+$/.test(url)) {
      state.history = state.history.filter(
        (r) => r.product.id !== Number(url.split("/").at(-1)),
      );
      data = true;
    } else if (
      ["/favorites", "/history", "/cart"].includes(url) &&
      method === "DELETE"
    ) {
      state[url.slice(1)] = [];
      data = true;
    } else if (/^\/orders\/\d+\/(pay|cancel|receive)$/.test(url)) {
      const [, , id, action] = url.split("/");
      const entry = state.orders.find((e) => e.order.id === Number(id));
      entry.order.status = {
        pay: "PAID",
        cancel: "CANCELLED",
        receive: "COMPLETED",
      }[action];
      data = entry.order;
    } else if (url === "/reviews") {
      const item = state.orders
        .flatMap((e) => e.items)
        .find((i) => i.productId === body.productId);
      state.reviews.push({ ...body, orderItemId: item.id });
      data = true;
    } else if (url === "/after-sales") {
      data = {
        ...body,
        id: 303,
        serviceNo: "UI-AS-303",
        productName: "测试清洁机器人",
        status: "SUBMITTED",
        createdAt: "2026-09-16T12:00:00",
        requestedAmount: 0,
      };
      state.services.push(data);
    } else if (/^\/after-sales\/\d+\/(logistics|cancel)$/.test(url)) {
      const s = state.services.find((s) => s.id === Number(url.split("/")[2]));
      s.status = url.endsWith("cancel") ? "CANCELLED" : "RETURN_SHIPPED";
      data = s;
    } else if (/^\/admin\/orders\/\d+\/ship$/.test(url)) {
      const e = state.orders.find(
        (e) => e.order.id === Number(url.split("/")[3]),
      );
      Object.assign(e.order, body, { status: "SHIPPED" });
      data = e.order;
    } else if (url.startsWith("/admin/products")) {
      if (state.failSave) {
        state.failSave = false;
        code = 400;
        message = "测试：保存失败，稍后重试";
      } else {
        if (method === "POST") {
          data = { ...body, id: 26 };
          state.products.push(data);
        } else {
          data = state.products.find(
            (p) => p.id === Number(url.split("/").at(-1)),
          );
          if (method === "DELETE") data.enabled = false;
          else Object.assign(data, body);
        }
      }
    } else if (url.startsWith("/admin/after-sales/")) {
      data = state.services.find((s) => s.id === Number(url.split("/").at(-1)));
      Object.assign(data, body);
    } else {
      code = 400;
      message = `Unhandled ${method} ${url}`;
    }
    await route.fulfill({
      status: code === 200 ? 200 : 400,
      contentType: "application/json",
      body: JSON.stringify({ code, message, data }),
    });
  });
}

try {
  for (const viewport of viewports)
    for (const theme of ["classic", "midnight"]) {
      const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
      });
      const state = fixture();
      await mockApi(context, state);
      await context.addInitScript((theme) => {
        localStorage.setItem("token", "isolated-ui-test");
        localStorage.setItem(
          "user",
          JSON.stringify({
            id: 2,
            username: "test-user",
            nickname: "测试用户",
            role: "USER",
            theme,
            accentColor: theme === "midnight" ? "#5aa7ff" : "#d9363e",
            backgroundColor: theme === "midnight" ? "#111827" : "#f5f6f8",
          }),
        );
      }, theme);
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(e.message));
      page.on("dialog", (dialog) => dialog.accept());
      const check = async (name, fn) => {
        await fn();
        assert.deepEqual(errors, []);
        report.push({ viewport: viewport.name, theme, name, passed: true });
      };
      const screen = async (name) => {
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.waitForTimeout(150);
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 2,
          ),
          false,
          `${name} overflow`,
        );
        await page.screenshot({
          path: path.join(output, `${viewport.name}-${theme}-${name}.png`),
        });
      };
      await check("cart-error-retry-address-quantity", async () => {
        state.failGet = "/cart";
        await page.goto("http://localhost:5173/cart");
        await page.getByRole("button", { name: "重新加载" }).click();
        await page.locator(".checkout-address").waitFor();
        assert.equal(await page.locator("#cart-address").inputValue(), "11");
        const quantity = page.locator(".qty-input");
        await quantity.fill("2");
        await quantity.dispatchEvent("change");
        await page.waitForFunction(
          () => document.querySelector(".qty-input")?.value === "2",
        );
        await screen("cart");
        await page.locator("#cart-address").selectOption("");
        await page.locator("#cart-receiver").fill("手动测试收货信息");
        await page.getByRole("button", { name: "刷新地址" }).click();
        assert.equal(await page.locator("#cart-address").inputValue(), "");
        await page.locator("#cart-address").selectOption("11");
        await page
          .getByRole("button", { name: "提交订单", exact: true })
          .evaluate((b) => {
            b.click();
            b.click();
          });
        await page.getByRole("heading", { name: "购物车还是空的" }).waitFor();
        const writes = state.writes.filter((w) => w.url === "/orders");
        assert.equal(writes.length, 1);
        assert.deepEqual(writes[0].body, { addressId: 11 });
      });
      await check("favorites-partial-delete-retry", async () => {
        await page.goto("http://localhost:5173/favorites");
        await page.locator(".product-select").first().click();
        await page.locator(".product-select").nth(1).click();
        state.failDelete = true;
        await screen("favorites");
        await page.getByRole("button", { name: "移除所选" }).click();
        await page
          .getByRole("status")
          .filter({ hasText: "1 项未成功" })
          .waitFor();
        assert.equal(state.favorites.length, 3);
        assert.equal(await page.locator(".product-select.selected").count(), 1);
        await page.getByRole("button", { name: "移除所选" }).click();
        await page
          .getByRole("status")
          .filter({ hasText: "已移除 1 项" })
          .waitFor();
        assert.equal(state.favorites.length, 2);
      });
      await check("history-time-and-batch-delete", async () => {
        await page.goto("http://localhost:5173/history");
        await page.locator(".product-viewed-at").first().waitFor();
        assert.match(
          await page.locator(".product-viewed-at").first().innerText(),
          /2026-09-16/,
        );
        await screen("history");
        await page.locator(".batch-toolbar input").check();
        await page.getByRole("button", { name: "删除所选" }).click();
        await page.getByRole("heading", { name: "从一次浏览开始" }).waitFor();
        assert.equal(state.history.length, 0);
      });
      await check("orders-filter-pay-receive-review", async () => {
        await page.goto("http://localhost:5173/orders");
        await page.locator(".orders-page .card").first().waitFor();
        await screen("orders");
        await page
          .locator(".page-tabs button")
          .filter({ hasText: "待支付" })
          .click();
        assert.equal(await page.locator(".orders-page .card").count(), 2);
        await page.locator(".page-tabs button").first().click();
        const pending = page
          .locator(".orders-page .card")
          .filter({ hasText: "UI-ORDER-101" });
        await pending.getByRole("button", { name: "模拟支付" }).click();
        await pending.getByText("待发货", { exact: true }).waitFor();
        const shipped = page
          .locator(".orders-page .card")
          .filter({ hasText: "UI-ORDER-103" });
        await shipped.getByRole("button", { name: "确认收货" }).click();
        await shipped.getByText("已完成", { exact: true }).waitFor();
        await shipped
          .getByLabel("商品评价内容")
          .fill("这是隔离环境的界面测试评价。");
        await shipped.getByRole("button", { name: "发表评价" }).click();
        await shipped.getByText("✓ 已评价").waitFor();
      });
      await check("after-sales-logistics-submit-cancel", async () => {
        await page.goto("http://localhost:5173/after-sales");
        await page.locator(".service-ticket").first().waitFor();
        await screen("after-sales");
        const ticket = page
          .locator(".service-ticket")
          .filter({ hasText: "UI-AS-301" });
        await ticket.getByLabel("寄回物流公司").fill("演示物流");
        await ticket.getByLabel("寄回物流单号").fill("UIRETURN001");
        await ticket.getByRole("button", { name: "提交寄回物流" }).click();
        await ticket.getByText("寄回途中", { exact: true }).first().waitFor();
        const form = page.locator(".service-form-card form");
        await form.locator("select").nth(0).selectOption("101");
        await form.locator("select").nth(1).selectOption("201");
        await form.locator('input[value="REPAIR"]').check();
        await form.locator("select").nth(2).selectOption({ label: "商品故障" });
        await form.locator("textarea").fill("演示问题描述，设备无法启动。");
        await form.getByRole("button", { name: "提交售后申请" }).click();
        const created = page
          .locator(".service-ticket")
          .filter({ hasText: "UI-AS-303" });
        await created.waitFor();
        await created.getByRole("button", { name: "取消申请" }).click();
        await created.getByText("已取消", { exact: true }).waitFor();
      });
      await check("compare-selection-reset-and-zero-value", async () => {
        await page.goto("http://localhost:5173/compare");
        await page.locator(".compare-products input").first().check();
        await page.locator(".compare-products input").nth(1).check();
        await page.getByRole("button", { name: "生成对比结果" }).click();
        await page.locator(".compare-result").waitFor();
        assert.match(
          await page
            .locator(".compare-table tbody tr")
            .filter({ hasText: "耗材余量" })
            .innerText(),
          /0/,
        );
        await screen("compare");
        await page.locator(".compare-products input").nth(1).uncheck();
        assert.equal(await page.locator(".compare-result").count(), 0);
      });
      await page.evaluate(() => {
        const u = JSON.parse(localStorage.getItem("user"));
        u.role = "ADMIN";
        u.id = 1;
        localStorage.setItem("user", JSON.stringify(u));
      });
      // Init script also runs on reload; replace it with a fresh admin context.
      const adminContext = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
      });
      await mockApi(adminContext, state);
      await adminContext.addInitScript((theme) => {
        localStorage.setItem("token", "isolated-ui-admin");
        localStorage.setItem(
          "user",
          JSON.stringify({
            id: 1,
            username: "admin",
            nickname: "测试管理员",
            role: "ADMIN",
            theme,
            accentColor: theme === "midnight" ? "#5aa7ff" : "#d9363e",
            backgroundColor: theme === "midnight" ? "#111827" : "#f5f6f8",
          }),
        );
      }, theme);
      const admin = await adminContext.newPage();
      admin.on("pageerror", (e) => errors.push(e.message));
      admin.on("dialog", (d) => d.accept());
      const adminScreen = async (name) => {
        await admin.evaluate(() => window.scrollTo(0, 0));
        await admin.waitForTimeout(150);
        assert.equal(
          await admin.evaluate(
            () => document.documentElement.scrollWidth > innerWidth + 2,
          ),
          false,
          name + " overflow",
        );
        await admin.screenshot({
          path: path.join(output, `${viewport.name}-${theme}-${name}.png`),
        });
      };
      await check("admin-search-pagination-editor-errors-focus", async () => {
        await admin.goto("http://localhost:5173/admin");
        await admin.locator(".admin-stat-grid").waitFor();
        await adminScreen("admin-dashboard");
        await admin
          .getByRole("button", { name: "商品管理", exact: true })
          .click();
        await admin.getByRole("button", { name: "下一页" }).click();
        assert.match(
          await admin.locator(".list-pagination").innerText(),
          /第 2/,
        );
        await admin
          .getByPlaceholder("搜索名称、型号或品牌")
          .fill("不存在的测试型号");
        await admin.getByRole("heading", { name: "没有匹配的商品" }).waitFor();
        await admin.getByRole("button", { name: "清除筛选" }).click();
        assert.match(
          await admin.locator(".list-pagination").innerText(),
          /第 1/,
        );
        await adminScreen("admin-products");
        await admin
          .getByRole("button", { name: "编辑", exact: true })
          .first()
          .click();
        const dialog = admin.getByRole("dialog");
        await dialog.waitFor();
        assert.equal(
          await dialog.getByLabel("商品名称 *", { exact: true }).inputValue(),
          "石头 G20S Ultra",
        );
        await adminScreen("admin-editor");
        await dialog.locator("summary").click();
        await dialog
          .getByLabel("参数 JSON", { exact: true })
          .fill("invalid-json");
        await dialog
          .getByRole("button", { name: "保存修改", exact: true })
          .click();
        await dialog.getByRole("alert").waitFor();
        assert.equal(
          state.writes.filter((w) => w.url === "/admin/products/1").length,
          0,
        );
        await dialog
          .getByLabel("参数 JSON", { exact: true })
          .fill('{"续航":"120分钟"}');
        state.failSave = true;
        await dialog
          .getByRole("button", { name: "保存修改", exact: true })
          .click();
        await dialog
          .getByRole("alert")
          .filter({ hasText: "保存失败" })
          .waitFor();
        assert.equal(
          await dialog.getByLabel("商品名称 *", { exact: true }).inputValue(),
          "石头 G20S Ultra",
        );
        await dialog
          .getByRole("button", { name: "保存修改", exact: true })
          .click();
        await dialog.waitFor({ state: "hidden" });
        await admin
          .getByRole("status")
          .filter({ hasText: "商品修改已保存" })
          .waitFor();
        await admin
          .getByRole("button", { name: "新增商品", exact: true })
          .click();
        assert.equal(
          await dialog.getByLabel("商品名称 *", { exact: true }).inputValue(),
          "",
        );
        await admin.keyboard.press("Escape");
        await dialog.waitFor({ state: "hidden" });
        assert.equal(
          await admin.evaluate(() =>
            document.activeElement?.textContent?.trim(),
          ),
          "新增商品",
        );
      });
      await check("admin-inline-shipping-cancel-submit", async () => {
        await admin
          .getByRole("button", { name: "订单管理", exact: true })
          .click();
        const order = admin
          .locator(".admin-order")
          .filter({ hasText: "UI-ORDER-102" });
        await order.getByRole("button", { name: "填写物流并发货" }).click();
        await order.getByLabel("物流公司", { exact: true }).fill("演示物流");
        await adminScreen("admin-shipping");
        await order.getByRole("button", { name: "取消", exact: true }).click();
        assert.equal(
          state.writes.filter((w) => w.url.endsWith("/102/ship")).length,
          0,
        );
        await order.getByRole("button", { name: "填写物流并发货" }).click();
        await order.getByLabel("物流公司", { exact: true }).fill("演示物流");
        await order.getByLabel("物流单号", { exact: true }).fill("UISHIP102");
        await order.getByRole("button", { name: "确认发货" }).evaluate((b) => {
          b.click();
          b.click();
        });
        await order.getByText("待收货", { exact: true }).waitFor();
        assert.equal(
          state.writes.filter((w) => w.url.endsWith("/102/ship")).length,
          1,
        );
      });
      await check("admin-user-service-tabs-processing", async () => {
        await admin
          .getByRole("button", { name: "用户管理", exact: true })
          .click();
        await admin.getByRole("button", { name: "管理员受保护" }).waitFor();
        assert.equal(
          await admin
            .getByRole("button", { name: "管理员受保护" })
            .isDisabled(),
          true,
        );
        await adminScreen("admin-users");
        await admin.getByRole("button", { name: /售后管理/ }).click();
        await adminScreen("admin-services");
        const request = admin
          .locator(".admin-service-card")
          .filter({ hasText: "UI-AS-302" });
        await request
          .getByLabel("处理状态", { exact: true })
          .selectOption("PROCESSING");
        await request
          .getByLabel("处理说明", { exact: true })
          .fill("演示检测中，将在记录中更新结果。");
        await request.getByRole("button", { name: "更新进度" }).click();
        await request.getByText("处理中", { exact: true }).first().waitFor();
      });
      await adminContext.close();
      await context.close();
      console.log(`${viewport.name}/${theme}: 9 操作组通过`);
    }
} catch (e) {
  report.push({ passed: false, error: e.stack });
  process.exitCode = 1;
} finally {
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(
      {
        isolatedApi: true,
        generatedAt: new Date().toISOString(),
        total: report.length,
        passed: report.filter((r) => r.passed).length,
        results: report,
      },
      null,
      2,
    ),
  );
  // Persist results first: Chrome shutdown must never swallow completed assertions.
  let shutdownTimer;
  try {
    const cleanup = await Promise.race([
      shutdown(),
      new Promise((_, reject) => {
        shutdownTimer = setTimeout(
          () => reject(new Error("浏览器退出超时")),
          10000,
        );
      }),
    ]);
    const saved = JSON.parse(await readFile(path.join(output, "report.json"), "utf8"));
    saved.browserCleanup = cleanup;
    await writeFile(path.join(output, "report.json"), JSON.stringify(saved, null, 2));
  } catch (error) {
    console.warn(error.message);
    process.exitCode = 1;
  } finally {
    clearTimeout(shutdownTimer);
  }
}
console.log(
  JSON.stringify({
    total: report.length,
    passed: report.filter((r) => r.passed).length,
    failed: report.filter((r) => !r.passed),
  }),
);
process.exit(process.exitCode || 0);
