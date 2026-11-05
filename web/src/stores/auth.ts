import { defineStore } from "pinia";
import api from "../api";
import { clearAllGuestData, guestMergePayload } from "./guestData";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: JSON.parse(localStorage.getItem("user") || "null") as any,
  }),
  getters: {
    logged: (s) => !!s.user,
    isAdmin: (s) => s.user?.role === "ADMIN",
  },
  actions: {
    async login(username: string, password: string) {
      // 当前后端直接返回用户对象；同时兼容以后改成 { token, user } 的结构。
      const result: any = await api.post("/auth/login", { username, password });
      const user = result.user ?? result;
      const token = result.token ?? user.token;
      if (!user || !token) throw new Error("登录响应缺少用户或令牌信息");
      localStorage.setItem("token", token);
      localStorage.setItem("user", JSON.stringify(user));
      this.user = user;
      const guest = guestMergePayload();
      if (guest.cart.length || guest.favorites.length || guest.history.length) {
        await api.post("/profile/merge-guest", guest);
        clearAllGuestData();
      }
    },
    logout() {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      this.user = null;
    },
  },
});
