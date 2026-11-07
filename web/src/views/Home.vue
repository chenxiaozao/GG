<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api";
import ProductGrid from "../components/ProductGrid.vue";
import UiIcon from "../components/UiIcon.vue";
import ProductImage from "../components/ProductImage.vue";

const router = useRouter();
const products = ref<any[]>([]);
const categories = ref<any[]>([]);
const brands = ref<any[]>([]);
const loading = ref(false);
const message = ref("");
const keyword = ref("");
const need = ref("");
const selectedScene = ref("地面清洁");
const sceneOptions = [
  { name: '地面清洁', label: '把家务交给它', note: '扫地、拖地与日常维护' },
  { name: '陪伴娱乐', label: '多一个小伙伴', note: '互动、看护与家庭陪伴' },
  { name: '教育编程', label: '从好奇到创造', note: '动手搭建，探索编程' },
  { name: '航拍无人机', label: '换个角度看世界', note: '从掌上跟拍到进阶航拍' },
];
const sceneProducts = computed(() => products.value.filter(p => p.categoryName === selectedScene.value).slice(0, 4));
const displayProducts = computed(() => sceneProducts.value.length ? sceneProducts.value : robots.value.slice(0, 4));
const coverProduct = computed(() => robots.value.find(p => p.name === '石头 Qrevo Curv') || robots.value.find(p => p.image && !p.image.endsWith('.svg')));
const scenarios = ['家里有宠物，想选省心的扫拖机器人', '想给孩子选一款编程机器人', '需要清洁窗户，想了解有哪些选择'];
function consult() {
  if (!need.value.trim()) return;
  sessionStorage.setItem('robotmall-advisor-entry', need.value.trim().slice(0, 1000));
  router.push('/advisor');
}
function focusNeed() {
  const input = document.getElementById('home-need');
  input?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' });
  input?.focus({ preventScroll: true });
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
      <div class="discovery-intro"><span class="discovery-label">小造智械 / 生活里的机器人</span><h1>把琐事交给它，<br />把时间留给生活。</h1><p>清洁家里，陪伴日常，或探索新的世界。<br />从你的生活方式出发，找到合适的机器人。</p><div class="editorial-hero-actions"><button class="btn" type="button" @click="focusNeed">让 AI 帮我选 <UiIcon name="arrow" :size="18" /></button><router-link to="/products">随便逛逛 →</router-link></div></div>
      <router-link v-if="coverProduct" class="editorial-cover" :to="`/product/${coverProduct.id}`"><ProductImage :product="coverProduct" eager /><div><span>日常清洁 / {{ coverProduct.brandName }}</span><b>{{ coverProduct.name }}</b><span>了解这款商品 ↗</span></div></router-link>
      <div v-else class="editorial-cover editorial-cover-empty">从生活出发，慢慢找到适合的一款。</div>
    </section>
      <form class="need-composer editorial-assistant" @submit.prevent="consult">
        <label for="home-need">你想让机器人帮你做什么？</label>
        <textarea id="home-need" v-model="need" maxlength="1000" placeholder="例如：家里有猫，90 平米，预算 4000 元，希望少清理毛发。" required />
        <div class="need-scenarios"><button v-for="scenario in scenarios" :key="scenario" type="button" @click="need = scenario">{{ scenario }}</button></div>
        <div class="need-submit"><small>仅在商城商品中助选 · 发送前可修改需求</small><button class="btn" :disabled="!need.trim()">开始 AI 助选 <UiIcon name="arrow" :size="18" /></button></div>
      </form>
    <div class="home-browse-bar"><form @submit.prevent="search"><UiIcon name="search" :size="18" /><input v-model="keyword" aria-label="搜索品牌或型号" placeholder="已有型号？搜索品牌或商品" /><button type="submit">搜索</button></form><router-link to="/compare">比较已有选择 →</router-link><router-link to="/profile">进入我的空间 →</router-link></div>

    <div class="container">
      <section class="scene-discovery" aria-label="按生活场景发现商品">
        <div class="scene-heading"><div><span class="section-kicker">从生活出发</span><h2>先找到你的使用场景</h2></div><router-link to="/products">所有分类 →</router-link></div>
        <div class="scene-tabs" role="group" aria-label="选择场景"><button v-for="scene in sceneOptions" :key="scene.name" :aria-pressed="selectedScene === scene.name" @click="selectedScene = scene.name"><strong>{{ scene.label }}</strong><span>{{ scene.note }}</span></button></div>
        <div v-if="displayProducts.length" class="scene-showcase">
          <router-link :to="`/product/${displayProducts[0].id}`" class="scene-feature"><div><span>{{ selectedScene }} / 了解一款</span><h3>{{ displayProducts[0].name }}</h3><p>{{ displayProducts[0].subtitle }}</p><b>¥{{ Number(displayProducts[0].price).toLocaleString('zh-CN') }} </b><em>查看商品 →</em></div><ProductImage :product="displayProducts[0]" /></router-link>
          <div class="scene-alternatives"><router-link v-for="product in displayProducts.slice(1)" :key="product.id" :to="`/product/${product.id}`"><ProductImage :product="product" /><div><small>{{ product.brandName }}</small><h3>{{ product.name }}</h3><span>¥{{ Number(product.price).toLocaleString('zh-CN') }}</span></div><span aria-hidden="true">↗</span></router-link></div>
        </div>
      </section>
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
            <h2 class="section-title">更多值得了解的选择</h2>
            <p>
              覆盖 {{ robots.length }} 款机器人主机与
              {{ accessories.length }} 款配件，参数来自公开商品资料。
            </p>
          </div>
          <router-link class="text-link" to="/products">查看全部 →</router-link>
        </div>
        <div class="notice" v-if="message">{{ message }}</div>
        <ProductGrid :loading="loading" :products="robots.filter(p => p.categoryName !== selectedScene).slice(0, 6)" />
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
          <strong>流程可体验</strong><span>购物车、订单与售后服务</span>
        </div>
        <div>
          <strong>个人数据分隔</strong
          ><span>收藏、浏览记录与购物车按账号保存</span>
        </div>
      </section>
    </div>
  </div>
</template>
