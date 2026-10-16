import { defineConfig } from 'vitest/config';

// 开发时 dev server 代理到后端（M3 API）；生产构建产物由 Maven -Pweb 拷入 Boot 静态目录
export default defineConfig({
  test: {
    environment: 'jsdom',
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
