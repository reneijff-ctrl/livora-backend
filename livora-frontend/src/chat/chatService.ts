import { webSocketService } from '../websocket/webSocketService';
import { Message } from '../types/chat';

/**
 * ChatService - Manages the chat message stream and provides synthetic message creation.
 * Encapsulates message logic to avoid state duplication and unnecessary re-renders.
 */
class ChatService {
  private messages: Message[] = [];
  private listeners: Set<(messages: Message[]) => void> = new Set();

  /**
   * Pushes a new message into the stream.
   */
  public pushMessage(message: Message) {
    // Ensure all messages have a unique ID for React keys
    if (!message.id) {
      message.id = `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    }
    this.messages = [...this.messages, message];
    this.notify();
  }

  /**
   * Creates a synthetic tip message from a tip event payload.
   */
  public createSyntheticTipMessage(payload: any): Message {
    return {
      id: `tip-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      type: 'TIP',
      username: payload.username || payload.viewer || 'Someone',
      amount: payload.amount,
      giftName: payload.giftName || '',
      content: payload.message || '',
      timestamp: new Date().toISOString(),
      role: 'SYSTEM',
      payload: payload
    };
  }

  /**
   * Sends a chat message to the specified room via WebSocket.
   */
  public sendMessage(roomId: string | number, senderId: string | number, role: string, content: string, creatorUserId?: string | number) {
    webSocketService.send('/app/chat.send', {
      creatorUserId,
      roomId,
      senderId,
      senderRole: role,
      content: content.trim(),
      type: 'CHAT'
    });
  }

  public subscribe(callback: (messages: Message[]) => void) {
    this.listeners.add(callback);
    callback(this.messages);
    return () => this.listeners.delete(callback);
  }

  public clear() {
    this.messages = [];
    this.notify();
  }

  private notify() {
    this.listeners.forEach(l => l(this.messages));
  }
}

export const chatService = new ChatService();
