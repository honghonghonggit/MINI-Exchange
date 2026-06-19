import { useState } from 'react';
import type { Trade } from '../types';
import { useStompSubscription } from '../ws/StompProvider';

const MAX_TRADES = 50;

export function useTrades() {
  const [trades, setTrades] = useState<Trade[]>([]);

  useStompSubscription<Trade[]>('/topic/trades', (incoming) => {
    setTrades(prev => [...incoming, ...prev].slice(0, MAX_TRADES));
  });

  return trades;
}
