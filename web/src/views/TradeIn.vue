<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import { computed, onMounted, reactive, ref, watch } from "vue";
import api from "../api";

const robots = ref<any[]>([]);
const selectionMode = ref<"catalog" | "manual">("catalog");
const brandId = ref("");
const categoryId = ref("");
const productId = ref("");
const loadingModels = ref(true);
const form = reactive({ brand: "", model: "", originalPrice: 0, age: 1, condition: "good", functionStatus: "normal", batteryHealth: "70-89", accessories: "complete" });
const result = ref<any>(null);
const message = ref("");
const loading = ref(false);

const brands = computed(() => {
  const groups = new Map<string, { id: string; name: string; count: number }>();
  robots.value.forEach((item) => {
    const id = String(item.brandId || "");
    const current = groups.get(id) || { id, name: item.brandName || "其他品牌", count: 0 };
    current.count += 1;
    groups.set(id, current);
  });
  return Array.from(groups.values()).sort((a, b) => b.count - a.count);
});

const categories = computed(() => {
  if (!brandId.value) return [];
  const groups = new Map<string, { id: string; name: string; count: number }>();
  robots.value.filter((item) => String(item.brandId) === brandId.value).forEach((item) => {
    const id = String(item.categoryId || "");
    const current = groups.get(id) || { id, name: item.categoryName || "其他类别", count: 0 };
    current.count += 1;
    groups.set(id, current);
  });
  return Array.from(groups.values());
});

const models = computed(() => robots.value
  .filter((item) => String(item.brandId) === brandId.value && String(item.categoryId) === categoryId.value)
  .sort((a, b) => Number(a.price) - Number(b.price)));
const selectedProduct = computed(() => robots.value.find((item) => String(item.id) === productId.value));
const progress = computed(() => {
  let count = 0;
  if (form.brand.trim() && form.model.trim()) count += 1;
  if (form.originalPrice >= 500) count += 1;
  if (form.condition && form.functionStatus) count += 1;
  if (form.batteryHealth && form.accessories) count += 1;
  return count * 25;
});

onMounted(async () => {
  try {
    const products = (await api.get("/products")) as any[];
    robots.value = products.filter((item) => item.type === "ROBOT");
  } catch (e: any) {
    message.value = e?.message || "商城型号加载失败，可切换为手动填写";
    selectionMode.value = "manual";
  } finally {
    loadingModels.value = false;
  }
});

watch(form, () => {
  result.value = null;
  if (!loading.value) message.value = "";
}, { deep: true });

function resetDevice() {
  form.brand = "";
  form.model = "";
  form.originalPrice = 0;
  result.value = null;
  message.value = "";
}

function switchMode(mode: "catalog" | "manual") {
  if (selectionMode.value === mode) return;
  selectionMode.value = mode;
  brandId.value = "";
  categoryId.value = "";
  productId.value = "";
  resetDevice();
}

function selectBrand(item: any) {
  brandId.value = item.id;
  categoryId.value = "";
  productId.value = "";
  resetDevice();
  const availableCategories = new Set(robots.value
    .filter((product) => String(product.brandId) === item.id)
    .map((product) => String(product.categoryId)));
  if (availableCategories.size === 1) categoryId.value = Array.from(availableCategories)[0];
}

function selectCategory(item: any) {
  categoryId.value = item.id;
  productId.value = "";
  resetDevice();
}

function modelName(item: any) {
  const aliases: Record<string, string> = {
    "石头科技": "石头", "宇树科技": "宇树", "云鲸智能": "云鲸",
    "追觅科技": "追觅", "普渡机器人": "普渡", "Autel Robotics": "Autel",
  };
  const prefixes = [item.brandName, aliases[item.brandName]].filter(Boolean);
  for (const prefix of prefixes) {
    if (item.name.startsWith(prefix)) return item.name.slice(prefix.length).trim();
  }
  return item.name;
}

function selectProduct(item: any) {
  productId.value = String(item.id);
  form.brand = item.brandName;
  form.model = modelName(item);
  form.originalPrice = Number(item.price);
  result.value = null;
  message.value = "";
}

async function estimate() {
  result.value = null;
  if (!form.brand.trim() || !form.model.trim()) {
    message.value = selectionMode.value === "catalog" ? "请依次选择品牌、类别和型号" : "请填写设备品牌和型号";
    return;
  }
  if (form.originalPrice < 500 || form.originalPrice > 1000000) { message.value = "原购买价格应在 500～1000000 元之间"; return; }
  if (form.age < 0 || form.age > 10) { message.value = "使用年限应在 0～10 年之间"; return; }
  message.value = "";
  loading.value = true;
  try { result.value = await api.post("/trade-in/estimate", form); }
  catch (e: any) { message.value = e?.message || "估价失败"; }
  finally { loading.value = false; }
}

function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", { maximumFractionDigits: 0 });
}
</script>

<template>
  <div class="trade-page">
    <div class="container">
      <div class="breadcrumb"><router-link to="/">首页</router-link><span>›</span><span>以旧换新</span></div>
      <section class="trade-hero">
        <div><span>设备换新参考</span><h1>旧机器人残值评估</h1><p>从商城型号库快速定位设备，也支持未收录机型手动填写。原价、机龄和设备状态共同参与估值。</p></div>
        <div class="trade-steps"><span class="active"><b>1</b>选择设备</span><i></i><span><b>2</b>状态评估</span><i></i><span><b>3</b>查看估价</span></div>
      </section>

      <div class="trade-layout">
        <form class="trade-form" @submit.prevent="estimate">
          <div class="trade-form-head"><div><h2>设备信息</h2><p>带 <em>*</em> 的信息用于计算模拟回收区间</p></div><span>{{ progress }}% 已完成</span></div>
          <div class="trade-progress"><i :style="{ width: `${progress}%` }"></i></div>

          <section>
            <h3>1. 确认设备型号</h3>
            <div class="trade-mode-tabs">
              <button type="button" :class="{ active: selectionMode === 'catalog' }" @click="switchMode('catalog')">从商城型号选择 <small>{{ robots.length }} 款</small></button>
              <button type="button" :class="{ active: selectionMode === 'manual' }" @click="switchMode('manual')">其他型号手动填写</button>
            </div>

            <div v-if="selectionMode === 'catalog'" class="trade-cascade">
              <div class="trade-cascade-block">
                <div class="trade-cascade-label"><b>① 选择品牌</b><span>仅显示商城已收录品牌</span></div>
                <div v-if="loadingModels" class="compat-inline-loading">正在加载商城型号…</div>
                <div v-else class="trade-brand-grid">
                  <button v-for="brand in brands" :key="brand.id" type="button" :class="{ selected: brandId === brand.id }" @click="selectBrand(brand)"><b>{{ brand.name }}</b><small>{{ brand.count }} 款设备</small></button>
                </div>
              </div>

              <div class="trade-cascade-block" :class="{ disabled: !brandId }">
                <div class="trade-cascade-label"><b>② 选择类别</b><span>{{ brandId ? "只显示该品牌下有商品的类别" : "请先选择品牌" }}</span></div>
                <div v-if="brandId" class="trade-category-pills">
                  <button v-for="category in categories" :key="category.id" type="button" :class="{ selected: categoryId === category.id }" @click="selectCategory(category)">{{ category.name }} <small>{{ category.count }}</small></button>
                </div>
              </div>

              <div class="trade-cascade-block" :class="{ disabled: !categoryId }">
                <div class="trade-cascade-label"><b>③ 选择具体型号</b><span>{{ categoryId ? `共 ${models.length} 款可选` : "请先选择类别" }}</span></div>
                <div v-if="categoryId" class="trade-model-grid">
                  <button v-for="item in models" :key="item.id" type="button" :class="{ selected: productId === String(item.id) }" @click="selectProduct(item)">
                    <img :src="resourceUrl(item.image)" :alt="item.name" />
                    <span><b>{{ item.name }}</b><small>{{ item.subtitle }}</small><em>商城价 ¥{{ formatPrice(item.price) }}</em></span>
                    <i>{{ productId === String(item.id) ? "已选择" : "选择" }}</i>
                  </button>
                </div>
              </div>

              <div v-if="selectedProduct" class="trade-selected-device">
                <img :src="resourceUrl(selectedProduct.image)" :alt="selectedProduct.name" />
                <div><small>当前估价设备</small><b>{{ selectedProduct.name }}</b><span>{{ selectedProduct.categoryName }} · {{ selectedProduct.brandName }}</span></div>
                <strong>已自动填写型号</strong>
              </div>
            </div>

            <div v-else class="trade-fields trade-manual-fields">
              <label>品牌 <em>*</em><input class="input" v-model.trim="form.brand" placeholder="例如：石头科技" /></label>
              <label>型号 <em>*</em><input class="input" v-model.trim="form.model" placeholder="例如：G20S Ultra" /></label>
            </div>

            <div class="trade-fields trade-price-fields" :class="{ disabled: selectionMode === 'catalog' && !selectedProduct }">
              <label>原购买价格 <em>*</em><div class="money-input"><span>¥</span><input type="number" min="500" max="1000000" v-model.number="form.originalPrice" /></div><small v-if="selectedProduct">已按商城当前售价预填，可按实际购买价修改</small></label>
              <label>使用年限 <em>*</em><select class="input" v-model.number="form.age"><option :value="0">不足 1 年</option><option v-for="year in 10" :key="year" :value="year">{{ year }} 年</option></select></label>
            </div>
          </section>

          <section><h3>2. 设备状态</h3><label>外观成色</label><div class="trade-choice-grid four"><label><input type="radio" value="excellent" v-model="form.condition" /><span><b>近新</b><small>无明显划痕</small></span></label><label><input type="radio" value="good" v-model="form.condition" /><span><b>良好</b><small>轻微使用痕迹</small></span></label><label><input type="radio" value="fair" v-model="form.condition" /><span><b>一般</b><small>明显划痕磨损</small></span></label><label><input type="radio" value="poor" v-model="form.condition" /><span><b>较差</b><small>破损或变形</small></span></label></div>
          <label>主要功能</label><div class="trade-choice-grid"><label><input type="radio" value="normal" v-model="form.functionStatus" /><span><b>全部正常</b><small>核心功能无异常</small></span></label><label><input type="radio" value="minor" v-model="form.functionStatus" /><span><b>轻微故障</b><small>不影响主要使用</small></span></label><label><input type="radio" value="major" v-model="form.functionStatus" /><span><b>严重故障</b><small>核心功能异常</small></span></label></div></section>

          <section><h3>3. 电池与配件</h3><div class="trade-fields"><label>电池健康度<select class="input" v-model="form.batteryHealth"><option value="90+">90% 以上</option><option value="70-89">70%～89%</option><option value="50-69">50%～69%</option><option value="below50">低于 50%</option></select></label><label>原装配件<select class="input" v-model="form.accessories"><option value="complete">包装与配件齐全</option><option value="partial">缺少部分配件</option><option value="none">无包装及配件</option></select></label></div></section>

          <div class="notice" v-if="message">{{ message }}</div><button class="btn trade-submit" :disabled="loading">{{ loading ? "正在计算估值…" : "计算回收参考价" }}</button>
        </form>

        <aside class="trade-result-panel">
          <template v-if="result"><span class="result-status">估价已完成</span><small class="result-device-brand">{{ result.brand }}</small><h2>{{ result.model }}</h2><p>模拟回收价格区间</p><strong>¥{{ formatPrice(result.estimateLow) }}－{{ formatPrice(result.estimateHigh) }}</strong><small>估值中位数 ¥{{ formatPrice(result.estimate) }}</small><div class="trade-bonus"><span>换新加价券</span><b>+ ¥{{ result.tradeInBonus }}</b></div><dl><div v-for="(value, key) in result.factors" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div></dl><p class="trade-disclaimer">{{ result.disclaimer }}</p><router-link class="btn secondary" to="/products">选择换新商品</router-link></template>
          <template v-else><div class="trade-placeholder"><b>¥ ?</b><h2>等待设备信息</h2><p>完成左侧信息后，系统将给出估价区间和各项折价系数。</p></div><div class="trade-help"><b>估值逻辑</b><p>参考残值从原购买价格的 55% 起算，再根据机龄、外观、功能、电池和配件情况逐项折算。</p></div></template>
        </aside>
      </div>
    </div>
  </div>
</template>
