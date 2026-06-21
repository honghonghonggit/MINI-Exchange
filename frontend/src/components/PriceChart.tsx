import { useState } from 'react';
import { AreaChart, Area, CartesianGrid, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
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
  const last = prices.length ? prices[prices.length - 1] : null;
  const first = prices.length ? prices[0] : null;
  const up = last !== null && first !== null && last >= first;
  const lineColor = up ? '#3fb950' : '#ff7b72';

  return (
    <div className="price-chart">
      <div className="chart-head">
        <span className="panel-title">가격 차트</span>
        {last !== null && (
          <span className="chart-last" style={{ color: lineColor }}>
            {last.toLocaleString()}
          </span>
        )}
      </div>
      {data.length === 0 ? (
        <div className="chart-empty">체결 대기중...</div>
      ) : (
        <ResponsiveContainer width="100%" height={500}>
          <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={lineColor} stopOpacity={0.35} />
                <stop offset="100%" stopColor={lineColor} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#21262d" strokeDasharray="3 3" vertical={false} />
            <XAxis
              dataKey="time"
              tick={{ fontSize: 12, fill: '#c9d1d9', fontWeight: 600 }}
              interval="preserveStartEnd"
              minTickGap={48}
              axisLine={{ stroke: '#484f58' }}
              tickLine={false}
            />
            <YAxis
              domain={[minPrice, maxPrice]}
              tick={{ fontSize: 12, fill: '#c9d1d9', fontWeight: 600 }}
              tickFormatter={v => Math.round(v).toLocaleString()}
              width={68}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              contentStyle={{ background: '#1c2333', border: '1px solid #30363d', borderRadius: 6, fontSize: 13 }}
              labelStyle={{ color: '#9aa5b1', marginBottom: 2 }}
              itemStyle={{ color: '#f0f6fc', fontWeight: 600 }}
              formatter={(v) => [Number(v).toLocaleString(), '가격']}
            />
            <Area
              type="monotone"
              dataKey="price"
              stroke={lineColor}
              strokeWidth={2.5}
              fill="url(#priceFill)"
              dot={false}
              activeDot={{ r: 4, fill: lineColor, stroke: '#0d1117', strokeWidth: 2 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
