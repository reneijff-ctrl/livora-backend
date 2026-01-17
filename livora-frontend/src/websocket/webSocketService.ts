import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getAccessToken } from '../api/apiClient';

/**
 * WebSocket utility using STOMP over SockJS.
 * This implementation connects to the /ws endpoint and handles JWT authentication.
 */
class WebSocketService {
  private client: Client | null = null;
  private url: string = `${import.meta.env.VITE_API_URL}/ws`;
  private subscriptions: Map<string, any> = new Map();
  private onConnectCallbacks: Set<() => void> = new Set();

  connect() {
    if (this.client?.active) return;

    const token = getAccessToken();
    if (!token) {
      console.warn('WS: Cannot connect without access token');
      return;
    }

    console.log('WS: Connecting to', this.url);

    this.client = new Client({
      webSocketFactory: () => new SockJS(this.url),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: (str) => {
        if (import.meta.env.DEV) console.log('WS DEBUG:', str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = (frame) => {
      console.log('WS: Connected', frame);
      this.onConnectCallbacks.forEach(cb => cb());
    };

    this.client.onStompError = (frame) => {
      console.error('WS STOMP Error:', frame.headers['message']);
      console.error('Details:', frame.body);
    };

    this.client.activate();
  }

  subscribe(destination: string, callback: (message: IMessage) => void) {
    const internalSubscribe = () => {
      if (this.client?.active) {
        const sub = this.client.subscribe(destination, callback);
        this.subscriptions.set(destination, sub);
        console.log(`WS: Subscribed to ${destination}`);
      }
    };

    if (this.client?.active) {
      internalSubscribe();
    } else {
      this.onConnectCallbacks.add(internalSubscribe);
    }

    return () => {
      this.onConnectCallbacks.delete(internalSubscribe);
      const sub = this.subscriptions.get(destination);
      if (sub) {
        sub.unsubscribe();
        this.subscriptions.delete(destination);
        console.log(`WS: Unsubscribed from ${destination}`);
      }
    };
  }

  send(destination: string, body: any) {
    if (this.client?.active) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    } else {
      console.error('WS: Cannot send message, client not active');
    }
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.subscriptions.clear();
      this.onConnectCallbacks.clear();
      console.log('WS: Disconnected');
    }
  }

  isConnected() {
    return this.client?.active || false;
  }
}

export const webSocketService = new WebSocketService();
export default webSocketService;
