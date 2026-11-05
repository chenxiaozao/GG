<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import api from "../api";
import ProductImage from "../components/ProductImage.vue";
import UiIcon from "../components/UiIcon.vue";

const all = ref<any[]>([]);
const ids = ref<number[]>([]);
const products = ref<any[]>([]);
const categoryId = ref("");
const loading = ref(false);
const message = ref("");
const onlyDifferences = ref(false);
const selectedProducts = computed(() =>
  all.value.filter((item) => ids.value.includes(item.id)),
);

const robots = computed(() =>
  all.value.filter((item) => item.type === "ROBOT"),
);
const categories = computed(() => {
  const map = new Map<string, { id: string; name: string; count: number }>();
  robots.value.forEach((item) => {
    const id = String(item.categoryId || "");
    if (!id) return;
    const current = map.get(id) || {
      id,
      name: item.categoryName || "未分类",
      count: 0,
    };
    current.count += 1;
    map.set(id, current);
  });
  return [...map.values()].filter((item) => item.count >= 2);
});
const available = computed(() =>
  robots.value.filter((item) => String(item.categoryId) === categoryId.value),
);
const parameterKeys = computed(() => {
  const keys: string[] = [];
  products.value.forEach((item) => {
    try {
      Object.keys(JSON.parse(item.parametersJson || "{}")).forEach((key) => {
        if (!keys.includes(key)) keys.push(key);
      });
    } catch {
      /* ignore malformed admin data */
    }
  });
  return keys;
});

function productParam(item: any, key: string) {
  try {
    return JSON.parse(item.parametersJson || "{}")[key] ?? "—";
  } catch {
    return "—";
  }
}

function isDifferent(key: string, type = "parameter") {
  const values = products.value.map((item) =>
    type === "parameter" ? productParam(item, key) : String(item[key] ?? "—"),
  );
  return new Set(values).size > 1;
}

function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", {
    maximumFractionDigits: 2,
  });
}

async function run() {
  if (ids.value.length < 2 || ids.value.length > 4) {
    message.value = "请在同一分类中选择 2～4 款商品";
    products.value = [];
    return;
  }
  loading.value = true;
  message.value = "";
  const selection = [...ids.value];
  const selectedCategory = categoryId.value;
  try {
    const result = (await api.post("/products/compare", selection)) as any[];
    if (
      categoryId.value === selectedCategory &&
      JSON.stringify(ids.value) === JSON.stringify(selection)
    ) {
      products.value = result;
      await nextTick();
      document.querySelector(".compare-result")?.scrollIntoView({
        block: "start",
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
      });
    }
  } catch (e: any) {
    message.value = e?.message || "参数对比失败";
    products.value = [];
  } finally {
    loading.value = false;
  }
}

watch(categoryId, () => {
  ids.value = [];
  products.value = [];
  message.value = "";
});
watch(
  ids,
  () => {
    products.value = [];
  },
  { deep: true },
);

onMounted(async () => {
  try {
    all.value = (await api.get("/products")) as any[];
    categoryId.value = categories.value[0]?.id || "";
  } catch (e: any) {
    message.value = e?.message || "商品加载失败";
  }
});
</script>

<template>
  <div class="compare-page">
    <div class="container">
      <div class="breadcrumb">
        <router-link to="/">首页</router-link><span>›</span
        ><span>参数对比</span>
      </div>
      <div class="compare-heading">
        <div>
          <h1>同类商品参数对比</h1>
          <p>先选择分类，再选择 2～4 款商品；不同分类不会被混在一张表中。</p>
        </div>
        <div class="compare-rule">
          <b>比较规则</b><span>同一分类</span><span>最多 4 款</span
          ><span>差异高亮</span>
        </div>
      </div>

      <div class="compare-category-tabs">
        <button
          v-for="category in categories"
          :key="category.id"
          :class="{ active: categoryId === category.id }"
          @click="categoryId = category.id"
        >
          {{ category.name }} <small>{{ category.count }}</small>
        </button>
      </div>

      <section class="compare-picker">
        <div class="compare-picker-head">
          <div>
            <b>选择商品</b><span>已选 {{ ids.length }}/4</span>
          </div>
          <div class="compare-selection-chips">
            <button
              v-for="item in selectedProducts"
              :key="item.id"
              type="button"
              :disabled="loading"
              :aria-label="`取消对比 ${item.name}`"
              @click="ids = ids.filter((id) => id !== item.id)"
            >
              {{ item.name }}<UiIcon name="close" :size="14" />
            </button>
          </div>
          <button
            class="btn"
            :disabled="loading || ids.length < 2"
            @click="run"
          >
            {{ loading ? "正在生成…" : "生成对比结果" }}
          </button>
        </div>
        <div class="compare-products">
          <label
            v-for="item in available"
            :key="item.id"
            :class="{
              selected: ids.includes(item.id),
              disabled: ids.length >= 4 && !ids.includes(item.id),
            }"
          >
            <input
              type="checkbox"
              :value="item.id"
              v-model="ids"
              :disabled="loading || (ids.length >= 4 && !ids.includes(item.id))"
            />
            <ProductImage :product="item" />
            <span
              ><b>{{ item.name }}</b
              ><em>¥{{ formatPrice(item.price) }}</em
              ><small
                >★ {{ Number(item.rating || 0).toFixed(1) }} · 已售
                {{ item.sales }}</small
              ></span
            >
          </label>
        </div>
      </section>

      <div class="notice" v-if="message">{{ message }}</div>
      <section v-if="products.length" class="compare-result">
        <div class="compare-result-title">
          <div>
            <span>COMPARISON</span>
            <h2>{{ products[0].categoryName }}对比结果</h2>
          </div>
          <p><i></i>强调底色表示该行存在差异</p>
        </div>
        <div class="compare-result-controls">
          <label
            ><input
              type="checkbox"
              v-model="onlyDifferences"
            />只看不同项</label
          ><button type="button" class="text-link" @click="ids = []">
            重新选择商品
          </button>
        </div>
        <div
          class="compare-table-wrap"
          :class="{ 'differences-only': onlyDifferences }"
        >
          <table class="compare-table">
            <thead>
              <tr>
                <th>对比项目</th>
                <th v-for="item in products" :key="item.id">
                  <ProductImage :product="item" /><b>{{ item.name }}</b
                  ><router-link :to="`/product/${item.id}`"
                    >查看详情 ›</router-link
                  >
                </th>
              </tr>
            </thead>
            <tbody>
              <tr :class="{ difference: isDifferent('price', 'field') }">
                <th>商城价格</th>
                <td v-for="item in products" :key="item.id">
                  <strong class="compare-price"
                    >¥{{ formatPrice(item.price) }}</strong
                  >
                </td>
              </tr>
              <tr :class="{ difference: isDifferent('brandName', 'field') }">
                <th>品牌</th>
                <td v-for="item in products" :key="item.id">
                  {{ item.brandName }}
                </td>
              </tr>
              <tr :class="{ difference: isDifferent('rating', 'field') }">
                <th>用户评分</th>
                <td v-for="item in products" :key="item.id">
                  ★ {{ Number(item.rating || 0).toFixed(1) }}
                </td>
              </tr>
              <tr
                v-for="key in parameterKeys"
                :key="key"
                :class="{ difference: isDifferent(key) }"
              >
                <th>{{ key }}</th>
                <td v-for="item in products" :key="item.id">
                  {{ productParam(item, key) }}
                </td>
              </tr>
              <tr :class="{ difference: isDifferent('shippingNote', 'field') }">
                <th>配送说明</th>
                <td v-for="item in products" :key="item.id">
                  {{ item.shippingNote || "—" }}
                </td>
              </tr>
              <tr :class="{ difference: isDifferent('warranty', 'field') }">
                <th>保修说明</th>
                <td v-for="item in products" :key="item.id">
                  {{ item.warranty || "—" }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>
