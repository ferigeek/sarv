import { defineConfig } from 'vite'

// Dashboard is served by FastAPI from ../static/dist (same origin as the APIs),
// so the template can reference stable filenames without manifest parsing.
export default defineConfig({
  base: '/static/dist/',
  build: {
    outDir: '../static/dist',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'dashboard.js',
        chunkFileNames: 'chunks/[name].js',
        assetFileNames: '[name][extname]',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/users': 'http://localhost:8001',
      '/usage': 'http://localhost:8001',
      '/events': 'http://localhost:8001',
      '/engagement': 'http://localhost:8001',
    },
  },
})
