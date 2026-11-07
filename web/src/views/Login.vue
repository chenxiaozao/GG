<script setup lang="ts">
import { reactive, ref } from "vue";
import { useAuthStore } from "../stores/auth";
import { useRoute, useRouter } from "vue-router";
import api from "../api";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const form = reactive({ username: "", password: "", nickname: "" });
const mode = ref<"login" | "register">("login");
const msg = ref(route.query.notice === "password-changed" ? "密码已修改，旧登录凭证已撤销，请重新登录" : "");
const loading = ref(false);

async function submit() {
  if (loading.value) return;
  msg.value = "";
  loading.value = true;
  try {
    if (mode.value === "login") {
      await auth.login(form.username.trim(), form.password);
      if (auth.user?.mustChangePassword) { await router.replace('/profile#security'); return; }
      const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "";
      await router.replace(auth.isAdmin ? "/admin" : redirect || "/");
    } else {
      await api.post("/auth/register", form);
      mode.value = "login";
      msg.value = "注册成功，请使用新账号登录";
    }
  } catch (e: any) {
    msg.value = e?.message || "操作失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container content-narrow">
    <form class="form" @submit.prevent="submit">
      <h1>{{ mode === "login" ? "账号登录" : "用户注册" }}</h1>
      <label>用户名</label>
      <input class="input" v-model="form.username" autocomplete="username" />
      <label>密码</label>
      <input class="input" type="password" v-model="form.password" autocomplete="current-password" />
      <template v-if="mode === 'register'">
        <label>昵称</label>
        <input class="input" v-model="form.nickname" />
      </template>
      <p>
        <button class="btn" type="submit" :disabled="loading">{{ loading ? "处理中…" : "提交" }}</button>
        <button class="btn secondary" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
          切换{{ mode === "login" ? "注册" : "登录" }}
        </button>
      </p>
      <p class="notice" v-if="msg">{{ msg }}</p>
      <small>本平台为课程与毕业设计演示，不产生真实交易。注册密码须为 8 至 64 位。</small>
    </form>
  </div>
</template>
