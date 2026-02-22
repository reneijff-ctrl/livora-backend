import apiClient from './apiClient';

const tipService = {
  async createTip(creatorId: string, amount: number): Promise<{ message: string, tipId: string }> {
    const response = await apiClient.post('/api/tips', { creatorId, amount });
    return response.data;
  },

  async createStripeCheckout(creatorId: number, amount: number): Promise<{ checkoutUrl: string }> {
    const response = await apiClient.post('/api/payments/tip', { creatorId, amount });
    return response.data;
  },

  async sendTokenTip(roomId: string, amount: number, message?: string, clientRequestId?: string): Promise<{ viewerBalance: number, creatorBalance: number, message: string }> {
    const response = await apiClient.post('/api/tips/send', { roomId, amount, message, clientRequestId });
    return response.data;
  },

  async previewTip(creatorId: number, amount: number): Promise<{ message: string, status: string }> {
    const response = await apiClient.post('/api/tips/preview', { creatorId, amount });
    return response.data;
  },
};

export default tipService;
