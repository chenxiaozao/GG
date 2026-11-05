<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import { computed, onMounted, ref } from "vue";
import api from "../api";

const robots = ref<any[]>([]);
const accessories = ref<any[]>([]);
const compatibleAccessories = ref<any[]>([]);
const categoryId = ref("");
const robotId = ref("");
const accessoryId = ref("");
const result = ref<any>(null);
const message = ref("");
const loading = ref(true);
const loadingAccessories = ref(false);
const checking = ref(false);

const categories = computed(() => {
  const groups = new Map<string, { id: string; name: string; count: number }>();
  robots.value.forEach((item) => {
    const id = String(item.categoryId || "");
    const current = groups.get(id) || { id, name: item.categoryName || "其他机器人", count: 0 };
    current.count += 1;
    groups.set(id, current);
  });
  return Array.from(groups.values());
});

const visibleRobots = computed(() => robots.value.filter((item) => !categoryId.value || String(item.categoryId) === categoryId.value));
const selectedRobot = computed(() => robots.value.find((item) => String(item.id) === robotId.value));
const selectedAccessory = computed(() => accessories.value.find((item) => String(item.id) === accessoryId.value));
const compatibleIds = computed(() => new Set(compatibleAccessories.value.map((item) => String(item.id))));
const otherAccessories = computed(() => accessories.value.filter((item) => !compatibleIds.value.has(String(item.id))));

onMounted(async () => {
  try {
    const products = (await api.get("/products")) as any[];
    robots.value = products.filter((item) => item.type === "ROBOT");
    accessories.value = products.filter((item) => item.type === "ACCESSORY");
  } catch (e: any) {
    message.value = e?.message || "商品加载失败";
  } finally {
    loading.value = false;
  }
});

function selectCategory(id: string) {
  categoryId.value = id;
  robotId.value = "";
  accessoryId.value = "";
  compatibleAccessories.value = [];
  result.value = null;
  message.value = "";
}

async function selectRobot(item: any) {
  robotId.value = String(item.id);
  accessoryId.value = "";
  compatibleAccessories.value = [];
  result.value = null;
  message.value = "";
  loadingAccessories.value = true;
  try {
    compatibleAccessories.value = (await api.get(`/products/${item.id}/accessories`)) as any[];
  } catch (e: any) {
    message.value = e?.message || "适配配件加载失败";
  } finally {
    loadingAccessories.value = false;
  }
}

function selectAccessory(item: any) {
  accessoryId.value = String(item.id);
  result.value = null;
  message.value = "";
}

async function check() {
  result.value = null;
  if (!robotId.value || !accessoryId.value) {
    message.value = "请先选择机器人主机和候选配件";
    return;
  }
  message.value = "";
  checking.value = true;
  try {
    result.value = await api.post("/compatibility/analyze", {
      mainId: Number(robotId.value), accessoryId: Number(accessoryId.value),
    });
  } catch (e: any) {
    message.value = e?.message || "适配检测失败";
  } finally {
    checking.value = false;
  }
}

function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", { maximumFractionDigits: 0 });
}
</script>

<template>
  <div class="compat-page">
    <div class="container">
      <div class="breadcrumb"><router-link to="/">首页</router-link><span>›</span><span>配件适配</span></div>
      <header class="compat-heading">
        <div><span>COMPATIBILITY CHECK</span><h1>配件适配检测</h1><p>先确定机器人主机，再从系统推荐或全部配件中选择需要核对的商品。</p></div>
        <div class="compat-summary"><b>{{ robots.length }}</b><span>款机器人主机</span><i></i><b>{{ accessories.length }}</b><span>款配件可检测</span></div>
      </header>

      <div v-if="loading" class="catalog-loading">正在加载商品资料…</div>
      <template v-else>
        <section class="compat-section">
          <div class="compat-section-head"><span>1</span><div><h2>选择机器人主机</h2><p>按类别缩小范围，再点击具体型号</p></div><b v-if="selectedRobot">已选：{{ selectedRobot.name }}</b></div>
          <div class="compat-category-tabs">
            <button :class="{ active: !categoryId }" @click="selectCategory('')">全部 <small>{{ robots.length }}</small></button>
            <button v-for="category in categories" :key="category.id" :class="{ active: categoryId === category.id }" @click="selectCategory(category.id)">{{ category.name }} <small>{{ category.count }}</small></button>
          </div>
          <div class="compat-product-grid">
            <button v-for="item in visibleRobots" :key="item.id" type="button" :class="{ selected: String(item.id) === robotId }" :aria-pressed="String(item.id) === robotId" @click="selectRobot(item)">
              <img :src="resourceUrl(item.image)" :alt="item.name" />
              <span><small>{{ item.brandName }} · {{ item.categoryName }}</small><b>{{ item.name }}</b><em>¥{{ formatPrice(item.price) }}</em></span>
              <i>{{ String(item.id) === robotId ? "已选择" : "选择" }}</i>
            </button>
          </div>
        </section>

        <section class="compat-section" :class="{ disabled: !selectedRobot }">
          <div class="compat-section-head"><span>2</span><div><h2>选择候选配件</h2><p>{{ selectedRobot ? `优先展示已确认适配 ${selectedRobot.name} 的配件` : "完成上一步后查看推荐配件" }}</p></div><b v-if="selectedAccessory">已选：{{ selectedAccessory.name }}</b></div>
          <template v-if="selectedRobot">
            <div v-if="loadingAccessories" class="compat-inline-loading">正在查找适配关系…</div>
            <template v-else>
              <div class="compat-subtitle"><b>系统推荐</b><span>数据库已建立适配关系</span></div>
              <div v-if="compatibleAccessories.length" class="compat-product-grid accessories">
                <button v-for="item in compatibleAccessories" :key="item.id" type="button" :class="{ selected: String(item.id) === accessoryId }" :aria-pressed="String(item.id) === accessoryId" @click="selectAccessory(item)">
                  <img :src="resourceUrl(item.image)" :alt="item.name" />
                  <span><small>{{ item.brandName }} · 推荐适配</small><b>{{ item.name }}</b><em>¥{{ formatPrice(item.price) }}</em></span>
                  <i>{{ String(item.id) === accessoryId ? "已选择" : "选择" }}</i>
                </button>
              </div>
              <div v-else class="compat-empty">暂未录入直接适配配件，可在下方全部配件中继续检测。</div>

              <details class="compat-other">
                <summary>查看其他候选配件 <span>{{ otherAccessories.length }} 件</span></summary>
                <p>以下商品尚未与当前主机建立确认关系，仍可选择并查看检测结果。</p>
                <div class="compat-product-grid accessories">
                  <button v-for="item in otherAccessories" :key="item.id" type="button" :class="{ selected: String(item.id) === accessoryId }" :aria-pressed="String(item.id) === accessoryId" @click="selectAccessory(item)">
                    <img :src="resourceUrl(item.image)" :alt="item.name" />
                    <span><small>{{ item.brandName }} · 待核对</small><b>{{ item.name }}</b><em>¥{{ formatPrice(item.price) }}</em></span>
                    <i>{{ String(item.id) === accessoryId ? "已选择" : "选择" }}</i>
                  </button>
                </div>
              </details>
            </template>
          </template>
        </section>

        <section class="compat-action-panel">
          <div><span>当前组合</span><b>{{ selectedRobot?.name || "尚未选择主机" }} <i>＋</i> {{ selectedAccessory?.name || "尚未选择配件" }}</b></div>
          <button class="btn" :disabled="!selectedRobot || !selectedAccessory || checking" @click="check">{{ checking ? "正在检测…" : "检测适配关系" }}</button>
        </section>
        <div class="notice" v-if="message">{{ message }}</div>
        <section v-if="result" class="compat-analysis" :class="{ success: result.compatible, warning: !result.compatible }">
          <header>
            <span>{{ result.compatible ? "✓" : "!" }}</span>
            <div><small>结构化适配结论</small><h2>{{ result.title }}</h2><p>{{ result.summary }}</p></div>
            <div class="compat-confidence"><strong>{{ result.confidence }}%</strong><small>资料可信度</small></div>
          </header>
          <div class="compat-check-list">
            <article v-for="item in result.checks" :key="item.name" :class="item.status.toLowerCase()">
              <i>{{ item.status === 'PASS' ? '✓' : '?' }}</i><div><b>{{ item.name }}</b><p>{{ item.detail }}</p></div>
            </article>
          </div>
          <div class="compat-risk"><b>使用前提醒</b><p>{{ result.riskNote }}</p></div>
          <footer><span>本结果仅根据平台已录入的型号资料，不以“未录入”推断“必然不兼容”。</span><router-link v-if="result.compatible && selectedAccessory" class="btn secondary" :to="`/product/${selectedAccessory.id}`">查看配件详情</router-link></footer>
        </section>
      </template>
    </div>
  </div>
</template>
