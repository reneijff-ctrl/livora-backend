import apiClient from './apiClient';
import { User } from '../types';

/**
 * UserService provides methods for user-related operations.
 */
export const getUserInfo = async (): Promise<Partial<User>> => {
  const response = await apiClient.get<Partial<User>>('/user/me');
  return response.data;
};

const UserService = {
  getUserInfo,
};

export default UserService;
