import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  server: {
    host: true,
    proxy: { 
      '/api': 'http://127.0.0.1:8080' 
    },
    allowedHosts: [
      'viewing-express-blip.ngrok-free.dev'
    ]
  }
});