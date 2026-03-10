import apiClient from './apiClient';

const tipService = {
  async createTip(creatorId: string, amount: number): Promise<{ message: string, tipId: string }> {
    const response = await apiClient.post('/tips', { creatorId, amount });
    return response.data;
  },

  async createStripeCheckout(creatorId: number, amount: number): Promise<{ checkoutUrl: string }> {
    const response = await apiClient.post('/payments/tip', { creatorId, amount });
    return response.data;
  },

  async sendTokenTip(roomId: string, amount: number, message?: string, clientRequestId?: string): Promise<{ viewerBalance: number, creatorBalance: number, message: string }> {
    const response = await apiClient.post('/tips/send', { roomId, amount, message, clientRequestId });
    return response.data;
  },

  async previewTip(creatorId: number, amount: number): Promise<{ message: string, status: string }> {
    const response = await apiClient.post('/tips/preview', { creatorId, amount });
    return response.data;
  },
};

export default tipService;
