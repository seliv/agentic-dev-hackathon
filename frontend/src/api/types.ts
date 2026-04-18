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
