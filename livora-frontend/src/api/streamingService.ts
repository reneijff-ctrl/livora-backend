import axios from 'axios';
import apiClient from './apiClient';

export interface StreamRoom {
  id: string;
  creatorId: number;
  isLive: boolean;
  isPremium: boolean;
  viewerCount: number;
  streamTitle: string;
  description?: string;
  minChatTokens?: number;
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

  async startStream(data: { title: string; description?: string; minChatTokens?: number }): Promise<StreamRoom> {
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
  }
};

export default streamingService;
