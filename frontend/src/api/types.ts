export interface User {
  id: number;
  email: string;
  displayName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SignUpRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface SignInRequest {
  email: string;
  password: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
