import apiClient from './apiClient';

export interface TokenPackage {
  id: string;
  name: string;
  tokenAmount: number;
  price: number;
  currency: string;
}

export interface TokenBalance {
  balance: number;
}

const tokenService = {
  async getPackages(): Promise<TokenPackage[]> {
    const response = await apiClient.get<TokenPackage[]>('/api/tokens/packages');
    return response.data;
  },

  async getBalance(): Promise<TokenBalance> {
    const response = await apiClient.get<TokenBalance>('/api/tokens/balance');
    return response.data;
  },

  async createCheckoutSession(packageId: string): Promise<{ redirectUrl: string }> {
    const response = await apiClient.post<{ redirectUrl: string }>('/api/tokens/checkout', { packageId });
    return response.data;
  },

  async sendTip(roomId: string, amount: number, clientRequestId?: string): Promise<void> {
    await apiClient.post('/api/tokens/tip', { roomId, amount, clientRequestId });
  },

  async sendTipByCreatorId(creatorId: number, amount: number): Promise<void> {
    await apiClient.post('/api/tokens/tip', { creatorId, amount });
  }
};

export default tokenService;
