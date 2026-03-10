import apiClient from './apiClient';
import { ContentItem } from './contentService';

const adminContentService = {
  async getAllContent(page: number = 0, size: number = 20): Promise<{ content: ContentItem[], totalPages: number, totalElements: number }> {
    const response = await apiClient.get<any>(`/admin/content?page=${page}&size=${size}`);
    return response.data;
  },

  async disableContent(id: string): Promise<void> {
    await apiClient.patch(`/admin/content/${id}/disable`);
  },

  async deleteContent(id: string): Promise<void> {
    await apiClient.delete(`/admin/content/${id}`);
  },
};

export default adminContentService;
