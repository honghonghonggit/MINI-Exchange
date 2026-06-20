import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE } from '../config';

// API_BASE 비어있으면 '/ws'(dev: vite 프록시), 설정되면 절대 주소(예: https://api.onrender.com/ws)
const WS_URL = `${API_BASE}/ws`;

interface StompContextValue {
  client: Client | null;
  connected: boolean;
}

const StompContext = createContext<StompContextValue>({ client: null, connected: false });

/**
 * 앱 전체가 공유하는 단일 STOMP(over SockJS) 연결.
 * 설계 결정: 호가창·체결테이프·차트가 각자 연결을 열면 WS 3개가 중복 → 하나로 공유.
 * 구독은 각 소비자 훅이 connected 상태가 되면 등록하고, 재연결 시 자동 재구독한다.
 */
export function StompProvider({ children }: { children: ReactNode }) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => { client.deactivate(); };
  }, []);

  return (
    <StompContext.Provider value={{ client: clientRef.current, connected }}>
      {children}
    </StompContext.Provider>
  );
}

/** 토픽을 구독한다. 연결되면 등록, 언마운트·재연결 시 정리/재구독. */
export function useStompSubscription<T>(topic: string, onMessage: (body: T) => void) {
  const { client, connected } = useContext(StompContext);
  const callbackRef = useRef(onMessage);
  callbackRef.current = onMessage;

  useEffect(() => {
    if (!client || !connected) return;
    const subscription = client.subscribe(topic, (msg) => {
      callbackRef.current(JSON.parse(msg.body) as T);
    });
    return () => subscription.unsubscribe();
  }, [client, connected, topic]);
}

/** 현재 WebSocket 연결 상태 */
export function useStompConnected(): boolean {
  return useContext(StompContext).connected;
}
