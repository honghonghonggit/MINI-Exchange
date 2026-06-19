import { useState } from 'react';
import type { OrderBookSnapshot } from '../types';
import { useStompSubscription, useStompConnected } from '../ws/StompProvider';

const EMPTY: OrderBookSnapshot = { bids: [], asks: [] };

export function useOrderBook() {
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot>(EMPTY);
  const connected = useStompConnected();

  useStompSubscription<OrderBookSnapshot>('/topic/orderbook', setSnapshot);

  return { snapshot, connected };
}
