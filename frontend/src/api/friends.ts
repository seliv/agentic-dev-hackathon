import client from './client.ts';
import type { Friendship } from './types.ts';

export const friendsApi = {
  sendRequest: async (userId: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>('/friends/request', { userId });
    return data;
  },

  getFriends: async (): Promise<Friendship[]> => {
    const { data } = await client.get<Friendship[]>('/friends');
    return data;
  },

  getPendingRequests: async (): Promise<Friendship[]> => {
    const { data } = await client.get<Friendship[]>('/friends/requests');
    return data;
  },

  acceptRequest: async (id: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>(`/friends/${id}/accept`);
    return data;
  },

  declineRequest: async (id: number): Promise<Friendship> => {
    const { data } = await client.post<Friendship>(`/friends/${id}/decline`);
    return data;
  },

  removeFriend: async (id: number): Promise<void> => {
    await client.delete(`/friends/${id}`);
  },
};
