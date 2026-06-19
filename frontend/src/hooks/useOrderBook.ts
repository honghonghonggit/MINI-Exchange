import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { OrderBookSnapshot } from '../types';

const EMPTY: OrderBookSnapshot = { bids: [], asks: [] };
const WS_URL = 'http://localhost:8080/ws';

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
