import client from './client.ts';
import type { ChatRoom } from './types.ts';

export const directMessagesApi = {
  getOrCreateDMRoom: async (userId: number): Promise<ChatRoom> => {
    const { data } = await client.post<ChatRoom>(`/direct-messages/${userId}`);
    return data;
  },
};
