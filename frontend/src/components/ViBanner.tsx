import { useEffect, useState } from 'react';
import { useVi } from '../hooks/useVi';
import './ViBanner.css';

/**
 * 변동성완화장치(VI) 발동 시 화면 상단에 거래정지 배너를 띄운다.
 * until(해제 예정 시각)까지 남은 초를 카운트다운한다.
 */
export function ViBanner() {
  const vi = useVi();
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (!vi?.halted) return;
    const tick = () => setRemaining(Math.max(0, Math.ceil((vi.until - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 200);
    return () => clearInterval(id);
  }, [vi]);

  if (!vi?.halted) return null;

  return (
    <div className="vi-banner" role="alert">
      <span className="vi-dot" />
      거래 일시정지 — 변동성완화장치(VI) 발동 · 기준가 {vi.referencePrice.toLocaleString()}원
      {remaining > 0 && <span className="vi-countdown"> · {remaining}초 후 재개</span>}
    </div>
  );
}
