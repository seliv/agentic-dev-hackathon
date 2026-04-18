import client from './client.ts';
import type { ChangePasswordRequest, DeleteAccountRequest, SessionInfo } from './types.ts';

export const usersApi = {
  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await client.put('/users/me/password', data);
  },

  deleteAccount: async (data: DeleteAccountRequest): Promise<void> => {
    await client.delete('/users/me', { data });
  },

  getSessions: async (): Promise<SessionInfo[]> => {
    const response = await client.get<SessionInfo[]>('/users/me/sessions');
    return response.data;
  },

  terminateSession: async (sessionId: string): Promise<void> => {
    await client.post(`/users/me/sessions/${sessionId}/invalidate`);
  },
};
