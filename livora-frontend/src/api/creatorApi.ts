import apiClient from './apiClient';
import { User } from '../types';
import { ICreator } from '../domain/creator/ICreator';
import { adaptCreator } from '../adapters/CreatorAdapter';

export interface CreatorEarnings {
  todayTokens: number;
  todayRevenue: number;
  totalTokens: number;
  totalRevenue: number;
  pendingPayout: number;
  lastUpdated: string;
}

export const EARNINGS_REFRESH_INTERVAL = 60000; // 60 seconds fallback polling

/**
 * Fetches the earnings for the currently authenticated creator.
 * @returns A promise that resolves to the creator's earnings data.
 */
export const getCreatorEarnings = async (): Promise<CreatorEarnings> => {
  const response = await apiClient.get<CreatorEarnings>('/api/creator/earnings');
  return response.data;
};

export interface CreatorSettings {
  username: string;
  category: string;
  active: boolean;
}

export const getCreatorSettings = async (): Promise<CreatorSettings> => {
  const response = await apiClient.get<CreatorSettings>('/api/creator/settings');
  return response.data;
};

export const updateCreatorSettings = async (settings: CreatorSettings): Promise<CreatorSettings> => {
  const response = await apiClient.put<CreatorSettings>('/api/creator/settings', settings);
  return response.data;
};

/**
 * Onboards the current user as a creator.
 * POST /api/creator/onboard
 * @returns A promise that resolves to the updated user data.
 */
export const becomeCreator = async (): Promise<User> => {
  const response = await apiClient.post<User>('/api/creator/onboard');
  const user = response.data;

  if (user) {
    // Ensure ID is a string if backend returns a number
    if (typeof user.id === 'number') {
      user.id = (user.id as number).toString();
    }

    // Normalize role: USER -> VIEWER
    // After onboarding the role should be 'CREATOR', but we keep this for consistency 
    // with other profile fetching logic.
    if (user.role === ('USER' as any)) {
      user.role = 'VIEWER';
    }
  }

  return user;
};

export interface CreatorDashboardStats {
  totalEarnings: number;
  totalFollowers: number;
  isVerified: boolean;
  availableBalance: number;
  activeStreams: number;
  contentCount: number;
  status: string;
}

export interface CreatorDashboard {
  creatorProfile: ICreator;
  stats: CreatorDashboardStats;
}

/**
 * Fetches the dashboard data for the currently authenticated creator.
 * GET /api/creator/dashboard
 * @returns A promise that resolves to the creator's dashboard data.
 */
export const getCreatorDashboard = async (skipToast = false): Promise<CreatorDashboard> => {
  const response = await apiClient.get('/api/creator/dashboard', {
    // @ts-ignore
    _skipToast: skipToast
  });
  
  const data = response.data as any;
  return {
    creatorProfile: adaptCreator(data?.creatorProfile),
    stats: data?.stats
  };
};
