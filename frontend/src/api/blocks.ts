import client from './client.ts';
import type { UserBlockInfo } from './types.ts';

export const blocksApi = {
  blockUser: async (userId: number): Promise<UserBlockInfo> => {
    const { data } = await client.post<UserBlockInfo>(`/users/${userId}/block`);
    return data;
  },

  unblockUser: async (userId: number): Promise<void> => {
    await client.delete(`/users/${userId}/block`);
  },

  getBlockedUsers: async (): Promise<UserBlockInfo[]> => {
    const { data } = await client.get<UserBlockInfo[]>('/users/me/blocks');
    return data;
  },
};
