/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 백엔드 베이스 URL (예: https://mini-exchange-api.onrender.com). 비우면 상대경로/프록시 사용. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
