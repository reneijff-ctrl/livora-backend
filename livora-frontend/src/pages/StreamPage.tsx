import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useWs } from '../ws/WsContext';
import streamingService, { StreamRoom } from '../api/streamingService';
import LiveChat from '../components/LiveChat';
import TokenBalance from '../components/TokenBalance';
import TipPanel from '../components/TipPanel';
import { showToast } from '../components/Toast';
import SEO from '../components/SEO';
import LiveVideoPlayer from '../components/LiveVideoPlayer';
import PrivateShowRequestButton from '../components/PrivateShowRequestButton';
import PrivateShowSessionOverlay from '../components/PrivateShowSessionOverlay';
import privateShowService, { PrivateSession, PrivateSessionStatus } from '../api/privateShowService';
import { getPrivateSettingsByCreator, PrivateSettings } from '@/api/privateSettingsService';
import { getActivePm, getPmMessages, sendPmMessage, markPmAsRead, PmSession, PmMessage } from '@/api/pmService';
import AbuseReportModal from '../components/AbuseReportModal';
import { ReportTargetType } from '../types/report';
import SafeAvatar from '@/components/ui/SafeAvatar';
import apiClient from '../api/apiClient';
import PinnedMessageBanner, { PinnedMessage } from '../components/live/PinnedMessageBanner';

const StreamPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const { user, isAuthenticated, hasPremiumAccess } = useAuth();
  const { subscribe, connected } = useWs();
  
  const [room, setRoom] = useState<StreamRoom | null>(null);
  const [loading, setLoading] = useState(true);
  const [isWatching, setIsWatching] = useState(false);
  const [hlsUrl, setHlsUrl] = useState<string | null>(null);
  const [streamEnded, setStreamEnded] = useState(false);
  const [privateSession, setPrivateSession] = useState<PrivateSession | null>(null);
  const [showReportModal, setShowReportModal] = useState(false);
  const [v2State, setV2State] = useState<'LIVE' | 'PAUSED' | 'CREATED' | 'ENDED' | null>(null);
  const [waitCountdown, setWaitCountdown] = useState(15);
  const [pinnedMessage, setPinnedMessage] = useState<PinnedMessage | null>(null);
  const [isEndingSession, setIsEndingSession] = useState(false);
  const [privateSettings, setPrivateSettings] = useState<PrivateSettings | null>(null);

  // PM States (multi-session)
  const [pmSessions, setPmSessions] = useState<PmSession[]>([]);
  const [activePmId, setActivePmId] = useState<number | null>(null);
  const [pmMessages, setPmMessages] = useState<Record<number, PmMessage[]>>({});
  const [pmInput, setPmInput] = useState('');
  const [pmEnded, setPmEnded] = useState(false);
  const [activeTab, setActiveTab] = useState<'chat' | 'pm' | 'users'>('chat');
  const pmEndRef = useRef<HTMLDivElement>(null);
  const activeTabRef = useRef<'chat' | 'pm' | 'users'>('chat');
  const activePmIdRef = useRef<number | null>(null);

  const handleEndSession = async () => {
    if (!privateSession || isEndingSession) return;
    setIsEndingSession(true);
    try {
      await privateShowService.endSession(privateSession.id);
      showToast('Private session ended', 'success');
    } catch (e) {
      console.error('Failed to end private session', e);
      showToast('Failed to end session', 'error');
    } finally {
      setIsEndingSession(false);
    }
  };

  // Fetch creator private show settings
  useEffect(() => {
    if (!room?.userId) return;
    getPrivateSettingsByCreator(room.userId)
      .then(res => setPrivateSettings(res.data))
      .catch(() => setPrivateSettings(null));
  }, [room?.userId]);

  // PM: keep refs in sync
  useEffect(() => {
    activeTabRef.current = activeTab;
  }, [activeTab]);
  useEffect(() => {
    activePmIdRef.current = activePmId;
  }, [activePmId]);

  // PM: fetch message history when active session changes
  useEffect(() => {
    if (!activePmId) return;
    getPmMessages(activePmId).then(msgs => {
      setPmMessages(prev => ({ ...prev, [activePmId]: msgs }));
    }).catch(() => {});
  }, [activePmId]);

  // PM: WebSocket subscriptions
  useEffect(() => {
    if (!connected || !user || !subscribe) return;

    const loadSessions = () => {
      getActivePm().then(sessions => {
        if (sessions.length > 0) {
          setPmSessions(sessions);
          setActivePmId(sessions[0].roomId);
          setActiveTab('pm');
        }
      }).catch(() => {});
    };

    loadSessions();

    const pmEventUnsub = subscribe('/user/queue/pm-events', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        console.log('PM EVENT:', data);
        if (data.type === 'PM_SESSION_STARTED') {
          const session: PmSession = {
            roomId: data.roomId,
            creatorId: data.creatorId,
            creatorUsername: data.creatorUsername || '',
            viewerId: Number(user.id),
            viewerUsername: '',
            createdAt: new Date().toISOString(),
            unreadCount: 0,
            lastMessage: null,
            lastMessageTime: null,
          };
          setPmSessions(prev => {
            const exists = prev.find(s => s.roomId === session.roomId);
            if (exists) return prev;
            return [...prev, session];
          });
          setActivePmId(prev => prev ?? session.roomId);
          setPmEnded(false);
          setActiveTab('pm');
          showToast('Creator started a private chat', 'info');
        }
        if (data.type === 'PM_SESSION_ENDED') {
          setPmSessions(prev => prev.filter(s => s.roomId !== data.roomId));
          setPmMessages(prev => {
            const next = { ...prev };
            delete next[data.roomId];
            return next;
          });
          setActivePmId(prev => prev === data.roomId ? null : prev);
          setPmEnded(true);
        }
      } catch (e) {}
    });

    const pmMsgUnsub = subscribe('/user/queue/pm-messages', (msg) => {
      try {
        const data: PmMessage = JSON.parse(msg.body);
        if (!data.roomId) return;
        // Message routing
        setPmMessages(prev => ({
          ...prev,
          [data.roomId]: [...(prev[data.roomId] || []), data]
        }));
        // Unread system: increment locally if not viewing this room
        if (activeTabRef.current !== 'pm' || activePmIdRef.current !== data.roomId) {
          setPmSessions(prev => prev.map(s =>
            s.roomId === data.roomId ? { ...s, unreadCount: (s.unreadCount || 0) + 1 } : s
          ));
        }
      } catch (e) {}
    });

    return () => {
      if (typeof pmEventUnsub === 'function') pmEventUnsub();
      if (typeof pmMsgUnsub === 'function') pmMsgUnsub();
    };
  }, [connected, user, subscribe]);

  const activeMessages = pmMessages[activePmId as number] || [];
  const activeSession = pmSessions.find(s => s.roomId === activePmId) || null;
  const totalUnread = pmSessions.reduce((sum, s) => sum + (s.unreadCount || 0), 0);

  useEffect(() => {
    pmEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [activeMessages]);

  const handleSendPm = () => {
    if (!activePmId || !pmInput.trim()) return;
    sendPmMessage(activePmId, pmInput.trim());
    setPmInput('');
  };

  const handleTabClick = (tab: 'chat' | 'pm' | 'users') => {
    setActiveTab(tab);
    if (tab === 'pm' && activePmId) {
      setPmSessions(prev => prev.map(s =>
        s.roomId === activePmId ? { ...s, unreadCount: 0 } : s
      ));
      markPmAsRead(activePmId).catch(() => {});
    }
  };

  const handleSessionClick = (roomId: number) => {
    setActivePmId(roomId);
    setPmSessions(prev => prev.map(s =>
      s.roomId === roomId ? { ...s, unreadCount: 0 } : s
    ));
    markPmAsRead(roomId).catch(() => {});
  };

  // 1. WebSocket connection and state updates
  useEffect(() => {
    if (!room?.userId || !subscribe) return;
    const creatorUserId = room.userId;

    // Subscribe to stream state updates
    const streamSub = subscribe(`/exchange/amq.topic/stream.v2.creator.${creatorUserId}.status`, (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.type === 'stream:state:update') {
          setV2State(data.payload.status);
          if (data.payload.status === 'ENDED') {
            setStreamEnded(true);
            setIsWatching(false);
          } else if (data.payload.status === 'LIVE') {
            setStreamEnded(false);
          }
        }
      } catch (e) {
        console.error('STREAM: Failed to parse stream state update', e);
      }
    });

    // NOTE: creators.presence is subscribed globally via WsContext — do not subscribe here.

    return () => {
      if (typeof streamSub === 'function') streamSub();
    };
  }, [room?.userId, subscribe]);

  // Initial data load
  useEffect(() => {
    const loadRoom = async () => {
      if (!roomId) return;
      try {
        setLoading(true);
        const data = await streamingService.getStream(roomId);
        setRoom(data);
        
        const securedHlsUrl = `${import.meta.env.VITE_API_URL}/api/stream/${roomId}/hls`;
        setHlsUrl(securedHlsUrl);

        if (data.isPremium && !hasPremiumAccess()) {
          showToast('This is a premium stream. Please upgrade your subscription.', 'error');
        } else {
          setIsWatching(true);
        }
      } catch (e) {
        console.error('Failed to load stream', e);
        showToast('Stream not found', 'error');
        navigate('/live');
      } finally {
        setLoading(false);
      }
    };

    if (isAuthenticated) {
      loadRoom();
      
      // Fetch initial pinned message
      apiClient.get(`/stream/${roomId}/pinned`).then(res => {
        if (res.status === 200 && res.data) {
          setPinnedMessage(res.data);
        }
      }).catch(err => console.log("No initial pinned message found", err));
    }
  }, [roomId, hasPremiumAccess, navigate, isAuthenticated]);

  // Fetch V2 livestream state for the creator of this room
  useEffect(() => {
    if (!room?.userId) return;
    const fetchV2 = async () => {
      try {
        const res = await apiClient.get(`/v2/public/creators/${room.userId}/live`);
        setV2State(res.data?.status || 'ENDED');
      } catch {
        setV2State('ENDED');
      }
    };
    fetchV2();
  }, [room?.userId]);

  // Poll countdown while waiting/paused
  useEffect(() => {
    if (!room?.userId) return;
    if (v2State === 'LIVE' || v2State === 'ENDED') return;
    setWaitCountdown(15);
    const tick = setInterval(() => {
      setWaitCountdown((s) => (s > 0 ? s - 1 : 0));
    }, 1000);
    
    // Safety refetch every 60 seconds (WebSocket should handle real-time)
    const refetch = setInterval(async () => {
      try {
        const res = await apiClient.get(`/v2/public/creators/${room.userId}/live`);
        setV2State(res.data?.status || 'ENDED');
      } catch { /* ignore */ }
    }, 60000);

    return () => {
      clearInterval(tick);
      clearInterval(refetch);
    };
  }, [v2State, room?.userId]);

  useEffect(() => {
    if (!roomId || !isAuthenticated || streamEnded) return;
    // Remove the 5s pollStatus interval as WebSockets handle it now.
    // Keeping only a slow safety check.
    const interval = setInterval(async () => {
      try {
        const data = await streamingService.getStreamStatus(roomId);
        if (!data.isLive) {
          setStreamEnded(true);
          setIsWatching(false);
        } else {
          setRoom(prev => prev ? { ...prev, viewerCount: data.viewerCount } : null);
        }
      } catch (e) {
        console.error('Polling error', e);
      }
    }, 60000);
    return () => clearInterval(interval);
  }, [roomId, isAuthenticated, streamEnded]);


  useEffect(() => {
    if (!roomId || !subscribe || !room) return;

    // Subscribe to viewer count updates
    const unsubscribeViewers = subscribe(`/exchange/amq.topic/viewers.${roomId}`, (msg) => {
      const data = JSON.parse(msg.body);
      if (data.viewerCount !== undefined) {
        setRoom(prev => prev ? { ...prev, viewerCount: data.viewerCount } : null);
      }
    });

    // Subscribe to chat room
    const unsubscribeChat = subscribe(`/exchange/amq.topic/chat.${room.userId}`, (msg) => {
      const data = JSON.parse(msg.body);
      
      if (data.type === 'PIN_MESSAGE') {
        setPinnedMessage(data.payload);
        return;
      }

      let chatMsg = data;
      if (data.type === 'chat' && data.chatMessage) {
        chatMsg = data.chatMessage;
      }
      if (chatMsg.system && chatMsg.content === 'Stream ended') {
        showToast('Stream has ended', 'info');
      }
    });

    return () => {
      if (typeof unsubscribeViewers === 'function') unsubscribeViewers();
      if (typeof unsubscribeChat === 'function') unsubscribeChat();
    };
  }, [roomId, subscribe, connected, room?.id]);

  useEffect(() => {
    if (!connected || !user) return;

    const unsubscribe = subscribe('/user/queue/private-show-status', (message) => {
      const data = JSON.parse(message.body);
      console.log('Private show notification:', data);
      
      if (data.type === 'PRIVATE_SHOW_ACCEPTED') {
        showToast('Creator accepted your private show request!', 'success');
        // If we don't have the session object yet (e.g. page refreshed), we might need to fetch it.
        // But for now, let's assume it was just created.
        setPrivateSession(prev => ({
          ...(prev || {}),
          id: data.payload.sessionId,
          status: PrivateSessionStatus.ACCEPTED,
          creatorId: room?.userId || 0,
          viewerId: Number(user.id),
          pricePerMinute: data.payload.pricePerMinute || prev?.pricePerMinute || 50,
        }) as PrivateSession);
      } else if (data.type === 'PRIVATE_SHOW_REJECTED') {
        showToast('Creator rejected your private show request.', 'info');
        setPrivateSession(null);
      } else if (data.type === 'PRIVATE_SHOW_STARTED') {
        showToast('Private show is now LIVE!', 'success');
        setPrivateSession(prev => ({
          ...(prev || {}),
          id: data.payload.sessionId,
          status: PrivateSessionStatus.ACTIVE,
          creatorId: room?.userId || 0,
          viewerId: Number(user.id),
          pricePerMinute: prev?.pricePerMinute || data.payload.pricePerMinute || 50,
        }) as PrivateSession);
      } else if (data.type === 'PRIVATE_SHOW_ENDED') {
        setPrivateSession(null);
      }
    });

    return () => {
      if (typeof unsubscribe === 'function') unsubscribe();
    };
  }, [connected, subscribe, user, room?.userId]);

  // Poll for session status updates (fallback for unreliable WebSocket)
  const privateSessionId = privateSession?.id;
  useEffect(() => {
    if (!privateSessionId) return;

    const interval = setInterval(async () => {
      try {
        const updated = await privateShowService.getSession(privateSessionId);
        const normalizedStatus = PrivateSessionStatus[updated.status as keyof typeof PrivateSessionStatus] || updated.status;

        setPrivateSession(prev => {
          if (!prev) return prev;

          // Stop polling handled by clearing interval when status is terminal
          if (prev.status === PrivateSessionStatus.ACTIVE || prev.status === PrivateSessionStatus.ENDED) {
            return prev;
          }

          if (prev.status !== normalizedStatus) {
            if (normalizedStatus === PrivateSessionStatus.ACCEPTED) {
              showToast('Creator accepted your private show request!', 'success');
            } else if (normalizedStatus === PrivateSessionStatus.ACTIVE) {
              showToast('Private show is now LIVE!', 'success');
            } else if (normalizedStatus === PrivateSessionStatus.REJECTED) {
              showToast('Creator rejected your private show request.', 'info');
              return null;
            } else if (normalizedStatus === PrivateSessionStatus.ENDED) {
              return null;
            }
            return { ...prev, ...updated, status: normalizedStatus };
          }

          return prev;
        });
      } catch (e) {
        console.warn('Failed to poll session status:', e);
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [privateSessionId]);

  const joinStream = () => {
    if (!room || !roomId) return;
    
    if (room.isPremium && !hasPremiumAccess()) {
      showToast('Premium access required', 'error');
      return;
    }

    if (v2State !== 'LIVE') {
      showToast(v2State === 'PAUSED' ? 'Creator stepped away. Please wait.' : 'Stream starting soon', 'info');
      return;
    }

    setIsWatching(true);
  };

  if (loading || !isAuthenticated) {
    return <div style={styles.centered}>Loading stream...</div>;
  }

  if (!room) {
    return <div style={styles.centered}>Stream not found</div>;
  }

  return (
    <div style={styles.container}>
      <SEO title={room.streamTitle || 'Live Stream'} />
      
      <div style={styles.layout}>
        {/* Main Content: Video and Info */}
        <div style={styles.main}>
          <div style={styles.videoContainer}>
            {isWatching && hlsUrl && !streamEnded && v2State === 'LIVE' ? (
              <LiveVideoPlayer 
                src={hlsUrl} 
                streamId={roomId}
              />
            ) : (
              <div style={styles.videoPlaceholder}>
                <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>📺</div>
                {streamEnded || v2State === 'ENDED' ? (
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: '#ff4d4f', marginBottom: '0.5rem' }}>Creator is offline</div>
                    <button onClick={() => navigate(`/creators/${room?.creatorId}`)} style={styles.joinButton}>View Profile</button>
                  </div>
                ) : v2State === 'PAUSED' ? (
                  <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>Creator stepped away</div>
                ) : v2State === 'CREATED' ? (
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: '1.25rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Starting soon</div>
                    <div style={{ opacity: 0.8 }}>Auto-check in {waitCountdown}s</div>
                  </div>
                ) : (
                  <button onClick={joinStream} style={styles.joinButton}>
                    {room.isPremium && !hasPremiumAccess() ? 'Locked (Premium)' : 'Watch Live'}
                  </button>
                )}
              </div>
            )}
            {room.isLive && !streamEnded && (
              <div style={styles.liveBadge}>LIVE</div>
            )}
            <PinnedMessageBanner pinnedMessage={pinnedMessage} />
          </div>

          <div style={styles.infoSection}>
            <div style={styles.titleRow}>
              <h1 style={styles.title}>{room.streamTitle}</h1>
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <TokenBalance />
                <div style={styles.viewerCount}>
                  <span style={{ color: '#ff4d4f', marginRight: '5px' }}>●</span>
                  {room.viewerCount} viewers
                </div>
                {isAuthenticated && user && Number(user.id) !== room.userId && (
                  <button 
                    onClick={() => setShowReportModal(true)}
                    style={styles.reportButton}
                    title="Report Stream"
                  >
                    Report
                  </button>
                )}
              </div>
            </div>
            <div style={styles.creatorInfo}>
              <SafeAvatar 
                src={null} 
                name={`Creator #${room.userId}`} 
                size={48} 
              />
              <div>
                <div style={styles.creatorName}>Creator #{room.userId}</div>
                <div style={styles.description}>{room.description || 'No description provided.'}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Sidebar: Chat and Tips */}
        <div style={styles.sidebar}>
          {/* Tab Bar */}
          <div style={{ display: 'flex', gap: '1.5rem', padding: '0.75rem 1.5rem', borderBottom: '1px solid rgba(255,255,255,0.05)', background: 'rgba(0,0,0,0.2)' }}>
            <button
              onClick={() => handleTabClick('chat')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', fontWeight: 900, textTransform: 'uppercase', letterSpacing: '0.2em', color: activeTab === 'chat' ? '#fff' : '#71717a', padding: '4px 0', position: 'relative' }}
            >
              Chat
              {activeTab === 'chat' && <div style={{ position: 'absolute', bottom: '-0.75rem', left: 0, right: 0, height: '2px', backgroundColor: '#6366f1', boxShadow: '0 0 10px rgba(99,102,241,0.5)' }} />}
            </button>
            <button
              onClick={() => handleTabClick('pm')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', fontWeight: 900, textTransform: 'uppercase', letterSpacing: '0.2em', color: activeTab === 'pm' ? '#fff' : '#71717a', padding: '4px 0', position: 'relative' }}
            >
              PM
              {totalUnread > 0 && activeTab !== 'pm' && (
                <span style={{ marginLeft: '6px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '16px', height: '16px', backgroundColor: '#ef4444', borderRadius: '50%', fontSize: '9px', fontWeight: 'bold', color: 'white', lineHeight: 1 }}>{totalUnread}</span>
              )}
              {activeTab === 'pm' && <div style={{ position: 'absolute', bottom: '-0.75rem', left: 0, right: 0, height: '2px', backgroundColor: '#6366f1', boxShadow: '0 0 10px rgba(99,102,241,0.5)' }} />}
            </button>
            <button
              onClick={() => handleTabClick('users')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', fontWeight: 900, textTransform: 'uppercase', letterSpacing: '0.2em', color: activeTab === 'users' ? '#fff' : '#71717a', padding: '4px 0', position: 'relative' }}
            >
              Users
              {activeTab === 'users' && <div style={{ position: 'absolute', bottom: '-0.75rem', left: 0, right: 0, height: '2px', backgroundColor: '#6366f1', boxShadow: '0 0 10px rgba(99,102,241,0.5)' }} />}
            </button>
          </div>

          {/* Tab Content */}
          {activeTab === 'chat' && (
            <div style={{ position: 'relative', flex: 1, overflow: 'hidden' }}>
              <LiveChat 
                streamId={privateSession?.status === PrivateSessionStatus.ACTIVE ? `private-session-${privateSession.id}` : room.id} 
                userId={room.userId} 
                isPaid={room.isPaid} 
                pricePerMessage={room.pricePerMessage} 
              />
              {v2State === 'CREATED' && (
                <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.6)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>
                  Chat opens when stream is LIVE
                </div>
              )}
            </div>
          )}

          {activeTab === 'pm' && (
            <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
              {/* LEFT — Sessions List */}
              <div style={{ width: '160px', flexShrink: 0, borderRight: '1px solid rgba(255,255,255,0.05)', overflowY: 'auto', background: 'rgba(0,0,0,0.3)' }}>
                {pmSessions.length === 0 ? (
                  <div style={{ padding: '12px', textAlign: 'center', color: '#52525b', fontSize: '10px' }}>No sessions</div>
                ) : (
                  pmSessions.map(session => (
                    <div
                      key={session.roomId}
                      onClick={() => handleSessionClick(session.roomId)}
                      style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        padding: '10px 12px', cursor: 'pointer', borderBottom: '1px solid rgba(255,255,255,0.05)',
                        background: activePmId === session.roomId ? 'rgba(99,102,241,0.2)' : 'transparent',
                        borderLeft: activePmId === session.roomId ? '2px solid #6366f1' : '2px solid transparent',
                      }}
                    >
                      <div style={{ fontSize: '11px', fontWeight: 500, color: 'white', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {session.creatorUsername || session.viewerUsername || 'User'}
                      </div>
                      {(session.unreadCount || 0) > 0 && (
                        <span style={{ marginLeft: '6px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', minWidth: '16px', height: '16px', backgroundColor: '#ef4444', borderRadius: '50%', fontSize: '9px', fontWeight: 'bold', color: 'white', lineHeight: 1, padding: '0 4px' }}>
                          {session.unreadCount}
                        </span>
                      )}
                    </div>
                  ))
                )}
              </div>

              {/* RIGHT — Active Chat */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                {!activeSession ? (
                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '2rem', textAlign: 'center', background: 'rgba(0,0,0,0.2)' }}>
                    <div style={{ width: '64px', height: '64px', background: 'rgba(255,255,255,0.05)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '1rem' }}>
                      <span style={{ fontSize: '1.5rem', opacity: 0.5 }}>{pmEnded ? '🔒' : '💬'}</span>
                    </div>
                    <h4 style={{ color: 'white', fontWeight: 'bold', marginBottom: '0.5rem' }}>{pmEnded ? 'Conversation ended' : (pmSessions.length > 0 ? 'Select a conversation' : 'No private messages')}</h4>
                    <p style={{ color: '#52525b', fontSize: '11px', maxWidth: '200px' }}>{pmEnded ? 'This private conversation has been closed by the creator.' : 'When a creator starts a private chat with you, it will appear here.'}</p>
                  </div>
                ) : (
                  <>
                    <div style={{ padding: '0.5rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.4)', flexShrink: 0 }}>
                      <p style={{ fontSize: '12px', fontWeight: 'bold', color: 'white', margin: 0 }}>💬 {activeSession.creatorUsername || activeSession.viewerUsername || 'User'}</p>
                    </div>
                    <div style={{ flex: 1, overflowY: 'auto', padding: '0.5rem 0.75rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                      {activeMessages.map((m, i) => (
                        <div key={i} style={{ display: 'flex', justifyContent: m.senderId === Number(user?.id) ? 'flex-end' : 'flex-start' }}>
                          <div style={{ maxWidth: '80%', padding: '6px 12px', borderRadius: '12px', fontSize: '12px', backgroundColor: m.senderId === Number(user?.id) ? '#4f46e5' : 'rgba(255,255,255,0.1)', color: m.senderId === Number(user?.id) ? 'white' : '#d4d4d8' }}>
                            {m.content}
                          </div>
                        </div>
                      ))}
                      <div ref={pmEndRef} />
                    </div>
                    <div style={{ padding: '0.5rem 0.75rem', borderTop: '1px solid rgba(255,255,255,0.1)', flexShrink: 0, display: 'flex', gap: '0.5rem' }}>
                      <input
                        value={pmInput}
                        onChange={e => setPmInput(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSendPm()}
                        placeholder="Type a message..."
                        style={{ flex: 1, backgroundColor: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '6px 12px', fontSize: '12px', color: 'white', outline: 'none' }}
                      />
                      <button
                        onClick={handleSendPm}
                        disabled={!pmInput.trim() || !activePmId}
                        style={{ padding: '6px 12px', backgroundColor: '#4f46e5', borderRadius: '8px', fontSize: '12px', fontWeight: 'bold', color: 'white', border: 'none', cursor: 'pointer', opacity: (pmInput.trim() && activePmId) ? 1 : 0.4 }}
                      >
                        Send
                      </button>
                    </div>
                  </>
                )}
              </div>
            </div>
          )}

          {activeTab === 'users' && (
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '2rem', textAlign: 'center', background: 'rgba(0,0,0,0.2)' }}>
              <div style={{ width: '64px', height: '64px', background: 'rgba(255,255,255,0.05)', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '1rem' }}>
                <span style={{ fontSize: '1.5rem', opacity: 0.5 }}>👥</span>
              </div>
              <h4 style={{ color: 'white', fontWeight: 'bold', marginBottom: '0.5rem' }}>Viewer List</h4>
              <p style={{ color: '#52525b', fontSize: '11px', maxWidth: '200px' }}>Coming soon!</p>
            </div>
          )}
          
          <div style={{ marginTop: '1rem' }}>
            {user && Number(user.id) !== room.userId && privateSettings?.enabled && !privateSession && (
              <PrivateShowRequestButton 
                creatorId={room.userId} 
                pricePerMinute={privateSettings.pricePerMinute}
                onSessionCreated={(session) => setPrivateSession(session)} 
              />
            )}
            
            {privateSession && privateSession.status === PrivateSessionStatus.REQUESTED && (
              <div style={styles.sessionStatus}>
                Waiting for creator to accept private show request...
              </div>
            )}

            {privateSession && privateSession.status === PrivateSessionStatus.ACCEPTED && (
              <div style={styles.sessionStatus}>
                Request accepted! Waiting for creator to start the session.
              </div>
            )}

            {privateSession && privateSession.status === PrivateSessionStatus.ACTIVE && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                <span style={styles.privateActiveLabel}>🔴 Private Active</span>
                <button
                  onClick={handleEndSession}
                  disabled={isEndingSession}
                  style={{
                    ...styles.endSessionButton,
                    opacity: isEndingSession ? 0.6 : 1,
                    cursor: isEndingSession ? 'not-allowed' : 'pointer',
                  }}
                >
                  {isEndingSession ? 'Ending...' : 'End Private Session'}
                </button>
              </div>
            )}
          </div>

          <TipPanel roomId={room.id} userId={room.userId} />
        </div>
      </div>

      {privateSession?.status === PrivateSessionStatus.ACTIVE && (
        <PrivateShowSessionOverlay 
          sessionId={privateSession.id}
          pricePerMinute={privateSession.pricePerMinute}
          isCreator={false}
          onSessionEnded={() => setPrivateSession(null)}
        />
      )}

      {showReportModal && room && (
        <AbuseReportModal
          isOpen={showReportModal}
          onClose={() => setShowReportModal(false)}
          targetType={ReportTargetType.STREAM}
          targetId={room.id}
          targetLabel={`Stream: ${room.streamTitle}`}
          reportedUserId={room.userId}
          streamId={room.id}
        />
      )}

    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '1rem',
    maxWidth: '1600px',
    margin: '0 auto',
    height: 'calc(100vh - 80px)', // Account for navbar
    color: '#F4F4F5',
  },
  layout: {
    display: 'flex',
    gap: '1.5rem',
    height: '100%',
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  main: {
    flex: '3',
    minWidth: '600px',
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  sidebar: {
    flex: '1',
    minWidth: '350px',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  },
  videoContainer: {
    position: 'relative',
    width: '100%',
    aspectRatio: '16/9',
    backgroundColor: '#000',
    borderRadius: '24px',
    overflow: 'hidden',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  video: {
    width: '100%',
    height: '100%',
    objectFit: 'contain',
  },
  videoPlaceholder: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.8)',
    color: '#fff',
    zIndex: 10,
  },
  joinButton: {
    padding: '0.875rem 2.5rem',
    backgroundColor: '#6366f1',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontWeight: '800',
    fontSize: '1.1rem',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
  liveBadge: {
    position: 'absolute',
    top: '20px',
    left: '20px',
    backgroundColor: '#ff4d4f',
    color: 'white',
    padding: '4px 12px',
    borderRadius: '4px',
    fontWeight: 'bold',
    fontSize: '0.8rem',
    zIndex: 5,
  },
  privateActiveLabel: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.25rem',
    padding: '0.35rem 0.75rem',
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    border: '1px solid rgba(239, 68, 68, 0.4)',
    borderRadius: '9999px',
    color: '#f87171',
    fontSize: '0.8rem',
    fontWeight: 700,
  } as React.CSSProperties,
  endSessionButton: {
    padding: '0.4rem 1rem',
    backgroundColor: '#dc2626',
    color: '#fff',
    border: 'none',
    borderRadius: '9999px',
    fontSize: '0.8rem',
    fontWeight: 700,
    transition: 'background-color 0.2s',
  } as React.CSSProperties,
  sessionStatus: {
    padding: '1rem',
    backgroundColor: 'rgba(103, 114, 229, 0.1)',
    color: '#6772e5',
    borderRadius: '8px',
    border: '1px solid rgba(103, 114, 229, 0.3)',
    textAlign: 'center',
    fontSize: '0.9rem',
    fontWeight: 'bold',
  },
  infoSection: {
    backgroundColor: '#0F0F14',
    padding: '2rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  titleRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '1rem',
  },
  title: {
    margin: 0,
    fontSize: '1.5rem',
    fontWeight: '800',
    color: '#F4F4F5',
    letterSpacing: '-0.02em',
  },
  viewerCount: {
    fontSize: '0.9rem',
    fontWeight: '700',
    color: '#A1A1AA',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    padding: '6px 14px',
    borderRadius: '9999px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
  },
  reportButton: {
    padding: '0',
    backgroundColor: 'transparent',
    color: '#ef4444',
    border: 'none',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
    marginLeft: '0.5rem',
    transition: 'color 0.2s',
  },
  creatorInfo: {
    display: 'flex',
    gap: '1rem',
    alignItems: 'center',
    paddingTop: '1.5rem',
    borderTop: '1px solid rgba(255, 255, 255, 0.05)',
  },
  avatarPlaceholder: {
    width: '48px',
    height: '48px',
    borderRadius: '24px',
    backgroundColor: '#6772e5',
    color: 'white',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 'bold',
  },
  creatorName: {
    fontWeight: '700',
    fontSize: '1.125rem',
    color: '#F4F4F5',
  },
  description: {
    fontSize: '0.95rem',
    color: '#71717A',
    marginTop: '0.25rem',
    lineHeight: '1.5',
  },
  centered: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
    fontSize: '1.2rem',
    color: '#6b7280',
  },
};

export default StreamPage;
