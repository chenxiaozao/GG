<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api";
import ProductGrid from "../components/ProductGrid.vue";
import UiIcon from "../components/UiIcon.vue";

const router = useRouter();
const products = ref<any[]>([]);
const categories = ref<any[]>([]);
const brands = ref<any[]>([]);
const loading = ref(false);
const message = ref("");
const keyword = ref("");
const need = ref("");
const scenarios = ['家里有宠物，想选省心的扫拖机器人', '想给孩子选一款编程机器人', '需要清洁窗户，想了解有哪些选择'];
function consult() {
  if (!need.value.trim()) return;
  sessionStorage.setItem('robotmall-advisor-entry', need.value.trim().slice(0, 1000));
  router.push('/advisor');
}

const robots = computed(() =>
  products.value.filter((item) => item.type === "ROBOT"),
);
const accessories = computed(() =>
  products.value.filter((item) => item.type === "ACCESSORY"),
);

function countByCategory(categoryId: number) {
  return products.value.filter((item) => item.categoryId === categoryId).length;
}

function search() {
  router.push({
    path: "/products",
    query: keyword.value.trim() ? { keyword: keyword.value.trim() } : {},
  });
}

onMounted(async () => {
  loading.value = true;
  try {
    [products.value, categories.value, brands.value] = (await Promise.all([
      api.get("/products"),
      api.get("/categories"),
      api.get("/brands"),
    ])) as any;
  } catch (e: any) {
    message.value = e?.message || "商城数据加载失败";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="home-page">
    <section class="discovery-hero">
      <div class="discovery-intro"><span class="discovery-label">小造智械 · 选购助手</span><h1>先说需求，<br />再选机器人。</h1><p>不必先弄懂每一个参数。说说你想解决的问题，我们一起缩小选择范围。</p><router-link to="/products">我有目标，直接逛商品 →</router-link></div>
      <form class="need-composer" @submit.prevent="consult">
        <label for="home-need">你想让机器人帮你做什么？</label>
        <textarea id="home-need" v-model="need" maxlength="1000" placeholder="例如：家里有猫，90 平米，预算 4000 元，希望少清理毛发。" required />
        <div class="need-scenarios"><button v-for="scenario in scenarios" :key="scenario" type="button" @click="need = scenario">{{ scenario }}</button></div>
        <div class="need-submit"><small>仅在商城商品中助选 · 发送前可修改需求</small><button class="btn" :disabled="!need.trim()">开始 AI 助选 <UiIcon name="arrow" :size="18" /></button></div>
      </form>
    </section>
    <div class="home-browse-bar"><form @submit.prevent="search"><UiIcon name="search" :size="18" /><input v-model="keyword" aria-label="搜索品牌或型号" placeholder="已有型号？搜索品牌或商品" /><button type="submit">搜索</button></form><router-link to="/compare">比较已有选择 →</router-link><router-link to="/profile">进入我的空间 →</router-link></div>

    <div class="container">
      <nav class="home-category-rail" aria-label="商品分类">
        <router-link
          v-for="category in categories"
          :key="category.id"
          :to="{ path: '/products', query: { categoryId: category.id } }"
        >
          {{ category.name }} <span>{{ countByCategory(category.id) }} 件</span>
        </router-link>
      </nav>

      <section class="home-section">
        <div class="home-section-head">
          <div>
            <p class="section-kicker">逛逛商城</p>
            <h2 class="section-title">近期热门商品</h2>
            <p>
              覆盖 {{ robots.length }} 款机器人主机与
              {{ accessories.length }} 款配件，参数来自公开商品资料。
            </p>
          </div>
          <router-link class="text-link" to="/products">查看全部 →</router-link>
        </div>
        <div class="notice" v-if="message">{{ message }}</div>
        <ProductGrid :loading="loading" :products="products.slice(0, 8)" />
      </section>

      <section class="home-section">
        <div class="home-section-head">
          <div>
            <p class="section-kicker">选购工具</p>
            <h2 class="section-title">把选择依据说明白</h2>
            <p>不是简单地陈列商品，而是帮助你缩小范围、核对适配并理解差异。</p>
          </div>
        </div>
        <div class="decision-tools-v3">
          <router-link class="decision-tool-v3" to="/advisor">
            <UiIcon name="spark" :size="26" /><small>按需求</small>
            <h3>选购顾问</h3>
            <p>根据预算、居住环境和关注功能，获得规则推荐与 AI 补充建议。</p>
          </router-link>
          <router-link class="decision-tool-v3" to="/compare">
            <UiIcon name="compare" :size="26" /><small>看差异</small>
            <h3>参数对比</h3>
            <p>并排比较同类设备的核心规格，突出不同型号间的关键差别。</p>
          </router-link>
          <router-link class="decision-tool-v3" to="/compatibility">
            <UiIcon name="settings" :size="26" /><small>查兼容</small>
            <h3>配件适配</h3>
            <p>按主机、接口和型号关系检查兼容性，降低配件买错的风险。</p>
          </router-link>
        </div>
      </section>

      <section class="home-section">
        <div class="home-section-head">
          <div>
            <p class="section-kicker">从熟悉的品牌开始</p>
            <h2 class="section-title">品牌与型号</h2>
          </div>
          <span class="page-subtitle"
            >当前共收录 {{ products.length }} 件商品</span
          >
        </div>
        <div class="brand-rail-v3">
          <router-link
            v-for="brand in brands"
            :key="brand.id"
            :to="{ path: '/products', query: { brandId: brand.id } }"
            >{{ brand.name }}</router-link
          >
        </div>
      </section>

      <section class="service-line-v3" aria-label="平台服务说明">
        <div>
          <strong>资料可追溯</strong><span>商品参数与图片来源单独记录</span>
        </div>
        <div>
          <strong>选购有依据</strong><span>对比、推荐与适配规则清晰展示</span>
        </div>
        <div>
          <strong>流程可体验</strong><span>覆盖购物车、订单与售后演示</span>
        </div>
        <div>
          <strong>个人数据分隔</strong
          ><span>收藏、浏览记录与购物车按账号保存</span>
        </div>
      </section>
    </div>
  </div>
</template>
