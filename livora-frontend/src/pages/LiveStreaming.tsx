import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../auth/useAuth';
import webRtcService from '../websocket/webRtcService';
import { SignalingType } from '../websocket/webRtcService';
import webSocketService from '../websocket/webSocketService';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import TokenStore from '../components/TokenStore';
import tokenService from '../api/tokenService';
import streamingService, { StreamRoom } from '../api/streamingService';
import badgeService, { Badge } from '../api/badgeService';
import StreamChat from '../components/StreamChat';

const LiveStreaming: React.FC = () => {
  const { user, hasPremiumAccess, tokenBalance, refreshTokenBalance } = useAuth();
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [isWatching, setIsWatching] = useState(false);
  const [currentRoom, setCurrentRoom] = useState<StreamRoom | null>(null);
  const [availableRooms, setAvailableRooms] = useState<StreamRoom[]>([]);
  const [badges, setBadges] = useState<Badge[]>([]);
  const [tips, setTips] = useState<any[]>([]);
  const [tipAmount, setTipAmount] = useState<number>(10);
  const [streamData, setStreamData] = useState({ title: '', description: '', minChatTokens: 0 });
  
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

    const unsubscribe = webSocketService.subscribe('/topic/streams', (msg) => {
      loadInitialData();
    });

    return () => {
      unsubscribe();
      webRtcService.cleanup();
    };
  }, []);

  useEffect(() => {
    if (!currentRoom) return;

    const unsubscribeTips = webSocketService.subscribe(`/topic/rooms/${currentRoom.id}/tips`, (msg) => {
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

    return () => unsubscribeTips();
  }, [currentRoom?.id]);

  useEffect(() => {
    // Example: Fetch live rooms. In a real app, this would be an API call
    // or a WebSocket subscription to /topic/streams
    const unsubscribe = webSocketService.subscribe('/topic/streams', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === SignalingType.STREAM_START) {
        showToast('A new stream has started!', 'info');
        // Refresh rooms list
      }
    });

    return () => {
      unsubscribe();
      webRtcService.cleanup();
    };
  }, []);

  const startBroadcast = async () => {
    try {
      const room = await streamingService.startStream({
        title: streamData.title || (user?.email + "'s Stream"),
        description: streamData.description,
        minChatTokens: streamData.minChatTokens
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

  const watchStream = async (roomId: string, isPremium: boolean) => {
    if (isPremium && !hasPremiumAccess()) {
      showToast('This is a premium stream. Please upgrade your subscription.', 'error');
      return;
    }

    try {
      const room = await streamingService.getStream(roomId);
      setCurrentRoom(room);
      setIsWatching(true);
      webRtcService.joinStream(roomId, (stream) => {
        if (remoteVideoRef.current) {
          remoteVideoRef.current.srcObject = stream;
        }
      });
    } catch (e) {
      showToast('Failed to join stream', 'error');
    }
  };

  const handleTip = async () => {
    if (!currentRoom) return;
    if (tokenBalance < tipAmount) {
      showToast('Insufficient token balance', 'error');
      return;
    }

    try {
      await tokenService.sendTip(currentRoom.id, tipAmount);
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
    <div style={{ padding: '1rem', fontFamily: 'sans-serif', maxWidth: '1200px', margin: '0 auto' }}>
      <SEO title="Live Streaming" />
      
      {/* Tip Animations Overlay */}
      <div style={{ position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none', zIndex: 1000, overflow: 'hidden' }}>
        {tips.map(tip => (
          <div key={tip.id} className={`tip-animation ${tip.animationType}`} style={{ 
            position: 'absolute', 
            left: `${Math.random() * 80 + 10}%`, 
            top: '80%',
            backgroundColor: 'rgba(103, 114, 229, 0.9)',
            color: 'white',
            padding: '10px 20px',
            borderRadius: '30px',
            fontWeight: 'bold',
            boxShadow: '0 4px 15px rgba(0,0,0,0.2)',
            animation: 'fly-up 4s ease-out forwards'
          }}>
            {tip.username} tipped {tip.amount} {tip.animationType === 'fireworks' ? '🔥' : '🪙'}
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <h1 style={{ margin: 0, fontSize: 'clamp(1.5rem, 5vw, 2.5rem)' }}>Live Streaming</h1>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div style={{ backgroundColor: '#f0f4f8', padding: '8px 16px', borderRadius: '20px', fontWeight: 'bold' }}>
            Balance: {tokenBalance} 🪙
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>
            {/* Broadcaster Section */}
            <section style={{ border: '1px solid #ddd', padding: '1.5rem', borderRadius: '12px', backgroundColor: '#fff' }}>
              <h2 style={{ marginTop: 0 }}>Creator Studio</h2>
              
              {!isBroadcasting && (
                <div style={{ marginBottom: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <input 
                    type="text" 
                    placeholder="Stream Title" 
                    value={streamData.title}
                    onChange={e => setStreamData({...streamData, title: e.target.value})}
                    style={{ padding: '0.8rem', borderRadius: '8px', border: '1px solid #ddd', fontSize: '16px' }}
                  />
                  <input 
                    type="text" 
                    placeholder="Description" 
                    value={streamData.description}
                    onChange={e => setStreamData({...streamData, description: e.target.value})}
                    style={{ padding: '0.8rem', borderRadius: '8px', border: '1px solid #ddd', fontSize: '16px' }}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <label style={{ fontSize: '0.9rem', fontWeight: 'bold' }}>Min Chat Tokens:</label>
                    <input 
                      type="number" 
                      value={streamData.minChatTokens}
                      onChange={e => setStreamData({...streamData, minChatTokens: Number(e.target.value)})}
                      style={{ width: '100px', padding: '0.8rem', borderRadius: '8px', border: '1px solid #ddd', fontSize: '16px' }}
                    />
                  </div>
                </div>
              )}

              <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', backgroundColor: '#000', borderRadius: '8px', overflow: 'hidden', boxShadow: '0 4px 12px rgba(0,0,0,0.2)' }}>
                <video 
                  ref={localVideoRef} 
                  autoPlay 
                  muted 
                  playsInline 
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                />
                {isBroadcasting && (
                  <div style={{ position: 'absolute', top: '15px', left: '15px', backgroundColor: '#ff4d4f', color: 'white', padding: '4px 12px', borderRadius: '20px', fontWeight: 'bold', fontSize: '0.8rem', boxShadow: '0 2px 4px rgba(0,0,0,0.2)' }}>
                    🔴 LIVE
                  </div>
                )}
              </div>
              <div style={{ marginTop: '1.5rem' }}>
                {!isBroadcasting ? (
                  <button 
                    onClick={startBroadcast}
                    style={{ padding: '1rem 2rem', backgroundColor: '#6772e5', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', width: '100%', fontSize: '1.1rem' }}
                  >
                    Go Live
                  </button>
                ) : (
                  <button 
                    onClick={stopBroadcast}
                    style={{ padding: '1rem 2rem', backgroundColor: '#ff4d4f', color: 'white', border: 'none', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', width: '100%', fontSize: '1.1rem' }}
                  >
                    End Stream
                  </button>
                )}
              </div>
            </section>

            {/* Viewer Section */}
            <section style={{ border: '1px solid #ddd', padding: '1.5rem', borderRadius: '12px', backgroundColor: '#fff' }}>
              <h2 style={{ marginTop: 0 }}>Watch Stream</h2>
              <div style={{ position: 'relative', width: '100%', aspectRatio: '16/9', backgroundColor: '#000', borderRadius: '8px', overflow: 'hidden', boxShadow: '0 4px 12px rgba(0,0,0,0.2)' }}>
                <video 
                  ref={remoteVideoRef} 
                  autoPlay 
                  playsInline 
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }} 
                />
                {!isWatching && (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#999', flexDirection: 'column', gap: '1rem' }}>
                    <span style={{ fontSize: '3rem' }}>📺</span>
                    <span>Select a stream to watch</span>
                  </div>
                )}
              </div>
              
              <div style={{ marginTop: '1.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                  <h3 style={{ margin: 0 }}>Tipping</h3>
                  {isWatching && (
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <select 
                        value={tipAmount} 
                        onChange={(e) => setTipAmount(Number(e.target.value))}
                        style={{ padding: '4px' }}
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
                          backgroundColor: '#ffd700', 
                          border: 'none', 
                          padding: '4px 12px', 
                          borderRadius: '4px', 
                          fontWeight: 'bold', 
                          cursor: 'pointer' 
                        }}
                      >
                        Send Tip
                      </button>
                    </div>
                  )}
                </div>

                <h3>Active Streams</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {availableRooms.length === 0 ? (
                    <p style={{ fontSize: '0.9rem', color: '#999' }}>No active streams right now.</p>
                  ) : (
                    availableRooms.map(room => (
                      <div key={room.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem', border: '1px solid #eee', borderRadius: '4px', alignItems: 'center' }}>
                        <div>
                          <strong>{room.streamTitle}</strong>
                          {room.isPremium && <span style={{ marginLeft: '5px' }}>💎</span>}
                          <div style={{ fontSize: '0.7rem', color: '#666' }}>{room.viewerCount} viewers</div>
                        </div>
                        <button 
                          onClick={() => watchStream(room.id, room.isPremium)} 
                          style={{ 
                            padding: '4px 12px', 
                            cursor: 'pointer',
                            backgroundColor: room.isPremium ? '#ffd700' : '#e2e8f0',
                            border: 'none',
                            borderRadius: '4px'
                          }}
                        >
                          Watch
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </section>
          </div>
        </div>

        {/* Sidebar Replacement with Grid for Responsiveness */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>
          {isWatching && currentRoom && (
            <div style={{ height: '500px' }}>
              <StreamChat streamId={currentRoom.id} minChatTokens={currentRoom.minChatTokens} />
            </div>
          )}
          
          <section style={{ border: '1px solid #ddd', padding: '1.5rem', borderRadius: '12px', backgroundColor: '#fff' }}>
            <h3 style={{ marginTop: 0 }}>Badge Shop</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '1rem' }}>
              {badges.map(badge => (
                <div key={badge.id} style={{ padding: '1rem', border: '1px solid #eee', borderRadius: '8px', textAlign: 'center', backgroundColor: '#fafafa' }}>
                  <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>
                    {badge.name === 'VIP' ? '💎' : badge.name === 'TOP_FAN' ? '🔥' : '⭐'}
                  </div>
                  <h4 style={{ margin: '0.2rem 0' }}>{badge.name}</h4>
                  <p style={{ fontSize: '0.8rem', color: '#666' }}>{badge.tokenCost} 🪙</p>
                  <button 
                    onClick={() => purchaseBadge(badge.id)}
                    style={{ width: '100%', padding: '6px', backgroundColor: '#6772e5', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 'bold' }}
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
