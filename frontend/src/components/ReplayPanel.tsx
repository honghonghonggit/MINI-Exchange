import { useState } from 'react';
import { API_BASE } from '../config';
import './ReplayPanel.css';

interface ReplayResult {
  inputEvents: number;
  regeneratedExecutions: number;
  regeneratedQuantity: number;
  recordedExecutions: number;
  recordedQuantity: number;
  matched: boolean;
  finalBidLevels: number;
  finalAskLevels: number;
}

/**
 * 이벤트 리플레이 패널. 버튼을 누르면 백엔드가 event_logs의 주문 입력을
 * 새 오더북에 가격-시간 우선으로 재생해 체결을 재구성하고, 원본과 대조한 결과를 보여준다.
 */
export function ReplayPanel() {
  const [result, setResult] = useState<ReplayResult | null>(null);
  const [loading, setLoading] = useState(false);

  const runReplay = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/replay`);
      setResult(await res.json());
    } catch {
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="replay-panel">
      <div className="panel-title">이벤트 리플레이</div>
      <button className="replay-btn" onClick={runReplay} disabled={loading}>
        {loading ? '재생 중…' : '최근 이벤트 재생'}
      </button>
      {result && (
        <div className="replay-result">
          <div>입력 이벤트 <b>{result.inputEvents.toLocaleString()}</b>건 재생</div>
          <div>
            재구성 체결 <b>{result.regeneratedExecutions.toLocaleString()}</b>건
            {' / '}원본 <b>{result.recordedExecutions.toLocaleString()}</b>건
          </div>
          <div className={result.matched ? 'replay-ok' : 'replay-diff'}>
            {result.matched
              ? '✓ 원본과 일치 — 이벤트 로그만으로 체결을 결정적으로 복원'
              : `△ 원본과 ${Math.abs(result.regeneratedExecutions - result.recordedExecutions)}건 차이 — VI 거래정지·윈도우 경계 효과로 일부 체결이 다르게 재구성됨`}
          </div>
        </div>
      )}
    </div>
  );
}
