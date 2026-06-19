import { OrderBookPanel } from './components/OrderBookPanel';
import './App.css';

export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <h1>미니 거래소 <span className="sim-badge">시뮬레이터</span></h1>
        <p className="disclaimer">실거래·실자금 없음 — 교육 목적 시뮬레이션</p>
      </header>
      <main className="app-main">
        <OrderBookPanel />
      </main>
    </div>
  );
}
