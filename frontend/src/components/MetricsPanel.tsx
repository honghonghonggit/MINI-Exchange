import { useMetrics } from '../hooks/useMetrics';
import './MetricsPanel.css';

export function MetricsPanel() {
  const m = useMetrics();

  return (
    <div className="metrics-panel">
      <div className="panel-title">시스템 메트릭</div>
      <div className="metrics-grid">
        <MetricCard label="최근 레이턴시" value={`${m.lastLatencyUs.toFixed(1)} µs`} />
        <MetricCard label="평균 레이턴시" value={`${m.avgLatencyUs.toFixed(1)} µs`} />
        <MetricCard label="TPS" value={m.tps.toFixed(1)} />
        <MetricCard label="미체결 주문" value={m.openOrderCount.toLocaleString()} />
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-card">
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  );
}
