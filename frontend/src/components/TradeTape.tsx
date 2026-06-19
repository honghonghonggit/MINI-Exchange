import { useTrades } from '../hooks/useTrades';
import './TradeTape.css';

export function TradeTape() {
  const trades = useTrades();

  return (
    <div className="trade-tape">
      <div className="panel-title">체결 테이프</div>
      <div className="tape-header">
        <span>가격</span>
        <span>수량</span>
      </div>
      <div className="tape-list">
        {trades.length === 0 ? (
          <div className="tape-empty">체결 대기중...</div>
        ) : (
          trades.map((t, i) => (
            <div key={i} className="tape-row">
              <span className="tape-price">{t.price.toLocaleString()}</span>
              <span className="tape-qty">{t.quantity}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
