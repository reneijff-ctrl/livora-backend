import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { refreshToken as refreshAccessToken } from '../api/authService';
import { getRefreshToken } from '../auth/jwt';
import { AdminRealtimeEventDTO } from '../types';
import { authStore } from '../store/authStore';

/**
 * WebSocketService - A singleton service for STOMP over WebSocket communications.
 */
class WebSocketService {
  private client: Client | null = null;
  private isConnecting: boolean = false;
  private queuedMessages: { destination: string; body: any }[] = [];
  private receiptResolvers: Map<string, { resolve: () => void; reject: (err: any) => void }> = new Map();
  private subscriptions: Map<string, {
    callbacks: Set<(message: IMessage) => void>;
    stompSubscription?: StompSubscription;
  }> = new Map();
  private isRefreshing = false;
  private lastAuthError: boolean = false;
  private lastActiveStreams: any[] = [];
  private onStateChangeCallbacks: Set<(connected: boolean) => void> = new Set();
  private pendingSubscriptions: Array<{ destination: string; callback: (message: IMessage) => void }> = [];
  constructor() {
  }

  public isConnected(): boolean {
    return this.client ? this.client.connected : false;
  }

  public getActiveStreams(): any[] {
    return this.lastActiveStreams;
  }

  /**
   * Register a callback for connection state changes.
   */
  public subscribeStateChange(callback: (connected: boolean) => void): () => void {
    this.onStateChangeCallbacks.add(callback);
    return () => this.onStateChangeCallbacks.delete(callback);
  }

  private notifyStateChange() {
    const connected = this.isConnected();
    console.log(`WS: Notifying state change: ${connected}`);
    this.onStateChangeCallbacks.forEach(cb => {
      try {
        cb(connected);
      } catch (e) {
        console.error("WS: Error in state change callback", e);
      }
    });
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

    // Guard: only skip if BOTH active and connected
    if ((this.client?.active && this.client?.connected) || this.isConnecting) {
      console.log(`WS: Already connected or connecting. active=${this.client?.active}, connected=${this.client?.connected}, isConnecting=${this.isConnecting}`);
      return;
    }

    console.log("WS: CONNECT attempt starting...");
    this.isConnecting = true;
    this.lastAuthError = false;

    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      debug: (str) => console.log('STOMP:', str),
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
      console.log("WS: [DEBUG] onConnect called. Socket is now OPEN.");
      this.isConnecting = false;
      this.notifyStateChange();
      // Re-subscribe to everything if this was a re-connection or client replacement
      this.reSubscribeAll();
      this.flushPendingSubscriptions();
      this.processQueuedMessages();
    };

    this.client.onStompError = (frame) => {
      const errorMsg = frame.headers["message"] || "Unknown STOMP error";
      console.error("WS: STOMP error", errorMsg);
      this.isConnecting = false;
      this.notifyStateChange();
      const msgLower = (errorMsg || '').toLowerCase();
      if (msgLower.includes('expired')) {
        console.warn('WS: Detected expired token on STOMP error. Attempting refresh and reconnect.');
        this.lastAuthError = true;
        void this.refreshAndReconnect();
      }
      this.failAllPendingReceipts(`STOMP error: ${errorMsg}`);
    };

    this.client.onWebSocketClose = () => {
      console.log("WS: [DEBUG] onWebSocketClose called. Connection lost or closed.");
      this.isConnecting = false;
      this.notifyStateChange();
      if (this.lastAuthError && !this.isRefreshing) {
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
    if (this.isRefreshing) {
      console.log('WS: Token refresh already in progress, skipping.');
      return;
    }

    const oldToken = localStorage.getItem("token");
    this.isRefreshing = true;

    try {
      const rToken = getRefreshToken();
      if (!rToken) {
        console.warn('WS: No refresh token available; cannot refresh. Disconnecting.');
        this.disconnect();
        return;
      }

      console.log('WS: Attempting to refresh access token for WebSocket...');
      await refreshAccessToken(rToken);

      const newToken = localStorage.getItem("token");
      if (oldToken === newToken) {
        console.log('WS: Token did not change after refresh. Skipping reconnect.');
        return;
      }

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
      this.isRefreshing = false;
      this.lastAuthError = false;
    }
  }

  private flushPendingSubscriptions() {
    if (!this.pendingSubscriptions.length) return;
    console.log(`WS: Flushing queued subscriptions: ${this.pendingSubscriptions.length}`);
    const queued = [...this.pendingSubscriptions];
    this.pendingSubscriptions = [];
    queued.forEach(sub => {
      this.subscribe(sub.destination, sub.callback);
    });
  }

  private reSubscribeAll() {
    console.log(`WS: Re-subscribing to ${this.subscriptions.size} destinations`);
    this.subscriptions.forEach((entry, destination) => {
      if (this.client?.connected) {
        entry.stompSubscription = this.client.subscribe(destination, (message) => {
          this.handleIncomingMessage(destination, message);
        });
      }
    });
  }

  private handleIncomingMessage(destination: string, message: IMessage) {
    if (destination === '/exchange/amq.topic/admin.streams') {
      try {
        const data = JSON.parse(message.body);
        if (Array.isArray(data)) {
          this.lastActiveStreams = data;
        }
      } catch (err) {
        console.error(`WS: Failed to parse active streams from ${destination}`, err);
      }
    }

    const entry = this.subscriptions.get(destination);
    if (entry) {
      entry.callbacks.forEach(cb => {
        try {
          cb(message);
        } catch (e) {
          console.error(`WS: Error in callback for ${destination}`, e);
        }
      });
    }
  }

  private internalUnsubscribe(destination: string, callback: (message: IMessage) => void) {
    const entry = this.subscriptions.get(destination);
    if (!entry) return;

    entry.callbacks.delete(callback);
    if (entry.callbacks.size === 0) {
      console.log(`WS: Last subscriber for ${destination} removed. Unsubscribing STOMP.`);
      if (entry.stompSubscription) {
        entry.stompSubscription.unsubscribe();
      }
      this.subscriptions.delete(destination);
      console.debug("Active WS subscriptions:", this.subscriptions.size);
    }
  }

  public async waitForConnection(): Promise<void> {
    if (this.isConnected()) return;

    if (!this.isConnected()) {
      console.log("WS: waitForConnection ensuring connection is initiated.");
      this.connect();
    }

    if (this.isConnected()) return;

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
   * Subscribe to a destination. Returns an unsubscribe function.
   * Purely synchronous to prevent infinite promise loops.
   * If not connected, queues the subscription until connection is established.
   * @param destination The topic or queue to subscribe to.
   * @param callback Function to handle incoming messages.
   */
  public subscribe(destination: string, callback: (message: IMessage) => void): (() => void) & { unsubscribe: () => void } {
    if (destination === '/user/queue/webrtc') {
      console.log("WS: [DEBUG] Subscribing to WebRTC queue (/user/queue/webrtc)");
    }

    if (!this.client?.connected) {
      console.warn(`WS: Queuing subscription until connected: ${destination}`);
      this.pendingSubscriptions.push({ destination, callback });
      const cancel = () => {
        this.pendingSubscriptions = this.pendingSubscriptions.filter(s => s.callback !== callback);
      };
      cancel.unsubscribe = cancel;
      return cancel as (() => void) & { unsubscribe: () => void };
    }

    const existingEntry = this.subscriptions.get(destination);
    const unsub = () => this.internalUnsubscribe(destination, callback);

    if (existingEntry) {
      console.log(`WS: Duplicate subscription attempt for ${destination} prevented. Multiplexing callback.`);
      existingEntry.callbacks.add(callback);
      const result = unsub as any;
      result.unsubscribe = unsub;
      return result;
    }

    // New subscription
    const callbacks = new Set([callback]);
    const stompSubscription = this.client.subscribe(destination, (message) => {
      this.handleIncomingMessage(destination, message);
    });

    const entry: any = { 
      callbacks,
      stompSubscription
    };
    
    this.subscriptions.set(destination, entry);
    console.debug("Active WS subscriptions:", this.subscriptions.size);

    const result = unsub as any;
    result.unsubscribe = unsub;
    return result;
  }

  /**
   * Async method that resolves to the raw StompSubscription after receipt.
   * Useful for WebRTC signaling where registration order is critical.
   */
  public async subscribeWithAck(destination: string, callback: (message: IMessage) => void): Promise<StompSubscription> {
    const existingEntry = this.subscriptions.get(destination);

    const unsub = () => this.internalUnsubscribe(destination, callback);

    if (existingEntry) {
      console.log(`WS: Duplicate subscribeWithAck attempt for ${destination} prevented. Multiplexing.`);
      existingEntry.callbacks.add(callback);
      return {
        id: existingEntry.stompSubscription?.id || `multiplexed-${destination}`,
        unsubscribe: () => unsub(),
      } as StompSubscription;
    }

    if (!this.client?.connected) {
      throw new Error("WS: Cannot subscribeWithAck while disconnected");
    }

    const callbacks = new Set([callback]);
    const entry: any = { callbacks };
    this.subscriptions.set(destination, entry);
    console.debug("Active WS subscriptions:", this.subscriptions.size);

    const subscription = this.client.subscribe(destination, (message) => {
      const e = this.subscriptions.get(destination);
      if (e) {
        e.callbacks.forEach(cb => {
          try {
            cb(message);
          } catch (err) {
            console.error(`WS: Error in callback for ${destination}`, err);
          }
        });
      }
    });

    entry.stompSubscription = subscription;

    return {
      id: subscription.id,
      unsubscribe: () => unsub(),
    } as StompSubscription;
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
    this.notifyStateChange();
    console.log("WS: Disconnected and client cleared.");
  }

  /**
   * Unsubscribe from a destination.
   * @param sub The subscription reference returned by subscribe().
   */
  public unsubscribe(sub: any) {
    if (sub && typeof sub.unsubscribe === 'function') {
      sub.unsubscribe();
    } else if (typeof sub === 'function') {
      sub();
    }
  }

  /**
   * Subscribe to real-time administrative events.
   * Only active if the user has ROLE_ADMIN.
   * @param callback Function to handle AdminRealtimeEventDTO.
   */
  public subscribeToAdminEvents(callback: (event: AdminRealtimeEventDTO) => void): (() => void) | null {
    const user = authStore.getState().user;
    if (user?.role !== 'ADMIN') {
      console.warn("WS: Attempted to subscribe to admin events without ROLE_ADMIN");
      return null;
    }

    console.log("WS: Subscribing to admin events topic");
    return this.subscribe('/exchange/amq.topic/admin.events', (message) => {
      try {
        const event = JSON.parse(message.body) as AdminRealtimeEventDTO;
        callback(event);
      } catch (e) {
        console.error("WS: Failed to parse admin event", e);
      }
    });
  }

  /**
   * Subscribe to real-time abuse radar events.
   * Only active if the user has ROLE_ADMIN.
   * @param callback Function to handle Abuse events.
   */
  public subscribeToAbuseEvents(callback: (event: any) => void): (() => void) | null {
    const user = authStore.getState().user;
    if (user?.role !== 'ADMIN') {
      console.warn("WS: Attempted to subscribe to abuse events without ROLE_ADMIN");
      return null;
    }

    console.log("WS: Subscribing to admin abuse topic");
    return this.subscribe('/exchange/amq.topic/admin.abuse', (message) => {
      try {
        const event = JSON.parse(message.body);
        callback(event);
      } catch (e) {
        console.error("WS: Failed to parse abuse event", e);
      }
    });
  }
}

export const webSocketService = new WebSocketService();
export default webSocketService;
