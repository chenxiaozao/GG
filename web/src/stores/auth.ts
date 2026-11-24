import { defineStore } from "pinia";
import api from "../api";
import { clearAllGuestData, guestMergePayload } from "./guestData";
/** 安全读取浏览器中的登录用户信息。 */
function storedUser() {
  try {
    const value = JSON.parse(localStorage.getItem("user") || "null");
    return value && typeof value === "object" ? value : null;
  } catch {
    return null;
  }
}

/** 维护当前用户及会话操作。 */
export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: storedUser() as any,
  }),
  getters: {
    logged: (s) => !!s.user,
    isAdmin: (s) => s.user?.role === "ADMIN",
  },
  actions: {
    /** 请求登录并迁移访客数据。 */
    async login(username: string, password: string) {
      // 当前后端直接返回用户对象；同时兼容以后改成 { token, user } 的结构。
      const result: any = await api.post("/auth/login", { username, password });
      const user = result.user ?? result;
      const token = result.token ?? user.token;
      if (!user || !token) throw new Error("登录响应缺少用户或令牌信息");
      localStorage.setItem("token", token);
      localStorage.setItem("user", JSON.stringify(user));
      this.user = user;
      if (user.mustChangePassword) return;
      const guest = guestMergePayload();
      if (guest.cart.length || guest.favorites.length || guest.history.length) {
        await api.post("/profile/merge-guest", guest);
        clearAllGuestData();
      }
    },
    /** 通知后端登出并清理本机会话。 */
    async logout() {
      try {
        await api.post("/auth/logout");
      } catch (error) {
        if (localStorage.getItem("token")) throw error;
      }
      this.clearSession();
    },
    /** 移除浏览器中的账号和令牌。 */
    clearSession() {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      this.user = null;
    },
  },
});
