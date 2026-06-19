import { useState } from 'react';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import type { Trade } from '../types';
import { useStompSubscription } from '../ws/StompProvider';
import './PriceChart.css';

interface PricePoint { time: string; price: number; }

const MAX_POINTS = 60;

export function PriceChart() {
  const [data, setData] = useState<PricePoint[]>([]);

  useStompSubscription<Trade[]>('/topic/trades', (trades) => {
    if (trades.length === 0) return;
    const point: PricePoint = {
      time: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      price: trades[0].price,
    };
    setData(prev => [...prev, point].slice(-MAX_POINTS));
  });

  const prices = data.map(d => d.price);
  const minPrice = prices.length ? Math.min(...prices) * 0.999 : 0;
  const maxPrice = prices.length ? Math.max(...prices) * 1.001 : 100;

  return (
    <div className="price-chart">
      <div className="panel-title">가격 차트</div>
      {data.length === 0 ? (
        <div className="chart-empty">체결 대기중...</div>
      ) : (
        <ResponsiveContainer width="100%" height={180}>
          <LineChart data={data}>
            <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#6b7280' }} interval="preserveStartEnd" />
            <YAxis
              domain={[minPrice, maxPrice]}
              tick={{ fontSize: 10, fill: '#6b7280' }}
              tickFormatter={v => v.toLocaleString()}
              width={70}
            />
            <Tooltip
              contentStyle={{ background: '#161b22', border: '1px solid #30363d', fontSize: 12 }}
              labelStyle={{ color: '#8b949e' }}
              formatter={(v) => [Number(v).toLocaleString(), '가격']}
            />
            <Line
              type="monotone"
              dataKey="price"
              stroke="#3b82f6"
              dot={false}
              strokeWidth={1.5}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
