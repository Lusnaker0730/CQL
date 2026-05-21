import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // monaco-editor intentionally NOT in manualChunks. Putting it here
        // (as a vendor chunk) caused Vite to add a <link rel="modulepreload">
        // for it in index.html, eagerly downloading ~970 KB gzipped on every
        // page load including Landing / Login. Letting Vite decide via the
        // import graph instead means monaco-editor's chunk is only fetched
        // when an editor-bearing route is navigated to.
        manualChunks: {
          'vendor-mui': ['@mui/material', '@mui/icons-material'],
          'vendor-state': ['@tanstack/react-query', 'react-redux', '@reduxjs/toolkit'],
          'vendor-recharts': ['recharts'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/cds-services': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
