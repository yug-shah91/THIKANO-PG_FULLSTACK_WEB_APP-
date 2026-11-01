import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The proxy forwards every /api request to Spring Boot (port 8080).
// This avoids CORS errors during development, so no backend change is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
