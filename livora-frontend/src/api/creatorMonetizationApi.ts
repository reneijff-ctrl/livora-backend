import apiClient from './apiClient';
import { CreatorMonetization } from '../types';

/**
 * Fetches the monetization settings and status for the currently authenticated creator.
 */
export const getMyMonetization = async (): Promise<CreatorMonetization> => {
  const response = await apiClient.get<CreatorMonetization>('/creator/monetization/me');
  return response.data;
};
