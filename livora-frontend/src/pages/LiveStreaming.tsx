import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { webSocketService } from '../websocket/webSocketService';
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
import { PrivateSession, PrivateSessionStatus } from '../api/privateShowService';

const LiveStreaming: React.FC = () => {
  const navigate = useNavigate();
  const { user, hasPremiumAccess, tokenBalance, refreshTokenBalance } = useAuth();
  const { subscribe, connected } = useWs();
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [isWatching] = useState(false);
  const [currentRoom, setCurrentRoom] = useState<StreamRoom | null>(null);
  const [availableRooms, setAvailableRooms] = useState<StreamRoom[]>([]);
  const [badges, setBadges] = useState<Badge[]>([]);
  const [tips, setTips] = useState<any[]>([]);
  const [tipAmount, setTipAmount] = useState<number>(10);
  const [streamData, setStreamData] = useState({ title: '', description: '', minChatTokens: 0, isPaid: false, pricePerMessage: 0 });
  const [activePrivateSession, setActivePrivateSession] = useState<PrivateSession | null>(null);
  const [chatHeight, setChatHeight] = useState(420);
  
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

    const unsubscribe = subscribe('/topic/streams', (_msg) => {
      loadInitialData();
    });

    return () => {
      if (unsubscribe) unsubscribe.unsubscribe();
      webRtcService.leaveStream();
    };
  }, [subscribe, connected, loadInitialData]);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeTips = subscribe(`/topic/chat/${currentRoom.userId}`, (msg) => {
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
      if (unsubscribeTips) unsubscribeTips.unsubscribe();
    };
  }, [currentRoom?.id, subscribe]);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeViewers = subscribe(`/topic/viewers/${currentRoom.id}`, (msg) => {
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
      if (unsubscribeViewers) unsubscribeViewers.unsubscribe();
    };
  }, [currentRoom?.id, subscribe]);


  useEffect(() => {
    if (!connected || !user) return;

    const unsubscribe = subscribe('/user/queue/private-show/notifications', (message) => {
      const data = JSON.parse(message.body);
      if (data.type === 'PRIVATE_SHOW_STARTED') {
        setActivePrivateSession({
          id: data.payload.sessionId,
          status: PrivateSessionStatus.ACTIVE,
          userId: Number(user.id),
          viewerId: 0, // Not strictly needed here for UI
          pricePerMinute: data.payload.pricePerMinute || 0,
          requestedAt: new Date().toISOString()
        });
      } else if (data.type === 'PRIVATE_SHOW_ENDED') {
        setActivePrivateSession(null);
      }
    });

    return () => {
      if (unsubscribe) unsubscribe.unsubscribe();
    };
  }, [connected, subscribe, user]);

  const handleSignalingData = async (signal: SignalingMessage, roomId: string) => {
    console.debug("STREAMING: Received signaling message (SFU mode):", signal.type);
  };

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
        await webRtcService.connect(roomIdStr, (signal) => {
          handleSignalingData(signal, roomIdStr);
        });

        const routerRtpCapabilities = await webRtcService.sendRequest(SignalingType.GET_ROUTER_CAPABILITIES, roomIdStr);
        const device = await webRtcService.initDevice(routerRtpCapabilities);

        const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, roomIdStr, { direction: 'send' });
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
            (transport.appData.restartAttempts ?? 0) + 1;
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
          producers.current.set('video', await transport.produce({ 
            track: videoTrack,
            encodings: SIMULCAST_ENCODINGS,
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
      
      <PrivateShowCreatorHandler />

      {activePrivateSession && (
        <div className="backdrop-blur-sm">
          <PrivateShowSessionOverlay 
            sessionId={activePrivateSession.id}
            pricePerMinute={activePrivateSession.pricePerMinute}
            isCreator={true}
            onSessionEnded={() => setActivePrivateSession(null)}
          />
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
