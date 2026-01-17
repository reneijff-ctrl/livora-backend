import apiClient from './apiClient';

const tipService = {
  async createTipIntent(creatorId: string, amount: number, message?: string): Promise<{ clientSecret: string }> {
    const response = await apiClient.post('/api/tips', { creatorId, amount, message });
    return response.data;
  },
};

export default tipService;
