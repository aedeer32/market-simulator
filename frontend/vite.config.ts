import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  server: {
    proxy: {
      '/ws-market': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true, // 重要
      },
    },
  },
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
});