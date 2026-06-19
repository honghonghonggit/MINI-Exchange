import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { OrderBookSnapshot } from '../types';

const EMPTY: OrderBookSnapshot = { bids: [], asks: [] };
// 상대 경로 → dev는 vite 프록시, prod는 동일 오리진으로 백엔드 연결
const WS_URL = '/ws';

export function useOrderBook() {
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot>(EMPTY);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/orderbook', (msg) => {
          setSnapshot(JSON.parse(msg.body));
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => { client.deactivate(); };
  }, []);

  return { snapshot, connected };
}
