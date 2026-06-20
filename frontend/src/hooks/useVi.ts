import { useState } from 'react';
import type { ViState } from '../types';
import { useStompSubscription } from '../ws/StompProvider';

/** /topic/vi 구독 — 변동성완화장치 상태(정지/해제). */
export function useVi() {
  const [vi, setVi] = useState<ViState | null>(null);
  useStompSubscription<ViState>('/topic/vi', setVi);
  return vi;
}
