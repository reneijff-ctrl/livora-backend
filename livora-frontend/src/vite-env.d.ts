/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_APP_URL: string
  readonly VITE_APP_ENV: string
  readonly VITE_SUPPORT_TOKENS: string
  // more env variables...
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
