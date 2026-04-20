import { useState, useEffect, useCallback } from 'react';
import { unreadApi } from '../api/unread.ts';

export function useUnread() {
  const [unreadCounts, setUnreadCounts] = useState<Map<string, number>>(new Map());

  useEffect(() => {
    unreadApi.getUnreadCounts()
      .then(counts => {
        const map = new Map<string, number>();
        counts.forEach(c => map.set(c.roomId, c.count));
        setUnreadCounts(map);
      })
      .catch(console.error);
  }, []);

  const incrementUnread = useCallback((roomId: string) => {
    setUnreadCounts(prev => {
      const next = new Map(prev);
      next.set(roomId, (next.get(roomId) || 0) + 1);
      return next;
    });
  }, []);

  const markAsRead = useCallback(async (roomId: string, lastMessageId: string) => {
    setUnreadCounts(prev => {
      const next = new Map(prev);
      next.delete(roomId);
      return next;
    });
    try {
      await unreadApi.markAsRead(roomId, lastMessageId);
    } catch (err) {
      console.error('Failed to mark as read', err);
    }
  }, []);

  const getUnreadCount = useCallback((roomId: string): number => {
    return unreadCounts.get(roomId) || 0;
  }, [unreadCounts]);

  return { unreadCounts, getUnreadCount, incrementUnread, markAsRead };
}
