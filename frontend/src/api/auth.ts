import client from './client.ts';
import type { User, SignUpRequest, SignInRequest } from './types.ts';

export const authApi = {
  signUp: async (data: SignUpRequest): Promise<User> => {
    const response = await client.post<User>('/users/signup', data);
    return response.data;
  },

  signIn: async (data: SignInRequest): Promise<User> => {
    const response = await client.post<User>('/auth/signin', data);
    return response.data;
  },

  logout: async (): Promise<void> => {
    await client.post('/auth/logout');
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await client.get<User>('/auth/me');
    return response.data;
  },
};
