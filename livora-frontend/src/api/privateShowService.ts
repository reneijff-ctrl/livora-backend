import apiClient from './apiClient';

export enum PrivateSessionStatus {
  REQUESTED = 'REQUESTED',
  ACCEPTED = 'ACCEPTED',
  REJECTED = 'REJECTED',
  ACTIVE = 'ACTIVE',
  ENDED = 'ENDED',
}

export interface PrivateSession {
  id: string;
  viewerId: number;
  creatorId: number;
  pricePerMinute: number;
  status: PrivateSessionStatus;
  startedAt?: string;
  endedAt?: string;
}

export enum SpySessionStatus {
  ACTIVE = 'ACTIVE',
  ENDED = 'ENDED',
}

export interface PrivateSpySession {
  id: string;
  privateSessionId: string;
  spyViewerId: number;
  spyPricePerMinute: number;
  status: SpySessionStatus;
  startedAt?: string;
  endedAt?: string;
}

const privateShowService = {
  async requestPrivateShow(userId: number, pricePerMinute: number): Promise<PrivateSession> {
    const response = await apiClient.post<PrivateSession>('/private-show/request', {
      userId,
      pricePerMinute,
    });
    return response.data;
  },

  async getSession(sessionId: string): Promise<PrivateSession> {
    const response = await apiClient.get<PrivateSession>(`/private-show/${sessionId}`);
    return response.data;
  },

  async acceptRequest(sessionId: string): Promise<PrivateSession> {
    const response = await apiClient.post<PrivateSession>(`/private-show/${sessionId}/accept`);
    return response.data;
  },

  async rejectRequest(sessionId: string): Promise<void> {
    await apiClient.post(`/private-show/${sessionId}/reject`);
  },

  async startSession(sessionId: string): Promise<PrivateSession> {
    const response = await apiClient.post<PrivateSession>(`/private-show/${sessionId}/start`);
    return response.data;
  },

  async endSession(sessionId: string): Promise<PrivateSession> {
    const response = await apiClient.post<PrivateSession>(`/private-show/${sessionId}/end`);
    return response.data;
  },

  // Spy endpoints
  async joinAsSpy(sessionId: string): Promise<PrivateSpySession> {
    const response = await apiClient.post<PrivateSpySession>(`/private-show/${sessionId}/spy`);
    return response.data;
  },

  async leaveSpySession(spySessionId: string): Promise<void> {
    await apiClient.post(`/private-show/spy/${spySessionId}/leave`);
  },

  async getSpyCount(sessionId: string): Promise<number> {
    const response = await apiClient.get<number>(`/private-show/${sessionId}/spy/count`);
    return response.data;
  },

  async getActiveSpySession(sessionId: string): Promise<PrivateSpySession | null> {
    const response = await apiClient.get(`/private-show/${sessionId}/spy/active`);
    if (response.status === 204) return null;
    return response.data as PrivateSpySession;
  },

  async getActiveSessionForCreator(creatorUserId: number): Promise<PrivateSession | null> {
    const response = await apiClient.get(`/private-show/active-for-creator/${creatorUserId}`);
    if (response.status === 204) return null;
    return response.data as PrivateSession;
  },

  async getAvailability(creatorUserId: number): Promise<PrivateSessionAvailability> {
    const response = await apiClient.get<PrivateSessionAvailability>(`/private-show/creator/${creatorUserId}/availability`);
    return response.data;
  },
};

export interface PrivateSessionAvailability {
  hasActivePrivate: boolean;
  allowSpyOnPrivate: boolean;
  spyPricePerMinute: number | null;
  canCurrentUserSpy: boolean;
  isCurrentUserPrivateViewer: boolean;
  currentUserPrivateViewer: boolean;
  isCurrentUserActiveSpy: boolean;
  currentUserActiveSpy: boolean;
  activeSessionId: string | null;
}

export default privateShowService;
