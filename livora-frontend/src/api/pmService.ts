import apiClient from '@/api/apiClient';
import { webSocketService } from '@/websocket/webSocketService';

export interface PmSession {
  roomId: number;
  creatorId: number;
  creatorUsername: string;
  viewerId: number;
  viewerUsername: string;
  createdAt: string;
  unreadCount: number;
  lastMessage: string | null;
  lastMessageTime: string | null;
}

export interface PmMessage {
  type: string;
  roomId: number;
  senderId: number;
  senderUsername: string;
  senderRole: string;
  content: string;
  createdAt: string;
}

export const startPm = async (viewerId: number): Promise<PmSession> => {
  const res = await apiClient.post('/pm/start', { viewerId });
  return res.data;
};

export const getActivePm = async (): Promise<PmSession[]> => {
  const res = await apiClient.get('/pm/active');
  return res.data;
};

export const getPmMessages = async (roomId: number): Promise<PmMessage[]> => {
  const res = await apiClient.get(`/pm/${roomId}/messages`);
  return (res.data || []).map((m: any) => ({
    type: m.type || 'PM_MESSAGE',
    roomId: m.roomId ?? roomId,
    senderId: m.senderId,
    senderUsername: m.senderUsername,
    senderRole: m.senderRole,
    content: m.content,
    createdAt: m.timestamp || m.createdAt,
  }));
};

export const endPmSession = async (roomId: number): Promise<void> => {
  await apiClient.post(`/pm/${roomId}/end`);
};

export const sendPmMessage = (roomId: number, content: string): void => {
  if (!roomId || !content?.trim()) return;
  webSocketService.send('/app/v2/pm.send', { roomId, content });
};

export const markPmAsRead = async (roomId: number): Promise<void> => {
  await apiClient.post(`/pm/${roomId}/read`);
};
