import apiClient from './apiClient';

export interface StripeAccount {
  stripeAccountId: string;
  onboardingCompleted: boolean;
  chargesEnabled: boolean;
  payoutsEnabled: boolean;
}

export interface Payout {
  id: string;
  tokenAmount: number;
  eurAmount: number;
  status: 'PENDING' | 'PAID' | 'FAILED';
  stripeTransferId: string | null;
  errorMessage: string | null;
  createdAt: string;
}

const payoutService = {
  async startOnboarding(): Promise<{ redirectUrl: string }> {
    const response = await apiClient.post<{ redirectUrl: string }>('/api/creator/connect/start');
    return response.data;
  },

  async getAccountStatus(): Promise<StripeAccount> {
    const response = await apiClient.get<StripeAccount>('/api/creator/payouts/account');
    return response.data;
  },

  async requestPayout(tokens: number): Promise<Payout> {
    const response = await apiClient.post<Payout>('/api/creator/payouts/request', { tokens });
    return response.data;
  },

  async getPayoutHistory(): Promise<Payout[]> {
    const response = await apiClient.get<Payout[]>('/api/creator/payouts/history');
    return response.data;
  },

  async getAllPayoutsAdmin(): Promise<Payout[]> {
    const response = await apiClient.get<Payout[]>('/api/admin/payouts');
    return response.data;
  }
};

export default payoutService;
