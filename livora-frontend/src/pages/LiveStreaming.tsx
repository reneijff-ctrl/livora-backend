import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import webRtcService from '../websocket/webRtcService';
import { SignalingType } from '../websocket/webRtcService';
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
  
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const loadInitialData = async () => {
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
    };
    loadInitialData();

    const unsubscribe = subscribe('/topic/streams', (_msg) => {
      loadInitialData();
    });

    return () => {
      if (unsubscribe) unsubscribe.unsubscribe();
      webRtcService.cleanup();
    };
  }, [subscribe, connected]);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeTips = subscribe(`/topic/rooms/${currentRoom.id}/tips`, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === 'ROOM_TIP') {
        const newTip = {
          id: Date.now(),
          ...data.payload
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
    // Example: Fetch live rooms. In a real app, this would be an API call
    // or a WebSocket subscription to /topic/streams
    const unsubscribe = subscribe('/topic/streams', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === SignalingType.STREAM_START) {
        showToast('A new stream has started!', 'info');
        // Refresh rooms list
      }
    });

    return () => {
      if (unsubscribe) unsubscribe.unsubscribe();
      webRtcService.cleanup();
    };
  }, [subscribe]);

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

  const startBroadcast = async () => {
    try {
      const room = await streamingService.startStream({
        title: streamData.title || (user?.email + "'s Stream"),
        description: streamData.description,
        minChatTokens: streamData.minChatTokens,
        isPaid: streamData.isPaid,
        pricePerMessage: streamData.pricePerMessage
      });
      setCurrentRoom(room);
      if (localVideoRef.current) {
        await webRtcService.startBroadcast(room.id, localVideoRef.current);
        setIsBroadcasting(true);
        showToast('You are now LIVE!', 'success');
      }
    } catch (err) {
      console.error('Failed to start broadcast', err);
      showToast('Could not start stream', 'error');
    }
  };

  const stopBroadcast = async () => {
    try {
      await streamingService.stopStream();
      webRtcService.cleanup();
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
    <div style={{ padding: '2rem', fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto', color: '#F4F4F5' }}>
      <SEO title="Live Streaming" />
      
      <PrivateShowCreatorHandler />

      {activePrivateSession && (
        <PrivateShowSessionOverlay 
          sessionId={activePrivateSession.id}
          pricePerMinute={activePrivateSession.pricePerMinute}
          isCreator={true}
          onSessionEnded={() => setActivePrivateSession(null)}
        />
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
            {tip.username} tipped {tip.amount} {tip.animationType === 'fireworks' ? '🔥' : '🪙'}
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
        <h1 style={{ margin: 0, fontSize: 'clamp(1.5rem, 5vw, 2.5rem)', fontWeight: 800 }}>Live Streaming</h1>
        <TokenBalance />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>
            {/* Broadcaster Section */}
            <section style={{ border: '1px solid rgba(255, 255, 255, 0.05)', padding: '2rem', borderRadius: '24px', backgroundColor: '#0F0F14', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
              <h2 style={{ marginTop: 0, color: '#F4F4F5', fontWeight: 700 }}>Creator Studio</h2>
              
              {!isBroadcasting && (
                <div style={{ marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <input 
                    type="text" 
                    placeholder="Stream Title" 
                    value={streamData.title}
                    onChange={e => setStreamData({...streamData, title: e.target.value})}
                    style={{ padding: '0.875rem', borderRadius: '12px', border: '1px solid rgba(255, 255, 255, 0.1)', fontSize: '16px', backgroundColor: '#08080A', color: 'white', outline: 'none' }}
                  />
                  <input 
                    type="text" 
                    placeholder="Description" 
                    value={streamData.description}
                    onChange={e => setStreamData({...streamData, description: e.target.value})}
                    style={{ padding: '0.875rem', borderRadius: '12px', border: '1px solid rgba(255, 255, 255, 0.1)', fontSize: '16px', backgroundColor: '#08080A', color: 'white', outline: 'none' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <label style={{ fontSize: '0.9rem', fontWeight: 'bold', color: '#A1A1AA' }}>Min Chat Tokens:</label>
                    <input 
                      type="number" 
                      value={streamData.minChatTokens}
                      onChange={e => setStreamData({...streamData, minChatTokens: Number(e.target.value)})}
                      style={{ width: '100px', padding: '0.875rem', borderRadius: '12px', border: '1px solid rgba(255, 255, 255, 0.1)', fontSize: '16px', backgroundColor: '#08080A', color: 'white', outline: 'none' }}
                    />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <label style={{ fontSize: '0.9rem', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', color: '#A1A1AA' }}>
                      <input
                        type="checkbox"
                        checked={streamData.isPaid}
                        onChange={e => setStreamData({...streamData, isPaid: e.target.checked})}
                        style={{ accentColor: '#6366f1' }}
                      />
                      Paid Chat (PPV)
                    </label>
                    {streamData.isPaid && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginLeft: '24px' }}>
                        <label style={{ fontSize: '0.8rem', color: '#71717A' }}>Tokens per message:</label>
                        <input
                          type="number"
                          value={streamData.pricePerMessage}
                          onChange={e => setStreamData({...streamData, pricePerMessage: Number(e.target.value)})}
                          style={{ width: '80px', padding: '0.5rem', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.1)', backgroundColor: '#08080A', color: 'white' }}
                          min={1}
                        />
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', backgroundColor: '#000', borderRadius: '16px', overflow: 'hidden', boxShadow: '0 10px 30px rgba(0,0,0,0.5)', border: '1px solid rgba(255, 255, 255, 0.05)' }}>
                <video 
                  ref={localVideoRef} 
                  autoPlay 
                  muted 
                  playsInline 
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                />
                {isBroadcasting && (
                  <div style={{ position: 'absolute', top: '15px', left: '15px', backgroundColor: '#ef4444', color: 'white', padding: '6px 14px', borderRadius: '20px', fontWeight: '800', fontSize: '0.75rem', boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)', border: '1px solid rgba(255, 255, 255, 0.1)', animation: 'pulse 2s infinite' }}>
                    🔴 LIVE
                  </div>
                )}
              </div>
              <div style={{ marginTop: '2rem' }}>
                {!isBroadcasting ? (
                  <button 
                    onClick={startBroadcast}
                    style={{ padding: '1rem 2.5rem', backgroundColor: '#6366f1', color: 'white', border: 'none', borderRadius: '12px', cursor: 'pointer', fontWeight: '800', width: '100%', fontSize: '1.1rem', boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)', transition: 'all 0.2s ease' }}
                  >
                    Go Live
                  </button>
                ) : (
                  <button 
                    onClick={stopBroadcast}
                    style={{ padding: '1rem 2.5rem', backgroundColor: '#ef4444', color: 'white', border: 'none', borderRadius: '12px', cursor: 'pointer', fontWeight: '800', width: '100%', fontSize: '1.1rem', boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)', transition: 'all 0.2s ease' }}
                  >
                    End Stream
                  </button>
                )}
              </div>
            </section>

            {/* Viewer Section */}
            <section style={{ border: '1px solid rgba(255, 255, 255, 0.05)', padding: '2rem', borderRadius: '24px', backgroundColor: '#0F0F14', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
              <h2 style={{ marginTop: 0, color: '#F4F4F5', fontWeight: 700 }}>Watch Stream</h2>
              <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', backgroundColor: '#000', borderRadius: '16px', overflow: 'hidden', boxShadow: '0 10px 30px rgba(0,0,0,0.5)', border: '1px solid rgba(255, 255, 255, 0.05)' }}>
                <video 
                  ref={remoteVideoRef} 
                  autoPlay 
                  playsInline 
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                />
                {!isWatching && (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#71717A', flexDirection: 'column', gap: '1rem' }}>
                    <span style={{ fontSize: '3rem' }}>📺</span>
                    <span style={{ fontWeight: 600 }}>Select a stream to watch</span>
                  </div>
                )}
              </div>
              
              <div style={{ marginTop: '2rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                  <h3 style={{ margin: 0, color: '#F4F4F5' }}>Tipping</h3>
                  {isWatching && (
                    <div style={{ display: 'flex', gap: '0.75rem' }}>
                      <select 
                        value={tipAmount} 
                        onChange={(e) => setTipAmount(Number(e.target.value))}
                        style={{ padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.1)', backgroundColor: '#08080A', color: 'white' }}
                      >
                        <option value={10}>10 🪙</option>
                        <option value={50}>50 🪙</option>
                        <option value={100}>100 🪙</option>
                        <option value={500}>500 💎</option>
                        <option value={1000}>1000 🔥</option>
                      </select>
                      <button 
                        onClick={handleTip}
                        style={{ 
                          backgroundColor: '#fbbf24', 
                          color: '#000',
                          border: 'none', 
                          padding: '8px 16px', 
                          borderRadius: '8px', 
                          fontWeight: '800', 
                          cursor: 'pointer',
                          boxShadow: '0 4px 12px rgba(251, 191, 36, 0.3)',
                          transition: 'all 0.2s ease'
                        }}
                      >
                        Send Tip
                      </button>
                    </div>
                  )}
                </div>

                <h3 style={{ color: '#F4F4F5' }}>Active Streams</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {availableRooms.length === 0 ? (
                    <p style={{ fontSize: '0.9rem', color: '#71717A', fontStyle: 'italic' }}>No active streams right now.</p>
                  ) : (
                    availableRooms.map(room => (
                      <div key={room.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '1rem', border: '1px solid rgba(255, 255, 255, 0.05)', borderRadius: '12px', alignItems: 'center', backgroundColor: '#08080A' }}>
                        <div>
                          <strong style={{ color: '#F4F4F5' }}>{room.streamTitle}</strong>
                          {room.isPremium && <span style={{ marginLeft: '8px' }}>💎</span>}
                          <div style={{ fontSize: '0.75rem', color: '#71717A', marginTop: '2px' }}>{room.viewerCount} viewers</div>
                        </div>
                        <button 
                          onClick={() => watchStream(room.id, room.isPremium)} 
                          style={{ 
                            padding: '8px 16px', 
                            cursor: 'pointer',
                            backgroundColor: room.isPremium ? '#fbbf24' : 'rgba(255, 255, 255, 0.05)',
                            color: room.isPremium ? '#000' : '#F4F4F5',
                            border: room.isPremium ? 'none' : '1px solid rgba(255, 255, 255, 0.1)',
                            borderRadius: '8px',
                            fontWeight: '700',
                            fontSize: '0.875rem',
                            transition: 'all 0.2s ease'
                          }}
                        >
                          Watch
                        </button>
                      </div>
                    ))
                  )}
                </div>

                <div style={{ marginTop: '2.5rem' }}>
                  <VodGallery />
                </div>
              </div>
            </section>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>
          {(isWatching || isBroadcasting) && currentRoom && (
            <div style={{ height: '500px', borderRadius: '24px', overflow: 'hidden', border: '1px solid rgba(255, 255, 255, 0.05)', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
              <LiveChat 
                streamId={activePrivateSession ? `private-session-${activePrivateSession.id}` : currentRoom.id} 
                userId={currentRoom.userId} 
                isPaid={currentRoom.isPaid} 
                pricePerMessage={currentRoom.pricePerMessage} 
              />
            </div>
          )}
          
          <section style={{ border: '1px solid rgba(255, 255, 255, 0.05)', padding: '2rem', borderRadius: '24px', backgroundColor: '#0F0F14', boxShadow: '0 20px 60px rgba(0,0,0,0.6)' }}>
            <h3 style={{ marginTop: 0, color: '#F4F4F5' }}>Badge Shop</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '1.25rem' }}>
              {badges.map(badge => (
                <div key={badge.id} style={{ padding: '1.25rem', border: '1px solid rgba(255, 255, 255, 0.05)', borderRadius: '16px', textAlign: 'center', backgroundColor: '#08080A', transition: 'transform 0.2s ease' }}>
                  <div style={{ fontSize: '2rem', marginBottom: '0.75rem' }}>
                    {badge.name === 'VIP' ? '💎' : badge.name === 'TOP_FAN' ? '🔥' : '⭐'}
                  </div>
                  <h4 style={{ margin: '0.25rem 0', color: '#F4F4F5', fontSize: '1rem' }}>{badge.name}</h4>
                  <p style={{ fontSize: '0.875rem', color: '#71717A', marginBottom: '1rem' }}>{badge.tokenCost} 🪙</p>
                  <button 
                    onClick={() => purchaseBadge(badge.id)}
                    style={{ width: '100%', padding: '10px', backgroundColor: '#6366f1', color: 'white', border: 'none', borderRadius: '10px', cursor: 'pointer', fontSize: '0.875rem', fontWeight: '800', boxShadow: '0 4px 12px rgba(99, 102, 241, 0.2)' }}
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
  );
};

export default LiveStreaming;
