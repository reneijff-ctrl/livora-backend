import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import webRtcService, { SignalingType, SignalingMessage } from '../websocket/webRtcService';
import { SIMULCAST_ENCODINGS, VIDEO_CONSTRAINTS } from '@/constants/webrtc';
import { Device, Transport, Producer, Consumer } from 'mediasoup-client';
import { useWs } from '../ws/WsContext';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import TokenStore from '../components/TokenStore';
import tokenService from '../api/tokenService';
import streamingService, { StreamRoom } from '../api/streamingService';
import badgeService, { Badge } from '../api/badgeService';
import LiveChat from '../components/LiveChat';
import TokenBalance from '../components/TokenBalance';
import VodGallery from '../components/VodGallery';
import PrivateShowCreatorHandler from '../components/PrivateShowCreatorHandler';
import PrivateShowSessionOverlay from '../components/PrivateShowSessionOverlay';
import privateShowService, { PrivateSession, PrivateSessionStatus } from '../api/privateShowService';

const LiveStreaming: React.FC = () => {
  const navigate = useNavigate();
  const { user, hasPremiumAccess, tokenBalance, refreshTokenBalance } = useAuth();
  const { subscribe, send, connected } = useWs();
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [isWatching] = useState(false);
  const [currentRoom, setCurrentRoom] = useState<StreamRoom | null>(null);
  const [availableRooms, setAvailableRooms] = useState<StreamRoom[]>([]);
  const [badges, setBadges] = useState<Badge[]>([]);
  const [tips, setTips] = useState<any[]>([]);
  const [tipAmount, setTipAmount] = useState<number>(10);
  const [streamData, setStreamData] = useState({ title: '', description: '', minChatTokens: 0, isPaid: false, pricePerMessage: 0 });
  const [activePrivateSession, setActivePrivateSession] = useState<PrivateSession | null>(null);
  const [isStartingSession, setIsStartingSession] = useState(false);
  const [isEndingSession, setIsEndingSession] = useState(false);
  const [chatHeight, setChatHeight] = useState(420);
  const [isReconnecting, setIsReconnecting] = useState(false);
  const reconnectAttemptsRef = useRef(0);
  const maxReconnectAttempts = 5;
  
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const sendTransport = useRef<Transport | null>(null);
  const recvTransport = useRef<Transport | null>(null);
  const producers = useRef<Map<string, Producer>>(new Map());
  const consumers = useRef<Map<string, Consumer>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);


  const loadInitialData = useCallback(async () => {
    try {
      const [rooms, badgeList] = await Promise.all([
        streamingService.getLiveStreams(),
        badgeService.getBadges()
      ]);
      setAvailableRooms(rooms);
      setBadges(badgeList);
    } catch (e) {
      console.error('Failed to load streaming data', e);
    }
  }, []);

  useEffect(() => {
    loadInitialData();

    const unsubscribe = subscribe('/exchange/amq.topic/streams', (_msg) => {
      loadInitialData();
    });

    return () => {
      if (typeof unsubscribe === 'function') unsubscribe();
      webRtcService.leaveStream();
    };
  }, [subscribe, connected, loadInitialData]);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeTips = subscribe(`/exchange/amq.topic/chat.${currentRoom.userId}`, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === 'TIP') {
        const newTip = {
          id: Date.now(),
          ...data
        };
        setTips(prev => [...prev, newTip]);
        // Auto-remove tip after animation
        setTimeout(() => {
          setTips(prev => prev.filter(t => t.id !== newTip.id));
        }, 5000);
      }
    });

    return () => {
      if (typeof unsubscribeTips === 'function') unsubscribeTips();
    };
  }, [currentRoom?.id, subscribe]);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeViewers = subscribe(`/exchange/amq.topic/viewers.${currentRoom.id}`, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.viewerCount !== undefined) {
        setCurrentRoom(prev => prev ? { ...prev, viewerCount: data.viewerCount } : null);
        // Also update in availableRooms list if present
        setAvailableRooms(prev => prev.map(room => 
          room.id === currentRoom.id ? { ...room, viewerCount: data.viewerCount } : room
        ));
      }
    });

    return () => {
      if (typeof unsubscribeViewers === 'function') unsubscribeViewers();
    };
  }, [currentRoom?.id, subscribe]);


  useEffect(() => {
    if (!connected || !user) return;

    const unsubscribe = subscribe('/user/queue/private-show-status', (message) => {
      const data = JSON.parse(message.body);
      if (data.type === 'PRIVATE_SHOW_ACCEPTED') {
        setActivePrivateSession({
          id: data.payload.sessionId,
          status: PrivateSessionStatus.ACCEPTED,
          creatorId: Number(user.id),
          viewerId: data.payload.viewerId || 0,
          pricePerMinute: data.payload.pricePerMinute || 0,
        });
      } else if (data.type === 'PRIVATE_SHOW_STARTED') {
        setActivePrivateSession(prev => ({
          id: data.payload.sessionId,
          status: PrivateSessionStatus.ACTIVE,
          creatorId: Number(user.id),
          viewerId: prev?.viewerId || 0,
          pricePerMinute: prev?.pricePerMinute || data.payload.pricePerMinute || 0,
        }));
      } else if (data.type === 'PRIVATE_SHOW_ENDED') {
        setActivePrivateSession(null);
        setIsStartingSession(false);
      }
    });

    return () => {
      if (typeof unsubscribe === 'function') unsubscribe();
    };
  }, [connected, subscribe, user]);

  const handleStartSession = async () => {
    if (!activePrivateSession || isStartingSession) return;
    setIsStartingSession(true);
    try {
      const updated = await privateShowService.startSession(activePrivateSession.id);
      setActivePrivateSession({
        id: updated.id,
        viewerId: updated.viewerId,
        creatorId: updated.creatorId,
        pricePerMinute: updated.pricePerMinute,
        status: PrivateSessionStatus.ACTIVE,
      });
      showToast('Private session started', 'success');
    } catch (error) {
      console.error('Failed to start private session', error);
      showToast('Failed to start session', 'error');
    } finally {
      setIsStartingSession(false);
    }
  };

  const handleEndSession = async () => {
    if (!activePrivateSession || isEndingSession) return;
    setIsEndingSession(true);
    try {
      await privateShowService.endSession(activePrivateSession.id);
      showToast('Private session ended', 'success');
    } catch (e) {
      console.error('Failed to end private session', e);
      showToast('Failed to end session', 'error');
    } finally {
      setIsEndingSession(false);
    }
  };

  const handleSignalingData = async (signal: SignalingMessage, roomId: string) => {
    console.debug("STREAMING: Received signaling message (SFU mode):", signal.type);
  };

  // Auto re-publish stream after node failure (creator side recovery)
  const republishStream = useCallback(async () => {
    if (!currentRoom || !localStreamRef.current || !user?.id) return;

    reconnectAttemptsRef.current += 1;
    const attempt = reconnectAttemptsRef.current;

    if (attempt > maxReconnectAttempts) {
      console.error('PRODUCER: Max republish attempts reached, giving up');
      showToast('Stream connection lost. Please restart your broadcast.', 'error');
      setIsReconnecting(false);
      return;
    }

    console.log(`PRODUCER: Republishing stream (attempt ${attempt}/${maxReconnectAttempts})...`);
    setIsReconnecting(true);

    try {
      // Backoff delay
      const delay = attempt * 1000;
      await new Promise(resolve => setTimeout(resolve, delay));

      const roomIdStr = currentRoom.id;
      const stream = localStreamRef.current;

      // Clean up old transport
      if (sendTransport.current && !sendTransport.current.closed) {
        sendTransport.current.close();
      }
      sendTransport.current = null;
      producers.current.clear();
      webRtcService.cleanup();

      // Re-connect signaling
      webRtcService.setCurrentUserId(Number(user.id));
      webRtcService.connect(roomIdStr, (signal) => {
        handleSignalingData(signal, roomIdStr);
      }, { subscribe, send });

      // Re-create transport and produce
      const routerRtpCapabilities = await webRtcService.sendRequest(SignalingType.GET_ROUTER_CAPABILITIES, roomIdStr);
      const device = await webRtcService.initDevice(routerRtpCapabilities);

      const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, roomIdStr, { direction: 'send' });
      const transport = device.createSendTransport({
        ...transportData,
        iceTransportPolicy: (window as any).FORCE_TURN ? 'relay' : 'all'
      });
      sendTransport.current = transport;

      transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
        try {
          await webRtcService.sendRequest(SignalingType.CONNECT_TRANSPORT, roomIdStr, { transportId: transport.id, dtlsParameters });
          callback();
        } catch (e: any) { errback(e); }
      });

      transport.on('produce', async ({ kind, rtpParameters, appData }, callback, errback) => {
        try {
          const { id } = await webRtcService.sendRequest(SignalingType.PRODUCE, roomIdStr, { transportId: transport.id, kind, rtpParameters, appData });
          callback({ id });
        } catch (e: any) { errback(e); }
      });

      const videoTrack = stream.getVideoTracks()[0];
      const audioTrack = stream.getAudioTracks()[0];
      if (videoTrack) {
        const isFirefox = navigator.userAgent.toLowerCase().includes('firefox');
        
        // [FIX-FIREFOX-02]: Single encoding for testing as per task requirements.
        // Firefox sometimes has issues with multiple simulcast layers on connection start.
        const encodings = [
          { 
            rid: "r0", 
            maxBitrate: 2500000, 
            scaleResolutionDownBy: 1.0 
          }
        ];

        producers.current.set('video', await transport.produce({
          track: videoTrack,
          encodings,
          codecOptions: { videoGoogleStartBitrate: 1000 }
        }));
      }
      if (audioTrack) {
        producers.current.set('audio', await transport.produce({ track: audioTrack }));
      }

      reconnectAttemptsRef.current = 0;
      setIsReconnecting(false);
      console.log('PRODUCER: Stream republished successfully after node failure');
      showToast('Stream recovered automatically', 'success');
    } catch (err) {
      console.error('PRODUCER: Failed to republish stream', err);
      // Retry after delay
      setTimeout(() => republishStream(), 2000);
    }
  }, [currentRoom, user?.id, subscribe, send]);

  // Listen for STREAM_RESTART_REQUIRED (creator-side: auto re-publish)
  useEffect(() => {
    if (!connected || !isBroadcasting || !currentRoom) return;
    let unsub = () => {};

    const result = subscribe('/exchange/amq.topic/streams.status', (msg) => {
      try {
        const event = JSON.parse(msg.body);
        if (event.type === 'STREAM_RESTART_REQUIRED') {
          const affectedStreamId = event.streamId;
          if (affectedStreamId === currentRoom.id || affectedStreamId === currentRoom.roomId) {
            console.log('PRODUCER: Node failure detected, initiating auto re-publish...');
            republishStream();
          }
        }
      } catch (e) {
        console.error('Error parsing stream status event', e);
      }
    });
    if (typeof result === 'function') {
      unsub = result;
    }

    return () => { unsub(); };
  }, [connected, subscribe, isBroadcasting, currentRoom, republishStream]);

  const startBroadcast = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ 
        video: VIDEO_CONSTRAINTS, 
        audio: true 
      });
      localStreamRef.current = stream;
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = stream;
      }

      const room = await streamingService.startStream({
        title: streamData.title || (user?.email + "'s Stream"),
        description: streamData.description,
        minChatTokens: streamData.minChatTokens,
        isPaid: streamData.isPaid,
        pricePerMessage: streamData.pricePerMessage
      });
      setCurrentRoom(room);
      
      if (user?.id && room.id) {
        const roomIdStr = room.id;
        
        // Mediasoup Publish Flow
        webRtcService.setCurrentUserId(Number(user.id));
        webRtcService.connect(roomIdStr, (signal) => {
          handleSignalingData(signal, roomIdStr);
        }, { subscribe, send });

        const routerRtpCapabilities = await webRtcService.sendRequest(SignalingType.GET_ROUTER_CAPABILITIES, roomIdStr);
        const device = await webRtcService.initDevice(routerRtpCapabilities);

        const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, roomIdStr, { direction: 'send' });
        console.log("SEND TRANSPORT CONFIG:", JSON.stringify(transportData, null, 2));
        const transport = device.createSendTransport(transportData);
        sendTransport.current = transport;

        transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
          try {
            await webRtcService.sendRequest(SignalingType.CONNECT_TRANSPORT, roomIdStr, { transportId: transport.id, dtlsParameters });
            callback();
          } catch (e: any) { errback(e); }
        });

        transport.on('produce', async ({ kind, rtpParameters, appData }, callback, errback) => {
          try {
            const { id } = await webRtcService.sendRequest(SignalingType.PRODUCE, roomIdStr, { transportId: transport.id, kind, rtpParameters, appData });
            callback({ id });
          } catch (e: any) { errback(e); }
        });

        const attemptIceRestart = async () => {
          if (!transport || transport.closed) return;

          if (!transport.appData.restartAttempts) {
            transport.appData.restartAttempts = 0;
          }

          if ((transport.appData.restartAttempts as number) >= 3) {
            console.error('PRODUCER: Max ICE restart attempts reached, stopping.');
            return;
          }

          transport.appData.restartAttempts =
            (Number(transport.appData.restartAttempts) || 0) + 1;
          const attempt = transport.appData.restartAttempts;

          try {
            // Add 3-6 second randomized delay between restart attempts to prevent signaling storms
            const jitterDelay = Math.floor(Math.random() * 3000) + 3000;
            await new Promise((resolve) => setTimeout(resolve, jitterDelay));
            if (transport.closed) return;

            console.log(`PRODUCER: Initiating ICE restart (attempt ${attempt}) after ${jitterDelay}ms delay...`);

            const response = await webRtcService.sendRequest(SignalingType.RESTART_ICE, roomIdStr, {
              transportId: transport.id
            });

            await transport.restartIce({
              iceParameters: response.iceParameters
            });
            console.log('PRODUCER: ICE restart success, gathering candidates...');
          } catch (e) {
            console.error('PRODUCER: ICE restart failed', e);
          }
        };

        transport.on('connectionstatechange', async (state) => {
          if (state === 'failed') {
            console.log('PRODUCER: SendTransport connection failed, initiating immediate restart...');
            attemptIceRestart();
          } else if (state === 'disconnected') {
            const jitterDelay = Math.floor(Math.random() * 3000) + 3000;
            console.log(`PRODUCER: SendTransport connection disconnected, waiting ${jitterDelay}ms before restart check...`);
            setTimeout(() => {
              if (transport.connectionState === 'disconnected') {
                console.log(`PRODUCER: Still disconnected after ${jitterDelay}ms, initiating restart...`);
                attemptIceRestart();
              } else {
                console.log('PRODUCER: Recovered from disconnected state automatically.');
              }
            }, jitterDelay);
          }
        });

        const videoTrack = stream.getVideoTracks()[0];
        const audioTrack = stream.getAudioTracks()[0];
        if (videoTrack) {
          // Firefox does not support scalabilityMode in RTCRtpEncodingParameters;
          // guard with a browser-safe copy that strips any residual SVC fields.
          const isFirefox = navigator.userAgent.toLowerCase().includes("firefox");
          
          // [FIX-FIREFOX-02]: Single encoding for testing as per task requirements.
          // Firefox sometimes has issues with multiple simulcast layers on connection start.
          const encodings = [
            { 
              rid: "r0", 
              maxBitrate: 2500000, 
              scaleResolutionDownBy: 1.0 
            }
          ];

          producers.current.set('video', await transport.produce({ 
            track: videoTrack,
            encodings,
            codecOptions: {
              videoGoogleStartBitrate: 1000
            }
          }));
        }
        if (audioTrack) producers.current.set('audio', await transport.produce({ track: audioTrack }));

        setIsBroadcasting(true);
        showToast('You are now LIVE!', 'success');
      }
    } catch (err) {
      console.error('Failed to start broadcast', err);
      showToast('Could not start stream', 'error');
      webRtcService.cleanup();
    }
  };

  const stopBroadcast = async () => {
    try {
      await streamingService.stopStream();
      webRtcService.leaveStream();
      
      localStreamRef.current?.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = null;
      }

      sendTransport.current = null;
      producers.current.clear();

      setIsBroadcasting(false);
      showToast('Broadcast ended', 'info');
    } catch (err) {
      console.error('Failed to stop stream', err);
    }
  };

  const watchStream = (roomId: string, isPremium: boolean) => {
    if (isPremium && !hasPremiumAccess()) {
      showToast('This is a premium stream. Please upgrade your subscription.', 'error');
      return;
    }
    navigate(`/stream/${roomId}`);
  };

  const handleTip = async () => {
    if (!currentRoom) return;
    if (tokenBalance < tipAmount) {
      showToast('Insufficient token balance', 'error');
      return;
    }

    try {
      const clientRequestId = crypto.randomUUID();
      await tokenService.sendTip(currentRoom.id, tipAmount, clientRequestId);
      showToast(`Sent ${tipAmount} tokens!`, 'success');
      refreshTokenBalance();
    } catch (error) {
      console.error('Failed to send tip', error);
      showToast('Tip failed. Please try again.', 'error');
    }
  };

  const purchaseBadge = async (badgeId: string) => {
    try {
      await badgeService.purchaseBadge(badgeId);
      showToast('Badge purchased!', 'success');
      refreshTokenBalance();
    } catch (e) {
      showToast('Purchase failed', 'error');
    }
  };

  return (
    <div className="bg-gradient-to-b from-zinc-950 via-black to-black min-h-screen">
      <div className="px-8 xl:px-12 py-6 font-sans text-zinc-100">
        <SEO title="Live Streaming" />
      
      <PrivateShowCreatorHandler onSessionAccepted={(session) => {
        setActivePrivateSession({
          id: session.id,
          viewerId: session.viewerId,
          creatorId: session.creatorId,
          pricePerMinute: session.pricePerMinute,
          status: PrivateSessionStatus.ACCEPTED,
        });
      }} />

      {activePrivateSession?.status === PrivateSessionStatus.ACCEPTED && (
        <div className="mb-6 p-4 bg-purple-600/20 border border-purple-500/30 rounded-2xl flex items-center justify-between">
          <div className="text-sm text-purple-200">
            <span className="font-bold text-white">Private session accepted</span>
            <span className="ml-2 text-purple-300">— {activePrivateSession.pricePerMinute} 🪙/min</span>
          </div>
          <button
            onClick={handleStartSession}
            disabled={isStartingSession}
            className="px-5 py-2.5 bg-purple-600 hover:bg-purple-700 disabled:bg-purple-600/50 disabled:cursor-not-allowed text-white rounded-xl font-bold text-sm shadow-lg shadow-purple-600/20 transition-all duration-200"
          >
            {isStartingSession ? 'Starting...' : '▶ Start Private Session'}
          </button>
        </div>
      )}

      {activePrivateSession?.status === PrivateSessionStatus.ACTIVE && (
        <div className="mb-6">
          <div className="mb-3 p-4 bg-red-600/15 border border-red-500/30 rounded-2xl flex items-center justify-between">
            <span className="px-2.5 py-1 bg-red-500/15 border border-red-500/40 rounded-full text-red-400 text-sm font-bold">🔴 Private Active</span>
            <button
              onClick={handleEndSession}
              disabled={isEndingSession}
              className="px-5 py-2.5 bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-xl font-bold text-sm shadow-lg transition-all duration-200"
            >
              {isEndingSession ? 'Ending...' : 'End Private Session'}
            </button>
          </div>
          <div className="backdrop-blur-sm">
            <PrivateShowSessionOverlay
              sessionId={activePrivateSession.id}
              pricePerMinute={activePrivateSession.pricePerMinute}
              isCreator={true}
              onSessionEnded={() => setActivePrivateSession(null)}
            />
          </div>
        </div>
      )}
      
      {/* Tip Animations Overlay */}
      <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none', zIndex: 1000, overflow: 'hidden' }}>
        {tips.map(tip => (
          <div key={tip.id} className={`tip-animation ${tip.animationType}`} style={{ 
            position: 'absolute', 
            left: `${Math.random() * 80 + 10}%`, 
            top: '80%',
            backgroundColor: 'rgba(99, 102, 241, 0.95)',
            color: 'white',
            padding: '12px 24px',
            borderRadius: '30px',
            fontWeight: '800',
            boxShadow: '0 10px 30px rgba(0,0,0,0.4)',
            animation: 'fly-up 4s ease-out forwards',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            backdropFilter: 'blur(8px)'
          }}>
            {tip.senderUsername} tipped {tip.amount} {tip.animationType === 'fireworks' ? '🔥' : '🪙'}
          </div>
        ))}
      </div>

      <div className="flex justify-between items-center mb-8 flex-wrap gap-8">
        <h1 className="m-0 text-4xl md:text-5xl font-extrabold tracking-tight">Live Streaming</h1>
        <TokenBalance />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1.4fr_0.9fr] gap-8">
        {/* Left Column: Video dominance */}
        <div className="flex flex-col space-y-10">
          {/* Broadcaster Section */}
            <section className="bg-black/40 backdrop-blur-xl border border-white/5 p-8 rounded-3xl shadow-2xl shadow-black/40">
              <h2 style={{ marginTop: 0, color: '#F4F4F5', fontWeight: 700 }}>Creator Studio</h2>
              
              {!isBroadcasting && (
                <div style={{ marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <input 
                    type="text" 
                    placeholder="Stream Title" 
                    value={streamData.title}
                    onChange={e => setStreamData({...streamData, title: e.target.value})}
                    className="w-full p-3.5 rounded-xl border border-white/10 bg-white/5 text-white outline-none focus:border-purple-500/50 transition-colors"
                  />
                  <input 
                    type="text" 
                    placeholder="Description" 
                    value={streamData.description}
                    onChange={e => setStreamData({...streamData, description: e.target.value})}
                    className="w-full p-3.5 rounded-xl border border-white/10 bg-white/5 text-white outline-none focus:border-purple-500/50 transition-colors"
                  />
                  <div className="flex items-center gap-4">
                    <label className="text-sm font-bold text-zinc-400">Min Chat Tokens:</label>
                    <input 
                      type="number" 
                      value={streamData.minChatTokens}
                      onChange={e => setStreamData({...streamData, minChatTokens: Number(e.target.value)})}
                      className="w-24 p-3.5 rounded-xl border border-white/10 bg-white/5 text-white outline-none focus:border-purple-500/50 transition-colors"
                    />
                  </div>
                  <div className="flex flex-col gap-2">
                    <label className="text-sm font-bold flex items-center gap-2 cursor-pointer text-zinc-400">
                      <input
                        type="checkbox"
                        checked={streamData.isPaid}
                        onChange={e => setStreamData({...streamData, isPaid: e.target.checked})}
                        className="accent-purple-500 h-4 w-4"
                      />
                      Paid Chat (PPV)
                    </label>
                    {streamData.isPaid && (
                      <div className="flex items-center gap-4 ml-6">
                        <label className="text-xs text-zinc-500">Tokens per message:</label>
                        <input
                          type="number"
                          value={streamData.pricePerMessage}
                          onChange={e => setStreamData({...streamData, pricePerMessage: Number(e.target.value)})}
                          className="w-20 p-2 rounded-lg border border-white/10 bg-white/5 text-white outline-none focus:border-purple-500/50 transition-colors"
                          min={1}
                        />
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div className="relative rounded-3xl overflow-hidden bg-gradient-to-br from-black via-zinc-900 to-black border border-white/10 shadow-2xl shadow-purple-500/10">
                <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', overflow: 'hidden' }}>
                  <video 
                    ref={localVideoRef} 
                    autoPlay 
                    muted 
                    playsInline 
                    className="w-full h-full object-cover" 
                  />
                  {isBroadcasting && (
                    <div className="absolute top-6 left-6 flex items-center gap-3">
                      <div className="px-4 py-1 rounded-full bg-red-600/90 text-xs font-semibold tracking-wider shadow-lg shadow-red-500/30">
                        🔴 LIVE
                      </div>
                      <div className="px-4 py-1 rounded-full bg-black/60 backdrop-blur-md text-xs border border-white/10">
                        {currentRoom?.viewerCount || 0} Viewers
                      </div>
                    </div>
                  )}
                </div>
              </div>
              <div className="mt-8">
                {!isBroadcasting ? (
                  <button 
                    onClick={startBroadcast}
                    className="w-full py-4 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-bold text-lg shadow-lg shadow-purple-600/20 transition-all duration-200"
                  >
                    Go Live
                  </button>
                ) : (
                  <button 
                    onClick={stopBroadcast}
                    className="bg-red-600 hover:bg-red-700 shadow-lg shadow-red-600/30 rounded-xl px-6 py-2 font-semibold transition-all duration-200"
                  >
                    Stop Live
                  </button>
                )}
              </div>
            </section>

            {/* Viewer Section */}
            <section className="bg-black/40 backdrop-blur-xl border border-white/5 p-8 rounded-3xl shadow-2xl shadow-black/40">
              <h2 style={{ marginTop: 0, color: '#F4F4F5', fontWeight: 700 }}>Watch Stream</h2>
              <div className="relative rounded-3xl overflow-hidden bg-gradient-to-br from-black via-zinc-900 to-black border border-white/10 shadow-2xl shadow-purple-500/10">
                <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', overflow: 'hidden' }}>
                  <video 
                    ref={remoteVideoRef} 
                    autoPlay 
                    playsInline 
                    className="w-full h-full object-cover" 
                  />
                  {!isWatching && (
                    <div className="backdrop-blur-sm flex flex-col items-center justify-center h-full text-zinc-500 gap-4 absolute inset-0 bg-black/40">
                      <span className="text-5xl">📺</span>
                      <span className="font-semibold text-zinc-400">Select a stream to watch</span>
                    </div>
                  )}
                </div>
              </div>
              
              <div className="mt-8">
                <div className="flex justify-between items-center mb-6">
                  <h3 className="m-0 text-zinc-100">Tipping</h3>
                  {isWatching && (
                    <div className="flex gap-3">
                      <select 
                        value={tipAmount} 
                        onChange={(e) => setTipAmount(Number(e.target.value))}
                        className="p-2 rounded-lg border border-white/10 bg-white/5 text-white outline-none focus:border-purple-500/50 transition-colors"
                      >
                        <option value={10}>10 🪙</option>
                        <option value={50}>50 🪙</option>
                        <option value={100}>100 🪙</option>
                        <option value={500}>500 💎</option>
                        <option value={1000}>1000 🔥</option>
                      </select>
                      <button 
                        onClick={handleTip}
                        className="bg-amber-400 hover:bg-amber-500 text-black px-4 py-2 rounded-lg font-extrabold shadow-lg shadow-amber-400/20 transition-all duration-200"
                      >
                        Send Tip
                      </button>
                    </div>
                  )}
                </div>

                <h3 className="text-zinc-100">Active Streams</h3>
                <div className="flex flex-col gap-6">
                  {availableRooms.length === 0 ? (
                    <p style={{ fontSize: '0.9rem', color: '#71717A', fontStyle: 'italic' }}>No active streams right now.</p>
                  ) : (
                    availableRooms.map(room => (
                    <div key={room.id} className="flex justify-between p-4 border border-white/5 rounded-2xl items-center bg-white/5">
                        <div>
                          <strong style={{ color: '#F4F4F5' }}>{room.streamTitle}</strong>
                          {room.isPremium && <span style={{ marginLeft: '8px' }}>💎</span>}
                          <div style={{ fontSize: '0.75rem', color: '#71717A', marginTop: '2px' }}>{room.viewerCount} viewers</div>
                        </div>
                        <button 
                          onClick={() => watchStream(room.id, room.isPremium)} 
                          className={`px-4 py-2 rounded-lg font-bold text-sm transition-all duration-200 ${
                            room.isPremium 
                              ? 'bg-amber-400 hover:bg-amber-500 text-black shadow-lg shadow-amber-400/20' 
                              : 'bg-white/5 hover:bg-white/10 text-zinc-100 border border-white/10'
                          }`}
                        >
                          Watch
                        </button>
                      </div>
                    ))
                  )}
                </div>

              </div>
            </section>

            <VodGallery />
        </div>

        {/* Right Column: Chat & Store */}
        <div className="flex flex-col space-y-10">
          {(isWatching || isBroadcasting) && currentRoom && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-4 px-2">
                <span className="text-[10px] uppercase tracking-widest text-gray-500">Chat Height</span>
                <input
                  type="range"
                  min={300}
                  max={800}
                  step={10}
                  value={chatHeight}
                  onChange={(e) => setChatHeight(Number(e.target.value))}
                  className="w-full accent-purple-500"
                />
              </div>
              <div 
                className="shadow-2xl shadow-black/40 rounded-3xl overflow-hidden"
                style={{ height: chatHeight }}
              >
                <LiveChat 
                  streamId={activePrivateSession ? `private-session-${activePrivateSession.id}` : currentRoom.id} 
                  userId={currentRoom.userId} 
                  isPaid={currentRoom.isPaid} 
                  pricePerMessage={currentRoom.pricePerMessage} 
                />
              </div>
            </div>
          )}
          
          <section className="bg-black/40 backdrop-blur-xl border border-white/5 p-8 rounded-3xl shadow-2xl shadow-black/40">
            <h3 className="mt-0 text-zinc-100">Badge Shop</h3>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-6">
              {badges.map(badge => (
                <div key={badge.id} className="p-5 border border-white/5 rounded-2xl text-center bg-white/5 transition-transform duration-200 hover:scale-[1.02]">
                  <div style={{ fontSize: '2rem', marginBottom: '0.75rem' }}>
                    {badge.name === 'VIP' ? '💎' : badge.name === 'TOP_FAN' ? '🔥' : '⭐'}
                  </div>
                  <h4 style={{ margin: '0.25rem 0', color: '#F4F4F5', fontSize: '1rem' }}>{badge.name}</h4>
                  <p style={{ fontSize: '0.875rem', color: '#71717A', marginBottom: '1rem' }}>{badge.tokenCost} 🪙</p>
                  <button 
                    onClick={() => purchaseBadge(badge.id)}
                    className="w-full py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-bold text-sm shadow-lg shadow-purple-600/20 transition-all duration-200"
                  >
                    Buy
                  </button>
                </div>
              ))}
            </div>
          </section>

          <TokenStore />
        </div>
      </div>

      <style>{`
        @keyframes fly-up {
          0% { transform: translateY(0) scale(0.5); opacity: 0; }
          10% { opacity: 1; transform: translateY(-20px) scale(1.1); }
          100% { transform: translateY(-500px) scale(1); opacity: 0; }
        }
      `}</style>
      </div>
    </div>
  );
};

export default LiveStreaming;
