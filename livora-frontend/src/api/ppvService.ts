import apiClient from './apiClient';

export interface PpvContent {
  id: string;
  title: string;
  description: string;
  price: number;
  currency: string;
  contentUrl?: string;
  active: boolean;
  createdAt: string;
}

const ppvService = {
  async getPurchaseIntent(ppvId: string): Promise<{ clientSecret: string }> {
    const response = await apiClient.post(`/ppv/${ppvId}/purchase`);
    return response.data;
  },

  async getAccessUrl(ppvId: string): Promise<{ accessUrl: string }> {
    const response = await apiClient.get(`/ppv/${ppvId}/access`);
    return response.data;
  },

  async getMyPpvContent(): Promise<PpvContent[]> {
    const token = localStorage.getItem("token");
    if (!token) return [];
    const response = await apiClient.get('/creator/ppv/mine');
    return response.data;
  },

  async createPpvContent(data: Partial<PpvContent>): Promise<PpvContent> {
    const response = await apiClient.post('/creator/ppv', data);
    return response.data;
  },

  async updatePpvContent(id: string, data: Partial<PpvContent>): Promise<PpvContent> {
    const response = await apiClient.put(`/creator/ppv/${id}`, data);
    return response.data;
  },

  async deletePpvContent(id: string): Promise<void> {
    await apiClient.delete(`/creator/ppv/${id}`);
  },
};

export default ppvService;
