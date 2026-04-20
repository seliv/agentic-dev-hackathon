import client from './client.ts';
import type { RoomBan, ChatRoomMember } from './types.ts';

export const moderationApi = {
  banUser: async (roomId: string, userId: number, reason?: string): Promise<RoomBan> => {
    const { data } = await client.post<RoomBan>(`/rooms/${roomId}/bans`, { userId, reason });
    return data;
  },

  unbanUser: async (roomId: string, userId: number): Promise<void> => {
    await client.delete(`/rooms/${roomId}/bans/${userId}`);
  },

  getBannedUsers: async (roomId: string): Promise<RoomBan[]> => {
    const { data } = await client.get<RoomBan[]>(`/rooms/${roomId}/bans`);
    return data;
  },

  changeRole: async (roomId: string, userId: number, role: string): Promise<ChatRoomMember> => {
    const { data } = await client.put<ChatRoomMember>(`/rooms/${roomId}/members/${userId}/role`, { role });
    return data;
  },

  kickMember: async (roomId: string, userId: number): Promise<void> => {
    await client.delete(`/rooms/${roomId}/members/${userId}`);
  },
};
