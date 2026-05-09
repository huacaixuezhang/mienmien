import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    /** 降低本机/代理把 index 或模块缓存成旧版本的概率，避免「改了代码却像没生效」 */
    headers: {
      "Cache-Control": "no-store, max-age=0"
    }
  }
});
