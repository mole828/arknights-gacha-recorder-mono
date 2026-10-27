import { defineConfig } from "vite";

export default defineConfig({
  build: {
    rollupOptions: {
      // Ktor's JS client also references `ws` for Node.js; the browser engine never uses it.
      external: ["ws"],
    },
  },
  server: {
    host: "127.0.0.1",
    proxy: {
      "/api/ark": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/ark/, ""),
      },
    },
  },
});
