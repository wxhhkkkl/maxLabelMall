import { defineConfig } from '@playwright/test'
import config from './playwright.solutions.config'
export default defineConfig({ ...config, testMatch: 'software.spec.ts' })
