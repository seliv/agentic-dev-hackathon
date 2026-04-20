import client from './client.ts';
import type { UnreadCount } from './types.ts';

export const unreadApi = {
  getUnreadCounts: async (): Promise<UnreadCount[]> => {
    const { data } = await client.get<UnreadCount[]>('/rooms/unread');
    return data;
  },

  markAsRead: async (roomId: string, lastReadMessageId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/read`, { lastReadMessageId });
  },
};
