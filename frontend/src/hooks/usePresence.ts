import { useState, useCallback } from 'react';
import type { PresenceStatus, PresenceEvent } from '../api/types.ts';

export function usePresence() {
  const [presenceMap, setPresenceMap] = useState<Map<number, PresenceStatus>>(new Map());

  const handlePresenceEvent = useCallback((event: PresenceEvent) => {
    setPresenceMap(prev => {
      const next = new Map(prev);
      next.set(event.userId, event.status);
      return next;
    });
  }, []);

  const getPresence = useCallback((userId: number): PresenceStatus => {
    return presenceMap.get(userId) || 'OFFLINE';
  }, [presenceMap]);

  return { presenceMap, getPresence, handlePresenceEvent };
}
