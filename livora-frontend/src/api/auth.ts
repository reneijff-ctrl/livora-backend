import api from './client';

export interface User {
  id: number;
  email: string;
  role: string;
}

export const login = async (email: string, password: string) => {
  const response = await api.post('/auth/login', {
    email,
    password,
  });
  return response.data;
};

export const getMe = async (): Promise<User> => {
  const response = await api.get('/auth/me');
  return response.data;
};
