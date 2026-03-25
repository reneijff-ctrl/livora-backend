export type ChatState = 'IDLE' | 'WAITING_FOR_CREATOR' | 'ACTIVE' | 'ENDED' | 'ERROR';

export interface Message {
  id?: string;
  messageId?: string;
  type?: string;
  senderId?: string;
  username?: string;
  senderUsername?: string;
  senderRole?: string;
  content: string;
  timestamp?: string;
  role?: string;
  amount?: number;
  giftName?: string;
  highlight?: string;
  payload?: any;
}
