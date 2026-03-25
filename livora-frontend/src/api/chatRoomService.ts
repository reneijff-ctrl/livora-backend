import api from './apiClient';

export interface ChatRoomDto {
  id: string;
  name: string;
  isLive: boolean;
  ppvRequired: boolean;
  creatorId: string | number;
}

const chatRoomService = {
  getLiveRooms: async (): Promise<ChatRoomDto[]> => {
    const response = await api.get<ChatRoomDto[]>('/chat/rooms/live');
    return response.data;
  },
  
  getRoom: async (roomId: string): Promise<ChatRoomDto> => {
    const response = await api.get<ChatRoomDto>(`/chat/rooms/${roomId}`);
    return response.data;
  }
};

export default chatRoomService;
