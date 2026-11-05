import axios, { AxiosError } from "axios";

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || "/api", timeout: 10_000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers["X-Token"] = token;
  return config;
});

api.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (!body || body.code !== 200) {
      return Promise.reject(new Error(body?.message || "请求失败"));
    }
    return body.data;
  },
  (error: AxiosError<any>) => {
    const message = error.response?.data?.message || error.message || "网络请求失败";
    if (error.response?.status === 401 || message.includes("登录已过期")) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      const login = import.meta.env.VITE_ROUTER_MODE === "hash"
        ? `${import.meta.env.BASE_URL}#/login`
        : `${import.meta.env.BASE_URL}login`;
      if (!window.location.href.includes(login)) window.location.assign(login);
    }
    return Promise.reject(new Error(message));
  },
);

export default api;
