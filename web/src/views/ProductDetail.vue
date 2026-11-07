<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import api from "../api";
import ProductImage from "../components/ProductImage.vue";
import { specs, specExplanation } from '../productSpecs';
import { advisorContext } from '../shoppingContext';
import { useAuthStore } from "../stores/auth";
import {
  addGuestCart,
  recordGuestHistory,
  toggleGuestFavorite,
} from "../stores/guestData";

const route = useRoute();
const auth = useAuthStore();
const product = ref<any>(null);
const reviews = ref<any[]>([]);
const accessories = ref<any[]>([]);
const sku = ref("标准版");
const qty = ref(1);
const message = ref("");
const loading = ref(false);
const error = ref("");
const imageUnavailable = ref(false);
const actionBusy = ref(false);
let loadSequence = 0;
const sourceLink = computed(() => {
  try {
    const url = new URL(product.value?.sourceUrl || "");
    return ["https:", "http:"].includes(url.protocol) ? url.href : "";
  } catch {
    return "";
  }
});

const skuOptions = computed(() =>
  String(product.value?.skuOptions || "标准版")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean),
);
const parameterRows = computed(() => {
  try {
    return specs(product.value).map(s=>[s.label,s.value]);
  } catch {
    return [];
  }
});
function formatPrice(value: unknown) {
  return Number(value || 0).toLocaleString("zh-CN", {
    maximumFractionDigits: 2,
  });
}

async function load() {
  const sequence = ++loadSequence;
  const productId = route.params.id;
  loading.value = true;
  error.value = "";
  message.value = "";
  product.value = null;
  reviews.value = [];
  accessories.value = [];
  imageUnavailable.value = false;
  try {
    const loaded = await api.get(`/products/${productId}`);
    if (sequence !== loadSequence) return;
    product.value = loaded;
    imageUnavailable.value = !product.value.image;
    if (!auth.logged) recordGuestHistory(Number(product.value.id));
    sku.value = skuOptions.value[0] || "标准版";
    qty.value = 1;
    const [accessoryResult, reviewResult] = await Promise.allSettled([
      api.get(`/products/${productId}/accessories`),
      api.get(`/products/${productId}/reviews`),
    ]);
    if (sequence !== loadSequence) return;
    accessories.value =
      accessoryResult.status === "fulfilled"
        ? (accessoryResult.value as any[])
        : [];
    reviews.value =
      reviewResult.status === "fulfilled" ? (reviewResult.value as any[]) : [];
  } catch (e: any) {
    if (sequence !== loadSequence) return;
    error.value = e?.message || "商品加载失败";
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

async function add() {
  if (actionBusy.value || !product.value || Number(product.value.stock) <= 0)
    return;
  const stock = Number(product.value.stock);
  qty.value = Math.max(1, Math.min(Math.trunc(Number(qty.value)) || 1, stock));
  if (!auth.logged) {
    addGuestCart(Number(product.value.id), qty.value, sku.value);
    message.value = "已加入游客购物车，登录后将自动合并";
    return;
  }
  actionBusy.value = true;
  try {
    await api.post("/cart", {
      productId: product.value.id,
      quantity: qty.value,
      selectedSku: sku.value,
    });
    message.value = "已加入购物车";
  } catch (e: any) {
    message.value = e?.message || "加入购物车失败";
  } finally {
    actionBusy.value = false;
  }
}

async function fav() {
  if (actionBusy.value || !product.value) return;
  if (!auth.logged) {
    const selected = toggleGuestFavorite(Number(product.value.id));
    message.value = selected
      ? "已加入游客收藏，登录后将自动合并"
      : "已取消游客收藏";
    return;
  }
  actionBusy.value = true;
  try {
    const result = await api.post(`/favorites/${product.value.id}`);
    message.value = result ? "已收藏" : "已取消收藏";
  } catch (e: any) {
    message.value = e?.message || "操作失败";
  } finally {
    actionBusy.value = false;
  }
}

onMounted(load);
watch(() => route.params.id, load);
</script>

<template>
  <div class="container product-detail-page">
    <div v-if="loading" class="empty">正在加载商品详情…</div>
    <div v-else-if="error" class="empty">
      <h2>商品加载失败</h2>
      <p>{{ error }}</p>
      <router-link class="btn" to="/products">返回商城</router-link>
    </div>
    <template v-else-if="product">
      <div class="breadcrumb product-breadcrumb">
        <router-link to="/">首页</router-link><span>›</span
        ><router-link
          :to="{ path: '/products', query: { categoryId: product.categoryId } }"
          >{{ product.categoryName || "机器人商城" }}</router-link
        ><span>›</span><span>{{ product.name }}</span>
      </div>
      <div class="detail product-detail">
        <div class="product-media">
          <div class="product-main-image">
            <ProductImage
              :product="product"
              eager
              @unavailable="imageUnavailable = $event"
            />
          </div>
          <p>
            {{
              imageUnavailable
                ? "图片暂时无法加载，已显示占位图。"
                : product.imageNote || "公开产品素材，具体型号图尚待核验。"
            }}
          </p>
          <p
            v-if="product.imageCheckedAt && !imageUnavailable"
            class="product-image-review"
          >
            型号图核验：{{ product.imageCheckedAt }} ·
            {{ product.sourceRegion }}
          </p>
        </div>
        <div class="product-buy-panel">
          <div class="detail-tags">
            <span>{{ product.brandName || "严选品牌" }}</span
            ><span>{{
              product.categoryName ||
              (product.type === "ACCESSORY" ? "机器人配件" : "机器人")
            }}</span>
          </div>
          <h1>{{ product.name }}</h1>
          <p class="detail-subtitle">
            {{ product.subtitle || "专业机器人商品" }}
          </p>
          <div class="detail-rating">
            <b>{{ Number(product.rating) > 0 ? `★ ${Number(product.rating).toFixed(1)} 评分` : '暂无评分' }}</b
            ><span>销量 {{ product.sales || 0 }}</span
            ><span>库存 {{ product.stock }}</span>
          </div>
          <div class="detail-price-box">
            <span>售价</span
            ><strong><i>¥</i>{{ formatPrice(product.price) }}</strong
            ><del v-if="Number(product.marketPrice) > Number(product.price)"
              >参考价 ¥{{ formatPrice(product.marketPrice) }}</del
            >
          </div>
          <p class="detail-description">{{ product.description }}</p>
          <div class="purchase-row">
            <label>选择配置</label>
            <div class="sku-list">
              <button
                v-for="option in skuOptions"
                :key="option"
                :class="{ active: sku === option }"
                @click="sku = option"
              >
                {{ option }}
              </button>
            </div>
          </div>
          <div class="purchase-row">
            <label>配送说明</label
            ><span>{{ product.shippingNote || "预计 48 小时内发出" }}</span>
          </div>
          <div class="purchase-row">
            <label>购买数量</label>
            <div class="quantity-stepper">
              <button type="button" aria-label="减少购买数量" :disabled="qty<=1" @click="qty = Math.max(1, qty - 1)">−</button
              ><input
                type="number"
                min="1"
                :max="product.stock"
                v-model.number="qty"
                aria-label="购买数量" @change="qty=Math.max(1,Math.min(product.stock,Math.trunc(Number(qty)||1)))"
              /><button type="button" aria-label="增加购买数量" :disabled="qty>=product.stock || product.stock<=0" @click="qty = Math.min(product.stock, (Number(qty)||1) + 1)">
                ＋
              </button>
            </div>
          </div>
          <div class="detail-actions">
            <button
              class="btn detail-cart-btn"
              :disabled="actionBusy || product.stock <= 0"
              @click="add"
            >
              {{
                actionBusy
                  ? "正在处理…"
                  : product.stock > 0
                    ? "加入购物车"
                    : "暂时缺货"
              }}
            </button>
            <button class="btn secondary" :disabled="actionBusy" @click="fav">
              ♡ 收藏商品
            </button>
          </div>
          <div class="detail-services">
            <span>✓ 订单服务</span><span>✓ 库存校验</span
            ><span
              >售后政策：{{
                product.warranty || "以商品说明为准"
              }}</span
            >
          </div>
          <div class="notice" v-if="message">{{ message }}</div>
        </div>
      </div>

      <div class="product-content-layout">
        <div class="product-content-main">
          <section class="card product-info-section">
            <h2>商品参数</h2>
            <router-link class="btn secondary" to="/advisor" @click="advisorContext([product], '这款机器人适合我的使用需求吗？请先问我预算和场景，并说明它的不足。')">问 AI：这款适合我吗？</router-link>
            <p class="muted">不同品类使用各自的参数结构，便于同类商品比较。</p>
            <table class="table parameter-table">
              <tbody>
                <tr v-for="row in parameterRows" :key="String(row[0])">
                  <th>{{ row[0] }}<details><summary>如何理解</summary><small>{{ specExplanation(String(row[0])) }}</small></details></th>
                  <td>{{ row[1] }}</td>
                </tr>
                <tr v-if="!parameterRows.length">
                  <td colspan="2">暂无详细参数</td>
                </tr>
              </tbody>
            </table>
          </section>
          <section class="card product-info-section">
            <h2>商品说明</h2>
            <p>{{ product.description || "暂无商品说明" }}</p>
            <div class="source-note">
              <b>资料与演示范围</b>
              <p>
                {{ product.imageNote || "型号图片及参数仍需逐项核验。" }}
                图片核验不代表所有参数、配置与适配关系均已核验；不同地区版本可能不同，请以对应型号官方资料为准。价格、参考价、库存、销量、评分、配送、保修及交易为毕设演示配置，不代表真实商家承诺。
              </p>
              <a
                v-if="sourceLink"
                :href="sourceLink"
                target="_blank"
                rel="noopener noreferrer"
                >查看公开资料来源 ↗</a
              ><span v-else class="muted">暂无有效公开资料链接</span>
            </div>
          </section>
          <section class="card product-info-section">
            <h2>
              用户评价 <small>({{ reviews.length }})</small>
            </h2>
            <p v-if="!reviews.length" class="muted">暂无已购用户评价</p>
            <div v-for="review in reviews" :key="review.id" class="review-item">
              <b>{{ review.score }} 星</b><span>{{ review.content }}</span>
            </div>
          </section>
        </div>
        <aside class="product-accessory-card card">
          <h2>平台配件关系</h2>
          <p class="muted">以平台维护关系展示，不构成官方兼容保证。</p>
          <p v-if="!accessories.length" class="muted">
            该商品暂无已维护的配件关系
          </p>
          <router-link
            v-for="item in accessories"
            :key="item.id"
            :to="`/product/${item.id}`"
            ><ProductImage :product="item" /><span
              ><b>{{ item.name }}</b
              ><em>¥{{ formatPrice(item.price) }}</em></span
            ></router-link
          >
        </aside>
      </div>
    </template>
  </div>
</template>
