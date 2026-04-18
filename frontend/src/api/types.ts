export interface User {
  id: number;
  email: string;
  username: string;
  displayName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SignUpRequest {
  email: string;
  username: string;
  password: string;
  displayName: string;
}

export interface SignInRequest {
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface DeleteAccountRequest {
  password: string;
}

export interface SessionInfo {
  sessionId: string;
  createdAt: string;
  lastAccessedAt: string;
  current: boolean;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface ChatRoom {
  id: string;
  name: string;
  description: string | null;
  type: string;
  ownerId: number;
  ownerUsername: string;
  memberCount: number;
  createdAt: string;
}

export interface ChatRoomMember {
  userId: number;
  username: string;
  displayName: string | null;
  role: string;
  joinedAt: string;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string | null;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoomRequest {
  name: string;
  description?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
