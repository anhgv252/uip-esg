import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'UIP Smart City',
        short_name: 'UIP',
        start_url: '/citizen',
        scope: '/',
        display: 'standalone',
        theme_color: '#1976D2',
        background_color: '#ffffff',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: '/icons/icon-192.svg', sizes: '192x192', type: 'image/svg+xml' },
          { src: '/icons/icon-512.svg', sizes: '512x512', type: 'image/svg+xml' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/offline\.html$/, /^\/api\//],
        runtimeCaching: [
          {
            urlPattern: /^https?:\/\/.*\/api\/v1\/citizen\/bills/,
            handler: 'NetworkFirst',
            options: { cacheName: 'citizen-bills', expiration: { maxEntries: 50, maxAgeSeconds: 7 * 24 * 60 * 60 } },
          },
          {
            urlPattern: /^https?:\/\/.*\/api\/v1\/environment/,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'environment-data', expiration: { maxEntries: 20, maxAgeSeconds: 5 * 60 } },
          },
        ],
      },
      devOptions: {
        enabled: true,
        type: 'module',
      },
    }),
  ],
  optimizeDeps: {
    // Prevent duplicate emotion/MUI instances when HMR cache is warm
    dedupe: ['@emotion/react', '@emotion/styled', '@mui/material'],
    // NOTE (2026-06-18): the Vite DEV server crashes with
    // `createTheme_default is not a function` (blank #root) because the
    // lockfile resolves `^5.15.14` up to @mui/material 5.18.0, whose
    // package.json `exports` map lacks a `./styles` entry — esbuild's
    // pre-bundle then fails to find the ESM entry for the named
    // `createTheme` export and falls back to a broken CJS interop.
    // `include: ['@mui/material/styles', ...]` does NOT fix it (tried).
    // The PRODUCTION build (`vite build` / `vite preview`) is unaffected —
    // Rollup resolves the export correctly. So for live demos and staging,
    // serve the production build, not the dev server. Permanent fix: pin
    // @mui/material (and @mui/icons-material) to an exact 5.15.x in a
    // follow-up dependency task. See mvp4-inner-browser-demo-2026-06-18.md.
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
    // Force single React instance — prevents "Invalid hook call" when root node_modules
    // has react (React Native) while frontend node_modules has react-dom
    dedupe: ['react', 'react-dom', '@tanstack/react-query'],
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    headers: {
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'X-XSS-Protection': '1; mode=block',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@mui/icons-material'],
          bpmn: ['bpmn-js'],
          recharts: ['recharts'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    onUnhandledRejection: 'warn',
    exclude: ['node_modules', 'dist', 'e2e/**'],
    // Inline @tanstack/react-query so it resolves the same React instance as test files
    server: {
      deps: {
        inline: ['@tanstack/react-query'],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      exclude: ['src/test/**', 'src/main.tsx'],
    },
  },
})
