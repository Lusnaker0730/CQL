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
        manualChunks: {
          'vendor-monaco': ['monaco-editor'],
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
