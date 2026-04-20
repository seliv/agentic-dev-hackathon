import { useRef, useCallback, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { ChatMessage, PresenceEvent, MessageEvent } from '../api/types.ts';

const WS_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/api$/, '').replace(/^http/, 'ws') + '/ws';

interface UseWebSocketOptions {
  onMessage: (roomId: string, message: ChatMessage) => void;
  onPresence?: (event: PresenceEvent) => void;
  onEvent?: (roomId: string, event: MessageEvent) => void;
}

export function useWebSocket({ onMessage, onPresence, onEvent }: UseWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }[]>>(new Map());
  const pendingSubscriptionsRef = useRef<Set<string>>(new Set());
  const onMessageRef = useRef(onMessage);
  const onPresenceRef = useRef(onPresence);
  const onEventRef = useRef(onEvent);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  onMessageRef.current = onMessage;
  onPresenceRef.current = onPresence;
  onEventRef.current = onEvent;

  const doSubscribe = useCallback((client: Client, roomId: string) => {
    if (subscriptionsRef.current.has(roomId)) return;

    const messageSub = client.subscribe(`/topic/rooms/${roomId}/messages`, (msg: IMessage) => {
      const message: ChatMessage = JSON.parse(msg.body);
      onMessageRef.current(roomId, message);
    });

    const eventSub = client.subscribe(`/topic/rooms/${roomId}/events`, (msg: IMessage) => {
      const event: MessageEvent = JSON.parse(msg.body);
      onEventRef.current?.(roomId, event);
    });

    subscriptionsRef.current.set(roomId, [messageSub, eventSub]);
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

        stompClient.subscribe('/topic/presence', (msg: IMessage) => {
          const event: PresenceEvent = JSON.parse(msg.body);
          onPresenceRef.current?.(event);
        });

        heartbeatRef.current = setInterval(() => {
          if (stompClient.connected) {
            stompClient.publish({ destination: '/app/presence/heartbeat', body: '{}' });
          }
        }, 15000);
      },
      onDisconnect: () => {
        if (heartbeatRef.current) {
          clearInterval(heartbeatRef.current);
          heartbeatRef.current = null;
        }
      },
    });

    const handleVisibilityChange = () => {
      if (!stompClient.connected) return;
      const status = document.hidden ? 'AFK' : 'ACTIVE';
      stompClient.publish({
        destination: '/app/presence/status',
        body: JSON.stringify({ status }),
      });
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    stompClient.activate();
    clientRef.current = stompClient;

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
      }
      subscriptionsRef.current.forEach(subs => subs.forEach(sub => sub.unsubscribe()));
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
    const subs = subscriptionsRef.current.get(roomId);
    if (subs) {
      subs.forEach(sub => sub.unsubscribe());
      subscriptionsRef.current.delete(roomId);
    }
  }, []);

  const sendMessage = useCallback((roomId: string, content: string, replyToId?: string) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify({ content, replyToId }),
    });
  }, []);

  return { subscribe, unsubscribe, sendMessage };
}
