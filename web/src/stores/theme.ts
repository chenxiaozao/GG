import { defineStore } from "pinia";
import api from "../api";
import { contrastText, readableAccent } from "../utils/colors";

export type ThemeName = "classic" | "midnight" | "warm" | "custom";
export type InterfaceLayout = "standard" | "editorial" | "compact";

type ThemePreference = {
  theme: ThemeName;
  accentColor: string;
  backgroundColor: string;
  interfaceLayout?: InterfaceLayout;
};

const presets: Record<Exclude<ThemeName, "custom">, ThemePreference> = {
  classic: {
    theme: "classic",
    accentColor: "#44634e",
    backgroundColor: "#fafaf7",
  },
  midnight: {
    theme: "midnight",
    accentColor: "#5aa7ff",
    backgroundColor: "#111827",
  },
  warm: { theme: "warm", accentColor: "#c65d32", backgroundColor: "#f7f1e7" },
};

function validColor(value: unknown, fallback: string) {
  return typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value)
    ? value
    : fallback;
}

export const useThemeStore = defineStore("theme", {
  state: (): ThemePreference & { saving: boolean } => ({
    theme: "classic",
    accentColor: "#44634e",
    backgroundColor: "#fafaf7",
    interfaceLayout: "standard",
    saving: false,
  }),
  actions: {
    apply() {
      const root = document.documentElement;
      root.dataset.theme = this.theme;
      root.dataset.layout = this.interfaceLayout || "standard";
      root.dataset.colorScheme =
        this.theme === "midnight" ||
        (this.theme === "custom" &&
          contrastText(this.backgroundColor) === "#ffffff")
          ? "dark"
          : "light";
      root.style.setProperty("--accent", this.accentColor);
      root.style.setProperty("--accent-text", contrastText(this.accentColor));
      const dark = root.dataset.colorScheme === "dark";
      const surfaces = dark
        ? ["#1b2533", "#222f40"]
        : this.theme === "warm"
          ? ["#fffdf9", "#f3eadf"]
          : ["#ffffff", "#f7f8fa"];
      root.style.setProperty(
        "--accent-ink",
        readableAccent(
          this.accentColor,
          [...surfaces, this.backgroundColor],
          dark ? "#ffffff" : "#000000",
        ),
      );
      root.style.setProperty("--page-bg", this.backgroundColor);
      localStorage.setItem(
        "robotmall-theme",
        JSON.stringify({
          theme: this.theme,
          accentColor: this.accentColor,
          backgroundColor: this.backgroundColor,
          interfaceLayout: this.interfaceLayout,
        }),
      );
    },
    initialize(user?: any) {
      let local: Partial<ThemePreference> = {};
      try {
        local = JSON.parse(localStorage.getItem("robotmall-theme") || "{}");
      } catch {
        local = {};
      }
      const selected = user?.theme || local.theme || "classic";
      this.theme = ["classic", "midnight", "warm", "custom"].includes(selected)
        ? selected
        : "classic";
      this.accentColor = validColor(
        user?.accentColor || local.accentColor,
        presets[this.theme === "custom" ? "classic" : this.theme].accentColor,
      );
      this.backgroundColor = validColor(
        user?.backgroundColor || local.backgroundColor,
        presets[this.theme === "custom" ? "classic" : this.theme].backgroundColor,
      );
      // Upgrade only the old standard preset; keep deliberate custom colors intact.
      if (this.theme === 'classic' && this.accentColor === '#d9363e' && this.backgroundColor === '#f5f6f8') {
        Object.assign(this, presets.classic);
      }
      const layout = user
        ? user.interfaceLayout || "standard"
        : local.interfaceLayout || "standard";
      this.interfaceLayout = ["standard", "editorial", "compact"].includes(
        layout,
      )
        ? layout
        : "standard";
      this.apply();
    },
    async select(theme: ThemeName) {
      this.theme = theme;
      if (theme !== "custom") Object.assign(this, presets[theme]);
      this.apply();
      await this.sync();
    },
    async customize(accentColor: string, backgroundColor: string) {
      this.theme = "custom";
      this.accentColor = validColor(accentColor, "#d9363e");
      this.backgroundColor = validColor(backgroundColor, "#f5f6f8");
      this.apply();
      await this.sync();
    },
    async selectLayout(layout: InterfaceLayout) {
      this.interfaceLayout = layout;
      this.apply();
      await this.sync();
    },
    async reset() {
      Object.assign(this, presets.classic);
      this.apply();
      await this.sync();
    },
    async sync() {
      if (!localStorage.getItem("token")) return;
      this.saving = true;
      try {
        const user: any = await api.put("/profile/theme", {
          theme: this.theme,
          accentColor: this.accentColor,
          backgroundColor: this.backgroundColor,
          interfaceLayout: this.interfaceLayout,
        });
        const existing = JSON.parse(localStorage.getItem("user") || "{}");
        localStorage.setItem("user", JSON.stringify({ ...existing, ...user }));
      } finally {
        this.saving = false;
      }
    },
  },
});
