import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'BACKEND_PROXY_TARGET');
  const backendTarget = env.BACKEND_PROXY_TARGET || 'http://127.0.0.1:8080';
  return {
  base: './',
  server: {
    host: true,
    allowedHosts: ['viewing-express-blip.ngrok-free.dev', 'passably-morse-acuteness.ngrok-free.dev'],
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', proxyReq => {
            if (proxyReq.getHeader('origin')) proxyReq.setHeader('origin', new URL(backendTarget).origin);
          });
        },
      },
    },
  },
  };
});
