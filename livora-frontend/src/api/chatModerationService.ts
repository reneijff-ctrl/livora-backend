import apiClient from './apiClient';

export interface MuteRequest {
  userId: number;
  durationSeconds: number;
  roomId: string;
}

export interface BanRequest {
  userId: number;
  roomId: string;
}

export interface DeleteRequest {
  roomId: string;
  messageId: string;
}

const chatModerationService = {
  async muteUser(data: MuteRequest): Promise<void> {
    await apiClient.post('/chat/moderation/mute', data);
  },

  async banUser(data: BanRequest): Promise<void> {
    await apiClient.post('/chat/moderation/ban', data);
  },

  async deleteMessage(data: DeleteRequest): Promise<void> {
    await apiClient.post('/chat/moderation/delete', data);
  },
};

export default chatModerationService;
