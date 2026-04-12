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
      force: true,
      include: [
        '@stomp/stompjs',
        '@stripe/stripe-js',
        'axios',
        'hls.js',
        'react-helmet-async',
        'react-router-dom',
      ],
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
      watch: {
        usePolling: true,
      },
      headers: {
        'Cache-Control': 'no-store',
      },
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          timeout: 60000,
        },
        '/auth': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/ws': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
        },
        '/stream': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/hls': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/debug': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/actuator': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/uploads': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
