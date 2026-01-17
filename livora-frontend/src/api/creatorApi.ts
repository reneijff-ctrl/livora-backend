import api from './client';

export interface CreatorEarnings {
  totalEarnings: number;
  monthlyEarnings: number;
  pendingPayout: number;
  currency: string;
}

/**
 * Fetches the earnings for the currently authenticated creator.
 * @returns A promise that resolves to the creator's earnings data.
 */
export const getCreatorEarnings = async (): Promise<CreatorEarnings> => {
  const response = await api.get<CreatorEarnings>('/creator/earnings');
  return response.data;
};
