import { Device, Transport, Producer, Consumer } from 'mediasoup-client';
import { IMessage } from '@stomp/stompjs';

export enum SignalingType {
  OFFER = 'OFFER',
  ANSWER = 'ANSWER',
  ICE_CANDIDATE = 'ICE_CANDIDATE',
  JOIN = 'JOIN',
  LEAVE = 'LEAVE',
  ERROR = 'ERROR',
  
  // Mediasoup signaling types
  NEW_PRODUCER = 'NEW_PRODUCER',
  GET_ROUTER_CAPABILITIES = 'GET_ROUTER_CAPABILITIES',
  CREATE_TRANSPORT = 'CREATE_TRANSPORT',
  CONNECT_TRANSPORT = 'CONNECT_TRANSPORT',
  PRODUCE = 'PRODUCE',
  CONSUME = 'CONSUME',
  RESUME_CONSUMER = 'RESUME_CONSUMER',
  RESTART_ICE = 'RESTART_ICE'
}

export interface SignalingMessage {
  type: string;
  senderId: number;
  roomId: string;
  streamId?: string;
  data?: any; // generic field for mediasoup payloads
  sdp?: any;
  candidate?: any;
}

class WebRtcService {
  private device: Device | null = null;
  private sendTransport: Transport | null = null;
  private recvTransport: Transport | null = null;
  private producers: Map<string, Producer> = new Map();
  private consumers: Map<string, Consumer> = new Map();
  private statsIntervals: Map<string, number> = new Map();
  
  private localStream: MediaStream | null = null;
  private onRemoteStreamCallback: ((stream: MediaStream) => void) | null = null;
  private onStreamStopCallback: (() => void) | null = null;
  private webrtcUnsub: (() => void) | null = null;
  private currentUserId: number | null = null;
  private currentRoomId: string | null = null;
  private pendingRequests: Map<string, { resolve: Function, reject: Function, timeout: any }> = new Map();

  // Injected WebSocket functions from WsContext
  private wsSend: ((destination: string, body: string, headers?: Record<string, string>) => void) | null = null;
  private wsSubscribe: ((destination: string, callback: (message: IMessage) => void) => () => void) | null = null;

  /**
   * Send a request and wait for a response.
   */
  async sendRequest(type: string, roomId: string, data: any = {}): Promise<any> {
    const requestId = Math.random().toString(36).substring(2, 15);
    const senderId = this.currentUserId || 0;

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(requestId);
        reject(new Error(`Request timeout: ${type}`));
      }, 10000);

      this.pendingRequests.set(requestId, { resolve, reject, timeout });

      this.sendSignal({
        type,
        senderId,
        roomId,
        data: { ...data, requestId }
      });
    });
  }

  /**
   * Handle incoming signal and resolve pending requests if any.
   */
  handleIncomingSignal(signal: SignalingMessage) {
    const { data } = signal;
    if (data && data.requestId && this.pendingRequests.has(data.requestId)) {
      const { resolve, reject, timeout } = this.pendingRequests.get(data.requestId)!;
      clearTimeout(timeout);
      this.pendingRequests.delete(data.requestId);

      if (data.error) {
        reject(new Error(data.error));
      } else {
        resolve(data);
      }
      return true; // Handled as request response
    }
    return false;
  }

  setCurrentUserId(userId: number) {
    this.currentUserId = userId;
  }

  /**
   * Initialize Mediasoup Device with router capabilities
   */
  async initDevice(routerRtpCapabilities: any) {
    try {
      console.log("routerCapabilities", routerRtpCapabilities);
      this.device = new Device();
      await this.device.load({ routerRtpCapabilities });
      console.log("device loaded", this.device.loaded);
      
      if (!this.device.loaded) {
        throw new Error("Mediasoup device failed to load capabilities");
      }
      
      return this.device;
    } catch (error: any) {
      if (error.name === 'UnsupportedError') {
        console.error('Browser not supported');
      }
      console.error('Failed to initialize Mediasoup device:', error);
      throw error;
    }
  }

  getDevice() {
    return this.device;
  }

  /**
   * Connect to the WebRTC signaling room topic.
   * Subscribes to two destinations:
   * 1. /user/queue/webrtc — for personal request-response signaling (requestId correlation)
   * 2. /exchange/amq.topic/webrtc.room.{roomId} — for broadcast events (NEW_PRODUCER, etc.)
   *
   * @param deps.subscribe - subscribe function injected from WsContext
   * @param deps.send - send function injected from WsContext
   */
  connect(
    roomId: string,
    onSignal: (msg: SignalingMessage) => void,
    deps: {
      subscribe: (destination: string, callback: (message: IMessage) => void) => () => void;
      send: (destination: string, body: string, headers?: Record<string, string>) => void;
    }
  ) {
    // Store injected WS functions for use by sendSignal/sendRequest
    this.wsSubscribe = deps.subscribe;
    this.wsSend = deps.send;

    if (typeof this.webrtcUnsub === 'function') {
      this.webrtcUnsub();
      this.webrtcUnsub = null;
    }

    this.currentRoomId = roomId;

    // Subscribe to personal queue for request-response signaling
    const personalUnsub = deps.subscribe('/user/queue/webrtc', (msg: IMessage) => {
      try {
        const signal = JSON.parse(msg.body);
        if (!this.handleIncomingSignal(signal)) {
          onSignal(signal);
        }
      } catch (e) {
        console.error("WS: Personal signaling parse error", e);
      }
    });

    // Subscribe to room topic for broadcast events (NEW_PRODUCER, STREAM_STOP, etc.)
    const roomTopic = `/exchange/amq.topic/webrtc.room.${roomId}`;
    const roomUnsub = deps.subscribe(roomTopic, (msg: IMessage) => {
      try {
        const signal = JSON.parse(msg.body);
        if (!this.handleIncomingSignal(signal)) {
          onSignal(signal);
        }
      } catch (e) {
        console.error("WS: Room signaling parse error", e);
      }
    });

    this.webrtcUnsub = () => {
      if (typeof personalUnsub === 'function') personalUnsub();
      if (typeof roomUnsub === 'function') roomUnsub();
    };
  }

  /**
   * Send a signaling message to the server for relay.
   */
  sendSignal(message: SignalingMessage) {
    if (!this.wsSend) {
      console.error('WS: Cannot send signal — WebSocket send function not injected. Call connect() first.');
      return;
    }
    this.wsSend('/app/webrtc.signal', message as any);
  }

  leaveStream() {
    if (typeof this.webrtcUnsub === 'function') {
      this.webrtcUnsub();
      this.webrtcUnsub = null;
    }
    this.cleanup();
  }

  cleanup() {
    this.localStream?.getTracks().forEach(track => track.stop());
    
    this.producers.forEach(p => p.close());
    this.producers.clear();
    
    this.consumers.forEach(c => {
      const intervalId = this.statsIntervals.get(c.id);
      if (intervalId) {
        window.clearInterval(intervalId);
        this.statsIntervals.delete(c.id);
      }
      c.close();
    });
    this.consumers.clear();
    
    this.sendTransport?.close();
    this.sendTransport = null;
    
    this.recvTransport?.close();
    this.recvTransport = null;
    
    this.device = null;
    this.localStream = null;
    this.currentRoomId = null;
  }

  public cleanupStatsIntervals() {
    this.statsIntervals.forEach((intervalId) => {
      window.clearInterval(intervalId);
    });
    this.statsIntervals.clear();
  }

  public registerStatsInterval(consumer: Consumer, callback: (stats: any) => void) {
    const intervalId = window.setInterval(async () => {
      if (consumer.closed) {
        const id = this.statsIntervals.get(consumer.id);
        if (id) {
          window.clearInterval(id);
          this.statsIntervals.delete(consumer.id);
        }
        return;
      }

      try {
        const stats = await consumer.getStats();
        callback(stats);
      } catch (err) {
        console.error(`Failed to get stats for consumer ${consumer.id}:`, err);
      }
    }, 2000);

    this.statsIntervals.set(consumer.id, intervalId);
    return intervalId;
  }

  public closeConsumer(consumerId: string) {
    const consumer = this.consumers.get(consumerId);
    if (consumer) {
      const intervalId = this.statsIntervals.get(consumer.id);
      if (intervalId) {
        window.clearInterval(intervalId);
        this.statsIntervals.delete(consumer.id);
      }
      consumer.close();
      this.consumers.delete(consumerId);
    }
  }

  public addConsumer(consumer: Consumer) {
    this.consumers.set(consumer.id, consumer);
  }

  /**
   * Helper to request ICE restart for a transport.
   */
  async restartIce(transportId: string) {
    if (!this.currentRoomId) {
      throw new Error("Cannot restart ICE: No active room session");
    }

    return this.sendRequest(SignalingType.RESTART_ICE, this.currentRoomId, {
      transportId
    });
  }
}

export const webRtcService = new WebRtcService();
export default webRtcService;
