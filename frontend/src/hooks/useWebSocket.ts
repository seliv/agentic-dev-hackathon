import { useRef, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { ChatMessage } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

export function useWebSocket(onMessage: (roomId: string, message: ChatMessage) => void) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  const doSubscribe = useCallback((client: Client, roomId: string) => {
    if (subscriptionsRef.current.has(roomId)) return;

    const subscription = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessageRef.current(roomId, message);
    });

    subscriptionsRef.current.set(roomId, subscription);
  }, []);

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        pendingSubscriptionsRef.current.forEach(roomId => {
          doSubscribe(stompClient, roomId);
        });
        pendingSubscriptionsRef.current.clear();
      },
    });

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      subscriptionsRef.current.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.clear();
      pendingSubscriptionsRef.current.clear();
      stompClient.deactivate();
    };
  }, [doSubscribe]);

  const subscribe = useCallback((roomId: string) => {
    const client = clientRef.current;
    if (!client?.connected) {
      pendingSubscriptionsRef.current.add(roomId);
      return;
    }

    doSubscribe(client, roomId);
  }, [doSubscribe]);

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
