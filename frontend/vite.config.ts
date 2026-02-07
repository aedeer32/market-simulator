import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  server: {
    proxy: {
      '/ws-market': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true, // 重要
      },
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
});
