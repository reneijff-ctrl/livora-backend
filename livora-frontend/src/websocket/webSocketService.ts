import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { refreshToken as refreshAccessToken } from '../api/authService';
import { getRefreshToken } from '../auth/jwt';

/**
 * WebSocketService - A singleton service for STOMP over WebSocket communications.
 */
class WebSocketService {
  private client: Client | null = null;
  private isConnecting: boolean = false;
  private queuedMessages: { destination: string; body: any }[] = [];
  private receiptResolvers: Map<string, { resolve: () => void; reject: (err: any) => void }> = new Map();
  private subscriptions: Set<{
    destination: string;
    callback: (message: IMessage) => void;
    currentSubscription?: StompSubscription;
  }> = new Set();
  private refreshingToken: boolean = false;
  private lastAuthError: boolean = false;
  constructor() {
  }

  public isConnected(): boolean {
    return this.client ? this.client.connected : false;
  }

  /**
   * Connect to the WebSocket broker if not already connected or connecting.
   */
  connect() {
    const token = localStorage.getItem("token");
    console.log("WS: Retrieved token from storage:", token);

    if (!token || !token.includes(".")) {
      console.log("WS: No valid token found. Skipping connection.");
      return;
    }

    // Guard: already connected or active
    if (this.client?.connected || this.client?.active || this.isConnecting) {
      console.log(`WS: Already connected or connecting. active=${this.client?.active}, connected=${this.client?.connected}, isConnecting=${this.isConnecting}`);
      return;
    }

    console.log("WS: CONNECT attempt starting...");
    this.isConnecting = true;
    this.lastAuthError = false;

    this.client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      debug: (str) => console.log(str),
    });

    (this.client as any).onReceipt = (frame: any) => {
      const receiptId = frame.headers["receipt-id"];
      console.log(`WS: RECEIPT received for ID: ${receiptId}`);
      const resolver = receiptId ? this.receiptResolvers.get(receiptId) : null;
      if (resolver) {
        resolver.resolve();
        this.receiptResolvers.delete(receiptId!);
      }
    };

    this.client.onConnect = () => {
      console.log("WS: CONNECT success");
      this.isConnecting = false;
      this.processQueuedMessages();
      // Re-subscribe to everything if this was a re-connection or client replacement
      this.reSubscribeAll();
    };

    this.client.onStompError = (frame) => {
      const errorMsg = frame.headers["message"] || "Unknown STOMP error";
      console.error("WS: STOMP error", errorMsg);
      this.isConnecting = false;
      const msgLower = (errorMsg || '').toLowerCase();
      if (msgLower.includes('expired')) {
        console.warn('WS: Detected expired token on STOMP error. Attempting refresh and reconnect.');
        this.lastAuthError = true;
        void this.refreshAndReconnect();
      }
      this.failAllPendingReceipts(`STOMP error: ${errorMsg}`);
    };

    this.client.onWebSocketClose = () => {
      console.log("WS: WebSocket connection closed");
      this.isConnecting = false;
      if (this.lastAuthError && !this.refreshingToken) {
        console.warn('WS: WebSocket closed after auth error. Ensuring refresh and reconnect.');
        void this.refreshAndReconnect();
      }
      this.failAllPendingReceipts("WebSocket connection closed");
    };

    this.client.activate();
  }

  private failAllPendingReceipts(reason: string) {
    if (this.receiptResolvers.size > 0) {
      console.log(`WS: Failing ${this.receiptResolvers.size} pending receipts. Reason: ${reason}`);
      this.receiptResolvers.forEach(({ reject }) => {
        try {
          reject(new Error(reason));
        } catch (e) {
          console.error("WS: Error rejecting receipt promise", e);
        }
      });
      this.receiptResolvers.clear();
    }
  }

  private async refreshAndReconnect() {
    if (this.refreshingToken) {
      console.log('WS: Token refresh already in progress, skipping.');
      return;
    }
    this.refreshingToken = true;
    try {
      const rToken = getRefreshToken();
      if (!rToken) {
        console.warn('WS: No refresh token available; cannot refresh. Disconnecting.');
        this.disconnect();
        return;
      }
      console.log('WS: Attempting to refresh access token for WebSocket...');
      await refreshAccessToken(rToken);
      console.log('WS: Access token refreshed. Reconnecting WebSocket...');
      if (this.client) {
        try {
          await this.client.deactivate();
        } catch (e) {
          console.warn('WS: Error during client deactivation before reconnect', e);
        }
        this.client = null;
      }
      this.isConnecting = false;
      this.connect();
    } catch (e) {
      console.error('WS: Token refresh failed. Disconnecting WebSocket.', e);
      this.disconnect();
    } finally {
      this.refreshingToken = false;
      this.lastAuthError = false;
    }
  }

  private reSubscribeAll() {
    console.log(`WS: Re-subscribing to ${this.subscriptions.size} destinations`);
    this.subscriptions.forEach(subObj => {
      if (this.client?.connected) {
        subObj.currentSubscription = this.client.subscribe(subObj.destination, subObj.callback);
      }
    });
  }

  public async waitForConnection(): Promise<void> {
    if (this.isConnected()) return Promise.resolve();

    return new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        if (this.isConnected()) {
          clearInterval(checkInterval);
          resolve();
        }
      }, 100);
      
      // Safety timeout after 10 seconds
      setTimeout(() => {
        clearInterval(checkInterval);
        resolve(); 
      }, 10000);
    });
  }

  /**
   * Subscribe to a destination. Returns an unsubscribe function that is also a Promise (Thenable).
   * The Promise resolves once the broker acknowledges the subscription via STOMP receipt.
   * This maintains backward compatibility for synchronous calls while allowing await.
   * @param destination The topic or queue to subscribe to.
   * @param callback Function to handle incoming messages.
   */
  public subscribe(destination: string, callback: (message: IMessage) => void): (() => void) & Promise<() => void> {
    console.log(`WS: Creating new subscription with receipt to ${destination}`);
    const receiptId = `sub-${Math.random().toString(36).substring(2, 9)}`;
    
    const subObj: {
      destination: string;
      callback: (message: IMessage) => void;
      currentSubscription?: StompSubscription;
    } = { destination, callback };

    this.subscriptions.add(subObj);

    const unsub = () => {
      console.log(`WS: Unsubscribing from ${destination}`);
      this.subscriptions.delete(subObj);
      if (subObj.currentSubscription) {
        subObj.currentSubscription.unsubscribe();
      }
    };

    const promise = (async () => {
      if (this.client?.connected) {
        const receiptPromise = new Promise<void>((resolve, reject) => {
          this.receiptResolvers.set(receiptId, { resolve, reject });
        });

        subObj.currentSubscription = this.client.subscribe(destination, callback, { receipt: receiptId });
        
        console.log(`WS: Awaiting receipt for ${destination} (ID: ${receiptId})`);
        try {
          await receiptPromise;
          console.log(`WS: Receipt confirmed for ${destination}`);
        } catch (err) {
          console.error(`WS: Receipt failed for ${destination}`, err);
          // We don't throw here to avoid breaking thenable contract, 
          // but the subscription might not be fully active on the broker.
        }
      } else {
        console.warn(`WS: Subscribe called while disconnected for ${destination}. Subscription will be established on connect.`);
      }
      return unsub;
    })();

    // Augment the unsubscribe function with Promise properties for backward compatibility + async support
    const result = unsub as any;
    result.then = (onfulfilled?: any, onrejected?: any) => promise.then(onfulfilled, onrejected);
    result.catch = (onrejected?: any) => promise.catch(onrejected);
    result.finally = (onfinally?: any) => promise.finally(onfinally);

    return result as (() => void) & Promise<() => void>;
  }

  /**
   * Async method that resolves to the raw StompSubscription after receipt.
   * Useful for WebRTC signaling where registration order is critical.
   */
  public async subscribeWithAck(destination: string, callback: (message: IMessage) => void): Promise<StompSubscription> {
    const receiptId = `ack-${Math.random().toString(36).substring(2, 9)}`;
    
    if (!this.client?.connected) {
      throw new Error("WS: Cannot subscribeWithAck while disconnected");
    }

    const receiptPromise = new Promise<void>((resolve, reject) => {
      this.receiptResolvers.set(receiptId, { resolve, reject });
    });

    const subscription = this.client.subscribe(destination, callback, { receipt: receiptId });
    
    const subObj = { destination, callback, currentSubscription: subscription };
    this.subscriptions.add(subObj);

    try {
      await receiptPromise;
      return subscription;
    } catch (err) {
      console.error(`WS: subscribeWithAck failed for ${destination}`, err);
      this.subscriptions.delete(subObj);
      subscription.unsubscribe();
      throw err;
    }
  }

  /**
   * Send a message to a destination. Queues if not connected.
   * @param destination The destination to send to.
   * @param body The message body (will be JSON stringified).
   */
  public send(destination: string, body: any) {
    if (this.client?.connected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
      console.log(`WS: MESSAGE sent to ${destination}`);
    } else {
      console.log(`WS: Queueing message to ${destination}`);
      this.queuedMessages.push({ destination, body });
    }
  }

  /**
   * Flushes all queued messages.
   */
  public processQueuedMessages() {
    if (this.queuedMessages.length > 0) {
      console.log(`WS: Processing ${this.queuedMessages.length} queued messages`);
      while (this.client?.connected && this.queuedMessages.length > 0) {
        const msg = this.queuedMessages.shift();
        if (msg) {
          this.send(msg.destination, msg.body);
        }
      }
    }
  }

  /**
   * Disconnect the client and stop auto-reconnect.
   */
  public disconnect() {
    console.log("WS: Disconnect requested.");
    this.failAllPendingReceipts("Service disconnected");
    if (this.client) {
      console.log("WS: Deactivating STOMP client.");
      this.client.deactivate();
      this.client = null;
    } else {
      console.log("WS: No active client to deactivate.");
    }
    this.isConnecting = false;
    console.log("WS: Disconnected and client cleared.");
  }
}

export const webSocketService = new WebSocketService();
export default webSocketService;
