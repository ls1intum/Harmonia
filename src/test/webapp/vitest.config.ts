import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

const mainWebapp = path.resolve(__dirname, '../../main/webapp');

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(mainWebapp, 'src'),
    },
  },
  server: {
    fs: {
      allow: [__dirname, mainWebapp],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: [],
    include: [path.join(__dirname, 'src/**/*.test.{ts,tsx}')],
  },
});
