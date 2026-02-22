import apiClient from './apiClient';

export type ContentAccessLevel = 'FREE' | 'PREMIUM' | 'CREATOR';

export interface ContentItem {
  id: string;
  title: string;
  description: string;
  thumbnailUrl: string;
  mediaUrl?: string | null;
  accessLevel: ContentAccessLevel;
  userId: number;
  creatorEmail: string;
  createdAt: string;
}

const contentService = {
  async getPublicContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/auth/content/public');
    return response.data;
  },

  async getFeed(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/content/feed');
    return response.data;
  },

  async getPremiumContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/content/premium');
    return response.data;
  },

  async getContentById(id: string): Promise<ContentItem> {
    const response = await apiClient.get<ContentItem>(`/api/content/${id}`);
    return response.data;
  },

  async getMediaAccess(id: string): Promise<string> {
    const response = await apiClient.get<string>(`/api/content/${id}/access`);
    return response.data;
  },
};

export default contentService;
