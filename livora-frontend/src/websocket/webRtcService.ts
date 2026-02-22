import webSocketService from '../websocket/webSocketService';
import { IMessage } from '@stomp/stompjs';

export enum SignalingType {
  OFFER = 'OFFER',
  ANSWER = 'ANSWER',
  ICE_CANDIDATE = 'ICE_CANDIDATE',
  JOIN_ROOM = 'JOIN_ROOM',
  LEAVE_ROOM = 'LEAVE_ROOM',
  STREAM_START = 'STREAM_START',
  STREAM_STOP = 'STREAM_STOP',
  ERROR = 'ERROR',
  ACCESS_DENIED = 'ACCESS_DENIED'
}

export interface SignalingMessage {
  type: SignalingType;
  roomId: string;
  senderId?: string;
  receiverId?: string;
  payload?: any;
  message?: string;
}

class WebRtcService {
  private peerConnection: RTCPeerConnection | null = null;
  private localStream: MediaStream | null = null;
  private roomId: string | null = null;
  private onRemoteStreamCallback: ((stream: MediaStream) => void) | null = null;
  private onStreamStopCallback: (() => void) | null = null;

  private iceServers = {
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
    ]
  };

  async startBroadcast(roomId: string, videoElement: HTMLVideoElement) {
    this.roomId = roomId;
    this.localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    videoElement.srcObject = this.localStream;

    this.setupPeerConnection();
    
    this.localStream.getTracks().forEach(track => {
      this.peerConnection?.addTrack(track, this.localStream!);
    });

    const offer = await this.peerConnection?.createOffer();
    await this.peerConnection?.setLocalDescription(offer);

    webSocketService.send('/app/webrtc/start', { roomId });
    // In a real SFU, we would send the offer to SFU.
    // Here we'll wait for viewers to join and then send offers to them or SFU.
  }

  async joinStream(roomId: string, onRemoteStream: (stream: MediaStream) => void) {
    this.roomId = roomId;
    this.onRemoteStreamCallback = onRemoteStream;
    
    webSocketService.subscribe('/user/queue/webrtc', (msg: IMessage) => {
      this.handleSignalingMessage(JSON.parse(msg.body));
    });

    webSocketService.send('/app/webrtc/join', { roomId });
  }

  private setupPeerConnection() {
    this.peerConnection = new RTCPeerConnection(this.iceServers);

    this.peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        webSocketService.send('/app/webrtc/ice', {
          type: SignalingType.ICE_CANDIDATE,
          roomId: this.roomId,
          payload: event.candidate
        });
      }
    };

    this.peerConnection.ontrack = (event) => {
      if (this.onRemoteStreamCallback) {
        this.onRemoteStreamCallback(event.streams[0]);
      }
    };
  }

  private async handleSignalingMessage(message: SignalingMessage) {
    switch (message.type) {
      case SignalingType.OFFER:
        await this.handleOffer(message);
        break;
      case SignalingType.ANSWER:
        await this.peerConnection?.setRemoteDescription(new RTCSessionDescription(message.payload));
        break;
      case SignalingType.ICE_CANDIDATE:
        await this.peerConnection?.addIceCandidate(new RTCIceCandidate(message.payload));
        break;
      case SignalingType.STREAM_STOP:
        console.log('Stream stopped by creator');
        this.cleanup();
        if (this.onStreamStopCallback) this.onStreamStopCallback();
        break;
      case SignalingType.ACCESS_DENIED:
        console.error('Access Denied:', message.message);
        this.cleanup();
        if (this.onAccessDeniedCallback) this.onAccessDeniedCallback(message.message || 'Access Denied');
        break;
      case SignalingType.ERROR:
        console.error('Signaling Error:', message.message);
        this.cleanup();
        if (this.onAccessDeniedCallback) this.onAccessDeniedCallback(message.message || 'Error occurred');
        break;
    }
  }

  private onAccessDeniedCallback: ((msg: string) => void) | null = null;

  setOnAccessDenied(callback: (msg: string) => void) {
    this.onAccessDeniedCallback = callback;
  }

  setOnStreamStop(callback: () => void) {
    this.onStreamStopCallback = callback;
  }

  private async handleOffer(message: SignalingMessage) {
    if (!this.peerConnection) this.setupPeerConnection();
    
    await this.peerConnection?.setRemoteDescription(new RTCSessionDescription(message.payload));
    const answer = await this.peerConnection?.createAnswer();
    await this.peerConnection?.setLocalDescription(answer);

    webSocketService.send('/app/webrtc/answer', {
      type: SignalingType.ANSWER,
      roomId: this.roomId,
      receiverId: message.senderId,
      payload: answer
    });
  }

  cleanup() {
    this.localStream?.getTracks().forEach(track => track.stop());
    this.peerConnection?.close();
    this.peerConnection = null;
    this.localStream = null;
    this.roomId = null;
  }
}

export const webRtcService = new WebRtcService();
export default webRtcService;
