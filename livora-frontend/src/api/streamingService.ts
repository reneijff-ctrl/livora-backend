import apiClient from './apiClient';

export interface StreamRoom {
  id: string;
  userId: number;
  creatorId: number;
  isLive: boolean;
  isPremium: boolean;
  viewerCount: number;
  streamTitle: string;
  description?: string;
  minChatTokens?: number;
  isPaid?: boolean;
  pricePerMessage?: number;
}

export interface LiveStream {
  id: string;
  userId: number;
  creatorId: number;
  title: string;
  isLive: boolean;
  isPremium: boolean;
  startedAt: string;
  endedAt?: string;
  hlsUrl?: string;
}

const streamingService = {
  async getLiveStreams(): Promise<StreamRoom[]> {
    const response = await apiClient.get<StreamRoom[]>('/api/streams/live');
    return response.data;
  },

  async getStream(id: string): Promise<StreamRoom> {
    const response = await apiClient.get<StreamRoom>(`/api/streams/${id}`);
    return response.data;
  },

  async startStream(data: { title: string; description?: string; minChatTokens?: number; recordingEnabled?: boolean; isPaid?: boolean; pricePerMessage?: number }): Promise<StreamRoom> {
    const response = await apiClient.post<StreamRoom>('/api/streams/creator/start', data);
    return response.data;
  },

  async stopStream(): Promise<StreamRoom> {
    const response = await apiClient.post<StreamRoom>('/api/streams/creator/stop');
    return response.data;
  },

  async getMyRoom(): Promise<StreamRoom> {
    const response = await apiClient.get<StreamRoom>('/api/streams/creator/room');
    return response.data;
  },

  async getLiveStatus(): Promise<{ isLive: boolean; viewerCount: number; streamTitle: string }> {
    const response = await apiClient.get<{ isLive: boolean; viewerCount: number; streamTitle: string }>('/api/creator/live/status');
    return response.data;
  },

  async getIngestInfo(): Promise<{ server: string; streamKey: string }> {
    const response = await apiClient.get<{ server: string; streamKey: string }>('/stream/ingest-info');
    return response.data;
  },

  async getStreamStatus(roomId: string): Promise<{ isLive: boolean; viewerCount: number }> {
    const response = await apiClient.get<{ isLive: boolean; viewerCount: number }>(`/stream/${roomId}/status`);
    return response.data;
  }
};

export default streamingService;
