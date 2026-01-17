import apiClient from './apiClient';
import { ContentItem } from './contentService';

const adminContentService = {
  async getAllContent(): Promise<ContentItem[]> {
    const response = await apiClient.get<ContentItem[]>('/api/admin/content');
    return response.data;
  },

  async disableContent(id: string): Promise<void> {
    await apiClient.patch(`/api/admin/content/${id}/disable`);
  },

  async deleteContent(id: string): Promise<void> {
    await apiClient.delete(`/api/admin/content/${id}`);
  },
};

export default adminContentService;
