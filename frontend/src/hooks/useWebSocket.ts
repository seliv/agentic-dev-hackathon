import { useRef, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { ChatMessage } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

export function useWebSocket(onMessage: (roomId: string, message: ChatMessage) => void) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }>>(new Map());

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
    });

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      subscriptionsRef.current.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.clear();
      stompClient.deactivate();
    };
  }, []);

  const subscribe = useCallback((roomId: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;

    if (subscriptionsRef.current.has(roomId)) return;

    const subscription = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessage(roomId, message);
    });

    subscriptionsRef.current.set(roomId, subscription);
  }, [onMessage]);

  const unsubscribe = useCallback((roomId: string) => {
    const sub = subscriptionsRef.current.get(roomId);
    if (sub) {
      sub.unsubscribe();
      subscriptionsRef.current.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, content: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;

    client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify({ content }),
    });
  }, []);

  return { subscribe, unsubscribe, sendMessage };
}
