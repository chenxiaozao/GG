import { createRouter, createWebHistory, createWebHashHistory } from "vue-router";
import Home from "./views/Home.vue";

const router = createRouter({
  history: import.meta.env.VITE_ROUTER_MODE === "hash"
    ? createWebHashHistory(import.meta.env.BASE_URL)
    : createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", component: Home },
    { path: "/products", component: () => import("./views/Products.vue") },
    {
      path: "/product/:id",
      component: () => import("./views/ProductDetail.vue"),
    },
    { path: "/compare", component: () => import("./views/Compare.vue") },
    {
      path: "/compatibility",
      component: () => import("./views/Compatibility.vue"),
    },
    { path: "/advisor", component: () => import("./views/Advisor.vue") },
    { path: "/login", component: () => import("./views/Login.vue") },
    { path: "/cart", component: () => import("./views/Cart.vue") },
    {
      path: "/orders",
      component: () => import("./views/Orders.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/after-sales",
      component: () => import("./views/AfterSales.vue"),
      meta: { requiresAuth: true },
    },
    { path: "/favorites", component: () => import("./views/Favorites.vue") },
    { path: "/history", component: () => import("./views/History.vue") },
    { path: "/trade-in", component: () => import("./views/TradeIn.vue") },
    {
      path: "/profile",
      component: () => import("./views/Profile.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/admin",
      component: () => import("./views/Admin.vue"),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition;
    // Profile hashes select panels, not anchors in a long settings document.
    if (to.path === "/profile")
      return _from.path === "/profile" ? false : { top: 0 };
    if (to.hash) return { el: to.hash, top: 120, behavior: "smooth" };
    return { top: 0 };
  },
});

router.beforeEach((to) => {
  const user = JSON.parse(localStorage.getItem("user") || "null");
  if (to.meta.requiresAuth && !user)
    return { path: "/login", query: { redirect: to.fullPath } };
  if (to.meta.requiresAdmin && user?.role !== "ADMIN")
    return user ? "/" : "/login";
  if (to.path === "/login" && user)
    return user.role === "ADMIN" ? "/admin" : "/";
});

export default router;
