// 백엔드 베이스 URL.
// - 빌드 시 VITE_API_BASE_URL 이 주입되면(prod 배포) 그 절대 주소로 백엔드에 붙는다.
//   예) https://mini-exchange-api.onrender.com
// - 비어 있으면 상대경로 → 로컬 dev에서는 vite 프록시(vite.config.ts)가 8080으로 포워딩.
// 뒤에 슬래시가 붙어 와도 안전하게 제거한다.
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
