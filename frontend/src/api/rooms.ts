import client from './client.ts';
import type { ChatRoom, ChatRoomMember, ChatMessage, CreateRoomRequest, PageResponse } from './types.ts';

export const roomsApi = {
  createRoom: async (request: CreateRoomRequest): Promise<ChatRoom> => {
    const { data } = await client.post<ChatRoom>('/rooms', request);
    return data;
  },

  getMyRooms: async (): Promise<ChatRoom[]> => {
    const { data } = await client.get<ChatRoom[]>('/rooms');
    return data;
  },

  getPublicRooms: async (search?: string, page = 0, size = 20): Promise<PageResponse<ChatRoom>> => {
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    params.set('page', String(page));
    params.set('size', String(size));
    const { data } = await client.get<PageResponse<ChatRoom>>(`/rooms/public?${params}`);
    return data;
  },

  getRoom: async (roomId: string): Promise<ChatRoom> => {
    const { data } = await client.get<ChatRoom>(`/rooms/${roomId}`);
    return data;
  },

  joinRoom: async (roomId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/join`);
  },

  leaveRoom: async (roomId: string): Promise<void> => {
    await client.post(`/rooms/${roomId}/leave`);
  },

  getMembers: async (roomId: string): Promise<ChatRoomMember[]> => {
    const { data } = await client.get<ChatRoomMember[]>(`/rooms/${roomId}/members`);
    return data;
  },

  getMessages: async (roomId: string, before?: string, limit = 50): Promise<ChatMessage[]> => {
    const params = new URLSearchParams();
    if (before) params.set('before', before);
    params.set('limit', String(limit));
    const { data } = await client.get<ChatMessage[]>(`/rooms/${roomId}/messages?${params}`);
    return data;
  },

  sendMessage: async (roomId: string, content: string): Promise<ChatMessage> => {
    const { data } = await client.post<ChatMessage>(`/rooms/${roomId}/messages`, { content });
    return data;
  },
};
