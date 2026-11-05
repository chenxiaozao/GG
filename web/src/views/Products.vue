<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../api";
import ProductGrid from "../components/ProductGrid.vue";
import UiIcon from "../components/UiIcon.vue";

const route = useRoute();
const router = useRouter();
const products = ref<any[]>([]);
const categories = ref<any[]>([]);
const brands = ref<any[]>([]);
const loading = ref(false);
const message = ref("");
const filtersOpen = ref(false);
const brandsExpanded = ref(false);
const page = ref(1);
const pageSize = 12;
let loadSequence = 0;
const query = reactive({
  keyword: "",
  categoryId: "",
  brandId: "",
  minPrice: "",
  maxPrice: "",
  sort: "sales",
});

const activeCategory = computed(() =>
  categories.value.find((item) => String(item.id) === String(query.categoryId)),
);
const activeBrand = computed(() =>
  brands.value.find((item) => String(item.id) === String(query.brandId)),
);
const visibleBrands = computed(() =>
  brandsExpanded.value ? brands.value : brands.value.slice(0, 8),
);
const totalPages = computed(() =>
  Math.max(1, Math.ceil(products.value.length / pageSize)),
);
const visibleProducts = computed(() =>
  products.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
const activeFilters = computed(() =>
  [
    { key: "keyword", value: query.keyword, label: `搜索：${query.keyword}` },
    {
      key: "categoryId",
      value: query.categoryId,
      label: activeCategory.value?.name,
    },
    { key: "brandId", value: query.brandId, label: activeBrand.value?.name },
    {
      key: "minPrice",
      value: query.minPrice,
      label: `最低 ¥${query.minPrice}`,
    },
    {
      key: "maxPrice",
      value: query.maxPrice,
      label: `最高 ¥${query.maxPrice}`,
    },
  ].filter((item) => item.value),
);

function syncFromRoute() {
  for (const key of Object.keys(query) as Array<keyof typeof query>)
    query[key] = String(route.query[key] || (key === "sort" ? "sales" : ""));
}

async function load(updateUrl = true) {
  const sequence = ++loadSequence;
  if (
    [query.minPrice, query.maxPrice].some(
      (value) =>
        value !== "" && (!Number.isFinite(Number(value)) || Number(value) < 0),
    ) ||
    (query.minPrice !== "" &&
      query.maxPrice !== "" &&
      Number(query.minPrice) > Number(query.maxPrice))
  ) {
    message.value = "请输入有效价格区间，最低价不能高于最高价";
    loading.value = false;
    return;
  }
  loading.value = true;
  message.value = "";
  try {
    const params = Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== ""),
    );
    const result = (await api.get("/products", { params })) as any[];
    if (sequence !== loadSequence) return;
    products.value = result;
    page.value = 1;
    if (updateUrl) {
      await router.replace({ path: "/products", query: params });
    }
  } catch (e: any) {
    if (sequence === loadSequence) message.value = e?.message || "商品加载失败";
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

function selectCategory(id: string | number) {
  query.categoryId = String(id || "");
  filtersOpen.value = false;
  load();
}

function selectBrand(id: string | number) {
  query.brandId = String(id || "");
  filtersOpen.value = false;
  load();
}

function resetFilters() {
  Object.assign(query, {
    keyword: "",
    categoryId: "",
    brandId: "",
    minPrice: "",
    maxPrice: "",
    sort: "sales",
  });
  load();
}
function removeFilter(key: string) {
  query[key as keyof typeof query] = "";
  load();
}
function changePage(next: number) {
  page.value = Math.max(1, Math.min(totalPages.value, next));
  document
    .querySelector(".catalog-toolbar")
    ?.scrollIntoView({
      block: "start",
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
}

onMounted(async () => {
  syncFromRoute();
  try {
    [categories.value, brands.value] = (await Promise.all([
      api.get("/categories"),
      api.get("/brands"),
    ])) as any;
  } catch (e: any) {
    message.value = e?.message || "筛选项加载失败";
  }
  await load(false);
});

watch(
  () => route.query,
  () => {
    if (
      (Object.keys(query) as Array<keyof typeof query>).some(
        (key) =>
          String(route.query[key] || (key === "sort" ? "sales" : "")) !==
          query[key],
      )
    ) {
      syncFromRoute();
      load(false);
    }
  },
);
</script>

<template>
  <div class="catalog-page products-page">
    <div class="container catalog-container">
      <div class="breadcrumb">
        <router-link to="/">首页</router-link><span>›</span
        ><span>全部商品</span>
      </div>
      <div class="catalog-heading products-head">
        <div>
          <p class="section-kicker">探索商城</p>
          <h1>{{ activeCategory?.name || "找到适合你的机器人" }}</h1>
          <p>从分类、品牌与预算开始，慢慢缩小选择范围。</p>
        </div>
        <form class="catalog-search products-search" @submit.prevent="load()">
          <UiIcon name="search" /><input
            v-model="query.keyword"
            aria-label="搜索商城商品"
            placeholder="搜索品牌或型号"
          /><button type="submit">搜索</button>
        </form>
      </div>

      <div class="catalog-layout">
        <button
          class="catalog-filter-toggle"
          type="button"
          :aria-expanded="filtersOpen"
          @click="filtersOpen = !filtersOpen"
        >
          <span>筛选商品</span
          ><b>{{ filtersOpen ? "收起" : "分类 · 品牌 · 价格" }}</b>
        </button>
        <aside
          class="catalog-sidebar filter-sidebar"
          :class="{ open: filtersOpen }"
        >
          <section class="filter-section">
            <h3>商品分类</h3>
            <button
              class="filter-option"
              :aria-pressed="!query.categoryId"
              :class="{ active: !query.categoryId }"
              @click="selectCategory('')"
            >
              全部分类</button
            ><button
              v-for="category in categories"
              :key="category.id"
              class="filter-option"
              :aria-pressed="String(query.categoryId) === String(category.id)"
              :class="{
                active: String(query.categoryId) === String(category.id),
              }"
              @click="selectCategory(category.id)"
            >
              {{ category.name }} <span>›</span>
            </button>
          </section>
          <section class="filter-section">
            <h3>品牌</h3>
            <div class="filter-brand-chips">
              <button
                class="filter-option"
                :class="{ active: !query.brandId }"
                @click="selectBrand('')"
              >
                不限品牌</button
              ><button
                v-for="brand in visibleBrands"
                :key="brand.id"
                class="filter-option"
                :aria-pressed="String(query.brandId) === String(brand.id)"
                :class="{ active: String(query.brandId) === String(brand.id) }"
                @click="selectBrand(brand.id)"
              >
                {{ brand.name }}
              </button>
            </div>
            <button
              v-if="brands.length > 8"
              class="filter-more"
              type="button"
              :aria-expanded="brandsExpanded"
              @click="brandsExpanded = !brandsExpanded"
            >
              {{ brandsExpanded ? "收起品牌" : `更多品牌（${brands.length}）` }}
            </button>
          </section>
          <form class="filter-section" @submit.prevent="load()">
            <h3>价格区间</h3>
            <div class="price-filter">
              <input
                type="number"
                min="0"
                v-model="query.minPrice"
                aria-label="最低价"
                placeholder="最低价"
              /><span>—</span
              ><input
                type="number"
                min="0"
                v-model="query.maxPrice"
                aria-label="最高价"
                placeholder="最高价"
              />
            </div>
            <button class="sidebar-apply" type="submit">应用价格</button>
          </form>
        </aside>

        <section class="catalog-main">
          <div class="catalog-active" v-if="activeFilters.length">
            <button
              v-for="filter in activeFilters"
              :key="filter.key"
              class="filter-chip"
              :aria-label="`移除筛选：${filter.label}`"
              @click="removeFilter(filter.key)"
            >
              {{ filter.label }}<UiIcon name="close" :size="14" /></button
            ><button class="filter-clear" @click="resetFilters">
              清除全部
            </button>
          </div>
          <div class="catalog-toolbar products-toolbar">
            <span aria-live="polite">{{
              loading ? "正在筛选…" : `找到 ${products.length} 件商品`
            }}</span
            ><label
              >排序<select
                v-model="query.sort"
                aria-label="商品排序"
                @change="load()"
              >
                <option value="sales">销量优先</option>
                <option value="rating">评分优先</option>
                <option value="priceAsc">价格从低到高</option>
                <option value="priceDesc">价格从高到低</option>
              </select></label
            >
          </div>
          <div class="notice catalog-error" role="alert" v-if="message">
            {{ message
            }}<button type="button" class="text-link" @click="load()">
              重新加载
            </button>
          </div>
          <ProductGrid :products="visibleProducts" :loading="loading" />
          <div v-if="!loading && !products.length" class="empty">
            <h2>没有找到相关商品</h2>
            <p>可以尝试更换分类、品牌或价格区间。</p>
            <button class="btn secondary" @click="resetFilters">
              重置筛选
            </button>
          </div>
          <nav
            v-if="!loading && totalPages > 1"
            class="catalog-pagination"
            aria-label="商品分页"
          >
            <span>第 {{ page }} / {{ totalPages }} 页</span>
            <div>
              <button
                type="button"
                :disabled="page === 1"
                @click="changePage(page - 1)"
              >
                上一页</button
              ><button
                v-for="number in totalPages"
                :key="number"
                type="button"
                :class="{ active: page === number }"
                :aria-current="page === number ? 'page' : undefined"
                :aria-label="`第 ${number} 页`"
                @click="changePage(number)"
              >
                {{ number }}</button
              ><button
                type="button"
                :disabled="page === totalPages"
                @click="changePage(page + 1)"
              >
                下一页
              </button>
            </div>
          </nav>
        </section>
      </div>
    </div>
  </div>
</template>
