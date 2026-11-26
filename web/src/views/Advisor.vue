<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import ProductImage from "../components/ProductImage.vue";
import { nextTick, onMounted, reactive, ref, shallowRef, watch } from "vue";
import api from "../api";
import AiAnswer from "../components/AiAnswer.vue";
import UiIcon from "../components/UiIcon.vue";
import { useRoute, useRouter } from "vue-router";
const router = useRouter();
const route = useRoute();

const form = reactive({
  budget: 5000,
  allowOverBudget: false,
  requiredFeatures: [] as string[],
  minEndurance: 0,
  scene: "cleaning",
  priority: "balanced",
});
const result = ref<any>(null);
const message = ref("");
const loading = ref(false);
const aiConfigured = ref(false);
const aiConnected = ref(false);
const quota = ref<{ remaining: number; limit: number; note: string } | null>(
  null,
);
const featureOptions = ["自动集尘", "避障", "防缠绕", "拖地", "编程", "遥控"];
/** 读取当前登录账号的助选剩余次数。 */
async function refreshQuota() {
  if (!localStorage.getItem("token")) return;
  try {
    quota.value = (await api.get("/advisor/quota")) as any;
  } catch {
    quota.value = null;
  }
}
const chatRegion = ref<HTMLElement | null>(null);
const chatInput = ref("");
const contextProducts = ref<any[]>([]);
const contextIds = shallowRef<number[]>([]);
const contextError = ref("");
/** 清除从商品详情或选购计划带入的候选范围。 */
async function clearContext() {
  if (chatLoading.value || historyLoading.value) return;
  if (activeThread.value && activeThread.value !== "legacy") {
    historyLoading.value = true;
    try {
      await api.delete(`/advisor/threads/${activeThread.value}/context`);
    } catch {
      contextError.value = "没能清除本会话的候选，请重试。";
      return;
    } finally {
      historyLoading.value = false;
    }
  }
  setCandidates([]);
}

/** 恢复当前会话的候选范围；标题和消息独立加载。 */
function setCandidates(ids: number[]) {
  contextIds.value = ids;
  contextProducts.value = [];
  contextError.value = "";
  if (!ids.length) return;
  void Promise.all(ids.map((id) => api.get(`/products/${id}`)))
    .then((products) => {
      if (contextIds.value === ids) contextProducts.value = products;
    })
    .catch(() => {
      if (contextIds.value === ids)
        contextError.value = "部分候选商品无法读取，可以清除候选后继续咨询。";
    });
}
/** 把对话需求与候选商品保存为本地选购计划草稿。 */
function savePlanDraft() {
  const requirement =
    chatInput.value.trim() ||
    [...chatMessages.value].reverse().find((m) => m.role === "user")?.content ||
    "";
  sessionStorage.setItem(
    "robotmall-plan-draft",
    JSON.stringify({
      owner: JSON.parse(localStorage.getItem("user") || "null")?.id,
      requirement,
      productIds: contextIds.value.length
        ? contextIds.value
        : (chatMessages.value.at(-1)?.products || [])
            .slice(0, 4)
            .map((p) => p.id),
    }),
  );
  router.push("/profile#plans");
}
const chatLoading = ref(false);
const historyLoading = ref(false);
const historyNotice = ref("");
const replyProvider = ref("");
const previousQuestion = ref("");
type AdvisorThread = { id: string; title: string; updatedAt: string };
const threads = ref<AdvisorThread[]>([]);
const activeThread = ref<string | null>(null);
const threadDrafts = new Map<string, string>();
const advisorMode = ref<"chat" | "form">("chat");
const prompts = [
  "家里有猫，90 平米，预算 5000 元，想选省心的扫拖机器人",
  "第一次买航拍无人机，预算 6000 元，更看重便携和易上手",
  "想找适合孩子入门编程的机器人，预算 3000 元",
];
/** 把示例问题填进输入框并聚焦。 */
function usePrompt(question: string) {
  chatInput.value = question;
  document
    .querySelector<HTMLTextAreaElement>(".advisor-chat-input textarea")
    ?.focus();
}
/** 取回最近一个用户问题以便重试。 */
function retryQuestion() {
  const last = [...chatMessages.value]
    .reverse()
    .find((item) => item.role === "user");
  if (last) usePrompt(last.content);
}
const chatMessages = ref<
  {
    role: "user" | "assistant";
    content: string;
    products?: any[];
    evidence?: Array<{
      product: { id: number; name: string };
      reasons: string[];
    }>;
    notice?: string;
    error?: boolean;
  }[]
>([
  {
    role: "assistant",
    content:
      "你可以直接告诉我家庭环境、预算和最在意的功能，我会在商城已有商品中给出选购建议。",
  },
]);
const welcomeMessage = chatMessages.value[0]!;

/** 把选中的会话写进路由，刷新页面后仍能打开它。 */
function syncThreadAddress(id: string | null) {
  const query = { ...route.query };
  if (id) query.chat = id;
  else delete query.chat;
  void router.replace({ query });
}

/** 只重置当前画面的临时内容，不清空账号中的旧对话。 */
function replaceChat(
  id: string | null,
  messages: typeof chatMessages.value,
  ids: number[],
) {
  if (activeThread.value) threadDrafts.set(activeThread.value, chatInput.value);
  activeThread.value = id;
  chatMessages.value = messages.length ? messages : [welcomeMessage];
  chatInput.value = id ? threadDrafts.get(id) || "" : "";
  previousQuestion.value = "";
  replyProvider.value = "";
  aiConnected.value = false;
  setCandidates(ids);
  syncThreadAddress(id);
}

/** 从账号读取会话列表，优先恢复当前链接指向的对话。 */
async function loadChatHistory(restore = true) {
  if (!localStorage.getItem("token")) return;
  historyLoading.value = true;
  historyNotice.value = "";
  try {
    threads.value = (await api.get("/advisor/threads")) as AdvisorThread[];
    if (restore) {
      const requested =
        typeof route.query.chat === "string" ? route.query.chat : "";
      const next =
        threads.value.find((thread) => thread.id === requested) ||
        threads.value[0];
      if (next) await openThread(next.id, true);
      else replaceChat(null, [], []);
    }
  } catch {
    historyNotice.value = "咨询记录暂时无法读取，可重试。";
  } finally {
    historyLoading.value = false;
  }
}

/** 打开一个窗口，只加载它自己的消息和候选。 */
async function openThread(id: string, nested = false) {
  if (chatLoading.value || (!nested && historyLoading.value)) return;
  if (!nested) {
    historyLoading.value = true;
    historyNotice.value = "";
  }
  try {
    const result = (await api.get(`/advisor/threads/${id}`)) as {
      messages: typeof chatMessages.value;
      contextProductIds: number[];
    };
    replaceChat(id, result.messages, result.contextProductIds);
  } catch {
    historyNotice.value = "这个对话暂时打不开，可能已在另一台设备删除。";
  } finally {
    if (!nested) historyLoading.value = false;
  }
}

/** 开始新的独立对话；第一次发送时才建立账号记录。 */
function startNewThread() {
  if (chatLoading.value || historyLoading.value) return;
  historyNotice.value = "";
  replaceChat(null, [], []);
  usePrompt("");
}

/** 删除选中的会话，不删除其他对话、浏览历史或画像。 */
async function clearChatHistory() {
  const id = activeThread.value;
  if (
    !id ||
    !window.confirm("删除当前对话？其他对话、浏览记录和偏好不受影响。")
  )
    return;
  historyLoading.value = true;
  historyNotice.value = "";
  try {
    await api.delete(`/advisor/threads/${id}`);
    replaceChat(null, [], []);
    threadDrafts.delete(id);
    threads.value = threads.value.filter((thread) => thread.id !== id);
  } catch {
    historyNotice.value = "当前对话未删除，请重试。";
  } finally {
    historyLoading.value = false;
  }
}
const scenes = [
  ["cleaning", "地面清洁", "扫拖与日常维护"],
  ["window", "窗面清洁", "玻璃与高层窗面"],
  ["garden", "庭院泳池", "户外割草与水下清洁"],
  ["companion", "陪伴娱乐", "家庭互动与看护"],
  ["drone", "航拍创作", "旅行影像与内容创作"],
  ["education", "教育编程", "学习、竞赛与开发"],
  ["quadruped", "四足平台", "科研开发与巡检"],
  ["service", "商用服务", "迎宾、配送与展示"],
];

/** 提交已经结构化的表单并展示确定性规则推荐。 */
async function recommend() {
  message.value = "";
  result.value = null;
  loading.value = true;
  try {
    result.value = await api.post("/advisor/recommend", form);
    if (!result.value.items?.length)
      message.value = "当前条件下暂无合适商品，可调整预算或使用场景后重试";
    else {
      await nextTick();
      document.querySelector(".advisor-results")?.scrollIntoView({
        block: "start",
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
      });
    }
  } catch (e: any) {
    message.value = e?.message || "推荐生成失败";
  } finally {
    loading.value = false;
  }
}

/** 格式化推荐卡片中的金额。 */
function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", {
    maximumFractionDigits: 2,
  });
}
/** 只发送原始问题和当前会话编号；Java 从该会话读取上下文。 */
async function sendChat(recall?: string) {
  const question = recall || chatInput.value.trim();
  if (!question || chatLoading.value || historyLoading.value) return;
  if (!localStorage.getItem("token")) {
    sessionStorage.setItem("robotmall-advisor-entry", question);
    sessionStorage.setItem(
      "robotmall-advisor-context",
      JSON.stringify(contextIds.value),
    );
    await router.push({ path: "/login", query: { redirect: "/advisor" } });
    return;
  }
  chatMessages.value.push({ role: "user", content: question });
  if (!recall) chatInput.value = "";
  chatLoading.value = true;
  try {
    if (!activeThread.value) {
      const thread = (await api.post("/advisor/threads", {
        contextProductIds: contextIds.value,
      })) as AdvisorThread;
      activeThread.value = thread.id;
      threads.value = [thread, ...threads.value];
      syncThreadAddress(thread.id);
    }
    const response: any = await api.post(
      "/advisor/chat",
      { question, threadId: activeThread.value },
      { timeout: 45000 },
    );
    aiConnected.value = response.provider === "DeepSeek";
    replyProvider.value = response.provider;
    previousQuestion.value = response.previousQuestion || "";
    chatMessages.value.push({
      role: "assistant",
      content: response.answer,
      products: response.products || [],
      evidence: response.evidence || [],
      notice: response.notice || "",
    });
    await loadChatHistory(false);
  } catch (e: any) {
    aiConnected.value = false;
    chatInput.value = question;
    chatMessages.value.push({
      role: "assistant",
      error: true,
      content: e?.message || "AI 顾问暂时无法回答，请稍后重试。",
    });
  } finally {
    chatLoading.value = false;
    await refreshQuota();
  }
}
watch(
  () => [chatMessages.value.length, chatLoading.value],
  async () => {
    await nextTick();
    const region = chatRegion.value;
    region?.scrollTo({
      top: region.scrollHeight,
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth",
    });
  },
);
onMounted(async () => {
  const entry = sessionStorage.getItem("robotmall-advisor-entry");
  const rawContext = sessionStorage.getItem("robotmall-advisor-context");
  sessionStorage.removeItem("robotmall-advisor-entry");
  sessionStorage.removeItem("robotmall-advisor-context");
  await loadChatHistory(!entry && !rawContext);
  try {
    const ids = JSON.parse(rawContext || "[]");
    if (Array.isArray(ids)) {
      if (rawContext)
        setCandidates(
          [
            ...new Set(ids.filter((id) => Number.isSafeInteger(id) && id > 0)),
          ].slice(0, 4) as number[],
        );
    }
  } catch {
    contextError.value =
      "候选商品读取失败，请返回原页面重新选择，或清除候选后咨询。";
  }
  await refreshQuota();
  if (entry) {
    chatInput.value = entry;
    await nextTick();
    document
      .querySelector<HTMLTextAreaElement>(".advisor-chat-input textarea")
      ?.focus();
  }
  try {
    const status: any = await api.get("/advisor/ai-status");
    aiConfigured.value = !!status.configured;
  } catch {
    aiConfigured.value = false;
  }
});
</script>

<template>
  <div class="advisor-page">
    <section class="context-strip container" aria-label="本次选购上下文">
      <div v-if="contextIds.length">
        <b>正在围绕 {{ contextProducts.length }} 款候选咨询</b>
        <p>只在这些候选内分析；没有满足条件的商品时不会自动放宽要求。</p>
        <router-link
          v-for="p in contextProducts"
          :key="p.id"
          :to="`/product/${p.id}`"
          >{{ p.name }} · </router-link
        ><button class="btn secondary" @click="clearContext">清除候选</button>
      </div>
      <p v-if="contextError" role="alert">{{ contextError }}</p>
      <button class="btn secondary" @click="savePlanDraft">
        保存为选购计划
      </button>
    </section>
    <section class="advisor-hero">
      <div class="container">
        <p class="section-kicker">选购顾问</p>
        <h1><span>不确定怎么选？</span><span>聊聊你的需求。</span></h1>
        <p>直接描述你的使用环境，或填写几个条件，让选购更有方向。</p>
      </div>
    </section>
    <div
      class="container advisor-mode-nav"
      role="tablist"
      aria-label="选择咨询方式"
    >
      <button
        id="advisor-chat-tab"
        type="button"
        role="tab"
        :aria-selected="advisorMode === 'chat'"
        aria-controls="advisor-chat-panel"
        :class="{ active: advisorMode === 'chat' }"
        @click="advisorMode = 'chat'"
      >
        <UiIcon name="spark" />AI 对话咨询<small>自然描述你的需求</small>
      </button>
      <button
        id="advisor-form-tab"
        type="button"
        role="tab"
        :aria-selected="advisorMode === 'form'"
        aria-controls="advisor-form-panel"
        :class="{ active: advisorMode === 'form' }"
        @click="advisorMode = 'form'"
      >
        <UiIcon name="settings" />按条件推荐<small>预算与场景快速筛选</small>
      </button>
    </div>
    <div
      v-show="advisorMode === 'form'"
      id="advisor-form-panel"
      role="tabpanel"
      aria-labelledby="advisor-form-tab"
      class="container advisor-layout advisor-main"
    >
      <form class="advisor-form card" @submit.prevent="recommend">
        <div class="notice" role="alert" v-if="message">{{ message }}</div>
        <div class="advisor-step">
          <b>01</b>
          <div>
            <h2>主要使用场景</h2>
            <p>先确定真正需要解决的问题</p>
          </div>
        </div>
        <div class="advisor-scene-grid">
          <label
            v-for="scene in scenes"
            :key="scene[0]"
            :class="{ selected: form.scene === scene[0] }"
            ><input type="radio" v-model="form.scene" :value="scene[0]" /><b>{{
              scene[1]
            }}</b
            ><small>{{ scene[2] }}</small></label
          >
        </div>

        <div class="advisor-step">
          <b>02</b>
          <div>
            <h2>预算和侧重点</h2>
            <p>预算用于筛选，侧重点参与综合排序</p>
          </div>
        </div>
        <div class="advisor-fields">
          <label
            ><span>最高预算</span>
            <div class="advisor-budget">
              <i>¥</i
              ><input
                type="number"
                min="500"
                max="1000000"
                step="100"
                v-model.number="form.budget"
                required
              />
            </div>
            <small>默认严格限制预算，只推荐有库存商品</small></label
          >
          <label
            ><span>选购侧重点</span
            ><select class="input" v-model="form.priority">
              <option value="balanced">综合均衡</option>
              <option value="value">性价比优先</option>
              <option value="performance">续航优先（按同类标注参数）</option>
              <option value="easy">售后信息完整优先</option>
            </select></label
          >
          <label
            ><span>最低续航（分钟）</span
            ><input
              class="input"
              type="number"
              min="0"
              max="1440"
              v-model.number="form.minEndurance"
            />
            <small>0 表示不限；目录缺少数据时不能视为满足要求</small></label
          >
          <label
            ><span>必要功能（可多选）</span>
            <div class="required-features">
              <label v-for="feature in featureOptions" :key="feature"
                ><input
                  type="checkbox"
                  :value="feature"
                  v-model="form.requiredFeatures"
                />{{ feature }}</label
              >
            </div>
            <small>必须有目录参数证据，否则不推荐</small></label
          >
        </div>
        <label class="budget-opt-in"
          ><input
            type="checkbox"
            v-model="form.allowOverBudget"
          />允许最多超预算 20% 的进阶备选</label
        >
        <button class="btn advisor-submit" type="submit" :disabled="loading">
          {{ loading ? "正在分析商品…" : "生成选购建议" }}
        </button>
        <p class="advisor-disclaimer">
          依据目录类别、预算、库存及已标明的信息筛选；不使用销量和评分推断真实性能。
        </p>
      </form>

      <aside class="advisor-explain card">
        <span>推荐逻辑</span>
        <h2>为什么值得参考？</h2>
        <ol>
          <li>
            <b>场景匹配</b><small>只比较能够解决目标问题的机器人类别</small>
          </li>
          <li>
            <b>预算约束</b
            ><small>默认不超预算，仅在明确勾选后允许适度上浮</small>
          </li>
          <li>
            <b>规则排序</b
            ><small>先验证硬条件，再按价格、续航或售后证据排序</small>
          </li>
          <li>
            <b>理由透明</b><small>每款商品展示进入推荐列表的具体原因</small>
          </li>
        </ol>
      </aside>
    </div>

    <section
      v-show="advisorMode === 'chat'"
      id="advisor-chat-panel"
      role="tabpanel"
      aria-labelledby="advisor-chat-tab"
      class="container advisor-chat-section"
    >
      <div class="advisor-result-head">
        <div>
          <h2>小造 AI</h2>
          <p>推荐商品可以直接查看图片、价格和详情。</p>
          <small v-if="quota && Number.isFinite(quota.remaining)"
            >今日剩余 {{ quota.remaining }} /
            {{ quota.limit }}
            次；UTC自然日重置。无关话题本地拒答不计AI额度</small
          >
        </div>
        <b :class="{ muted: !aiConfigured }">{{
          aiConnected
            ? "DeepSeek 已连接"
            : aiConfigured
              ? "DeepSeek 已配置"
              : "规则推荐可用"
        }}</b>
      </div>
      <div class="advisor-conversation-layout">
        <aside class="advisor-thread-sidebar card" aria-label="选购对话列表">
          <button
            class="btn advisor-new-thread"
            type="button"
            :disabled="chatLoading || historyLoading"
            @click="startNewThread"
          >
            ＋ 新建对话
          </button>
          <p class="advisor-thread-heading">历史对话</p>
          <p v-if="!threads.length && !historyLoading" class="muted">
            发出第一条咨询后，会话会保存在这里。
          </p>
          <ul v-else class="advisor-thread-list">
            <li v-for="thread in threads" :key="thread.id">
              <button
                type="button"
                :disabled="chatLoading || historyLoading"
                :aria-current="thread.id === activeThread ? 'page' : undefined"
                :title="thread.title"
                @click="openThread(thread.id)"
              >
                {{ thread.title }}
              </button>
            </li>
          </ul>
        </aside>
        <div class="advisor-chat card">
          <header class="advisor-active-thread">
            <h3>
              {{
                threads.find((thread) => thread.id === activeThread)?.title ||
                "新对话"
              }}
            </h3>
            <small class="muted">每个对话单独保存，不共享聊天上下文。</small>
          </header>
          <div v-if="!historyLoading && activeThread" class="advisor-chat-note">
            <button
              class="btn secondary"
              :disabled="
                chatLoading || !chatMessages.some((m) => m.role === 'user')
              "
              @click="sendChat('我上次问了什么')"
            >
              找回上次咨询
            </button>
            <button
              class="btn secondary"
              :disabled="chatLoading"
              @click="clearChatHistory"
            >
              删除当前对话
            </button>
          </div>
          <p v-if="historyLoading" class="advisor-chat-note" role="status">
            正在处理咨询记录…
          </p>
          <p v-if="historyNotice" class="advisor-chat-note" role="status">
            {{ historyNotice }}
            <button
              class="btn secondary"
              :disabled="chatLoading"
              @click="loadChatHistory()"
            >
              重新读取
            </button>
          </p>
          <p v-if="replyProvider" class="advisor-chat-note">
            本轮来源：{{ replyProvider }}
            <button
              v-if="previousQuestion"
              class="btn secondary"
              :disabled="chatLoading"
              @click="usePrompt(previousQuestion)"
            >
              把上次问题带回输入框
            </button>
          </p>
          <div
            ref="chatRegion"
            class="advisor-chat-messages"
            role="log"
            aria-label="AI 选购对话"
            aria-live="polite"
          >
            <article
              v-for="(item, index) in chatMessages"
              :key="index"
              :class="item.role"
            >
              <b>{{ item.role === "user" ? "我" : "小造 AI" }}</b
              ><AiAnswer
                v-if="item.role === 'assistant'"
                :content="item.content"
              />
              <p v-else>{{ item.content }}</p>
              <p v-if="item.notice" class="ai-evidence-notice">
                {{ item.notice }}
              </p>
              <details v-if="item.evidence?.length" class="ai-evidence">
                <summary>查看推荐参数依据</summary>
                <div v-for="row in item.evidence" :key="row.product.id">
                  <b>{{ row.product.name }}</b>
                  <ul>
                    <li v-for="reason in row.reasons" :key="reason">
                      {{ reason }}
                    </li>
                  </ul>
                </div>
              </details>
              <button
                v-if="item.error"
                type="button"
                class="ai-retry"
                @click="retryQuestion"
              >
                修改问题后重试 →
              </button>
              <div v-if="item.products?.length" class="ai-product-list">
                <router-link
                  v-for="product in item.products"
                  :key="product.id"
                  class="ai-product-card"
                  :to="`/product/${product.id}`"
                  ><ProductImage :product="product" /><span
                    ><b>{{ product.name }}</b
                    ><small
                      >{{ product.brandName }} · {{ product.categoryName
                      }}{{
                        product.imageKind === "REFERENCE"
                          ? " · 参考图"
                          : product.imageKind === "ILLUSTRATION"
                            ? " · 示意图"
                            : ""
                      }}</small
                    ><strong>¥{{ formatPrice(product.price) }}</strong></span
                  ><i>→</i></router-link
                >
              </div>
            </article>
            <article v-if="chatLoading" class="assistant">
              <b>小造 AI</b>
              <p class="ai-thinking">
                <span><i></i><i></i><i></i></span>正在整理选购建议，请稍等…
              </p>
            </article>
          </div>
          <div
            v-if="chatMessages.length === 1"
            class="advisor-prompts"
            aria-label="试试这些问题"
          >
            <small>可以从这里开始</small
            ><button
              v-for="prompt in prompts"
              :key="prompt"
              type="button"
              @click="usePrompt(prompt)"
            >
              {{ prompt }}<UiIcon name="arrow" :size="16" />
            </button>
          </div>
          <form class="advisor-chat-input" @submit.prevent="sendChat()">
            <textarea
              class="input"
              rows="2"
              maxlength="800"
              v-model="chatInput"
              aria-label="向 AI 描述选购需求"
              @keydown.ctrl.enter.prevent="!$event.isComposing && sendChat()"
              @keydown.meta.enter.prevent="!$event.isComposing && sendChat()"
              placeholder="例如：90 平米、有猫、木地板为主，预算 5000 元，更在意毛发清理…"
            ></textarea
            ><button
              class="btn"
              :disabled="chatLoading || historyLoading || !chatInput.trim()"
            >
              {{ chatLoading ? "正在回答…" : "发送咨询"
              }}<UiIcon name="arrow" :size="17" />
            </button>
          </form>
          <p class="advisor-chat-note">
            登录后可跨设备打开各个对话，每个对话保留最近 20
            轮有效咨询。需要模型解释时才发送当前对话的必要上下文给
            DeepSeek，请勿输入隐私。明确表达的选购偏好可在<router-link
              to="/profile#preferences"
              >我的选购偏好</router-link
            >中纠正或删除。管理员可查看偏好摘要，不查看聊天正文。
          </p>
        </div>
      </div>
    </section>

    <section
      v-if="result?.items?.length"
      v-show="advisorMode === 'form'"
      class="container advisor-results"
    >
      <div class="advisor-result-head">
        <div>
          <span>筛选结果</span>
          <h2>{{ result.categoryName }}推荐结果</h2>
          <p>{{ result.summary }} · 预算 ¥{{ formatPrice(result.budget) }}</p>
          <small v-if="result.unverifiedCount"
            >{{
              result.unverifiedCount
            }}
            款同类商品因必要功能或续航缺少证据，未当作满足要求。</small
          >
        </div>
        <b>{{ result.items.length }} 款候选</b>
      </div>
      <div class="advisor-result-list">
        <article
          v-for="(item, index) in result.items"
          :key="item.product.id"
          class="advisor-result-card card"
        >
          <div class="advisor-rank">{{ index + 1 }}</div>
          <img
            :src="resourceUrl(item.product.image)"
            :alt="item.product.name"
          />
          <div class="advisor-product-copy">
            <span
              >{{ item.product.brandName }} ·
              {{ item.product.categoryName }}</span
            >
            <h3>{{ item.product.name }}</h3>
            <p>{{ item.product.subtitle }}</p>
            <ul>
              <li v-for="reason in item.reasons" :key="reason">{{ reason }}</li>
            </ul>
          </div>
          <div class="advisor-score">
            <strong>{{ item.score }}</strong
            ><small>匹配分</small
            ><em :class="{ over: !item.withinBudget }">{{
              item.withinBudget ? "预算内" : "进阶备选"
            }}</em
            ><b>¥{{ formatPrice(item.product.price) }}</b
            ><router-link class="btn" :to="`/product/${item.product.id}`"
              >查看商品</router-link
            >
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.advisor-conversation-layout {
  display: grid;
  grid-template-columns: minmax(11rem, 15rem) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.advisor-thread-sidebar {
  min-width: 0;
  padding: 1rem;
}

.advisor-new-thread {
  width: 100%;
}

.advisor-thread-heading {
  margin: 1.25rem 0 0.5rem;
  font-size: 0.9rem;
  font-weight: 700;
}

.advisor-thread-list {
  display: grid;
  gap: 0.25rem;
  max-height: 29rem;
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}

.advisor-thread-list button {
  display: block;
  width: 100%;
  padding: 0.55rem 0.65rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.advisor-thread-list button:hover,
.advisor-thread-list button[aria-current="page"] {
  border-color: currentColor;
}

.advisor-active-thread {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--line, #dce1e4);
}

.advisor-active-thread h3 {
  margin: 0 0 0.2rem;
}

@media (max-width: 740px) {
  .advisor-conversation-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .advisor-thread-list {
    display: flex;
    overflow: auto;
  }

  .advisor-thread-list li {
    flex: 0 0 min(13rem, 72vw);
  }
}
</style>
