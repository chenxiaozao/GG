import axios, { AxiosError } from "axios";
import { backendState, offlineResponse, supportsOffline } from './offlineCatalog';

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || "/api", timeout: 10_000 });

api.interceptors.request.use((config) => {
  if (backendState.offline) {
    config.adapter = async request => {
      if (!supportsOffline(request.url || '', request.method)) throw new Error('演示后端未开启；当前只读浏览，不会保存、下单或调用AI。请先恢复连接。');
      const data = await offlineResponse(request.url || '', request.params, request.data);
      return { data: {code:200,data}, status:200, statusText:'offline snapshot', headers:{}, config:request };
    };
  } else if (supportsOffline(config.url || '', config.method)) config.timeout = 3000;
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
  async (error: AxiosError<any>) => {
    if (!error.response && error.config && supportsOffline(error.config.url || '', error.config.method)) {
      return offlineResponse(error.config.url || '', error.config.params, error.config.data);
    }
    if (!error.response && error.isAxiosError) backendState.offline = true;
    const message = !error.response && error.isAxiosError
      ? '演示后端未开启或连接中断；本次操作未确认成功，请恢复连接后核对，不会伪造保存结果。'
      : error.response?.data?.message || error.message || "网络请求失败";
    if (error.response?.status === 401 || message.includes("登录已过期")) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.dispatchEvent(new Event("robotmall-session-expired"));
      const login = import.meta.env.VITE_ROUTER_MODE === "hash"
        ? `${import.meta.env.BASE_URL}#/login`
        : `${import.meta.env.BASE_URL}login`;
      if (!window.location.href.includes(login)) window.location.assign(login);
    }
    return Promise.reject(new Error(message));
  },
);

export default api;
