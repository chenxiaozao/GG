import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

// The backend owns credentials. Only synthetic questions and public catalog data leave the app.
const apiUrl = process.env.AUDIT_API_URL || "http://127.0.0.1:8080/api";
const output = path.resolve("../tmp/ai-live-audit");
await mkdir(output, { recursive: true });
const results = [];
async function request(endpoint, payload) {
  const response = await fetch(apiUrl + endpoint, {
    method: payload ? "POST" : "GET",
    headers: { "Content-Type": "application/json" },
    body: payload ? JSON.stringify(payload) : undefined,
    signal: AbortSignal.timeout(45000),
  });
  const body = await response.json();
  if (!response.ok || body.code !== 200)
    throw new Error(body.message || `HTTP ${response.status}`);
  return body.data;
}
try {
  const status = await request("/advisor/ai-status");
  assert.equal(status.configured, true, "后端尚未读取 AI 配置");
  const catalog = await request("/products");
  const known = new Map(catalog.map((product) => [product.id, product]));
  let history = [];
  for (const [name, question] of [
    [
      "清洁选购",
      "这是毕设联调的虚构场景：90平方米、木地板、有猫，预算3000元，推荐1至2款商城内扫地机器人，说明取舍。",
    ],
    [
      "连续追问",
      "上一轮推荐里哪款更适合处理猫毛？不要重复目录，说明推荐依据不足的地方。",
    ],
    [
      "配件检索",
      "另一项虚构测试：我已有宇树 Go2 Pro，想找商城里的 Go2 智能电池。请给出商品，并说明不能仅凭同品牌保证适配。",
    ],
  ]) {
    if (name === "配件检索") history = [];
    const started = Date.now();
    const response = await request("/advisor/chat", { question, history });
    assert.equal(response.provider, "DeepSeek");
    assert.ok(response.answer?.trim());
    assert.ok(Array.isArray(response.products));
    assert.ok(response.products.length <= 3);
    for (const product of response.products) {
      assert.ok(known.has(product.id), "商品卡片必须对应商城真实记录");
      assert.equal(product.name, known.get(product.id).name);
      assert.ok(product.image, "推荐卡片需要图片");
    }
    if (name !== "连续追问")
      assert.ok(response.products.length > 0, "明确商品推荐应返回可跳转卡片");
    if (name === "配件检索")
      assert.ok(response.products.some((x) => x.name === "Go2 智能电池"));
    results.push({
      name,
      passed: true,
      question,
      answer: response.answer,
      products: response.products.map((x) => ({
        id: x.id,
        name: x.name,
        image: x.image,
        path: `/product/${x.id}`,
      })),
      durationMs: Date.now() - started,
    });
    history.push(
      { role: "user", content: question },
      { role: "assistant", content: response.answer },
    );
    console.log(`${name}通过；推荐卡片 ${response.products.length} 张`);
  }
} catch (error) {
  results.push({ passed: false, error: error.message });
  process.exitCode = 1;
} finally {
  await writeFile(
    path.join(output, "report.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        syntheticQuestionsOnly: true,
        results,
      },
      null,
      2,
    ),
  );
}
console.log(
  JSON.stringify({
    total: results.length,
    passed: results.filter((x) => x.passed).length,
    failures: results.filter((x) => !x.passed),
  }),
);
