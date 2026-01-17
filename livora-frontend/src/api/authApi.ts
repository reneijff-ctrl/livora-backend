import api from './api';

export interface User {
  id: number;
  email: string;
  role: string;
}

export interface LoginResponse {
  accessToken: string;
  user: User;
}

export interface RegisterResponse {
  message: string;
}

/**
 * Performs a login request.
 * @param email The user's email
 * @param password The user's password
 * @returns A promise that resolves to the login response containing the access token and user info
 */
export const login = async (email: string, password: string): Promise<LoginResponse> => {
  const response = await api.post<LoginResponse>('/auth/login', {
    email,
    password,
  });
  return response.data;
};

/**
 * Performs a registration request.
 * @param email The user's email
 * @param password The user's password
 * @returns A promise that resolves to the registration response
 */
export const register = async (email: string, password: string): Promise<RegisterResponse> => {
  const response = await api.post<RegisterResponse>('/auth/register', {
    email,
    password,
  });
  return response.data;
};
