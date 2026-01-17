import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  return {
    plugins: [react()],
    define: {
      global: 'window',
    },
    optimizeDeps: {
      esbuildOptions: {
        define: {
          global: 'globalThis',
          'process.env': '{}',
        },
      },
    },
    base: mode === 'production' ? '/' : '/',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
