import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    proxy: {
      '/orders':    'http://localhost:8080',
      '/orderbook': 'http://localhost:8080',
      '/trades':    'http://localhost:8080',
      '/metrics':   'http://localhost:8080',
      '/events':    'http://localhost:8080',
      '/replay':    'http://localhost:8080',
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: false,
      },
    },
  },
});
