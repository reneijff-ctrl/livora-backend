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
  async startOnboarding(): Promise<{ onboardingUrl: string }> {
    const response = await apiClient.post<{ onboardingUrl: string }>('/api/creator/payouts/onboard');
    return response.data;
  },

  async getAccountStatus(): Promise<StripeAccount> {
    const response = await apiClient.get<StripeAccount>('/api/creator/payouts/account');
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
