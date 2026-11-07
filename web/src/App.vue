<script setup lang="ts">
import { resourceUrl } from "./resourceUrl";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useAuthStore } from "./stores/auth";
import { useThemeStore } from "./stores/theme";
import { useRoute, useRouter } from "vue-router";
import UiIcon from "./components/UiIcon.vue";
import AccountNavigation from "./components/AccountNavigation.vue";
import { backendState, retryBackend } from './offlineCatalog';
import {useComparison} from './stores/comparison';
const comparison=useComparison();
const connectionMessage = ref('');
async function reconnect() { try { connectionMessage.value=''; await retryBackend(); } catch { connectionMessage.value='后端仍未连接，请确认服务已经启动'; } }
const a = useAuthStore(),
  r = useRouter(),
  route = useRoute(),
  theme = useThemeStore();
const scrolled = ref(false);
const accountMenuOpen = ref(false);
const personalPage = computed(() => ['/profile', '/orders', '/after-sales', '/favorites', '/history', '/cart'].includes(route.path));
const headerAvatarFailed = ref(false);
watch(() => a.user?.avatar, () => { headerAvatarFailed.value = false; });
watch(accountMenuOpen, async (open) => {
  await nextTick();
  if (open)
    document.querySelector<HTMLButtonElement>(".account-nav-close")?.focus();
});
function accountKeyboard(event: KeyboardEvent) {
  if (!accountMenuOpen.value) return;
  if (event.key === "Escape") {
    accountMenuOpen.value = false;
    document.querySelector<HTMLButtonElement>(".account-menu-toggle")?.focus();
  }
  if (event.key === "Tab") {
    const controls = Array.from(
      document.querySelectorAll<HTMLElement>(
        "#account-navigation a, #account-navigation button",
      ),
    ).filter((el) => el.getClientRects().length);
    const first = controls[0],
      last = controls.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }
}
const mobileMenu = ref<HTMLDetailsElement | null>(null);
function updateScroll() {
  scrolled.value = window.scrollY > 18;
}
function scrollTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}
theme.initialize(a.user);
watch(
  () => a.user?.id,
  () => {theme.initialize(a.user);comparison.clear();},
);
watch(
  () => route.fullPath,
  () => {
    accountMenuOpen.value = false;
    if (mobileMenu.value) mobileMenu.value.open = false;
  },
);
onMounted(() => {
  updateScroll();
  window.addEventListener("scroll", updateScroll, { passive: true });
  window.addEventListener("keydown", accountKeyboard);
});
onBeforeUnmount(() => {
  window.removeEventListener("scroll", updateScroll);
  window.removeEventListener("keydown", accountKeyboard);
});
async function logout() {
  try { await a.logout(); await r.push("/"); }
  catch {
    if (window.confirm('无法确认服务端是否已退出。是否只清除本机登录？这不能保证服务端凭证已撤销，恢复连接后请核对。')) {
      a.clearSession(); await r.push('/login');
    }
  }
}
</script>
<template>
  <div v-if="backendState.offline" class="offline-notice" role="status">
    <span>后端未开启，当前为只读目录快照（{{ backendState.snapshotAt.slice(0,10) }}）。价格、库存不实时；账号、交易、评价、适配与 AI 需连接后端。</span>
    <button type="button" @click="reconnect" :disabled="backendState.checking">{{ backendState.checking ? '检查中…' : '重新连接' }}</button>
    <span v-if="connectionMessage">{{ connectionMessage }}</span>
  </div>
  <div :class="['app-shell', 'experience-shell', { 'personal-workspace': personalPage, 'shopping-workspace': !personalPage, 'has-comparison-tray': route.path!=='/compare' && comparison.items.length>0 }]">
    <div :class="['site-chrome', { scrolled }]">
      <header class="site-header">
        <div class="header-inner">
          <router-link class="logo" to="/"
            ><b>小造智械</b><small>ROBOT MALL</small></router-link
          >
          <nav class="main-nav" aria-label="主导航">
            <router-link to="/">首页</router-link
            ><router-link to="/products">全部商品</router-link
            ><router-link to="/compare">参数对比</router-link
            ><router-link to="/compatibility">配件适配</router-link
            ><router-link to="/advisor">选购顾问</router-link
            ><router-link to="/trade-in">以旧换新</router-link>
          </nav>
          <div class="actions header-actions">
            <router-link class="cart-link" to="/cart"
              ><UiIcon name="cart" :size="18" />购物车</router-link
            ><template v-if="a.logged"
              ><router-link
                class="account-link"
                :title="a.user.nickname"
                to="/profile"
                ><img v-if="a.user?.avatar && !headerAvatarFailed" class="header-user-avatar" :src="resourceUrl(a.user.avatar)" alt="" @error="headerAvatarFailed = true" /><UiIcon v-else name="user" :size="18" />我的空间</router-link
              ><button class="link" @click="logout">退出</button></template
            ><router-link v-else to="/login">登录</router-link>
          </div>
          <details ref="mobileMenu" class="mobile-menu">
            <summary aria-label="展开导航">
              <span></span><span></span><span></span><b>菜单</b>
            </summary>
            <div class="mobile-panel">
              <router-link to="/">首页</router-link
              ><router-link to="/products">机器人商城</router-link>
              <router-link to="/compare">参数对比</router-link
              ><router-link to="/compatibility">配件适配</router-link>
              <router-link to="/advisor">选购顾问</router-link
              ><router-link to="/trade-in">以旧换新</router-link>
              <router-link to="/cart">购物车</router-link
              ><router-link to="/favorites">我的收藏</router-link
              ><router-link to="/history">浏览记录</router-link>
              <router-link v-if="a.logged" to="/orders">我的订单</router-link
              ><router-link v-if="a.logged" to="/after-sales"
                >售后服务</router-link
              >
              <template v-if="a.logged"
                ><router-link :to="a.isAdmin ? '/admin' : '/profile'">{{
                  a.user.nickname
                }}</router-link
                ><button class="link" @click="logout">退出</button></template
              >
              <router-link v-else to="/login">登录</router-link>
            </div>
          </details>
        </div>
      </header>
    </div>
    <button
      class="account-menu-toggle"
      type="button"
      :aria-expanded="accountMenuOpen"
      aria-controls="account-navigation"
      @click="accountMenuOpen = !accountMenuOpen"
    >
      <UiIcon name="user" :size="18" />空间导航
    </button>
    <button
      v-if="accountMenuOpen"
      class="account-menu-backdrop"
      aria-label="关闭账户导航"
      @click="accountMenuOpen = false"
    ></button>
    <div class="workspace-layout">
      <AccountNavigation
        :open="accountMenuOpen"
        @close="accountMenuOpen = false"
      />
      <main class="workspace-content" id="page-content">
        <p v-if="comparison.message" class="notice" role="status">{{ comparison.message }}</p>
        <router-view v-slot="{ Component, route: viewRoute }"
          ><transition name="page-shift" mode="out-in"
            ><component :is="Component" :key="viewRoute.path" /></transition
        ></router-view>
      </main>
    </div>
    <transition name="float-button"
      ><button
        v-if="scrolled"
        class="back-to-top"
        type="button"
        aria-label="返回顶部"
        @click="scrollTop"
      >
        <UiIcon name="arrow" :size="20" /><small>返回顶部</small>
      </button></transition
    >
    <section v-if="comparison.items.length && route.path!=='/compare'" class="comparison-tray" aria-label="待对比商品"><div class="comparison-tray-heading"><strong>商品对比 · {{ comparison.items.length }}/4</strong><span>选同类商品，再开始对比</span><button type="button" @click="comparison.clear()">清空</button></div><ul><li v-for="p in comparison.items" :key="p.id"><span>{{ p.name }}</span><button type="button" :aria-label="`移除对比 ${p.name}`" @click="comparison.remove(p.id)">×</button></li></ul><button type="button" class="btn" :disabled="comparison.items.length<2" @click="r.push({path:'/compare',query:{ids:comparison.items.map(p=>p.id).join(',')}})">开始对比（{{ comparison.items.length }}）</button><p v-if="comparison.message" role="status">{{ comparison.message }}</p></section>
    <footer class="site-footer">
      <div>
        <div>
          <b>小造智械</b>
          <p>面向机器人商品选购、交易与设备服务的毕业设计演示平台。</p>
        </div>
        <div>
          <b>选购服务</b><router-link to="/compare">参数对比</router-link
          ><router-link to="/compatibility">配件适配</router-link
          ><router-link to="/advisor">场景选购</router-link>
        </div>
        <div>
          <b>用户服务</b><router-link to="/cart">购物车</router-link
          ><router-link to="/orders">订单中心</router-link
          ><router-link to="/after-sales">售后服务</router-link
          ><router-link to="/trade-in">以旧换新</router-link>
        </div>
      </div>
      <p>© 2026 小造智械机器人垂直电商平台 · 毕业设计非商业演示</p>
      <details class="platform-notice"><summary>平台使用说明</summary><p>本平台用于课程与毕业设计，商品价格、库存、销量、评分、支付和配送流程为模拟数据，不收取真实款项，不提供实际商品交易承诺。图片中的道具不代表所选配置包含。</p></details>
    </footer>
  </div>
</template>
