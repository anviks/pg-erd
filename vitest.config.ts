import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/**/*.test.ts'],
    exclude: process.env.RUN_IT ? [] : ['test/integration/**'],
    testTimeout: 120_000,
    hookTimeout: 180_000,
  },
});
