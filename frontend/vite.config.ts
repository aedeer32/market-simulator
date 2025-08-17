import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import globals from 'rollup-plugin-node-globals';

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
    global: {},
  },
  build: {
    rollupOptions: {
      plugins: [globals()],
    },
  },
});