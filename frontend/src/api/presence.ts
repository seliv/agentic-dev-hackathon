import client from './client.ts';
import type { PresenceEvent } from './types.ts';

export const presenceApi = {
  getRoomMemberPresence: async (roomId: string): Promise<PresenceEvent[]> => {
    const { data } = await client.get<PresenceEvent[]>(`/rooms/${roomId}/members/presence`);
    return data;
  },
};
