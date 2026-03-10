import apiClient from './apiClient';

export type ContentAccessLevel = 'FREE' | 'PREMIUM' | 'CREATOR';

export interface ContentItem {
  id: string;
  title: string;
  description: string;
  thumbnailUrl: string;
  mediaUrl?: string | null;
  accessLevel: ContentAccessLevel;
  type?: 'PHOTO' | 'VIDEO' | 'CLIP';
  userId?: number;
  creatorId?: number;
  creatorEmail: string;
  status?: string;
  unlockPriceTokens?: number;
  createdAt: string;
}

const contentService = {
  async getPublicContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/auth/content/public');
    return response.data;
  },

  async getFeed(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/content/feed');
    return response.data;
  },

  async getPremiumContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/content/premium');
    return response.data;
  },

  async getContentById(id: string): Promise<ContentItem> {
    const response = await apiClient.get<ContentItem>(`/content/${id}`);
    return response.data;
  },

  async getMediaAccess(id: string): Promise<string> {
    const response = await apiClient.get<string>(`/content/${id}/access`);
    return response.data;
  },
};

export default contentService;
