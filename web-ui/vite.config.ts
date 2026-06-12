import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In dev, the API runs separately (./gradlew :solver-api:run) — proxy to it.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: process.env.SOLVER_API_URL ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  test: {
    environment: "node",
  },
});
