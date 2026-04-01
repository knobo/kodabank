import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    tailwindcss(),
    react(),
  ],
  server: {
    proxy: {
      // Proxy API calls to the payment gateway to avoid CORS issues in dev
      '/api/v1': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
    },
  },
})
