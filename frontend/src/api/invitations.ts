import client from './client.ts';
import type { RoomInvitation } from './types.ts';

export const invitationsApi = {
  inviteToRoom: async (roomId: string, userId: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/rooms/${roomId}/invitations`, { userId });
    return data;
  },

  getMyInvitations: async (): Promise<RoomInvitation[]> => {
    const { data } = await client.get<RoomInvitation[]>('/users/me/invitations');
    return data;
  },

  acceptInvitation: async (id: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/invitations/${id}/accept`);
    return data;
  },

  declineInvitation: async (id: number): Promise<RoomInvitation> => {
    const { data } = await client.post<RoomInvitation>(`/invitations/${id}/decline`);
    return data;
  },
};
