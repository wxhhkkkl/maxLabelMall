/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_TITLE: string
  readonly VITE_TENANT_ID: string
  readonly VITE_BASE_URL: string
  readonly VITE_API_URL: string
  readonly VITE_PORT: string
  readonly VITE_BASE_PATH: string
  readonly VITE_OUT_DIR: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
