<script setup lang="ts">
import { resourceUrl } from "../resourceUrl";
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import UiIcon from "../components/UiIcon.vue";
import api from "../api";
import { chinaRegions, regionDataVersion } from "../data/chinaRegionsFull";
import { useAuthStore } from "../stores/auth";
import {
  type ThemeName,
  type InterfaceLayout,
  useThemeStore,
} from "../stores/theme";

const auth = useAuthStore();
const theme = useThemeStore();
const route = useRoute();
const router = useRouter();
const sections = [
  { id: "overview", name: "账户概览", icon: "user" },
  { id: "account", name: "个人资料", icon: "settings" },
  { id: "addresses", name: "收货地址", icon: "location" },
  { id: "security", name: "账号安全", icon: "lock" },
  { id: "appearance", name: "界面与主题", icon: "palette" },
];
const activeSection = computed(() => {
  const id = route.hash.slice(1);
  return sections.some((item) => item.id === id) ? id : "overview";
});
function switchSection(id: string) {
  message.value = "";
  router.replace({ path: "/profile", hash: id === "overview" ? "" : `#${id}` });
}
watch(activeSection, async () => {
  await nextTick();
  document
    .querySelector<HTMLElement>(`#${activeSection.value} h2`)
    ?.focus({ preventScroll: true });
});
const summary = ref<any>(null);
const loading = ref(true);
const message = ref("");
const accent = ref(theme.accentColor);
const background = ref(theme.backgroundColor);
const addresses = ref<any[]>([]);
const editingAddressId = ref<number | null>(null);
const avatarUploading = ref(false);
const avatarFailed = ref(false);
const previewAvatarFailed = ref(false);
const avatarEditorOpen = ref(false);
const avatarSource = ref("");
const avatarImage = ref<HTMLImageElement | null>(null);
const avatarZoom = ref(1);
const avatarOffsetX = ref(0);
const avatarOffsetY = ref(0);
let dragStart: { x: number; y: number; ox: number; oy: number } | null = null;
const profileForm = reactive({
  nickname: "",
  phone: "",
  email: "",
  avatar: "",
});
watch(
  () => auth.user?.avatar,
  () => {
    avatarFailed.value = false;
  },
);
watch(
  () => profileForm.avatar,
  () => {
    previewAvatarFailed.value = false;
  },
);
const passwordForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});
const addressForm = reactive({
  receiver: "",
  phone: "",
  province: "",
  city: "",
  district: "",
  detail: "",
  defaultAddress: false,
});
const manualRegion = ref(false);
const selectedProvince = ref("");
const selectedCity = ref("");
const selectedDistrict = ref("");
const provinceOptions = Object.keys(chinaRegions);
const cityOptions = computed(() =>
  Object.keys(chinaRegions[selectedProvince.value] || {}),
);
const districtOptions = computed(
  () => chinaRegions[selectedProvince.value]?.[selectedCity.value] || [],
);
const themes: Array<{
  id: ThemeName;
  name: string;
  description: string;
  colors: string[];
}> = [
  {
    id: "classic",
    name: "智械经典",
    description: "清晰克制的商城风格",
    colors: ["#d9363e", "#26364a", "#f5f6f8"],
  },
  {
    id: "midnight",
    name: "深海科技",
    description: "低亮度深色科技风格",
    colors: ["#5aa7ff", "#1d2939", "#111827"],
  },
  {
    id: "warm",
    name: "暖居生活",
    description: "适合家用机器人的温暖配色",
    colors: ["#c65d32", "#6f5846", "#f7f1e7"],
  },
];
const layouts: Array<{
  id: InterfaceLayout;
  name: string;
  description: string;
}> = [
  {
    id: "standard",
    name: "经典商城",
    description: "双栏首页、侧边账户导航与常规商品卡片",
  },
  {
    id: "editorial",
    name: "生活展厅",
    description: "居中大留白首页、横向账户导航与舒展的大卡片",
  },
  {
    id: "compact",
    name: "高效选购",
    description: "精简首页、紧凑账户区与横向商品列表",
  },
];

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 11) return "早上好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

async function load() {
  loading.value = true;
  try {
    const [summaryResult, addressResult] = await Promise.all([
      api.get("/profile/summary"),
      api.get("/addresses"),
    ]);
    summary.value = summaryResult;
    addresses.value = addressResult as any[];
    Object.assign(profileForm, {
      nickname: auth.user?.nickname || "",
      phone: auth.user?.phone || "",
      email: auth.user?.email || "",
      avatar: auth.user?.avatar || "",
    });
  } catch (error: any) {
    message.value = error?.message || "个人信息加载失败";
  } finally {
    loading.value = false;
  }
}

async function saveProfile() {
  try {
    const user: any = await api.put("/profile", profileForm);
    auth.user = user;
    localStorage.setItem("user", JSON.stringify(user));
    message.value = "个人资料已保存";
  } catch (error: any) {
    message.value = error?.message || "个人资料保存失败";
  }
}

async function cropAvatar(): Promise<Blob> {
  const image = avatarImage.value;
  if (!image) throw new Error("请先选择头像图片");
  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 512;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("浏览器不支持图片处理");
  const base = Math.max(512 / image.naturalWidth, 512 / image.naturalHeight);
  const scale = base * avatarZoom.value;
  const width = image.naturalWidth * scale,
    height = image.naturalHeight * scale;
  const maxX = Math.max(0, (width - 512) / 2),
    maxY = Math.max(0, (height - 512) / 2);
  const x =
    (512 - width) / 2 +
    Math.max(-maxX, Math.min(maxX, avatarOffsetX.value * 2));
  const y =
    (512 - height) / 2 +
    Math.max(-maxY, Math.min(maxY, avatarOffsetY.value * 2));
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, 512, 512);
  context.drawImage(image, x, y, width, height);
  return await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("头像处理失败"))),
      "image/jpeg",
      0.86,
    ),
  );
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    message.value = "请选择 JPG、PNG 或 WebP 图片";
    input.value = "";
    return;
  }
  try {
    if (avatarSource.value) URL.revokeObjectURL(avatarSource.value);
    avatarSource.value = URL.createObjectURL(file);
    avatarImage.value = await new Promise<HTMLImageElement>(
      (resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = reject;
        image.src = avatarSource.value;
      },
    );
    avatarZoom.value = 1;
    avatarOffsetX.value = 0;
    avatarOffsetY.value = 0;
    avatarEditorOpen.value = true;
  } catch (error: any) {
    message.value = error?.message || "图片读取失败";
  } finally {
    input.value = "";
  }
}
function startAvatarDrag(event: PointerEvent) {
  dragStart = {
    x: event.clientX,
    y: event.clientY,
    ox: avatarOffsetX.value,
    oy: avatarOffsetY.value,
  };
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
}
function moveAvatarDrag(event: PointerEvent) {
  if (!dragStart) return;
  avatarOffsetX.value = dragStart.ox + event.clientX - dragStart.x;
  avatarOffsetY.value = dragStart.oy + event.clientY - dragStart.y;
}
function endAvatarDrag() {
  dragStart = null;
}
function closeAvatarEditor() {
  avatarEditorOpen.value = false;
  if (avatarSource.value) URL.revokeObjectURL(avatarSource.value);
  avatarSource.value = "";
  avatarImage.value = null;
}
async function confirmAvatar() {
  avatarUploading.value = true;
  try {
    const blob = await cropAvatar();
    const form = new FormData();
    form.append("file", blob, "avatar.jpg");
    const user: any = await api.post("/profile/avatar", form);
    auth.user = user;
    profileForm.avatar = user.avatar || "";
    localStorage.setItem("user", JSON.stringify(user));
    message.value = "头像已按当前裁剪范围保存";
    closeAvatarEditor();
  } catch (error: any) {
    message.value = error?.message || "头像上传失败";
  } finally {
    avatarUploading.value = false;
  }
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.value = "两次输入的新密码不一致";
    return;
  }
  try {
    await api.put("/profile/password", {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    });
    Object.assign(passwordForm, {
      oldPassword: "",
      newPassword: "",
      confirmPassword: "",
    });
    message.value = "密码修改成功，请妥善保管新密码";
  } catch (error: any) {
    message.value = error?.message || "密码修改失败";
  }
}

function resetAddressForm() {
  editingAddressId.value = null;
  manualRegion.value = false;
  selectedProvince.value = "";
  selectedCity.value = "";
  selectedDistrict.value = "";
  Object.assign(addressForm, {
    receiver: "",
    phone: profileForm.phone || "",
    province: "",
    city: "",
    district: "",
    detail: "",
    defaultAddress: addresses.value.length === 0,
  });
}

function editAddress(address: any) {
  editingAddressId.value = address.id;
  Object.assign(addressForm, address);
  const knownDistricts = chinaRegions[address.province]?.[address.city] || [];
  manualRegion.value = !knownDistricts.includes(address.district);
  selectedProvince.value = manualRegion.value ? "" : address.province;
  selectedCity.value = manualRegion.value ? "" : address.city;
  selectedDistrict.value = manualRegion.value ? "" : address.district;
  document
    .querySelector("#address-form")
    ?.scrollIntoView({ behavior: "smooth", block: "center" });
}

function chooseProvince() {
  selectedCity.value = "";
  selectedDistrict.value = "";
  Object.assign(addressForm, {
    province: selectedProvince.value,
    city: "",
    district: "",
  });
}

function chooseCity() {
  selectedDistrict.value = "";
  Object.assign(addressForm, { city: selectedCity.value, district: "" });
}

function chooseDistrict() {
  addressForm.district = selectedDistrict.value;
}

function setRegionMode(manual: boolean) {
  manualRegion.value = manual;
  selectedProvince.value = "";
  selectedCity.value = "";
  selectedDistrict.value = "";
  Object.assign(addressForm, { province: "", city: "", district: "" });
}

async function saveAddress() {
  try {
    if (editingAddressId.value)
      await api.put(`/addresses/${editingAddressId.value}`, addressForm);
    else await api.post("/addresses", addressForm);
    message.value = editingAddressId.value
      ? "收货地址已更新"
      : "收货地址已添加";
    addresses.value = (await api.get("/addresses")) as any[];
    resetAddressForm();
  } catch (error: any) {
    message.value = error?.message || "地址保存失败";
  }
}

async function removeAddress(address: any) {
  if (!window.confirm(`确定删除 ${address.receiver} 的收货地址吗？`)) return;
  try {
    await api.delete(`/addresses/${address.id}`);
    addresses.value = (await api.get("/addresses")) as any[];
    if (editingAddressId.value === address.id) resetAddressForm();
    message.value = "收货地址已删除";
  } catch (error: any) {
    message.value = error?.message || "地址删除失败";
  }
}

async function chooseTheme(id: ThemeName) {
  try {
    await theme.select(id);
    accent.value = theme.accentColor;
    background.value = theme.backgroundColor;
    message.value = "界面主题已保存到当前账号";
  } catch (error: any) {
    message.value = error?.message || "主题保存失败";
  }
}
async function chooseLayout(id: InterfaceLayout) {
  try {
    await theme.selectLayout(id);
    message.value = "界面布局已保存，可前往首页和商品列表查看";
  } catch (error: any) {
    message.value = error?.message || "布局保存失败";
  }
}

async function saveCustomTheme() {
  try {
    await theme.customize(accent.value, background.value);
    message.value = "自定义配色已保存";
  } catch (error: any) {
    message.value = error?.message || "配色保存失败";
  }
}

function formatTime(value: string) {
  return value
    ? new Date(value).toLocaleString("zh-CN", {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";
}

onMounted(() => {
  resetAddressForm();
  load();
});
</script>

<template>
  <div class="profile-page">
    <div class="container profile-container">
      <section class="profile-hero">
        <div class="profile-avatar">
          <img
            v-if="auth.user?.avatar && !avatarFailed"
            :src="resourceUrl(auth.user.avatar)"
            alt="用户头像"
            @error="avatarFailed = true"
          /><span v-else>{{
            (auth.user?.nickname || auth.user?.username || "U").slice(0, 1)
          }}</span>
        </div>
        <div>
          <span class="profile-eyebrow">我的空间</span>
          <h1>
            {{ greeting }}，{{ auth.user?.nickname || auth.user?.username }}
          </h1>
          <p>喜欢的商品、还没做完的选择，都留在这里。</p>
        </div>
        <button
          class="profile-security"
          type="button"
          @click="switchSection('appearance')"
        >
          <UiIcon name="palette" :size="18" />定制我的界面
        </button>
      </section>

      <div class="notice" v-if="message">{{ message }}</div>
      <section v-if="activeSection === 'overview'" class="space-continuation" aria-label="继续我的选购">
        <div><span>从上次的选择继续</span><h2>慢慢挑，找到适合自己的。</h2><p>先看看收藏，或把候选商品放在一起比较。还没方向时，也可以让选购助手帮忙。</p></div>
        <nav><router-link to="/favorites"><UiIcon name="heart" /><b>我的心愿清单</b><small>重看收藏的商品 →</small></router-link><router-link to="/compare"><UiIcon name="compare" /><b>比较候选商品</b><small>看清参数差异 →</small></router-link><router-link to="/advisor"><UiIcon name="spark" /><b>和选购助手聊聊</b><small>从使用需求开始 →</small></router-link></nav>
      </section>
      <div class="profile-layout">
        <div class="profile-main">
          <section
            v-show="activeSection === 'overview'"
            id="overview"
            class="profile-section"
          >
            <div class="profile-section-head">
              <div>
                <span>选购与订单</span>
                <h2 tabindex="-1">我的日常</h2>
              </div>
              <small>不同账号的数据相互独立</small>
            </div>
            <div v-if="loading" class="empty">正在加载个人数据…</div>
            <template v-else-if="summary">
              <div class="profile-stats">
                <router-link to="/orders"
                  ><strong>{{ summary.orderCount }}</strong
                  ><span>全部订单</span
                  ><small
                    >{{ summary.pendingPaymentCount }} 个待支付</small
                  ></router-link
                >
                <router-link to="/cart"
                  ><strong>{{ summary.cartCount }}</strong
                  ><span>购物车商品</span
                  ><small>继续完成选购</small></router-link
                >
                <router-link to="/favorites"
                  ><strong>{{ summary.favoriteCount }}</strong
                  ><span>收藏商品</span
                  ><small>只属于当前账号</small></router-link
                >
                <router-link to="/history"
                  ><strong>{{ summary.historyCount }}</strong
                  ><span>浏览记录</span><small>可随时清理</small></router-link
                >
              </div>
              <div class="profile-panels">
                <div class="card profile-panel">
                  <div class="profile-panel-head">
                    <h3>最近浏览</h3>
                    <router-link to="/history">全部记录 →</router-link>
                  </div>
                  <div
                    v-if="!summary.recentProducts?.length"
                    class="profile-inline-empty"
                  >
                    还没有浏览记录
                  </div>
                  <router-link
                    v-for="entry in summary.recentProducts"
                    :key="entry.product.id"
                    :to="`/product/${entry.product.id}`"
                    class="profile-recent-product"
                  >
                    <img
                      :src="resourceUrl(entry.product.image)"
                      :alt="entry.product.name"
                    />
                    <span
                      ><b>{{ entry.product.name }}</b
                      ><small>{{ formatTime(entry.viewTime) }}</small></span
                    >
                    <em
                      >¥{{
                        Number(entry.product.price).toLocaleString("zh-CN")
                      }}</em
                    >
                  </router-link>
                </div>
                <div class="card profile-panel">
                  <div class="profile-panel-head">
                    <h3>订单提醒</h3>
                    <router-link to="/orders">订单中心 →</router-link>
                  </div>
                  <div class="profile-order-reminder">
                    <b>{{ summary.pendingPaymentCount }}</b
                    ><span>待支付订单</span>
                  </div>
                  <div class="profile-order-reminder">
                    <b>{{ summary.pendingReceiptCount }}</b
                    ><span>待收货订单</span>
                  </div>
                </div>
              </div>
            </template>
          </section>

          <section
            v-show="activeSection === 'account'"
            id="account"
            class="profile-section profile-form-section"
          >
            <div class="profile-section-head">
              <div>
                <span>ACCOUNT</span>
                <h2 tabindex="-1">个人资料</h2>
              </div>
              <small>完善联系方式，方便订单服务</small>
            </div>
            <form class="profile-form-grid" @submit.prevent="saveProfile">
              <div class="avatar-editor profile-form-wide">
                <div class="avatar-editor-preview">
                  <img
                    v-if="profileForm.avatar && !previewAvatarFailed"
                    :src="resourceUrl(profileForm.avatar)"
                    alt="头像预览"
                    @error="previewAvatarFailed = true"
                  /><span v-else>{{
                    (profileForm.nickname || auth.user?.username || "U").slice(
                      0,
                      1,
                    )
                  }}</span>
                </div>
                <div>
                  <b>个人头像</b>
                  <p>
                    选择图片后可拖动、缩放并预览裁剪范围，支持 JPG、PNG 和
                    WebP。
                  </p>
                  <label class="btn secondary avatar-upload-button"
                    ><input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      @change="uploadAvatar"
                    /><span>{{
                      avatarUploading ? "正在处理…" : "选择本地图片"
                    }}</span></label
                  >
                </div>
              </div>
              <label
                ><span>用户名</span
                ><input
                  class="input"
                  :value="auth.user?.username"
                  disabled
                /><small>登录用户名暂不支持修改</small></label
              >
              <label
                ><span>昵称</span
                ><input
                  class="input"
                  v-model.trim="profileForm.nickname"
                  maxlength="30"
                  required
              /></label>
              <label
                ><span>手机号码</span
                ><input
                  class="input"
                  v-model.trim="profileForm.phone"
                  inputmode="numeric"
                  maxlength="11"
                  placeholder="用于订单联系"
              /></label>
              <label
                ><span>电子邮箱</span
                ><input
                  class="input"
                  v-model.trim="profileForm.email"
                  type="email"
                  maxlength="80"
                  placeholder="用于接收服务通知"
              /></label>
              <label class="profile-form-wide"
                ><span>头像图片地址（可选）</span
                ><input
                  class="input"
                  v-model.trim="profileForm.avatar"
                  maxlength="500"
                  placeholder="也可以填写公开图片地址，留空使用昵称首字"
              /></label>
              <div class="profile-form-actions">
                <button class="btn" type="submit">保存个人资料</button>
              </div>
            </form>
          </section>

          <section
            v-show="activeSection === 'addresses'"
            id="addresses"
            class="profile-section profile-form-section"
          >
            <div class="profile-section-head">
              <div>
                <span>DELIVERY</span>
                <h2 tabindex="-1">收货地址</h2>
              </div>
              <small
                >已保存 {{ addresses.length }} 个地址 ·
                {{ regionDataVersion }}</small
              >
            </div>
            <div class="address-list" v-if="addresses.length">
              <article
                v-for="item in addresses"
                :key="item.id"
                class="address-card"
                :class="{ selected: item.defaultAddress }"
              >
                <div>
                  <b>{{ item.receiver }}</b
                  ><span>{{ item.phone }}</span
                  ><em v-if="item.defaultAddress">默认地址</em>
                </div>
                <p>
                  {{ item.province }}{{ item.city }}{{ item.district }}
                  {{ item.detail }}
                </p>
                <footer>
                  <button type="button" @click="editAddress(item)">编辑</button
                  ><button type="button" @click="removeAddress(item)">
                    删除
                  </button>
                </footer>
              </article>
            </div>
            <div v-else class="profile-inline-empty">
              还没有保存收货地址，请在下方添加
            </div>
            <form
              id="address-form"
              class="address-form"
              @submit.prevent="saveAddress"
            >
              <h3>{{ editingAddressId ? "编辑收货地址" : "添加收货地址" }}</h3>
              <div class="address-region-mode">
                <button
                  type="button"
                  :class="{ active: !manualRegion }"
                  @click="setRegionMode(false)"
                >
                  省市区联动选择</button
                ><button
                  type="button"
                  :class="{ active: manualRegion }"
                  @click="setRegionMode(true)"
                >
                  手动填写其他地区
                </button>
              </div>
              <div class="profile-form-grid">
                <label
                  ><span>收货人</span
                  ><input
                    class="input"
                    v-model.trim="addressForm.receiver"
                    maxlength="30"
                    required
                /></label>
                <label
                  ><span>手机号码</span
                  ><input
                    class="input"
                    v-model.trim="addressForm.phone"
                    maxlength="11"
                    required
                /></label>
                <template v-if="!manualRegion">
                  <label
                    ><span>省份</span
                    ><select
                      class="input"
                      v-model="selectedProvince"
                      @change="chooseProvince"
                      required
                    >
                      <option value="" disabled>请选择省份</option>
                      <option
                        v-for="item in provinceOptions"
                        :key="item"
                        :value="item"
                      >
                        {{ item }}
                      </option>
                    </select></label
                  >
                  <label
                    ><span>城市</span
                    ><select
                      class="input"
                      v-model="selectedCity"
                      @change="chooseCity"
                      :disabled="!selectedProvince"
                      required
                    >
                      <option value="" disabled>请选择城市</option>
                      <option
                        v-for="item in cityOptions"
                        :key="item"
                        :value="item"
                      >
                        {{ item }}
                      </option>
                    </select></label
                  >
                  <label
                    ><span>区/县</span
                    ><select
                      class="input"
                      v-model="selectedDistrict"
                      @change="chooseDistrict"
                      :disabled="!selectedCity"
                      required
                    >
                      <option value="" disabled>请选择区/县</option>
                      <option
                        v-for="item in districtOptions"
                        :key="item"
                        :value="item"
                      >
                        {{ item }}
                      </option>
                    </select></label
                  >
                </template>
                <template v-else>
                  <label
                    ><span>省份/地区</span
                    ><input
                      class="input"
                      v-model.trim="addressForm.province"
                      placeholder="例如：江苏省"
                      required
                  /></label>
                  <label
                    ><span>城市</span
                    ><input
                      class="input"
                      v-model.trim="addressForm.city"
                      placeholder="例如：南京市"
                      required
                  /></label>
                  <label
                    ><span>区/县</span
                    ><input
                      class="input"
                      v-model.trim="addressForm.district"
                      placeholder="例如：鼓楼区"
                      required
                  /></label>
                </template>
                <label class="profile-form-wide"
                  ><span>详细地址</span
                  ><input
                    class="input"
                    v-model.trim="addressForm.detail"
                    maxlength="100"
                    placeholder="街道、小区、楼栋和门牌号"
                    required
                /></label>
              </div>
              <div class="address-form-actions">
                <label
                  ><input
                    type="checkbox"
                    v-model="addressForm.defaultAddress"
                  />
                  设为默认地址</label
                >
                <div>
                  <button
                    v-if="editingAddressId"
                    class="btn secondary"
                    type="button"
                    @click="resetAddressForm"
                  >
                    取消编辑</button
                  ><button class="btn" type="submit">
                    {{ editingAddressId ? "保存修改" : "添加地址" }}
                  </button>
                </div>
              </div>
            </form>
          </section>

          <section
            v-show="activeSection === 'security'"
            id="security"
            class="profile-section profile-form-section"
          >
            <div class="profile-section-head">
              <div>
                <span>SECURITY</span>
                <h2 tabindex="-1">账号安全</h2>
              </div>
              <small>密码修改后当前设备仍保持登录</small>
            </div>
            <form class="password-form" @submit.prevent="changePassword">
              <label
                ><span>原密码</span
                ><input
                  class="input"
                  type="password"
                  autocomplete="current-password"
                  v-model="passwordForm.oldPassword"
                  required
              /></label>
              <label
                ><span>新密码</span
                ><input
                  class="input"
                  type="password"
                  autocomplete="new-password"
                  minlength="8"
                  maxlength="64"
                  v-model="passwordForm.newPassword"
                  required
                /><small>使用 8 至 64 个字符</small></label
              >
              <label
                ><span>确认新密码</span
                ><input
                  class="input"
                  type="password"
                  autocomplete="new-password"
                  minlength="8"
                  maxlength="64"
                  v-model="passwordForm.confirmPassword"
                  required
              /></label>
              <button class="btn" type="submit">修改密码</button>
            </form>
          </section>

          <section
            v-show="activeSection === 'appearance'"
            id="appearance"
            class="profile-section appearance-section"
          >
            <div class="profile-section-head">
              <div>
                <span>个性化</span>
                <h2 tabindex="-1">界面与主题</h2>
              </div>
              <small>{{
                theme.saving ? "正在同步…" : "自动同步到当前账号"
              }}</small>
            </div>
            <h3 class="appearance-subheading">布局方案</h3>
            <div class="layout-options" aria-label="界面布局方案">
              <button
                v-for="item in layouts"
                :key="item.id"
                type="button"
                class="layout-option"
                :class="{ selected: theme.interfaceLayout === item.id }"
                :aria-pressed="theme.interfaceLayout === item.id"
                :disabled="theme.saving"
                @click="chooseLayout(item.id)"
              >
                <span
                  class="layout-preview"
                  :data-preview="item.id"
                  aria-hidden="true"
                  ><i></i><i></i><i></i
                ></span>
                <b>{{ item.name }}</b
                ><small>{{ item.description }}</small>
                <em>{{
                  theme.interfaceLayout === item.id
                    ? "✓ 当前使用"
                    : "使用此布局"
                }}</em>
              </button>
            </div>
            <h3 class="appearance-subheading">配色方案</h3>
            <div class="theme-grid">
              <button
                v-for="item in themes"
                :key="item.id"
                class="theme-option"
                :class="{ selected: theme.theme === item.id }"
                :aria-pressed="theme.theme === item.id"
                :disabled="theme.saving"
                @click="chooseTheme(item.id)"
              >
                <span class="theme-preview"
                  ><i
                    v-for="color in item.colors"
                    :key="color"
                    :style="{ background: color }"
                  ></i
                ></span>
                <b>{{ item.name }}</b
                ><small>{{ item.description }}</small
                ><em>{{
                  theme.theme === item.id ? "✓ 当前使用" : "选择主题"
                }}</em>
              </button>
              <div
                class="theme-option custom-theme"
                :class="{ selected: theme.theme === 'custom' }"
              >
                <b>我的配色</b
                ><small class="palette-hint">点击下方色块打开调色板</small>
                <div class="custom-colors">
                  <label title="点击选择主题色"
                    ><input type="color" v-model="accent" /><span
                      >主题色<strong>{{ accent }}</strong></span
                    ></label
                  >
                  <label title="点击选择背景色"
                    ><input type="color" v-model="background" /><span
                      >背景色<strong>{{ background }}</strong></span
                    ></label
                  >
                </div>
                <button
                  class="btn"
                  :disabled="theme.saving"
                  @click="saveCustomTheme"
                >
                  {{ theme.saving ? "正在保存…" : "应用并保存" }}
                </button>
              </div>
            </div>
            <p class="theme-tip">
              为保证文字清晰，导航、正文和卡片使用固定的可读层级；调色板主要调整强调色和页面背景。
            </p>
          </section>
        </div>
      </div>
    </div>
    <div
      v-if="avatarEditorOpen"
      class="avatar-crop-modal"
      role="dialog"
      aria-modal="true"
      aria-label="裁剪头像"
    >
      <div class="avatar-crop-dialog card">
        <header>
          <div>
            <b>调整头像</b><small>拖动图片调整位置，滑动控件调整大小</small>
          </div>
          <button type="button" @click="closeAvatarEditor" aria-label="关闭">
            ×
          </button>
        </header>
        <div
          class="avatar-crop-stage"
          @pointerdown="startAvatarDrag"
          @pointermove="moveAvatarDrag"
          @pointerup="endAvatarDrag"
          @pointercancel="endAvatarDrag"
        >
          <img
            :src="avatarSource"
            alt="待裁剪头像"
            :style="{
              transform: `translate(${avatarOffsetX}px, ${avatarOffsetY}px) scale(${avatarZoom})`,
            }"
            draggable="false"
          />
          <i></i>
        </div>
        <label class="avatar-zoom"
          ><span>缩放</span
          ><input
            type="range"
            min="1"
            max="3"
            step="0.01"
            v-model.number="avatarZoom"
          /><b>{{ avatarZoom.toFixed(1) }}×</b></label
        >
        <footer>
          <button
            class="btn secondary"
            type="button"
            @click="closeAvatarEditor"
          >
            取消</button
          ><button
            class="btn"
            type="button"
            :disabled="avatarUploading"
            @click="confirmAvatar"
          >
            {{ avatarUploading ? "正在保存…" : "使用当前裁剪" }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
