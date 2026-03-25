import apiClient from './apiClient';
import { 
  UserAdminResponse, 
  FailedLogin, 
  PaymentAnomaly, 
  ChargebackHistory, 
  FraudSignal, 
  FraudEvent, 
  RiskScore,
  FraudRiskLevel
} from '../types/fraud';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const fraudService = {
  getUsersByRiskLevel: async (riskLevel: FraudRiskLevel, page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<UserAdminResponse>>('/admin/fraud/users', {
      params: { riskLevel, page, size }
    });
    return response.data;
  },

  getFailedLogins: async (page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<FailedLogin>>('/admin/fraud/failed-logins', {
      params: { page, size }
    });
    return response.data;
  },

  getPaymentAnomalies: async (page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<PaymentAnomaly>>('/admin/fraud/anomalies', {
      params: { page, size }
    });
    return response.data;
  },

  getChargebacks: async (page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<ChargebackHistory>>('/admin/fraud/chargebacks', {
      params: { page, size }
    });
    return response.data;
  },

  getSignals: async (page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<FraudSignal>>('/admin/fraud/signals', {
      params: { page, size }
    });
    return response.data;
  },

  getUserSignals: async (userId: string, page = 0, size = 20) => {
    const response = await apiClient.get<PageResponse<FraudSignal>>(`/admin/fraud/signals/${userId}`, {
      params: { page, size }
    });
    return response.data;
  },

  resolveSignal: async (id: string) => {
    await apiClient.post(`/admin/fraud/signals/${id}/resolve`);
  },

  overrideRiskLevel: async (userId: string, riskLevel: FraudRiskLevel) => {
    await apiClient.post(`/admin/fraud/users/${userId}/fraud-risk`, { riskLevel });
  },

  unblockUser: async (userId: string) => {
    await apiClient.post(`/admin/fraud/users/${userId}/unblock`);
  },

  getUserFraudHistory: async (userId: string) => {
    const response = await apiClient.get<FraudEvent[]>(`/admin/fraud/users/${userId}/events`);
    return response.data;
  },

  getUserRiskScore: async (userId: string) => {
    const response = await apiClient.get<RiskScore>(`/admin/fraud/users/${userId}/risk-score`);
    return response.data;
  }
};

export default fraudService;
