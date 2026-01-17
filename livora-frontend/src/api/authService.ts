import apiClient from './apiClient';

/**
 * Role types as defined in the backend.
 */
export type Role = 'USER' | 'PREMIUM' | 'ADMIN' | 'CREATOR';

/**
 * User interface.
 */
export interface User {
  id: string;
  email: string;
  role: Role;
  subscription: {
    status: 'NONE' | 'FREE' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED';
    renewalDate: string | null;
  };
}

/**
 * Response for the /auth/me endpoint.
 */
export interface UserResponse extends User {}

/**
 * Request for login.
 */
export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Request for register.
 */
export interface RegisterRequest {
  email: string;
  password: string;
}

/**
 * Response for auth actions (login, refresh).
 */
export interface AuthResponse {
  accessToken: string;
  message: string;
}

/**
 * Authentication API service.
 * 
 * Provides methods for:
 * - Login
 * - Registration
 * - Token Refresh
 * - Logout
 * - Get current user info
 */
const authService = {
  /**
   * Performs login.
   * Credentials (access/refresh tokens) are handled via HTTP-only cookies by the backend.
   */
  async login(data: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', data);
    return response.data;
  },

  /**
   * Registers a new user.
   */
  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/register', data);
    return response.data;
  },

  /**
   * Refreshes the authentication tokens.
   * Expects the refreshToken cookie to be present.
   */
  async refresh(): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/refresh');
    return response.data;
  },

  /**
   * Logs out the user and clears authentication cookies.
   */
  async logout(): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/logout');
    return response.data;
  },

  /**
   * Retrieves the current authenticated user's information.
   */
  async getMe(): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/auth/me');
    return response.data;
  },
};

export default authService;
