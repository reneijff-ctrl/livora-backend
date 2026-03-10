import apiClient from './apiClient';
import { 
  adaptTokenBalance, 
  adaptTokenPackages, 
  SafeTokenBalance, 
  SafeTokenPackage 
} from "@/adapters/WalletAdapter";

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
  async getPackages(): Promise<SafeTokenPackage[]> {
    const response = await apiClient.get("/tokens/packages");
    return adaptTokenPackages(response.data);
  },

  async getBalance(): Promise<SafeTokenBalance> {
    const response = await apiClient.get("/tokens/balance");
    return adaptTokenBalance(response.data);
  },

  async createCheckoutSession(packageId: string): Promise<{ redirectUrl: string }> {
    const response = await apiClient.post<{ redirectUrl: string }>('/tokens/checkout', { packageId });
    return response.data;
  },

  async sendTip(roomId: string, amount: number, clientRequestId?: string): Promise<void> {
    await apiClient.post('/tokens/tip', { roomId, amount, clientRequestId });
  },

  async sendTipByCreatorId(creatorId: number, amount: number): Promise<void> {
    await apiClient.post('/tokens/tip', { creatorId, amount });
  }
};

export default tokenService;
