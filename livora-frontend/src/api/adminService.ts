import apiClient from './apiClient';
import { ICreator } from '../domain/creator/ICreator';
import { adaptCreator } from '../adapters/CreatorAdapter';
import { MediasoupStats, FraudDashboardMetrics, FraudSignal, StreamRiskStatus } from '../types';

export interface AdminCreator extends ICreator {
  email: string;
}

export interface CreatorApplication {
  id: number;
  userId: number;
  username: string;
  email: string;
  status: string;
  termsAccepted: boolean;
  ageVerified: boolean;
  submittedAt: string;
  approvedAt?: string;
  reviewNotes?: string;
}

export interface AdminUser {
  id: number;
  email: string;
  username: string;
  adminRole?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const adminService = {
  getCreators: async (page = 0, size = 20): Promise<PageResponse<AdminCreator>> => {
    const response = await apiClient.get<PageResponse<any>>(`/admin/creators?page=${page}&size=${size}`);
    return {
      ...response.data,
      content: response.data.content.map((raw: any) => ({
        ...adaptCreator(raw),
        email: raw.email
      }))
    };
  },

  updateCreatorStatus: async (userId: number, status: string): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/status`, { status });
  },

  getApplications: async (page = 0, size = 20, status?: string): Promise<PageResponse<CreatorApplication>> => {
    let url = `/admin/creator-applications?page=${page}&size=${size}`;
    if (status) {
      url += `&status=${status}`;
    }
    const response = await apiClient.get<PageResponse<CreatorApplication>>(url);
    return response.data;
  },

  approveApplication: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/creator-applications/${id}/approve`);
  },

  rejectApplication: async (id: number, reviewNotes: string): Promise<void> => {
    await apiClient.post(`/admin/creator-applications/${id}/reject`, { reviewNotes });
  },

  /**
   * Creator Verification Moderation
   */
  getCreatorVerifications: async (status?: string, page = 0, size = 20): Promise<any> => {
    try {
      const params = new URLSearchParams();
      if (status) params.append('status', status);
      params.append('page', page.toString());
      params.append('size', size.toString());
      
      const response = await apiClient.get(`/admin/creator-verifications?${params.toString()}`);
      return response.data || { content: [], totalElements: 0, totalPages: 0 };
    } catch (err) {
      console.warn('Failed to load verifications, returning empty state', err);
      return { content: [], totalElements: 0, totalPages: 0 };
    }
  },

  getCreatorVerification: async (id: number): Promise<any> => {
    const response = await apiClient.get(`/admin/creator-verifications/${id}`);
    return response.data;
  },

  approveVerification: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/creator-verifications/${id}/approve`);
  },

  rejectVerification: async (id: number, reason: string): Promise<void> => {
    await apiClient.post(`/admin/creator-verifications/${id}/reject`, { reason });
  },

  getStreams: async (page = 0, size = 20): Promise<PageResponse<any>> => {
    const response = await apiClient.get<PageResponse<any>>(`/admin/streams?page=${page}&size=${size}`);
    return response.data;
  },

  stopStream: async (streamId: string): Promise<void> => {
    await apiClient.post(`/admin/streams/${streamId}/stop`);
  },

  enableSlowMode: async (roomId: string): Promise<void> => {
    await apiClient.post(`/admin/rooms/${roomId}/enable-slow-mode`);
  },

  disableSlowMode: async (roomId: string): Promise<void> => {
    await apiClient.post(`/admin/rooms/${roomId}/disable-slow-mode`);
  },

  muteUser: async (streamId: string, userId: number): Promise<void> => {
    await apiClient.post('/stream/moderation/mute', { streamId, userId });
  },

  kickUser: async (streamId: string, userId: number): Promise<void> => {
    await apiClient.post('/stream/moderation/kick', { streamId, userId });
  },

  getHealth: async (): Promise<any> => {
    try {
      const response = await apiClient.get('/admin/health', { _skipToast: true });
      return response.data;
    } catch (e) {
      console.warn('[HEALTH] request failed', e);
      return null;
    }
  },

  getSystemHealth: async (): Promise<any> => {
    const response = await apiClient.get('/admin/system-health');
    return response.data;
  },

  getActivity: async (): Promise<any[]> => {
    const response = await apiClient.get('/admin/activity');
    return response.data;
  },
  
  getDashboardData: async (): Promise<any> => {
    const response = await apiClient.get('/admin/dashboard');
    return response.data;
  },

  getDashboardCharts: async (): Promise<any> => {
    const response = await apiClient.get('/admin/dashboard/charts');
    return response.data;
  },

  getDashboardMetrics: async (): Promise<any> => {
    const response = await apiClient.get('/admin/dashboard/metrics');
    return response.data;
  },

  getMediasoupStats: async (): Promise<MediasoupStats> => {
    const response = await apiClient.get<MediasoupStats>('/admin/system/mediasoup/stats');
    return response.data;
  },

  getFraudDashboard: async (): Promise<FraudDashboardMetrics> => {
    const response = await apiClient.get<FraudDashboardMetrics>('/admin/fraud/dashboard');
    return response.data;
  },

  getFraudSignals: async (params: { resolved?: boolean; riskLevel?: string; type?: string; page?: number; size?: number } = {}): Promise<PageResponse<FraudSignal>> => {
    const queryParams = new URLSearchParams();
    if (params.resolved !== undefined) queryParams.append('resolved', params.resolved.toString());
    if (params.riskLevel) queryParams.append('riskLevel', params.riskLevel);
    if (params.type) queryParams.append('type', params.type);
    if (params.page !== undefined) queryParams.append('page', params.page.toString());
    if (params.size !== undefined) queryParams.append('size', params.size.toString());
    
    const response = await apiClient.get<PageResponse<FraudSignal>>(`/admin/fraud/signals?${queryParams.toString()}`);
    return response.data;
  },

  resolveFraudSignal: async (id: string, reason: string): Promise<void> => {
    await apiClient.post(`/admin/fraud/signals/${id}/resolve`, { reason });
  },
  
  getStreamRisks: async (): Promise<StreamRiskStatus[]> => {
    const response = await apiClient.get<StreamRiskStatus[]>('/admin/streams/risk');
    return response.data;
  },

  getStreamRisk: async (): Promise<any[]> => {
    const response = await apiClient.get<any[]>('/admin/streams/risk');
    return response.data;
  },

  getActiveStreams: async (): Promise<any[]> => {
    const response = await apiClient.get('/admin/streams/active');
    return response.data?.content ?? [];
  },

  getRecentAuditEvents: async (type = 'CREATOR', limit = 20): Promise<any[]> => {
    try {
      const response = await apiClient.get(`/admin/audit/recent?type=${type}&limit=${limit}`, { _skipToast: true });
      return response.data;
    } catch (e) {
      console.warn('[AUDIT] failed to fetch recent events', e);
      return [];
    }
  },

  // -------------------------------------------------------------------------
  // Unified creator lifecycle API
  // -------------------------------------------------------------------------

  getUnifiedCreators: async (params: { status?: string; search?: string; page?: number; size?: number } = {}): Promise<PageResponse<any>> => {
    const q = new URLSearchParams();
    if (params.status) q.append('status', params.status);
    if (params.search) q.append('search', params.search);
    q.append('page', String(params.page ?? 0));
    q.append('size', String(params.size ?? 20));
    const response = await apiClient.get(`/admin/creators?${q.toString()}`);
    return response.data;
  },

  getUnifiedCreator: async (userId: number): Promise<any> => {
    const response = await apiClient.get(`/admin/creators/${userId}`);
    return response.data;
  },

  getApplicationQueue: async (): Promise<any[]> => {
    const response = await apiClient.get('/admin/creators/queue/applications');
    return response.data;
  },

  getVerificationQueue: async (): Promise<any[]> => {
    const response = await apiClient.get('/admin/creators/queue/verifications');
    return response.data;
  },

  approveCreatorApplication: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/approve-application`);
  },

  rejectCreatorApplication: async (userId: number, reason: string): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/reject-application`, { reason });
  },

  approveCreatorVerification: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/approve-verification`);
  },

  rejectCreatorVerification: async (userId: number, reason: string): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/reject-verification`, { reason });
  },

  suspendCreator: async (userId: number, reason: string): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/suspend`, { reason });
  },

  unsuspendCreator: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/unsuspend`);
  },

  approveCreator: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/approve`);
  },

  rejectCreator: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/reject`);
  },

  activateCreator: async (userId: number): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/activate`);
  },

  suspendCreatorProfile: async (userId: number, reason: string): Promise<void> => {
    await apiClient.post(`/admin/creators/${userId}/suspend-profile`, { reason });
  },
  getAdmins: async (): Promise<AdminUser[]> => {
    const response = await apiClient.get<AdminUser[]>('/admin/admins');
    return response.data;
  },
  updateAdminRole: async (id: number, adminRole: string): Promise<AdminUser> => {
    const response = await apiClient.put<AdminUser>(`/admin/admins/${id}/role`, { adminRole });
    return response.data;
  },
};

export default adminService;

export const getCreatorVerifications = adminService.getCreatorVerifications;
