import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { Trade } from '../types';

const MAX_TRADES = 50;

export function useTrades() {
  const [trades, setTrades] = useState<Trade[]>([]);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe('/topic/trades', (msg) => {
          const incoming: Trade[] = JSON.parse(msg.body);
          setTrades(prev => [...incoming, ...prev].slice(0, MAX_TRADES));
        });
      },
    });

    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); };
  }, []);

  return trades;
}
