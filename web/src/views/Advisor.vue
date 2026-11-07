<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import ProductImage from "../components/ProductImage.vue";
import { nextTick, onMounted, reactive, ref, watch } from "vue";
import api from "../api";
import AiAnswer from "../components/AiAnswer.vue";
import UiIcon from "../components/UiIcon.vue";
import { useRouter } from "vue-router";
const router = useRouter();

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
const quota = ref<{remaining:number;limit:number;note:string}|null>(null);
const featureOptions = ['自动集尘', '避障', '防缠绕', '拖地', '编程', '遥控'];
async function refreshQuota() {
  if (!localStorage.getItem('token')) return;
  try { quota.value = await api.get('/advisor/quota') as any; } catch { quota.value = null; }
}
const chatRegion = ref<HTMLElement | null>(null);
const chatInput = ref("");
const contextProducts = ref<any[]>([]);
const contextIds = ref<number[]>([]);
const contextError = ref('');
function clearContext(){contextIds.value=[];contextProducts.value=[];contextError.value='';sessionStorage.removeItem('robotmall-advisor-context');}
function savePlanDraft(){
 const requirement=chatInput.value.trim() || [...chatMessages.value].reverse().find(m=>m.role==='user')?.content || '';
 sessionStorage.setItem('robotmall-plan-draft',JSON.stringify({owner:JSON.parse(localStorage.getItem('user')||'null')?.id,requirement,productIds:contextIds.value.length?contextIds.value:(chatMessages.value.at(-1)?.products||[]).slice(0,4).map(p=>p.id)}));
 router.push('/profile#plans');
}
const chatLoading = ref(false);
const advisorMode = ref<"chat" | "form">("chat");
const prompts = [
  "家里有猫，90 平米，预算 5000 元，想选省心的扫拖机器人",
  "第一次买航拍无人机，预算 6000 元，更看重便携和易上手",
  "想找适合孩子入门编程的机器人，预算 3000 元",
];
function usePrompt(question: string) {
  chatInput.value = question;
  document
    .querySelector<HTMLTextAreaElement>(".advisor-chat-input textarea")
    ?.focus();
}
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
    evidence?: Array<{product:{id:number;name:string};reasons:string[]}>;
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

function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", {
    maximumFractionDigits: 2,
  });
}
async function sendChat() {
  const question = chatInput.value.trim();
  if (!question || chatLoading.value) return;
  if (!localStorage.getItem("token")) {
    sessionStorage.setItem("robotmall-advisor-entry", question);
    await router.push({ path: "/login", query: { redirect: "/advisor" } });
    return;
  }
  const history = chatMessages.value
    .filter((item) => !item.error)
    .slice(-6)
    .map(({ role, content }) => ({ role, content }));
  chatMessages.value.push({ role: "user", content: question });
  chatInput.value = "";
  chatLoading.value = true;
  try {
    const response: any = await api.post(
      "/advisor/chat",
      { question, history, contextProductIds: contextIds.value },
      { timeout: 45000 },
    );
    aiConnected.value = response.provider === 'DeepSeek';
    chatMessages.value.push({
      role: "assistant",
      content: response.answer,
      products: response.products || [],
      evidence: response.evidence || [],
      notice: response.notice || '',
    });
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
  try {
    const ids=JSON.parse(sessionStorage.getItem('robotmall-advisor-context')||'[]');
    if(Array.isArray(ids)) {
      contextIds.value=[...new Set(ids.filter(id=>Number.isSafeInteger(id)&&id>0))].slice(0,4) as number[];
      contextProducts.value=await Promise.all(contextIds.value.map(id=>api.get(`/products/${id}`)));
    }
  } catch { contextError.value='候选商品读取失败，请返回原页面重新选择，或清除候选后咨询。'; }
  await refreshQuota();
  const entry = sessionStorage.getItem('robotmall-advisor-entry');
  if (entry) {
    chatInput.value = entry;
    sessionStorage.removeItem('robotmall-advisor-entry');
    await nextTick();
    document.querySelector<HTMLTextAreaElement>('.advisor-chat-input textarea')?.focus();
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
    <section class="context-strip container" aria-label="本次选购上下文"><div v-if="contextIds.length"><b>正在围绕 {{ contextProducts.length }} 款候选咨询</b><p>只在这些候选内分析；没有满足条件的商品时不会自动放宽要求。</p><router-link v-for="p in contextProducts" :key="p.id" :to="`/product/${p.id}`">{{ p.name }} · </router-link><button class="btn secondary" @click="clearContext">清除候选</button></div><p v-if="contextError" role="alert">{{ contextError }}</p><button class="btn secondary" @click="savePlanDraft">保存为选购计划</button></section>
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
            ><input class="input" type="number" min="0" max="1440" v-model.number="form.minEndurance" />
            <small>0 表示不限；目录缺少数据时不能视为满足要求</small></label
          >
          <label
            ><span>必要功能（可多选）</span
            ><div class="required-features"><label v-for="feature in featureOptions" :key="feature"><input type="checkbox" :value="feature" v-model="form.requiredFeatures" />{{ feature }}</label></div>
            <small>必须有目录参数证据，否则不推荐</small></label
          >
        </div>
        <label class="budget-opt-in"><input type="checkbox" v-model="form.allowOverBudget" />允许最多超预算 20% 的进阶备选</label>
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
            <b>预算约束</b><small>默认不超预算，仅在明确勾选后允许适度上浮</small>
          </li>
          <li>
            <b>规则排序</b><small>先验证硬条件，再按价格、续航或售后证据排序</small>
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
          <small v-if="quota && Number.isFinite(quota.remaining)">今日剩余 {{ quota.remaining }} / {{ quota.limit }} 次；UTC自然日重置。无关话题本地拒答不计AI额度</small>
        </div>
        <b :class="{ muted: !aiConfigured }">{{
          aiConnected
            ? "DeepSeek 已连接"
            : aiConfigured
              ? "DeepSeek 已配置"
              : "待配置 Token"
        }}</b>
      </div>
      <div class="advisor-chat card">
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
            <p v-if="item.notice" class="ai-evidence-notice">{{ item.notice }}</p>
            <details v-if="item.evidence?.length" class="ai-evidence"><summary>查看推荐参数依据</summary><div v-for="row in item.evidence" :key="row.product.id"><b>{{ row.product.name }}</b><ul><li v-for="reason in row.reasons" :key="reason">{{ reason }}</li></ul></div></details>
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
        <form class="advisor-chat-input" @submit.prevent="sendChat">
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
          ><button class="btn" :disabled="chatLoading || !chatInput.trim()">
            {{ chatLoading ? "正在回答…" : "发送咨询"
            }}<UiIcon name="arrow" :size="17" />
          </button>
        </form>
        <p class="advisor-chat-note">
          本助手仅用于机器人选购与商城使用，不处理无关作业。有效咨询会发送至 DeepSeek，请勿输入手机号等隐私；回答可能有误，请核对参数依据。本系统的选购偏好默认不收集，可在<router-link to="/profile#preferences">我的选购偏好</router-link>中自行开启、纠正或删除；档案开关不影响你主动发出的 AI 请求。
        </p>
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
          <small v-if="result.unverifiedCount">{{ result.unverifiedCount }} 款同类商品因必要功能或续航缺少证据，未当作满足要求。</small>
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
