import { defineConfig } from '@playwright/test'

/** Static solutions pages: independent of the backend and transaction E2E setup. */
export default defineConfig({
  testDir: './e2e',
  testMatch: 'solutions.spec.ts',
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    browserName: 'chromium',
    channel: 'chrome',
    headless: true,
    locale: 'zh-CN',
  },
  webServer: {
    command: 'pnpm exec vite --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
  },
})
