import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  server: {
    host: true,
    allowedHosts: ['viewing-express-blip.ngrok-free.dev', 'passably-morse-acuteness.ngrok-free.dev'],
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', proxyReq => {
            if (proxyReq.getHeader('origin')) proxyReq.setHeader('origin', 'http://127.0.0.1:8080');
          });
        },
      },
    },
  },
});
