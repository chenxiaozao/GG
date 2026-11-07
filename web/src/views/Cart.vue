<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import { computed, onMounted, ref } from "vue";
import api from "../api";
import EmptyState from "../components/EmptyState.vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import {
  clearGuestCart,
  getGuestCart,
  removeGuestCart,
  updateGuestCart,
} from "../stores/guestData";

const auth = useAuthStore();
const router = useRouter();
const items = ref<any[]>([]);
const message = ref("");
const loading = ref(false);
const loadError = ref("");
const receiverInfo = ref("");
const addresses = ref<any[]>([]);
const selectedAddressId = ref("");
const addressError = ref("");
const addressLoading = ref(false);
const addressesInitialized = ref(false);
const selectedAddress = computed(() =>
  addresses.value.find((a) => String(a.id) === selectedAddressId.value),
);
const submitting = ref(false);
const mutating = ref(false);
const unavailable = computed(() =>
  items.value.some(
    (item) =>
      !item.product?.enabled ||
      Number(item.product?.stock || 0) < Number(item.quantity),
  ),
);
const total = computed(() =>
  items.value.reduce(
    (sum, item) =>
      sum + Number(item.product?.price || 0) * Number(item.quantity || 0),
    0,
  ),
);

async function loadAddresses() {
  if (!auth.logged) return;
  addressLoading.value = true;
  addressError.value = "";
  try {
    addresses.value = (await api.get("/addresses")) as any[];
    if (
      !addressesInitialized.value &&
      !selectedAddressId.value &&
      !receiverInfo.value
    )
      selectedAddressId.value = String(
        (addresses.value.find((a) => a.defaultAddress) || addresses.value[0])
          ?.id || "",
      );
    if (selectedAddressId.value && !selectedAddress.value)
      selectedAddressId.value = "";
    addressesInitialized.value = true;
  } catch (e: any) {
    addressError.value = e?.message || "地址加载失败，可手动填写收货信息";
  } finally {
    addressLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    if (auth.logged) {
      items.value = (await api.get("/cart")) as any[];
    } else {
      const guestItems = getGuestCart();
      items.value = (
        await Promise.all(
          guestItems.map(async (item, index) => {
            try {
              const product = await api.get(`/products/${item.productId}`);
              return { ...item, id: `guest-${index}`, product, guest: true };
            } catch {
              return null;
            }
          }),
        )
      ).filter(Boolean) as any[];
    }
  } catch (e: any) {
    loadError.value = e?.message || "购物车加载失败";
  } finally {
    loading.value = false;
  }
}

async function update(item: any) {
  if (mutating.value || submitting.value || loading.value) return;
  const stock = Number(item.product?.stock || 0);
  if (stock < 1 || !item.product?.enabled) return;
  item.quantity = Math.max(
    1,
    Math.min(Math.floor(Number(item.quantity) || 1), stock),
  );
  mutating.value = true;
  try {
    if (auth.logged)
      await api.put(`/cart/${item.id}`, { quantity: item.quantity });
    else updateGuestCart(item.productId, item.selectedSku, item.quantity);
    message.value = "数量已更新";
    await load();
  } catch (e: any) {
    message.value = e?.message || "数量更新失败";
    await load();
  } finally {
    mutating.value = false;
  }
}
function stepQuantity(item: any, delta: number) {
  if (
    loading.value ||
    mutating.value ||
    submitting.value ||
    !item.product?.enabled ||
    !item.product?.stock
  )
    return;
  item.quantity = Math.max(
    1,
    Math.min(Number(item.product.stock), Number(item.quantity) + delta),
  );
  update(item);
}

async function remove(item: any) {
  if (mutating.value || submitting.value || loading.value) return;
  mutating.value = true;
  try {
    if (auth.logged) await api.delete(`/cart/${item.id}`);
    else removeGuestCart(item.productId, item.selectedSku);
    message.value = "商品已移出购物车";
    await load();
  } catch (e: any) {
    message.value = e?.message || "删除失败";
  } finally {
    mutating.value = false;
  }
}

async function clearAll() {
  if (mutating.value || submitting.value || loading.value) return;
  if (
    !window.confirm(
      auth.logged
        ? "确定清空当前账号的购物车吗？"
        : "确定清空本机游客购物车吗？",
    )
  )
    return;
  mutating.value = true;
  try {
    if (auth.logged) await api.delete("/cart");
    else clearGuestCart();
    items.value = [];
    message.value = auth.logged
      ? "已清空当前账号的购物车"
      : "已清空本机游客购物车";
  } catch (e: any) {
    message.value = e?.message || "清空失败";
  } finally {
    mutating.value = false;
  }
}

async function createOrder() {
  if (
    submitting.value ||
    loading.value ||
    mutating.value ||
    addressLoading.value
  )
    return;
  if (!auth.logged) {
    await router.push({ path: "/login", query: { redirect: "/cart" } });
    return;
  }
  if (!selectedAddress.value && !receiverInfo.value.trim()) {
    message.value = "请填写收货信息";
    return;
  }
  if (!items.value.length || unavailable.value) {
    message.value = "请先移除下架商品或调整超出库存的数量";
    return;
  }
  submitting.value = true;
  try {
    const payload = selectedAddress.value
      ? { addressId: Number(selectedAddressId.value) }
      : { receiverInfo: receiverInfo.value.trim() };
    const order: any = await api.post("/orders", payload);
    message.value = `订单 ${order.orderNo} 创建成功`;
    await load();
  } catch (e: any) {
    message.value = e?.message || "创建订单失败";
  } finally {
    submitting.value = false;
  }
}

function imageFallback(event: Event) {
  const image = event.target as HTMLImageElement;
  if (!image.src.endsWith('/products/placeholder.svg')) image.src = resourceUrl();
}

onMounted(() => {
  load();
  loadAddresses();
});
</script>

<template>
  <div class="container cart-page">
    <div class="record-heading">
      <div>
        <h1>购物车</h1>
        <p>
          {{
            auth.logged
              ? "数据仅属于当前登录账号"
              : "游客数据保存在本机，登录后自动合并"
          }}
        </p>
      </div>
      <button
        v-if="items.length"
        class="btn secondary"
        :disabled="loading || mutating || submitting"
        @click="clearAll"
      >
        清空购物车
      </button>
    </div>
    <div v-if="!auth.logged && items.length" class="guest-data-tip">
      <span>游客购物车</span>
      <p>登录后会与账号中的商品自动合并，不会覆盖原有数据。</p>
      <router-link :to="{ path: '/login', query: { redirect: '/cart' } }"
        >登录并同步 →</router-link
      >
    </div>
    <div class="notice" role="status" v-if="message">{{ message }}</div>
    <div v-if="loadError" class="collection-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="btn secondary" :disabled="loading" @click="load">
        重新加载
      </button>
    </div>
    <div v-if="loading" class="empty">正在加载购物车…</div>
    <div v-if="items.length" class="cart-layout">
      <section class="cart-items" aria-label="购物车商品">
        <div class="cart-row" v-for="item in items" :key="item.id">
          <img
            :src="resourceUrl(item.product?.image)"
            :alt="item.product?.name"
            width="120"
            @error="imageFallback"
          />
          <div class="cart-copy">
            <h3>
              <router-link :to="`/product/${item.productId}`">{{
                item.product?.name || "商品已不存在"
              }}</router-link>
            </h3>
            <p>{{ item.selectedSku || "标准版" }}</p>
            <b>¥{{ item.product?.price || 0 }}</b>
            <p v-if="!item.product?.enabled" class="danger-text">
              商品已下架，请删除后再结算
            </p>
            <p v-else-if="!item.product?.stock" class="danger-text">
              暂时缺货，请移除后再结算
            </p>
          </div>
          <div class="cart-controls">
            <div class="quantity-stepper">
              <button
                type="button"
                :aria-label="`减少 ${item.product?.name || '商品'}的数量`"
                :disabled="
                  loading ||
                  mutating ||
                  submitting ||
                  !item.product?.enabled ||
                  !item.product?.stock ||
                  item.quantity <= 1
                "
                @click="stepQuantity(item, -1)"
              >
                −
              </button>
              <input
                class="input qty-input"
                :aria-label="`${item.product?.name || '商品'}的数量`"
                :disabled="
                  loading ||
                  mutating ||
                  submitting ||
                  !item.product?.enabled ||
                  !item.product?.stock
                "
                type="number"
                min="1"
                :max="item.product?.stock || 1"
                v-model.number="item.quantity"
                @change="update(item)"
              />
              <button
                type="button"
                :aria-label="`增加 ${item.product?.name || '商品'}的数量`"
                :disabled="
                  loading ||
                  mutating ||
                  submitting ||
                  !item.product?.enabled ||
                  !item.product?.stock ||
                  item.quantity >= item.product?.stock
                "
                @click="stepQuantity(item, 1)"
              >
                +
              </button>
            </div>
            <button
              class="link"
              :disabled="loading || mutating || submitting"
              @click="remove(item)"
            >
              移除
            </button>
          </div>
        </div>
      </section>
      <aside class="cart-summary">
        <h2>订单结算</h2>
        <template v-if="auth.logged">
          <label for="cart-address">收货地址</label>
          <select
            id="cart-address"
            class="input"
            v-model="selectedAddressId"
            :disabled="submitting || addressLoading"
          >
            <option value="">手动填写地址</option>
            <option v-for="a in addresses" :key="a.id" :value="String(a.id)">
              {{ a.defaultAddress ? "默认 · " : "" }}{{ a.receiver }} ·
              {{ a.city }}{{ a.district }}
            </option>
          </select>
          <div v-if="selectedAddress" class="checkout-address">
            <b>{{ selectedAddress.receiver }} · {{ selectedAddress.phone }}</b>
            <p>
              {{ selectedAddress.province }}{{ selectedAddress.city
              }}{{ selectedAddress.district }}{{ selectedAddress.detail }}
            </p>
          </div>
          <template v-else
            ><label for="cart-receiver" class="manual-address-label"
              >手动收货信息</label
            ><textarea
              id="cart-receiver"
              class="input"
              rows="3"
              maxlength="500"
              v-model="receiverInfo"
              placeholder="请填写收货人、联系电话及详细地址"
              :disabled="submitting"
            ></textarea>
          </template>
          <p v-if="addressError" class="inline-error" role="alert">
            {{ addressError }}
            <button
              class="text-action"
              :disabled="addressLoading"
              @click="loadAddresses"
            >
              重试
            </button>
          </p>
          <div class="address-management">
            <router-link to="/profile#addresses">管理地址 →</router-link
            ><button
              class="text-action"
              :disabled="addressLoading || submitting"
              @click="loadAddresses"
            >
              {{ addressLoading ? "更新中…" : "刷新地址" }}
            </button>
          </div>
        </template>
        <div class="cart-total">
          <span>共 {{ items.length }} 项商品</span
          ><strong>¥{{ total.toFixed(2) }}</strong>
        </div>
        <button
          class="btn"
          :disabled="
            loading ||
            mutating ||
            addressLoading ||
            submitting ||
            (auth.logged && unavailable)
          "
          @click="createOrder"
        >
          {{
            submitting ? "正在提交…" : auth.logged ? "提交订单" : "登录后结算"
          }}
        </button>
        <p v-if="unavailable">存在下架或库存不足的商品，请先处理后再结算。</p>
        <p v-else>
          {{
            auth.logged
              ? "提交后可在订单中心进行模拟支付。本商城不进行真实扣款。"
              : "游客商品保存在本机，登录后自动合并到当前账号。"
          }}
        </p>
        <router-link v-if="auth.logged" to="/orders"
          >查看我的订单 →</router-link
        >
      </aside>
    </div>
    <EmptyState
      v-if="!loading && !loadError && !items.length"
      title="购物车还是空的"
      description="把感兴趣的商品加入购物车，再统一确认规格和数量。"
      action-label="继续选购"
      to="/products"
    />
  </div>
</template>
