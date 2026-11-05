<script setup lang="ts">
import { useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";
import UiIcon from "./UiIcon.vue";
defineProps<{ open: boolean }>();
defineEmits<{ close: [] }>();
const route = useRoute();
const auth = useAuthStore();
const groups = [
  {
    name: "我的账户",
    items: [
      { name: "我的空间", to: "/profile", icon: "user" },
      { name: "订单中心", to: "/orders", icon: "orders" },
      { name: "售后服务", to: "/after-sales", icon: "settings" },
      { name: "我的收藏", to: "/favorites", icon: "heart" },
      { name: "浏览记录", to: "/history", icon: "history" },
      { name: "购物车", to: "/cart", icon: "cart" },
      { name: "以旧换新", to: "/trade-in", icon: "compare" },
    ],
  },
  {
    name: "账户设置",
    items: [
      { name: "个人资料", to: "/profile#account", icon: "user" },
      { name: "收货地址", to: "/profile#addresses", icon: "location" },
      { name: "账号安全", to: "/profile#security", icon: "lock" },
      { name: "界面与主题", to: "/profile#appearance", icon: "palette" },
    ],
  },
];
function active(to: string) {
  const [path, hash = ""] = to.split("#");
  return (
    route.path === path &&
    (path !== "/profile" ||
      (route.hash.slice(1) || "overview") === (hash || "overview"))
  );
}
</script>
<template>
  <aside
    id="account-navigation"
    :class="['account-navigation', { 'is-open': open }]"
    aria-label="账户导航"
  >
    <div class="account-nav-heading">
      <span>{{ auth.logged ? auth.user.nickname : "欢迎来到小造智械" }}</span
      ><button
        type="button"
        class="account-nav-close"
        aria-label="关闭账户导航"
        @click="$emit('close')"
      >
        <UiIcon name="close" />
      </button>
    </div>
    <nav v-for="group in groups" :key="group.name" :aria-label="group.name">
      <h2>{{ group.name }}</h2>
      <router-link
        v-for="item in group.items"
        :key="item.to"
        :to="item.to"
        :class="{ 'is-active': active(item.to) }"
        :aria-current="active(item.to) ? 'page' : undefined"
        ><UiIcon :name="item.icon" :size="19" /><span>{{
          item.name
        }}</span></router-link
      >
    </nav>
    <router-link v-if="auth.isAdmin" class="account-admin-link" to="/admin"
      >进入管理后台 →</router-link
    >
    <router-link v-if="!auth.logged" class="account-admin-link" to="/login"
      >登录以管理个人数据 →</router-link
    >
  </aside>
</template>
