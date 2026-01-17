import apiClient from './apiClient';

export interface Badge {
  id: string;
  name: string;
  tokenCost: number;
  durationDays?: number;
}

export interface UserBadge {
  id: string;
  badge: Badge;
  expiresAt?: string;
}

const badgeService = {
  async getBadges(): Promise<Badge[]> {
    const response = await apiClient.get<Badge[]>('/api/badges');
    return response.data;
  },

  async purchaseBadge(badgeId: string): Promise<UserBadge> {
    const response = await apiClient.post<UserBadge>(`/api/badges/purchase/${badgeId}`);
    return response.data;
  },

  async getMyBadges(): Promise<UserBadge[]> {
    const response = await apiClient.get<UserBadge[]>('/api/badges/me');
    return response.data;
  }
};

export default badgeService;
