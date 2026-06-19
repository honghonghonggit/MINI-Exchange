import type { PriceLevel } from '../types';
import { useOrderBook } from '../hooks/useOrderBook';
import './OrderBookPanel.css';

const MAX_LEVELS = 10;

function fmt(n: number) {
  return n.toLocaleString('ko-KR');
}

function AskRows({ levels }: { levels: PriceLevel[] }) {
  const rows = [...levels].sort((a, b) => b.price - a.price).slice(0, MAX_LEVELS);
  return (
    <>
      {rows.map((l) => (
        <tr key={l.price} className="ask-row">
          <td className="qty">{fmt(l.quantity)}</td>
          <td className="price ask-price">{fmt(l.price)}</td>
          <td className="cnt">{l.orderCount}</td>
        </tr>
      ))}
    </>
  );
}

function BidRows({ levels }: { levels: PriceLevel[] }) {
  const rows = [...levels].sort((a, b) => b.price - a.price).slice(0, MAX_LEVELS);
  return (
    <>
      {rows.map((l) => (
        <tr key={l.price} className="bid-row">
          <td className="qty">{fmt(l.quantity)}</td>
          <td className="price bid-price">{fmt(l.price)}</td>
          <td className="cnt">{l.orderCount}</td>
        </tr>
      ))}
    </>
  );
}

export function OrderBookPanel() {
  const { snapshot, connected } = useOrderBook();

  return (
    <div className="ob-container">
      <div className="ob-header">
        <span className="ob-title">호가창</span>
        <span className={`ob-status ${connected ? 'connected' : 'disconnected'}`}>
          {connected ? '● 실시간' : '○ 연결 중...'}
        </span>
      </div>

      <table className="ob-table">
        <thead>
          <tr>
            <th>수량</th>
            <th>가격</th>
            <th>건수</th>
          </tr>
        </thead>
        <tbody>
          <AskRows levels={snapshot.asks} />
          <tr className="spread-row">
            <td colSpan={3} className="spread-label">
              {snapshot.asks.length && snapshot.bids.length
                ? `스프레드 ${fmt(
                    Math.min(...snapshot.asks.map((a) => a.price)) -
                    Math.max(...snapshot.bids.map((b) => b.price))
                  )}`
                : '—'}
            </td>
          </tr>
          <BidRows levels={snapshot.bids} />
        </tbody>
      </table>
    </div>
  );
}
