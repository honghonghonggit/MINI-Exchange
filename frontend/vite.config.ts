import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/orders':    'http://localhost:8080',
      '/orderbook': 'http://localhost:8080',
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: false,        // SockJS는 HTTP로 폴링 후 업그레이드 — ws:true 불필요
      },
    },
  },
});
