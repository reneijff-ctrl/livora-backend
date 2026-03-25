import apiClient from './apiClient';

export interface DashboardSummary {
  role: string;
  emailVerified: boolean;
  trustScore: number;
  status: string;
  createdAt: string;
}

export interface CreatorDashboard {
  totalEarnings: number;
  availableBalance: number;
  activeStreams: number;
  totalSubscribers: number;
  contentCount: number;
}

export interface CreatorDashboardSummary {
  id: number;
  displayName: string;
  totalEarnings: number;
  accountStatus: string;
}

export interface ViewerDashboard {
  tokenBalance: number;
  activeSubscriptions: number;
  followedCreators: number;
  totalSpent: number;
  recentPurchases?: any[];
}

/**
 * Fetches a general summary for the user's dashboard.
 * Calls the backend once and returns structured JSON data.
 */
export const getDashboardSummary = async (): Promise<DashboardSummary> => {
  const response = await apiClient.get<DashboardSummary>('/dashboard/summary');
  return response.data;
};

/**
 * Fetches data specifically for the creator dashboard.
 * Calls the backend once and returns structured JSON data.
 */
export const getCreatorDashboard = async (): Promise<CreatorDashboard> => {
  const response = await apiClient.get<CreatorDashboard>('/dashboard/creator');
  return response.data;
};

/**
 * Fetches the basic summary for the creator dashboard.
 */
export const getCreatorDashboardSummary = async (): Promise<CreatorDashboardSummary> => {
  const response = await apiClient.get<CreatorDashboardSummary>('/creator/dashboard/summary');
  return response.data;
};

/**
 * Fetches data specifically for the viewer dashboard.
 */
export const getViewerDashboard = async (): Promise<ViewerDashboard> => {
  const response = await apiClient.get<ViewerDashboard>('/viewer/dashboard');
  return response.data;
};
