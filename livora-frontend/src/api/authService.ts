import apiClient from './apiClient';
import { LoginResponse, User } from '../types';
import { setAccessToken, setRefreshToken, clearTokens } from '../auth/jwt';
import { adaptCreator } from '../adapters/CreatorAdapter';

/**
 * AuthService provides methods for authentication and user session management.
 * It uses the centralized Axios instance and stores the JWT in localStorage.
 */

export const login = async (email: string, password: string): Promise<LoginResponse> => {
  const response = await apiClient.post<LoginResponse>('/auth/login', { email, password });
  if (response.data) {
    if (response.data.token) {
      setAccessToken(response.data.token);
    }
    if (response.data.refreshToken) {
      setRefreshToken(response.data.refreshToken);
    }
  }
  return response.data;
};

export const logout = async (): Promise<void> => {
  try {
    // Attempt to notify backend to clear refresh token cookie
    await apiClient.post('/auth/logout');
  } catch (error) {
    console.warn('Backend logout failed or session already expired', error);
  } finally {
    // Always clear local storage tokens
    clearTokens();
  }
};

export const getCurrentUser = async (): Promise<User> => {
  const response = await apiClient.get<User>('/auth/me');
  const user = response.data;
  
  if (user) {
    // Ensure ID is a string if backend returns a number
    if (typeof user.id === 'number') {
      user.id = (user.id as number).toString();
    }
    
    // Normalize role: USER -> VIEWER
    if (user.role === ('USER' as any)) {
      user.role = 'VIEWER';
    }

    // Adapt creator profile if present
    if (user.creatorProfile) {
      user.creatorProfile = adaptCreator(user.creatorProfile);
    }
  }
  
  return user;
};

export const register = async (data: { email: string; password: string }): Promise<{ message: string }> => {
  const response = await apiClient.post<{ message: string }>('/auth/register', data);
  return response.data;
};

export const verifyEmail = async (token: string): Promise<{ message: string }> => {
  const response = await apiClient.post<{ message: string }>(`/auth/verify-email?token=${token}`);
  return response.data;
};

export const resendVerification = async (): Promise<{ message: string }> => {
  const response = await apiClient.post<{ message: string }>('/auth/resend-verification');
  return response.data;
};

export const refreshToken = async (token: string): Promise<{ accessToken: string; refreshToken: string }> => {
  const response = await apiClient.post<{ accessToken: string; refreshToken: string }>('/auth/refresh', { refreshToken: token });
  if (response.data) {
    setAccessToken(response.data.accessToken);
    if (response.data.refreshToken) {
      setRefreshToken(response.data.refreshToken);
    }
  }
  return response.data;
};

const AuthService = {
  login,
  logout,
  getCurrentUser,
  register,
  verifyEmail,
  resendVerification,
  refreshToken,
};

export default AuthService;
