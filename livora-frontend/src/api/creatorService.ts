import apiClient from './apiClient';
import { ContentItem } from './contentService';

const creatorService = {
  async getMyContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/creator/content/mine');
    return response.data;
  },

  async createContent(data: Partial<ContentItem>): Promise<ContentItem> {
    const response = await apiClient.post<ContentItem>('/api/creator/content', data);
    return response.data;
  },

  async updateContent(id: string, data: Partial<ContentItem>): Promise<ContentItem> {
    const response = await apiClient.put<ContentItem>(`/api/creator/content/${id}`, data);
    return response.data;
  },

  async deleteContent(id: string): Promise<void> {
    await apiClient.delete(`/api/creator/content/${id}`);
  },

  async getDashboardStats(): Promise<any> {
    const response = await apiClient.get('/api/creator/dashboard/stats');
    return response.data;
  },

  async getEarningsHistory(): Promise<any[]> {
    const response = await apiClient.get('/api/creator/dashboard/earnings');
    return response.data;
  },

  async getConnectStatus(): Promise<any> {
    const response = await apiClient.get('/api/creator/connect/status');
    return response.data;
  },

  async startOnboarding(): Promise<{ redirectUrl: string }> {
    const response = await apiClient.post('/api/creator/connect/start');
    return response.data;
  },
};

export default creatorService;
