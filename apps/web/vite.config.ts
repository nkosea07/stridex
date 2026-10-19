import { defineConfig } from 'vite'
import { reactRouter } from '@react-router/dev/vite'
import { fileURLToPath } from 'node:url'

const srcDir = fileURLToPath(new URL('./src', import.meta.url))

export default defineConfig({
  plugins: [...reactRouter()],
  resolve: {
    alias: {
      '@': srcDir,
    },
  },
  optimizeDeps: {
    include: ['cookie'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
