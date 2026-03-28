import React, { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { webSocketService } from '@/websocket/webSocketService';
import webRtcService, { SignalingMessage, SignalingType } from '@/websocket/webRtcService';
import { SIMULCAST_ENCODINGS, VIDEO_CONSTRAINTS } from '@/constants/webrtc';
import { Producer, Transport } from 'mediasoup-client';
import apiClient from '@/api/apiClient';
import { safeRender } from '@/utils/safeRender';
import ChatComponent from '@/components/ChatComponent';
import GiftSoundControls from '@/components/GiftSoundControls';
import CreatorModerationSettingsPanel from '@/components/live/CreatorModerationSettingsPanel';
import PinnedMessageBanner, { PinnedMessage } from '@/components/live/PinnedMessageBanner';
import StreamHealthWidget from '@/components/creator/StreamHealthWidget';
import TipMenuManager from '@/components/creator/tipmenu/TipMenuManager';
import GoalGroupBuilder from '@/components/creator/goalgroup/GoalGroupBuilder';
import { getPrivateSettings, updatePrivateSettings, PrivateSettings } from '@/api/privateSettingsService';
import PrivateShowCreatorHandler from '@/components/PrivateShowCreatorHandler';
import privateShowService, { PrivateSession, PrivateSessionStatus } from '@/api/privateShowService';
import { startPm, getActivePm, getPmMessages, endPmSession, sendPmMessage, markPmAsRead, PmSession, PmMessage } from '@/api/pmService';
import { showToast } from '@/components/Toast';

// Removed local SignalingMessage interface - using webRtcService version

interface TipGoal {
  id: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  active: boolean;
  autoReset: boolean;
  orderIndex: number;
}

const CreatorLiveDashboard: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  
  const [isLive, setIsLive] = useState(false);
  const [loading, setLoading] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [minChatTokens, setMinChatTokens] = useState(0);
  const [pricePerMessage, setPricePerMessage] = useState(0);
  const [isPaid, setIsPaid] = useState(false);
  const [admissionPrice, setAdmissionPrice] = useState(20);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [viewerCount, setViewerCount] = useState(0);
  const [activeTips, setActiveTips] = useState<any[]>([]);
  const [isModSettingsOpen, setIsModSettingsOpen] = useState(false);
  const [pinnedMessage, setPinnedMessage] = useState<PinnedMessage | null>(null);
  
  // Customization states
  const [chatFontSize, setChatFontSize] = useState(() => {
    const saved = localStorage.getItem('livora-creator-chat-font-size');
    return saved ? Number(saved) : 16;
  });
  const [showPinnedBanner, setShowPinnedBanner] = useState(() => {
    const saved = localStorage.getItem('livora-creator-show-pinned-banner');
    return saved !== null ? saved === 'true' : true;
  });
  const [showPreviewLabels, setShowPreviewLabels] = useState(() => {
    const saved = localStorage.getItem('livora-creator-show-preview-labels');
    return saved !== null ? saved === 'true' : true;
  });
  const [showTipOverlays, setShowTipOverlays] = useState(() => {
    const saved = localStorage.getItem('livora-creator-show-tip-overlays');
    return saved !== null ? saved === 'true' : true;
  });
  const [chatWidth, setChatWidth] = useState(620);
  const [previewWidth, setPreviewWidth] = useState(800);
  const [activeTab, setActiveTab] = useState('CHAT');
  const [sessionTokens, setSessionTokens] = useState(0);
  
  // Private Show Settings
  const [privateSettings, setPrivateSettings] = useState<PrivateSettings | null>(null);

  // Private Session State
  const [activePrivateSession, setActivePrivateSession] = useState<PrivateSession | null>(null);
  const [isStartingSession, setIsStartingSession] = useState(false);
  const [isEndingSession, setIsEndingSession] = useState(false);
  const [privateSessionSeconds, setPrivateSessionSeconds] = useState(0);
  const [spyCount, setSpyCount] = useState(0);

  // PM States
  const [pmSessions, setPmSessions] = useState<PmSession[]>([]);
  const [activePm, setActivePm] = useState<PmSession | null>(null);
  const [pmMessages, setPmMessages] = useState<Record<number, PmMessage[]>>({});
  const [pmInput, setPmInput] = useState('');
  const [pmSending, setPmSending] = useState(false);
  const [viewers, setViewers] = useState<any[]>([]);
  const [viewersLoading, setViewersLoading] = useState(false);

  // Moderator management
  const [moderatorList, setModeratorList] = useState<{userId: number; username: string; persistent: boolean}[]>([]);
  const [keepModerators, setKeepModerators] = useState(() => {
    const saved = localStorage.getItem('livora-keep-moderators');
    return saved !== null ? saved === 'true' : true;
  });

  // Room bans & moderation audit
  const [bannedUsers, setBannedUsers] = useState<any[]>([]);
  const [auditLog, setAuditLog] = useState<any[]>([]);
  const pmMessagesEndRef = useRef<HTMLDivElement>(null);
  const pmMessagesContainerRef = useRef<HTMLDivElement>(null);
  const activePmRef = useRef<PmSession | null>(null);

  // Tip Goal States
  const [goals, setGoals] = useState<TipGoal[]>([]);
  const [goalTitle, setGoalTitle] = useState('');
  const [goalTarget, setGoalTarget] = useState(1000);
  const [goalAutoReset, setGoalAutoReset] = useState(false);
  const [isUpdatingGoal, setIsUpdatingGoal] = useState(false);
  const [goalSwitching, setGoalSwitching] = useState(false);
  const [editingGoalId, setEditingGoalId] = useState<string | null>(null);

  const tipGoal = goals.find(g => g.active) || null;
  
  

  const handleTip = useCallback((payload: any) => {
    const tipId = Math.random().toString(36).substring(2, 9);
    const newTip = { id: tipId, ...payload };
    setActiveTips(prev => [...prev, newTip]);
    
    // Auto-remove after animation
    setTimeout(() => {
      setActiveTips(prev => prev.filter(t => t.id !== tipId));
    }, 2500);
  }, []);


  useEffect(() => {
    getPrivateSettings().then(res => setPrivateSettings(res.data)).catch(() => {});
  }, []);

  const handlePrivateSettingsUpdate = async (data: {
    enabled: boolean;
    pricePerMinute: number;
    allowSpyOnPrivate?: boolean;
    spyPricePerMinute?: number;
    maxSpyViewers?: number | null;
  }) => {
    try {
      const res = await updatePrivateSettings(data);
      setPrivateSettings(res.data);
    } catch (e) {
      console.error('Failed to update private settings', e);
    }
  };

  const fetchGoals = useCallback(async () => {
    try {
      const res = await apiClient.get('/creator/tip-goals', {
        // @ts-ignore
        _skipToast: true
      });
      if (res.data) {
        setGoals(res.data);
      } else {
        setGoals([]);
      }
    } catch (err: any) {
      if (err.response?.status !== 404) {
        console.error('DASHBOARD: Failed to fetch tip goals', err);
      } else {
        setGoals([]);
      }
    }
  }, []);

  const videoRef = useRef<HTMLVideoElement>(null);
  const sendTransport = useRef<Transport | null>(null);
  const producers = useRef<Map<string, Producer>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);
  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => { isMountedRef.current = false; };
  }, []);

  const viewerCountUnsubRef = useRef<(() => void) | null>(null);
  const pinUnsubRef = useRef<(() => void) | null>(null);
  const tipUnsubRef = useRef<(() => void) | null>(null);

  const startResize = (e: React.MouseEvent) => {
    const startX = e.clientX;
    const startWidth = chatWidth;

    const onMouseMove = (moveEvent: MouseEvent) => {
      // Dragging left means (moveEvent.clientX - startX) is negative
      // We want to INCREASE width when dragging left, so we subtract the delta
      const newWidth = startWidth - (moveEvent.clientX - startX);
      if (newWidth > 420 && newWidth < 900) {
        setChatWidth(newWidth);
      }
    };

    const onMouseUp = () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };

    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
  };

  // 1. Initialize camera preview on mount
  useEffect(() => {
    const initCamera = async () => {
      setCameraError(null);
      try {
        let stream = await navigator.mediaDevices.getUserMedia({ 
          video: VIDEO_CONSTRAINTS, 
          audio: true 
        });

        const videoTrackInit = stream.getVideoTracks()[0];
        console.log("CAMERA INIT - VIDEO TRACK:", videoTrackInit);
        console.log("CAMERA INIT - TRACK SETTINGS:", videoTrackInit?.getSettings());
        console.log("CAMERA INIT - TRACK READY STATE:", videoTrackInit?.readyState);

        // Firefox camera restart: if track is not live, stop and re-acquire
        if (videoTrackInit && videoTrackInit.readyState !== "live") {
          console.warn("CAMERA INIT - Track not live, restarting camera...");
          videoTrackInit.stop();
          stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: "user" },
            audio: true
          });
          const retriedTrack = stream.getVideoTracks()[0];
          console.log("CAMERA INIT - RETRIED TRACK:", retriedTrack);
          console.log("CAMERA INIT - RETRIED TRACK SETTINGS:", retriedTrack?.getSettings());
          console.log("CAMERA INIT - RETRIED TRACK READY STATE:", retriedTrack?.readyState);
        }

        setLocalStream(stream);
        localStreamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      } catch (err) {
        console.error('LIVE: Error accessing camera/mic:', err);
        setCameraError('Camera failed to initialize. Please check permissions.');
      }
    };

    initCamera();

    return () => {
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach(track => track.stop());
      }
    };
  }, []);

  // 2. Subscribe to dedicated viewer count topic
  // NOTE: creators.presence is subscribed globally via WsContext — do not subscribe here.
  useEffect(() => {
    if (!user?.id) return;

    let isMounted = true;

    // Dedicated Viewer Count Subscription
    const viewerCountUnsub = webSocketService.subscribe(`/exchange/amq.topic/viewers.${user.id}`, (msg) => {
      try {
        const data = JSON.parse(msg.body);
        const payload = data.payload || data;
        if (payload.viewerCount !== undefined && payload.viewerCount !== null) {
          console.log("CREATOR-DASH: Viewer count update received", payload);
          setViewerCount(Number(payload.viewerCount));
        }
      } catch (e) {
        console.error('CREATOR-DASH: Failed to parse viewer count update', e);
      }
    });

    if (!isMounted) {
      viewerCountUnsub();
    } else {
      viewerCountUnsubRef.current = viewerCountUnsub;
    }

    return () => {
      console.log("CREATOR-DASH: Cleaning up viewer count and signaling subscriptions");
      isMounted = false;
      if (viewerCountUnsubRef.current) {
        viewerCountUnsubRef.current();
        viewerCountUnsubRef.current = null;
      }
      webRtcService.leaveStream();
    };
  }, [user?.id]);

  useEffect(() => {
    if (!user?.id) return;

    let isMounted = true;

    const initPinSubscription = async () => {
      try {
        const unsub = webSocketService.subscribe(`/exchange/amq.topic/chat.${user.id}`, (msg) => {
          const data = JSON.parse(msg.body);
          if (data.type === 'PIN_MESSAGE') {
            setPinnedMessage(data.payload);
          } else if (data.type === 'GOAL_PROGRESS' || data.type === 'GOAL_STATUS' || data.type === 'GOAL_COMPLETED') {
            setGoals(prev => prev.map(g => {
              if (g.title === data.title) {
                return {
                  ...g,
                  currentAmount: data.currentAmount ?? g.currentAmount,
                  targetAmount: data.targetAmount ?? g.targetAmount,
                  active: data.active ?? g.active
                };
              }
              if (data.active && g.active && g.title !== data.title) {
                return { ...g, active: false };
              }
              return g;
            }));
          } else if (data.type === 'GOAL_SWITCH') {
            setGoalSwitching(true);
            setTimeout(() => {
              fetchGoals();
              setGoalSwitching(false);
            }, 2000);
          } else if (data.type === 'MODERATOR_LIST_UPDATED') {
            fetchModerators();
            fetchViewers();
          } else if (data.type === 'BAN_LIST_UPDATED') {
            fetchBannedUsers();
            fetchAuditLog();
          }
        });

        if (!isMounted) {
          unsub();
          return;
        }
        pinUnsubRef.current = unsub;

        // Initial fetch
        apiClient.get(`/stream/creator/${user.id}/pinned`).then(r => {
          if (isMounted && r.status === 200 && r.data) setPinnedMessage(r.data);
        }).catch(() => {});
      } catch (e) {
        console.error("CREATOR-DASH: Failed to init pin subscription", e);
      }
    };

    initPinSubscription();

    return () => {
      isMounted = false;
      if (pinUnsubRef.current) {
        pinUnsubRef.current();
        pinUnsubRef.current = null;
      }
    };
  }, [user?.id]);

  // Subscribe to tip events to accumulate session earnings
  useEffect(() => {
    if (!user?.id) return;

    let isMounted = true;
    // Private creator tip queue
    const tipUnsub = webSocketService.subscribe('/user/queue/tips', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        const amount = (data && (data.amount ?? data.tokenAmount ?? data.payload?.amount));
        if (typeof amount === 'number' && !isNaN(amount)) {
          setSessionTokens((prev) => prev + amount);
        }
      } catch (e) {
        console.error('CREATOR-DASH: Failed to parse tip event', e);
      }
    });

    if (!isMounted) {
      tipUnsub();
    } else {
      tipUnsubRef.current = tipUnsub;
    }

    return () => {
      isMounted = false;
      if (tipUnsubRef.current) {
        tipUnsubRef.current();
        tipUnsubRef.current = null;
      }
    };
  }, [user?.id]);

  // Private session: WS subscription for status events
  useEffect(() => {
    if (!user?.id) return;

    const unsub = webSocketService.subscribe('/user/queue/private-show-status', (message) => {
      try {
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
            creatorId: Number(user!.id),
            viewerId: prev?.viewerId || 0,
            pricePerMinute: prev?.pricePerMinute || data.payload.pricePerMinute || 0,
          }));
        } else if (data.type === 'PRIVATE_SHOW_ENDED') {
          setActivePrivateSession(null);
          setIsStartingSession(false);
          setSpyCount(0);
        } else if (data.type === 'SPY_COUNT_UPDATE') {
          setSpyCount(data.payload?.spyCount ?? 0);
        }
      } catch (e) {
        console.error('CREATOR-DASH: Failed to parse private show status', e);
      }
    });

    return () => { if (unsub) unsub(); };
  }, [user?.id]);

  // Private session: recover active session on refresh
  useEffect(() => {
    if (!user?.id) return;
    apiClient.get('/private-show/active').then(res => {
      if (res.data && res.data.id) {
        const s = res.data;
        const normalizedStatus = PrivateSessionStatus[s.status as keyof typeof PrivateSessionStatus] || s.status;
        if (normalizedStatus === PrivateSessionStatus.ACCEPTED || normalizedStatus === PrivateSessionStatus.ACTIVE) {
          setActivePrivateSession({
            id: s.id,
            viewerId: s.viewerId,
            creatorId: s.creatorId,
            pricePerMinute: s.pricePerMinute,
            status: normalizedStatus,
          });
        }
      }
    }).catch(() => {});
  }, [user?.id]);

  // Private session: elapsed timer
  useEffect(() => {
    if (activePrivateSession?.status !== PrivateSessionStatus.ACTIVE) {
      setPrivateSessionSeconds(0);
      return;
    }
    const interval = setInterval(() => setPrivateSessionSeconds(prev => prev + 1), 1000);
    return () => clearInterval(interval);
  }, [activePrivateSession?.status]);

  const formatSessionTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
    const secs = (totalSeconds % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
  };

  const handleStartPrivateSession = async () => {
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

  const handleEndPrivateSession = async () => {
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

  // PM: keep activePmRef in sync
  useEffect(() => {
    activePmRef.current = activePm;
  }, [activePm]);

  // PM: fetch message history when switching active session
  useEffect(() => {
    if (!activePm) return;
    getPmMessages(activePm.roomId).then(msgs => {
      setPmMessages(prev => ({ ...prev, [activePm.roomId]: msgs }));
    }).catch(() => {});
    setPmSessions(prev => prev.map(s =>
      s.roomId === activePm.roomId ? { ...s, unreadCount: 0 } : s
    ));
    markPmAsRead(activePm.roomId).catch(() => {});
  }, [activePm?.roomId]);

  // PM: WebSocket subscriptions + session recovery
  useEffect(() => {
    if (!user?.id) return;

    const loadSessions = () => {
      getActivePm().then(sessions => setPmSessions(sessions)).catch(() => {});
    };

    loadSessions();

    const pmEventUnsub = webSocketService.subscribe('/user/queue/pm-events', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.type === 'PM_SESSION_STARTED') {
          const newSession: PmSession = {
            roomId: data.roomId,
            creatorId: data.creatorId,
            creatorUsername: data.creatorUsername || '',
            viewerId: data.viewerId || 0,
            viewerUsername: data.viewerUsername || '',
            createdAt: new Date().toISOString(),
            unreadCount: 0,
            lastMessage: null,
            lastMessageTime: null,
          };
          setPmSessions(prev => {
            if (prev.some(s => s.roomId === newSession.roomId)) return prev;
            return [...prev, newSession];
          });
          setActiveTab('PM');
        }
        if (data.type === 'PM_SESSION_ENDED') {
          setPmSessions(prev => prev.filter(s => s.roomId !== data.roomId));
          setActivePm(prev => prev?.roomId === data.roomId ? null : prev);
          setPmMessages(prev => {
            const copy = { ...prev };
            delete copy[data.roomId];
            return copy;
          });
        }
      } catch (e) {
        console.error('PM event parse error', e);
      }
    });

    const pmMsgUnsub = webSocketService.subscribe('/user/queue/pm-messages', (msg) => {
      try {
        const data: PmMessage = JSON.parse(msg.body);
        setPmMessages(prev => ({
          ...prev,
          [data.roomId]: [...(prev[data.roomId] || []), data],
        }));
        // Unread tracking: increment if message is not in active room
        if (activePmRef.current?.roomId !== data.roomId) {
          setPmSessions(prev => prev.map(s =>
            s.roomId === data.roomId ? { ...s, unreadCount: (s.unreadCount || 0) + 1 } : s
          ));
        }
      } catch (e) {
        console.error('PM message parse error', e);
      }
    });

    // Handle WS reconnect: reload sessions + messages
    const reconnectHandler = () => {
      loadSessions();
      const currentActive = activePmRef.current;
      if (currentActive) {
        getPmMessages(currentActive.roomId).then(msgs => {
          setPmMessages(prev => ({ ...prev, [currentActive.roomId]: msgs }));
        }).catch(() => {});
      }
    };
    webSocketService.onReconnect?.(reconnectHandler);

    return () => {
      pmEventUnsub();
      pmMsgUnsub();
    };
  }, [user?.id]);

  // PM: auto-scroll messages
  useEffect(() => {
    if (!pmMessagesContainerRef.current) return;
    pmMessagesContainerRef.current.scrollTo({
      top: pmMessagesContainerRef.current.scrollHeight,
      behavior: 'smooth',
    });
  }, [pmMessages, activePm]);

  // Fetch viewers when USERS tab is active, with auto-refresh
  const fetchViewers = useCallback(async () => {
    if (!user?.id) return;
    setViewersLoading(true);
    try {
      const res = await apiClient.get(`/stream/moderation/viewers/${user.id}`);
      setViewers(res.data || []);
    } catch {
      setViewers([]);
    } finally {
      setViewersLoading(false);
    }
  }, [user?.id]);

  useEffect(() => {
    if (activeTab !== 'USERS' || !user?.id) return;
    fetchViewers();
    const interval = setInterval(fetchViewers, 10000);
    return () => clearInterval(interval);
  }, [activeTab, user?.id, fetchViewers]);

  // Moderation actions for Users tab
  const handleMuteViewer = async (viewerId: number, minutes: number) => {
    try {
      await apiClient.post('/stream/moderation/mute', {
        creatorId: Number(user?.id),
        userId: viewerId,
        durationMinutes: minutes
      });
      showToast(`User muted for ${minutes} minutes`, 'success');
    } catch {
      showToast('Failed to mute user', 'error');
    }
  };

  const handleShadowMuteViewer = async (viewerId: number) => {
    try {
      await apiClient.post('/stream/moderation/shadow-mute', {
        creatorId: Number(user?.id),
        userId: viewerId
      });
      showToast('User shadow muted', 'success');
    } catch {
      showToast('Failed to shadow mute user', 'error');
    }
  };

  const handleKickViewer = async (viewerId: number) => {
    try {
      await apiClient.post('/stream/moderation/kick', {
        creatorId: Number(user?.id),
        userId: viewerId
      });
      showToast('User kicked', 'success');
      setViewers(prev => prev.filter(v => v.id !== viewerId));
    } catch {
      showToast('Failed to kick user', 'error');
    }
  };

  const fetchModerators = useCallback(async () => {
    if (!user?.id) return;
    try {
      const res = await apiClient.get(`/stream/moderators/${user.id}/details`);
      setModeratorList(res.data || []);
    } catch {
      setModeratorList([]);
    }
  }, [user?.id]);

  useEffect(() => {
    if (!user?.id) return;
    fetchModerators();
  }, [user?.id, fetchModerators]);

  const handleGrantMod = async (userId: number) => {
    try {
      await apiClient.post('/stream/moderators/grant', { creatorId: Number(user?.id), userId, persistent: keepModerators });
      showToast('Moderator granted', 'success');
      fetchViewers();
      fetchModerators();
    } catch {
      showToast('Failed to grant moderator', 'error');
    }
  };

  const handleRevokeMod = async (userId: number) => {
    try {
      await apiClient.post('/stream/moderators/revoke', { creatorId: Number(user?.id), userId });
      showToast('Moderator removed', 'success');
      fetchViewers();
      fetchModerators();
    } catch {
      showToast('Failed to remove moderator', 'error');
    }
  };

  const handleClearAllMods = async () => {
    if (!user?.id) return;
    try {
      const res = await apiClient.post('/stream/moderators/clear-all', { creatorId: Number(user.id) });
      showToast(`All moderators cleared (${res.data?.count || 0})`, 'success');
      fetchViewers();
      fetchModerators();
    } catch {
      showToast('Failed to clear moderators', 'error');
    }
  };

  // Room ban handlers
  const fetchBannedUsers = useCallback(async () => {
    if (!user?.id) return;
    try {
      const res = await apiClient.get(`/room-bans/active/${user.id}`);
      setBannedUsers(res.data || []);
    } catch {
      setBannedUsers([]);
    }
  }, [user?.id]);

  const fetchAuditLog = useCallback(async () => {
    if (!user?.id) return;
    try {
      const res = await apiClient.get(`/room-bans/audit/${user.id}?limit=50`);
      setAuditLog(res.data || []);
    } catch {
      setAuditLog([]);
    }
  }, [user?.id]);

  useEffect(() => {
    if (!user?.id) return;
    fetchBannedUsers();
    fetchAuditLog();
  }, [user?.id, fetchBannedUsers, fetchAuditLog]);

  const handleBanViewer = async (viewerId: number, banType: string) => {
    try {
      await apiClient.post('/room-bans/ban', {
        creatorId: Number(user?.id),
        targetUserId: viewerId,
        banType
      });
      const labels: Record<string, string> = { '5m': '5 minutes', '30m': '30 minutes', '24h': '24 hours', 'permanent': 'permanently' };
      showToast(`User banned ${banType === 'permanent' ? 'permanently' : 'for ' + (labels[banType] || banType)}`, 'success');
      fetchViewers();
      fetchBannedUsers();
      fetchAuditLog();
    } catch (e: any) {
      showToast(e?.response?.data?.error || 'Failed to ban user', 'error');
    }
  };

  const handleUnbanUser = async (targetUserId: number) => {
    try {
      await apiClient.post('/room-bans/unban', {
        creatorId: Number(user?.id),
        targetUserId
      });
      showToast('User unbanned', 'success');
      fetchBannedUsers();
      fetchAuditLog();
    } catch (e: any) {
      showToast(e?.response?.data?.error || 'Failed to unban user', 'error');
    }
  };

  const handleStartPm = async (viewerId: number) => {
    try {
      const session = await startPm(viewerId);
      setPmSessions(prev => {
        if (prev.some(s => s.roomId === session.roomId)) return prev;
        return [...prev, session];
      });
      setActivePm(session);
      setActiveTab('PM');
    } catch (e) {
      console.error('Failed to start PM', e);
    }
  };

  const handleSendPm = () => {
    if (!activePm?.roomId || !pmInput.trim()) return;
    setPmSending(true);
    try {
      sendPmMessage(activePm.roomId, pmInput.trim());
      setPmInput('');
    } catch (e) {
      console.error('Failed to send PM', e);
    } finally {
      setPmSending(false);
    }
  };

  const handleEndPm = async (roomId: number) => {
    try {
      await endPmSession(roomId);
      setPmSessions(prev => prev.filter(s => s.roomId !== roomId));
      setActivePm(prev => prev?.roomId === roomId ? null : prev);
      setPmMessages(prev => {
        const copy = { ...prev };
        delete copy[roomId];
        return copy;
      });
    } catch (e) {
      console.error('Failed to end PM session', e);
    }
  };

  useEffect(() => {
    if (user?.id) {
      fetchGoals();
    }
  }, [user?.id, fetchGoals]);

  const handleCreateGoal = async () => {
    if (!goalTitle || goalTarget <= 0) return;
    setIsUpdatingGoal(true);
    try {
      await apiClient.post('/creator/tip-goals', {
        title: goalTitle,
        targetAmount: goalTarget,
        autoReset: goalAutoReset,
        active: goals.length === 0,
        orderIndex: goals.length
      });
      setGoalTitle('');
      setGoalTarget(1000);
      setGoalAutoReset(false);
      fetchGoals();
    } catch (err) {
      console.error('DASHBOARD: Failed to create tip goal', err);
    } finally {
      setIsUpdatingGoal(false);
    }
  };

  const handleUpdateGoal = async () => {
    if (!editingGoalId || !goalTitle || goalTarget <= 0) return;
    setIsUpdatingGoal(true);
    try {
      const g = goals.find(g => g.id === editingGoalId);
      await apiClient.put(`/creator/tip-goals/${editingGoalId}`, {
        title: goalTitle,
        targetAmount: goalTarget,
        autoReset: goalAutoReset,
        active: g?.active ?? false
      });
      setEditingGoalId(null);
      setGoalTitle('');
      setGoalTarget(1000);
      setGoalAutoReset(false);
      fetchGoals();
    } catch (err) {
      console.error('DASHBOARD: Failed to update tip goal', err);
    } finally {
      setIsUpdatingGoal(false);
    }
  };

  const handleToggleGoalActive = async (goal: TipGoal, active: boolean) => {
    setIsUpdatingGoal(true);
    try {
      await apiClient.put(`/creator/tip-goals/${goal.id}`, {
        title: goal.title,
        targetAmount: goal.targetAmount,
        autoReset: goal.autoReset,
        active: active
      });
      fetchGoals();
    } catch (err) {
      console.error('DASHBOARD: Failed to toggle tip goal status', err);
    } finally {
      setIsUpdatingGoal(false);
    }
  };

  const handleDeleteGoal = async (id: string) => {
    if (!confirm('Are you sure you want to delete this goal?')) return;
    setIsUpdatingGoal(true);
    try {
      await apiClient.delete(`/creator/tip-goals/${id}`);
      fetchGoals();
      if (editingGoalId === id) {
        setEditingGoalId(null);
        setGoalTitle('');
        setGoalTarget(1000);
        setGoalAutoReset(false);
      }
    } catch (err) {
      console.error('DASHBOARD: Failed to delete tip goal', err);
    } finally {
      setIsUpdatingGoal(false);
    }
  };

  const moveGoal = async (index: number, direction: 'UP' | 'DOWN') => {
    const sortedGoals = [...goals].sort((a,b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    const targetIndex = direction === 'UP' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= sortedGoals.length) return;
    
    [sortedGoals[index], sortedGoals[targetIndex]] = [sortedGoals[targetIndex], sortedGoals[index]];
    
    try {
      await apiClient.put('/creator/tip-goals/reorder', sortedGoals.map(g => g.id));
      fetchGoals();
    } catch (err) {
      console.error('DASHBOARD: Failed to reorder goals', err);
    }
  };

  const startEditGoal = (goal: TipGoal) => {
    setEditingGoalId(goal.id);
    setGoalTitle(goal.title);
    setGoalTarget(goal.targetAmount);
    setGoalAutoReset(goal.autoReset);
  };

  const cancelEdit = () => {
    setEditingGoalId(null);
    setGoalTitle('');
    setGoalTarget(1000);
    setGoalAutoReset(false);
  };


  const handleGoLive = async () => {
    if (!user) return;
    setLoading(true);
    try {
      // 1. Tell backend we are starting
      const startRes = await apiClient.post('/creator/live/start', {
        title,
        description,
        minChatTokens,
        isPaid,
        admissionPrice: isPaid ? admissionPrice : null,
        pricePerMessage
      });

      const streamRoomId = startRes.data.roomId;
      if (!streamRoomId) {
        throw new Error("Failed to get stream room ID from server");
      }
      
      // 2. Initialize signaling and webRtcService
      webRtcService.setCurrentUserId(Number(user.id));
      await webRtcService.connect(streamRoomId, (signal) => {
        handleSignalingData(signal, streamRoomId);
      });

      // 3. Mediasoup Publish Flow
      // a. Get Router Capabilities
      const routerRtpCapabilities = await webRtcService.sendRequest(SignalingType.GET_ROUTER_CAPABILITIES, streamRoomId);
      
      // b. Load Device
      const device = await webRtcService.initDevice(routerRtpCapabilities);
      
      // c. Create Send Transport on server
      const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, streamRoomId, {
        direction: 'send'
      });

      // d. Create Send Transport on client
      console.log("SEND TRANSPORT CONFIG:", JSON.stringify(transportData, null, 2));
      const transport = device.createSendTransport(transportData);
      sendTransport.current = transport;

      transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
        try {
          await webRtcService.sendRequest(SignalingType.CONNECT_TRANSPORT, streamRoomId, {
            transportId: transport.id,
            dtlsParameters
          });
          callback();
        } catch (error: any) {
          errback(error);
        }
      });

      transport.on('produce', async ({ kind, rtpParameters, appData }, callback, errback) => {
        try {
          const { id } = await webRtcService.sendRequest(SignalingType.PRODUCE, streamRoomId, {
            transportId: transport.id,
            kind,
            rtpParameters,
            appData
          });
          callback({ id });
        } catch (error: any) {
          errback(error);
        }
      });

      const attemptIceRestart = async () => {
        if (!transport || transport.closed) return;

        if (!transport.appData.restartAttempts) {
          transport.appData.restartAttempts = 0;
        }

        if ((transport.appData.restartAttempts as number) >= 3) {
          console.error('CREATOR: Max ICE restart attempts reached, stopping.');
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

          console.log(`CREATOR: Initiating ICE restart (attempt ${attempt}) after ${jitterDelay}ms delay...`);

          const response = await webRtcService.sendRequest(SignalingType.RESTART_ICE, streamRoomId, {
            transportId: transport.id
          });

          await transport.restartIce({
            iceParameters: response.iceParameters
          });
          console.log('CREATOR: ICE restart success, gathering candidates...');
        } catch (error) {
          console.error('CREATOR: ICE restart failed', error);
        }
      };

      transport.on('connectionstatechange', async (state) => {
        if (state === 'failed') {
          console.log('CREATOR: SendTransport connection failed, initiating immediate restart...');
          attemptIceRestart();
        } else if (state === 'disconnected') {
          const jitterDelay = Math.floor(Math.random() * 3000) + 3000;
          console.log(`CREATOR: SendTransport connection disconnected, waiting ${jitterDelay}ms before restart check...`);
          setTimeout(() => {
            if (transport.connectionState === 'disconnected') {
              console.log(`CREATOR: Still disconnected after ${jitterDelay}ms, initiating restart...`);
              attemptIceRestart();
            } else {
              console.log('CREATOR: Recovered from disconnected state automatically.');
            }
          }, jitterDelay);
        }
      });

      // e. Produce tracks from local stream
      if (localStreamRef.current) {
        const videoTrack = localStreamRef.current.getVideoTracks()[0];
        const audioTrack = localStreamRef.current.getAudioTracks()[0];

        console.log("PRODUCE - VIDEO TRACK:", videoTrack);
        console.log("PRODUCE - VIDEO TRACK SETTINGS:", videoTrack?.getSettings());
        console.log("PRODUCE - VIDEO TRACK READY STATE:", videoTrack?.readyState);
        console.log("PRODUCE - AUDIO TRACK:", audioTrack);
        console.log("PRODUCE - AUDIO TRACK SETTINGS:", audioTrack?.getSettings());
        console.log("PRODUCE - AUDIO TRACK READY STATE:", audioTrack?.readyState);

        if (videoTrack && videoTrack.readyState === "live") {
          // Firefox does not support scalabilityMode in RTCRtpEncodingParameters;
          // SIMULCAST_ENCODINGS already has it removed, but guard with a
          // browser-safe copy that strips any residual SVC fields.
          const isFirefox = navigator.userAgent.toLowerCase().includes("firefox");
          const encodings = isFirefox
            ? SIMULCAST_ENCODINGS.map(({ rid, maxBitrate, scaleResolutionDownBy, maxFramerate }) => ({
                rid, maxBitrate, scaleResolutionDownBy, ...(maxFramerate ? { maxFramerate } : {})
              }))
            : SIMULCAST_ENCODINGS;

          try {
            const videoProducer = await transport.produce({ 
              track: videoTrack,
              encodings,
              codecOptions: {
                videoGoogleStartBitrate: 1000
              }
            });
            console.log("VIDEO PRODUCER CREATED", videoProducer.id);
            producers.current.set('video', videoProducer);
          } catch (err) {
            console.error("VIDEO PRODUCER FAILED", err);
          }
        } else {
          console.error("VIDEO TRACK NOT AVAILABLE OR NOT LIVE:", videoTrack?.readyState);
        }

        if (audioTrack && audioTrack.readyState === "live") {
          try {
            const audioProducer = await transport.produce({ track: audioTrack });
            console.log("AUDIO PRODUCER CREATED", audioProducer.id);
            producers.current.set('audio', audioProducer);
          } catch (err) {
            console.error("AUDIO PRODUCER FAILED", err);
          }
        } else {
          console.error("AUDIO TRACK NOT AVAILABLE OR NOT LIVE:", audioTrack?.readyState);
        }
      }

      setIsLive(true);
      console.log('LIVE: Mediasoup streaming started');
    } catch (err) {
      console.error('LIVE: Failed to go live (Mediasoup):', err);
      alert('Failed to start live stream. Please try again.');
      webRtcService.cleanup();
    } finally {
      setLoading(false);
    }
  };

  const handleStopLive = async () => {
    if (!user) return;
    setLoading(true);
    try {
      await apiClient.post('/creator/live/stop');
      
      webRtcService.leaveStream();
      
      sendTransport.current = null;
      producers.current.clear();
      
      setIsLive(false);
      console.log('LIVE: Mediasoup streaming stopped');
    } catch (err) {
      console.error('LIVE: Failed to stop live:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSignalingData = async (signal: SignalingMessage, roomId: string) => {
    // With Mediasoup SFU, the creator doesn't need to handle JOIN/OFFER/ICE from viewers.
    // All viewers connect to the SFU, not the creator.
    // The only signals the creator might receive are from the SFU itself (via relay).
    console.debug("CREATOR: Received signaling message (SFU mode):", signal.type);
  };

  return (
    <div className="flex flex-col min-h-screen">
      <PrivateShowCreatorHandler onSessionAccepted={(session) => {
        setActivePrivateSession({
          id: session.id,
          viewerId: session.viewerId,
          creatorId: session.creatorId,
          pricePerMinute: session.pricePerMinute,
          status: PrivateSessionStatus.ACCEPTED,
        });
      }} />

      {/* Private Session Accepted Banner */}
      {activePrivateSession?.status === PrivateSessionStatus.ACCEPTED && (
        <div className="mx-6 mt-4 p-4 bg-purple-600/20 border border-purple-500/30 rounded-2xl flex items-center justify-between">
          <div className="text-sm text-purple-200">
            <span className="font-bold text-white">Private session accepted</span>
            <span className="ml-2 text-purple-300">— {activePrivateSession.pricePerMinute} 🪙/min</span>
          </div>
          <button
            onClick={handleStartPrivateSession}
            disabled={isStartingSession}
            className="px-5 py-2.5 bg-purple-600 hover:bg-purple-700 disabled:bg-purple-600/50 disabled:cursor-not-allowed text-white rounded-xl font-bold text-sm shadow-lg shadow-purple-600/20 transition-all duration-200"
          >
            {isStartingSession ? 'Starting...' : '▶ Start Private Session'}
          </button>
        </div>
      )}

      {/* Private Session Active Banner */}
      {activePrivateSession?.status === PrivateSessionStatus.ACTIVE && (
        <div className="mx-6 mt-4 p-4 bg-red-600/15 border border-red-500/30 rounded-2xl flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="px-2.5 py-1 bg-red-500/15 border border-red-500/40 rounded-full text-red-400 text-sm font-bold flex items-center gap-1.5">
              <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />
              Private Active
            </span>
            <span className="text-white font-bold text-lg font-mono tracking-wider">{formatSessionTime(privateSessionSeconds)}</span>
            <span className="text-red-300/70 text-sm">·</span>
            <span className="text-red-300/80 text-sm font-medium">{activePrivateSession.pricePerMinute} 🪙/min</span>
            {spyCount > 0 && (
              <>
                <span className="text-red-300/70 text-sm">·</span>
                <span className="px-2 py-0.5 bg-amber-500/15 border border-amber-500/30 rounded-full text-amber-300 text-xs font-bold">👁 {spyCount} {spyCount === 1 ? 'spy' : 'spies'}</span>
              </>
            )}
          </div>
          <button
            onClick={handleEndPrivateSession}
            disabled={isEndingSession}
            className="px-5 py-2.5 bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-xl font-bold text-sm shadow-lg transition-all duration-200"
          >
            {isEndingSession ? 'Ending...' : 'End Private Session'}
          </button>
        </div>
      )}
      {/* Header */}
      <header className="flex items-center justify-between py-4 border-b border-white/5 sticky top-0 z-50 bg-black/40 backdrop-blur-xl px-6">
        <div className="flex items-center gap-4">
          <button 
            onClick={() => navigate('/creator/dashboard')}
            className="text-zinc-400 hover:text-white transition"
          >
            ← Back
          </button>
          <h1 className="text-xl font-bold text-zinc-100">Live Dashboard</h1>
        </div>
        
        <div className="flex flex-col items-end gap-3">
          {!isLive && (
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm font-medium text-zinc-400 cursor-pointer">
                <input 
                  type="checkbox" 
                  checked={isPaid} 
                  onChange={(e) => setIsPaid(e.target.checked)}
                  className="rounded border-[#16161D] bg-[#08080A] text-indigo-600 focus:ring-indigo-500"
                />
                Paid stream
              </label>
              {isPaid && (
                <div className="flex items-center gap-2 bg-white/5 px-3 py-1 rounded-lg border border-[#16161D]">
                  <input 
                    type="number" 
                    value={admissionPrice} 
                    onChange={(e) => setAdmissionPrice(Number(e.target.value))}
                    className="w-16 bg-transparent text-sm font-bold focus:outline-none text-white"
                    min="1"
                  />
                  <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider">Tokens</span>
                </div>
              )}
            </div>
          )}
          <div className="flex items-center gap-4">
            {isLive && (
              <div className="flex items-center gap-2 px-3 py-1 bg-red-500/10 text-red-500 rounded-full text-sm font-bold animate-pulse border border-red-500/20">
                <span className="w-2 h-2 bg-red-500 rounded-full" />
                LIVE
              </div>
            )}
            <button
              onClick={() => setIsModSettingsOpen(true)}
              className="p-2 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 text-white transition-all flex items-center justify-center"
              title="Moderation Settings"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </button>
            <button
              onClick={isLive ? handleStopLive : handleGoLive}
              disabled={loading || !localStream}
              className={`px-6 py-2 rounded-xl font-semibold transition-all shadow-lg ${
                isLive 
                  ? 'bg-red-600 hover:bg-red-700 text-white shadow-red-600/30' 
                  : 'bg-gradient-to-r from-purple-600 to-pink-600 text-white shadow-purple-500/20 hover:opacity-90'
              } disabled:bg-zinc-800 disabled:text-zinc-500`}
            >
              {loading ? 'Processing...' : (isLive ? 'Stop Live' : 'Go Live')}
            </button>
          </div>
        </div>
      </header>

      <div className="flex flex-col">
        {/* LIVE AREA */}
        <div className="flex flex-col lg:flex-row gap-6 px-6 mt-6 mb-6">
          {/* Preview Section */}
          <div className="flex-1 flex flex-col">
            <div 
              className="bg-black rounded-2xl overflow-hidden shadow-2xl relative w-full"
              style={{ maxWidth: previewWidth, aspectRatio: '16/9' }}
            >
              <video
                ref={videoRef}
                autoPlay
                muted
                playsInline
                className="w-full aspect-video object-cover"
              />
              {showPinnedBanner && <PinnedMessageBanner pinnedMessage={pinnedMessage} />}
              {/* Tip Overlay */}
              {showTipOverlays && <div className="absolute inset-0 pointer-events-none z-40 overflow-hidden flex flex-col items-center justify-center">
                {activeTips.map((tip) => (
                  <div 
                    key={tip.id} 
                    className="tip-overlay-item flex flex-col items-center gap-1"
                  >
                    <div className="bg-white/10 backdrop-blur-xl border border-[#16161D] px-6 py-3 rounded-full shadow-2xl">
                      <span className="text-xl font-black text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.5)]">
                        +{Number(tip.amount || 0)} tokens <span className="animate-bounce inline-block">💖</span>
                      </span>
                    </div>
                    <span className="text-[10px] font-bold text-white/60 uppercase tracking-widest bg-black/40 px-3 py-1 rounded-full">
                      {(tip.username || tip.senderUsername || tip.viewer || 'Someone').toString()} tipped!
                    </span>
                  </div>
                ))}
              </div>}
              {!localStream && (
                <div className="absolute inset-0 flex items-center justify-center text-white bg-zinc-900">
                  {cameraError ? (
                    <p className="text-red-400 text-sm font-medium px-8 text-center">{cameraError}</p>
                  ) : (
                    <p>Initializing camera...</p>
                  )}
                </div>
              )}
              {showPreviewLabels && (
                <div className="absolute bottom-6 left-6 flex items-center gap-3">
                  <div className="bg-black/50 backdrop-blur-md px-4 py-2 rounded-lg border border-[#16161D]">
                    <p className="text-white text-sm font-medium">Local Preview</p>
                  </div>
                </div>
              )}

              {/* Legacy goal switching overlay — hidden in favor of Goal Groups */}
              {false && goalSwitching && tipGoal && (
                <div className="absolute inset-0 flex items-center justify-center bg-black/80 animate-fadeIn z-50">
                  <div className="text-center">
                    <h2 className="text-xl font-bold text-purple-400">
                      🎯 NEXT GOAL STARTING
                    </h2>
                    <p className="mt-2 text-lg text-white">
                      {safeRender(tipGoal.title)} – {safeRender(tipGoal.targetAmount)} Tokens
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div
            className="chat-resizer"
            onMouseDown={startResize}
          />

          {/* Chat Panel */}
          <div 
            className="creator-chat flex flex-col bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl max-h-[65vh] overflow-hidden"
            style={{ width: chatWidth }}
          >
            <div 
              className="flex flex-col flex-1 min-h-0"
              style={{ fontSize: chatFontSize }}
            >
              {/* TAB HEADER */}
              <div className="flex items-center px-4 border-b border-white/5 bg-black/20 shrink-0">
                 <button onClick={() => setActiveTab('CHAT')} className={`px-4 py-3 text-[10px] font-black tracking-[0.2em] transition-all duration-200 border-b-2 ${activeTab === 'CHAT' ? 'text-white border-indigo-500' : 'text-zinc-500 border-transparent hover:text-white'}`}>CHAT</button>
                 <button onClick={() => setActiveTab('PM')} className={`px-4 py-3 text-[10px] font-black tracking-[0.2em] transition-all duration-200 border-b-2 ${activeTab === 'PM' ? 'text-white border-indigo-500' : 'text-zinc-500 border-transparent hover:text-white'}`}>PM</button>
                 <button onClick={() => setActiveTab('USERS')} className={`px-4 py-3 text-[10px] font-black tracking-[0.2em] transition-all duration-200 border-b-2 ${activeTab === 'USERS' ? 'text-white border-indigo-500' : 'text-zinc-500 border-transparent hover:text-white'}`}>
                   VIEWERS
                   {viewers.length > 0 && (
                     <span className="ml-1.5 inline-flex items-center justify-center min-w-[16px] h-4 px-1 bg-indigo-500/30 border border-indigo-500/40 rounded-full text-[9px] font-bold text-indigo-300 leading-none">
                       {viewers.length}
                     </span>
                   )}
                 </button>
                 <div className="flex-1" />
                 <button 
                    onClick={() => setActiveTab('SETTINGS')} 
                    className={`p-2 transition-all duration-200 rounded-lg hover:bg-white/5 ${activeTab === 'SETTINGS' ? 'text-white' : 'text-zinc-500 hover:text-white'}`}
                    title="Dashboard Settings"
                 >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                 </button>
              </div>

              <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
                {/* CHAT panel — always mounted to preserve WebSocket subscription and message state */}
                <div style={{ display: activeTab === 'CHAT' ? 'flex' : 'none' }} className="flex-col flex-1 min-h-0 h-full">
                  {user && (
                    <ChatComponent creatorId={Number(user.id)} onTip={handleTip} chatFontSize={chatFontSize} />
                  )}
                </div>

                {/* PM panel */}
                <div style={{ display: activeTab === 'PM' ? 'flex' : 'none' }} className="flex-1 flex-col bg-black/20 h-full min-h-0 overflow-hidden">
                    {pmSessions.length === 0 && !activePm ? (
                      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                        <div className="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mb-4 text-zinc-600">
                          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
                          </svg>
                        </div>
                        <p className="text-zinc-500 font-bold text-sm mb-1">No Private Messages</p>
                        <p className="text-zinc-600 text-[11px] max-w-[200px]">Start a conversation from the VIEWERS tab.</p>
                      </div>
                    ) : (
                      <div className="flex flex-1 min-h-0 overflow-hidden">
                        {/* Session list */}
                        <div className="w-1/3 border-r border-white/5 overflow-y-auto">
                          {pmSessions.map(s => (
                            <div
                              key={s.roomId}
                              onClick={() => setActivePm(s)}
                              className={`px-3 py-3 cursor-pointer border-b border-white/5 hover:bg-white/5 transition relative ${activePm?.roomId === s.roomId ? 'bg-indigo-600/20 border-l-2 border-l-indigo-500' : ''}`}
                            >
                              <div className="flex items-center justify-between">
                                <p className="text-xs font-bold text-white truncate">{s.viewerUsername || `User #${s.viewerId}`}</p>
                                {(s.unreadCount || 0) > 0 && (
                                  <span className="ml-1 min-w-[18px] h-[18px] bg-red-500 rounded-full text-[10px] font-bold text-white flex items-center justify-center px-1">{s.unreadCount}</span>
                                )}
                              </div>
                              <p className="text-[10px] text-zinc-500 truncate">
                                {(pmMessages[s.roomId]?.slice(-1)[0]?.content) || 'No messages yet'}
                              </p>
                            </div>
                          ))}
                        </div>
                        {/* Messages */}
                        <div className="flex flex-col flex-1 min-h-0">
                          {activePm ? (
                            <>
                              <div className="px-3 py-2 border-b border-white/5 bg-black/30 shrink-0 flex items-center justify-between">
                                <p className="text-xs font-bold text-white">{activePm.viewerUsername || `User #${activePm.viewerId}`}</p>
                                <button
                                  onClick={() => handleEndPm(activePm.roomId)}
                                  className="text-[10px] text-red-400 hover:text-red-300 font-bold transition"
                                  title="End session"
                                >
                                  ✕ End
                                </button>
                              </div>
                              <div ref={pmMessagesContainerRef} className="flex-1 overflow-y-auto min-h-0 px-3 py-2 space-y-2">
                                {(pmMessages[activePm.roomId] || []).map((m, i) => (
                                  <div key={i} className={`flex ${m.senderId === Number(user?.id) ? 'justify-end' : 'justify-start'}`}>
                                    <div className={`max-w-[80%] px-3 py-1.5 rounded-xl text-xs ${m.senderId === Number(user?.id) ? 'bg-indigo-600 text-white' : 'bg-white/10 text-zinc-300'}`}>
                                      {m.content}
                                    </div>
                                  </div>
                                ))}
                                <div ref={pmMessagesEndRef} />
                              </div>
                              <div className="px-3 py-2 border-t border-white/5 shrink-0 flex gap-2">
                                <input
                                  value={pmInput}
                                  onChange={e => setPmInput(e.target.value)}
                                  onKeyDown={e => e.key === 'Enter' && handleSendPm()}
                                  placeholder="Type a message..."
                                  className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white placeholder-zinc-500 outline-none focus:border-indigo-500"
                                />
                                <button
                                  onClick={handleSendPm}
                                  disabled={pmSending || !pmInput.trim() || !activePm?.roomId}
                                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 rounded-lg text-xs font-bold text-white transition"
                                >
                                  Send
                                </button>
                              </div>
                            </>
                          ) : (
                            <div className="flex-1 flex items-center justify-center text-zinc-500 text-xs">Select a conversation</div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>

                {/* VIEWERS panel */}
                <div style={{ display: activeTab === 'USERS' ? 'flex' : 'none' }} className="flex-1 flex-col bg-black/20 overflow-y-auto">
                    {viewersLoading ? (
                      <div className="flex-1 flex items-center justify-center text-zinc-500 text-xs">Loading viewers...</div>
                    ) : viewers.length === 0 ? (
                      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                        <div className="w-12 h-12 bg-white/5 rounded-full flex items-center justify-center mb-4 text-zinc-600">
                          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                          </svg>
                        </div>
                        <p className="text-zinc-500 font-bold text-sm mb-1">No Viewers</p>
                        <p className="text-zinc-600 text-[11px] max-w-[200px]">Viewers will appear here when your stream is live.</p>
                      </div>
                    ) : (
                      <div className="divide-y divide-white/5">
                        {viewers.map((v: any) => (
                          <div key={v.id} className="px-4 py-3 hover:bg-white/5 transition space-y-2">
                            <div className="flex items-center justify-between">
                              <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-1.5">
                                  <p className="text-xs font-bold text-white truncate">{v.displayName || v.username || `User #${v.id}`}</p>
                                  {v.isModerator && (
                                    <span className="shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wide bg-green-500/15 text-green-400 border border-green-500/20">🛡 Mod</span>
                                  )}
                                  {v.isFollower && (
                                    <span className="shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wide bg-amber-500/15 text-amber-400 border border-amber-500/20">★ Follower</span>
                                  )}
                                </div>
                              </div>
                              <div className="flex items-center gap-1 ml-2">
                                {v.isModerator ? (
                                  <button
                                    onClick={() => handleRevokeMod(v.id)}
                                    className="px-2 py-1 bg-red-500/20 hover:bg-red-500/30 rounded-lg text-[10px] font-bold text-red-400 border border-red-500/20 transition shrink-0"
                                  >
                                    Remove Mod
                                  </button>
                                ) : (
                                  <button
                                    onClick={() => handleGrantMod(v.id)}
                                    className="px-2 py-1 bg-green-500/20 hover:bg-green-500/30 rounded-lg text-[10px] font-bold text-green-400 border border-green-500/20 transition shrink-0"
                                  >
                                    Grant Mod
                                  </button>
                                )}
                                <button
                                  onClick={() => handleStartPm(v.id)}
                                  className="px-3 py-1 bg-indigo-600 hover:bg-indigo-500 rounded-lg text-[10px] font-bold text-white transition shrink-0"
                                >
                                  💬 Message
                                </button>
                              </div>
                            </div>
                            <div className="flex items-center gap-1 flex-wrap">
                              <button onClick={() => handleMuteViewer(v.id, 5)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">Mute 5m</button>
                              <button onClick={() => handleMuteViewer(v.id, 30)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">30m</button>
                              <button onClick={() => handleMuteViewer(v.id, 1440)} className="px-2 py-1 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 text-[10px] font-bold border border-white/5 transition">24h</button>
                              <button onClick={() => handleShadowMuteViewer(v.id)} className="px-2 py-1 rounded-lg bg-indigo-500/20 text-indigo-300 hover:bg-indigo-500/30 text-[10px] font-bold border border-indigo-500/20 transition">Shadow Mute</button>
                              <button onClick={() => handleKickViewer(v.id)} className="px-2 py-1 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 text-[10px] font-bold border border-red-500/20 transition">Kick</button>
                            </div>
                            <div className="flex items-center gap-1 flex-wrap mt-1">
                              <span className="text-[9px] text-zinc-600 font-bold uppercase mr-1">Ban:</span>
                              <button onClick={() => handleBanViewer(v.id, '5m')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">5m</button>
                              <button onClick={() => handleBanViewer(v.id, '30m')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">30m</button>
                              <button onClick={() => handleBanViewer(v.id, '24h')} className="px-2 py-1 rounded-lg bg-orange-500/15 text-orange-400 hover:bg-orange-500/25 text-[10px] font-bold border border-orange-500/20 transition">24h</button>
                              <button onClick={() => handleBanViewer(v.id, 'permanent')} className="px-2 py-1 rounded-lg bg-red-600/20 text-red-400 hover:bg-red-600/30 text-[10px] font-bold border border-red-600/30 transition">Perm Ban</button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Current Moderators Section */}
                    {moderatorList.length > 0 && (
                      <div className="border-t border-white/10 mt-2">
                        <div className="px-4 py-2 flex items-center justify-between bg-green-500/5">
                          <h4 className="text-[10px] font-black text-green-400 uppercase tracking-[0.15em]">🛡 Current Moderators ({moderatorList.length})</h4>
                          <button
                            onClick={handleClearAllMods}
                            className="px-2 py-1 bg-red-500/15 hover:bg-red-500/25 rounded text-[9px] font-bold text-red-400 border border-red-500/20 transition"
                          >
                            Clear All
                          </button>
                        </div>
                        <div className="divide-y divide-white/5">
                          {moderatorList.map((mod) => (
                            <div key={mod.userId} className="px-4 py-2 flex items-center justify-between hover:bg-white/5 transition">
                              <div className="flex items-center gap-2">
                                <span className="text-xs font-bold text-white">{mod.username}</span>
                                <span className="text-[9px] text-zinc-500">#{mod.userId}</span>
                                <span className="shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wide bg-green-500/15 text-green-400 border border-green-500/20">🛡 Mod</span>
                                {mod.persistent ? (
                                  <span className="px-1.5 py-0.5 rounded text-[8px] font-bold bg-indigo-500/15 text-indigo-400 border border-indigo-500/20">Persistent</span>
                                ) : (
                                  <span className="px-1.5 py-0.5 rounded text-[8px] font-bold bg-amber-500/15 text-amber-400 border border-amber-500/20">Stream Only</span>
                                )}
                              </div>
                              <button
                                onClick={() => handleRevokeMod(mod.userId)}
                                className="px-2 py-1 bg-red-500/20 hover:bg-red-500/30 rounded text-[10px] font-bold text-red-400 border border-red-500/20 transition"
                              >
                                Remove
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Banned Users Section */}
                    {bannedUsers.length > 0 && (
                      <div className="border-t border-white/10 mt-2">
                        <div className="px-4 py-2 bg-red-500/5">
                          <h4 className="text-[10px] font-black text-red-400 uppercase tracking-[0.15em]">🚫 Banned Users ({bannedUsers.length})</h4>
                        </div>
                        <div className="divide-y divide-white/5">
                          {bannedUsers.map((ban: any) => (
                            <div key={ban.id} className="px-4 py-2 flex items-center justify-between hover:bg-white/5 transition">
                              <div className="flex items-center gap-2 flex-wrap">
                                <span className="text-xs font-bold text-white">{ban.targetUsername}</span>
                                <span className="text-[9px] text-zinc-500">#{ban.targetUserId}</span>
                                <span className={`shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold tracking-wide ${
                                  ban.banType === 'permanent'
                                    ? 'bg-red-500/15 text-red-400 border border-red-500/20'
                                    : 'bg-orange-500/15 text-orange-400 border border-orange-500/20'
                                }`}>
                                  {ban.banType === 'permanent' ? '⛔ Permanent' : `⏱ ${ban.banType}`}
                                </span>
                                <span className="text-[8px] text-zinc-600">by {ban.issuedByUsername}</span>
                                {ban.expiresAt && (
                                  <span className="text-[8px] text-zinc-600">expires {new Date(ban.expiresAt).toLocaleTimeString()}</span>
                                )}
                              </div>
                              <button
                                onClick={() => handleUnbanUser(ban.targetUserId)}
                                className="px-2 py-1 bg-green-500/20 hover:bg-green-500/30 rounded text-[10px] font-bold text-green-400 border border-green-500/20 transition shrink-0"
                              >
                                Unban
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Moderation History Section */}
                    {auditLog.length > 0 && (
                      <div className="border-t border-white/10 mt-2">
                        <div className="px-4 py-2 bg-zinc-800/50">
                          <h4 className="text-[10px] font-black text-zinc-400 uppercase tracking-[0.15em]">📋 Moderation History</h4>
                        </div>
                        <div className="divide-y divide-white/5 max-h-[200px] overflow-y-auto">
                          {auditLog.slice(0, 30).map((entry: any) => {
                            const actionColors: Record<string, string> = {
                              BAN: 'text-red-400 bg-red-500/10',
                              UNBAN: 'text-green-400 bg-green-500/10',
                              MUTE: 'text-amber-400 bg-amber-500/10',
                              SHADOW_MUTE: 'text-indigo-400 bg-indigo-500/10',
                              KICK: 'text-orange-400 bg-orange-500/10',
                              GRANT_MOD: 'text-green-400 bg-green-500/10',
                              REVOKE_MOD: 'text-red-400 bg-red-500/10',
                              DELETE_MESSAGE: 'text-zinc-400 bg-zinc-500/10',
                            };
                            const colorClass = actionColors[entry.actionType] || 'text-zinc-400 bg-zinc-500/10';
                            return (
                              <div key={entry.id} className="px-4 py-1.5 flex items-center gap-2 text-[10px] hover:bg-white/5 transition">
                                <span className={`shrink-0 px-1.5 py-0.5 rounded font-bold ${colorClass}`}>{entry.actionType}</span>
                                <span className="text-zinc-300 font-bold">{entry.targetUsername || `#${entry.targetUserId}`}</span>
                                <span className="text-zinc-600">by</span>
                                <span className="text-zinc-400">{entry.actorUsername}</span>
                                <span className="text-[8px] px-1 py-0.5 rounded bg-zinc-800 text-zinc-500">{entry.actorRole}</span>
                                <span className="text-zinc-600 ml-auto shrink-0">{new Date(entry.createdAt).toLocaleTimeString()}</span>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}
                  </div>

                {/* SETTINGS panel */}
                <div style={{ display: activeTab === 'SETTINGS' ? 'block' : 'none' }} className="flex-1 overflow-y-auto">
                  <div className="p-6 space-y-8">
                    <div>
                       <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Dashboard Audio</h4>
                       <div className="space-y-4">
                          <div className="flex justify-between items-center">
                            <span className="text-[10px] uppercase tracking-widest text-gray-500">Alert & Notification Volume</span>
                          </div>
                          <GiftSoundControls isStatic={true} />
                       </div>
                    </div>
                    <div>
                       <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Chat</h4>
                       <div className="space-y-4">
                          <div>
                            <div className="flex justify-between items-center mb-2">
                              <span className="text-[10px] uppercase tracking-widest text-gray-500">Chat Font Size</span>
                              <span className="text-xs text-purple-400">{chatFontSize}px</span>
                            </div>
                            <input
                              type="range"
                              min="10"
                              max="24"
                              value={chatFontSize}
                              onChange={(e) => {
                                const v = Number(e.target.value);
                                setChatFontSize(v);
                                localStorage.setItem('livora-creator-chat-font-size', String(v));
                              }}
                              className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-purple-500 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:shadow-lg [&::-webkit-slider-thumb]:shadow-purple-500/40"
                            />
                          </div>
                       </div>
                    </div>
                    <div>
                       <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Preview Display</h4>
                       <div className="space-y-3">
                          <div className="flex items-center justify-between">
                            <span className="text-[11px] text-zinc-400">Show Pinned Message Banner</span>
                            <button
                              onClick={() => { setShowPinnedBanner(v => { const nv = !v; localStorage.setItem('livora-creator-show-pinned-banner', String(nv)); return nv; }); }}
                              className={`relative w-9 h-5 rounded-full transition-colors duration-200 ${showPinnedBanner ? 'bg-indigo-500' : 'bg-zinc-700'}`}
                            >
                              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200 ${showPinnedBanner ? 'translate-x-4' : ''}`} />
                            </button>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-[11px] text-zinc-400">Show Tip Animations</span>
                            <button
                              onClick={() => { setShowTipOverlays(v => { const nv = !v; localStorage.setItem('livora-creator-show-tip-overlays', String(nv)); return nv; }); }}
                              className={`relative w-9 h-5 rounded-full transition-colors duration-200 ${showTipOverlays ? 'bg-indigo-500' : 'bg-zinc-700'}`}
                            >
                              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200 ${showTipOverlays ? 'translate-x-4' : ''}`} />
                            </button>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-[11px] text-zinc-400">Show Preview Labels</span>
                            <button
                              onClick={() => { setShowPreviewLabels(v => { const nv = !v; localStorage.setItem('livora-creator-show-preview-labels', String(nv)); return nv; }); }}
                              className={`relative w-9 h-5 rounded-full transition-colors duration-200 ${showPreviewLabels ? 'bg-indigo-500' : 'bg-zinc-700'}`}
                            >
                              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200 ${showPreviewLabels ? 'translate-x-4' : ''}`} />
                            </button>
                          </div>
                       </div>
                    </div>
                    <div>
                       <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-4">Moderators</h4>
                       <div className="space-y-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <span className="text-[11px] text-zinc-400">Keep moderators for future streams</span>
                              <p className="text-[9px] text-zinc-600 mt-0.5">When enabled, newly granted moderators persist across streams. When disabled, they are removed when the stream ends.</p>
                            </div>
                            <button
                              onClick={() => { setKeepModerators(v => { const nv = !v; localStorage.setItem('livora-keep-moderators', String(nv)); return nv; }); }}
                              className={`relative w-9 h-5 rounded-full transition-colors duration-200 shrink-0 ml-3 ${keepModerators ? 'bg-green-500' : 'bg-zinc-700'}`}
                            >
                              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200 ${keepModerators ? 'translate-x-4' : ''}`} />
                            </button>
                          </div>
                       </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* STREAM HEALTH */}
        <div className="px-6 mt-6">
          <StreamHealthWidget
            isLive={isLive}
            viewerCount={viewerCount}
            sendTransport={sendTransport}
            producers={producers}
          />
        </div>

        {/* DASHBOARD AREA */}
        <div className="space-y-10 px-6 pb-16">
          {/* Stream Info Section */}
          <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 shadow-2xl shadow-black/40">
            <h2 className="font-bold text-zinc-100 mb-4">Stream Info</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8 text-sm">
              <div className="flex flex-col gap-4">
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-[10px] uppercase tracking-widest text-gray-500">Preview Width</span>
                    <span className="text-xs text-purple-400">{previewWidth}px</span>
                  </div>
                  <input 
                    type="range" 
                    min="400" 
                    max="1200" 
                    step="10"
                    value={previewWidth} 
                    onChange={(e) => setPreviewWidth(Number(e.target.value))}
                    className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-purple-500 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:shadow-lg [&::-webkit-slider-thumb]:shadow-purple-500/40"
                  />
                </div>
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-[10px] uppercase tracking-widest text-gray-500">Chat Font Size</span>
                    <span className="text-xs text-purple-400">{chatFontSize}px</span>
                  </div>
                  <input 
                    type="range" 
                    min="10" 
                    max="24" 
                    value={chatFontSize} 
                    onChange={(e) => {
                      const v = Number(e.target.value);
                      setChatFontSize(v);
                      localStorage.setItem('livora-creator-chat-font-size', String(v));
                    }}
                    className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-purple-500 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:shadow-lg [&::-webkit-slider-thumb]:shadow-purple-500/40"
                  />
                </div>
              </div>

              {/* Earnings */}
              <div className={`bg-gradient-to-br from-purple-600/10 via-pink-500/5 to-transparent border border-purple-500/20 rounded-3xl p-6 backdrop-blur-md shadow-xl self-start transition-all duration-500 ${sessionTokens > 0 ? 'shadow-purple-500/30' : 'shadow-purple-500/10'}`}>
                <h4 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-2">Session Earnings</h4>
                <p className={`text-4xl font-bold bg-gradient-to-r from-purple-400 to-pink-400 bg-clip-text text-transparent transition-all duration-500 ${sessionTokens > 0 ? 'animate-pulse' : ''}`}>{safeRender(sessionTokens)} Tokens</p>
                <p className="text-sm text-gray-400 mt-1">≈ €{(sessionTokens * 0.05).toFixed(2)}</p>
              </div>
              
              <div className="space-y-4">
                <div>
                  <p className="text-zinc-500 mb-1">Title</p>
                  <input 
                    type="text" 
                    value={title} 
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Stream title..."
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 focus:outline-none focus:border-purple-500/50 transition-colors text-white"
                    disabled={isLive}
                  />
                </div>
                <div>
                  <p className="text-zinc-500 mb-1">Description</p>
                  <textarea 
                    value={description} 
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Stream description..."
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 focus:outline-none focus:border-purple-500/50 transition-colors min-h-[80px] text-white"
                    disabled={isLive}
                  />
                </div>
              </div>

              <div className="space-y-4">
                <div className="flex gap-4">
                  <div>
                    <p className="text-zinc-500 mb-1">Min Chat Tokens</p>
                    <input 
                      type="number" 
                      value={minChatTokens} 
                      onChange={(e) => setMinChatTokens(Number(e.target.value))}
                      className="w-full bg-[#08080A] border border-[#16161D] rounded-lg px-3 py-2 focus:outline-none focus:ring-1 focus:ring-indigo-500 text-white"
                      disabled={isLive}
                      min="0"
                    />
                  </div>
                  <div>
                    <p className="text-zinc-500 mb-1">Token per Message</p>
                    <input 
                      type="number" 
                      value={pricePerMessage} 
                      onChange={(e) => setPricePerMessage(Number(e.target.value))}
                      className="w-full bg-[#08080A] border border-[#16161D] rounded-lg px-3 py-2 focus:outline-none focus:ring-1 focus:ring-indigo-500 text-white"
                      disabled={isLive}
                      min="0"
                    />
                  </div>
                </div>
                <div className="flex justify-between items-center">
                  <div>
                    <p className="text-zinc-500 mb-1 text-xs uppercase tracking-wider">Status</p>
                    <p className={`font-semibold ${isLive ? 'text-green-500' : 'text-zinc-500'}`}>
                      {isLive ? 'Streaming' : 'Offline'}
                    </p>
                  </div>
                  <div>
                    <p className="text-zinc-500 mb-1 text-xs uppercase tracking-wider">Active Viewers</p>
                    <p className="font-semibold text-white text-right">{isLive ? safeRender(viewerCount) : 0}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Tip Goal Section — hidden in favor of Goal Groups */}
          {false && <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 shadow-2xl shadow-black/40">
            <div className="flex justify-between items-center mb-6">
              <h2 className="font-bold text-zinc-100 flex items-center gap-2">
                <span className="text-xl">🎯</span> Tip Goal Chain
              </h2>
              {!editingGoalId && !goalTitle && (
                <button 
                  onClick={() => { setEditingGoalId(null); setGoalTitle('New Goal'); }}
                  className="text-[10px] font-black uppercase tracking-widest bg-indigo-500/10 text-indigo-400 px-3 py-1.5 rounded-lg border border-indigo-500/20 hover:bg-indigo-500/20 transition-all"
                >
                  + Add New Goal
                </button>
              )}
            </div>
            
            <div className="space-y-4">
              {[...goals].sort((a,b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)).map((goal, index) => (
                <div key={goal.id} className={`flex items-center gap-4 p-4 rounded-xl border transition-all ${goal.active ? 'bg-indigo-500/5 border-indigo-500/20' : 'bg-[#08080A] border-white/5'}`}>
                  {/* Reorder Buttons */}
                  <div className="flex flex-col gap-1">
                    <button 
                      onClick={() => moveGoal(index, 'UP')}
                      disabled={index === 0}
                      className="p-1 hover:bg-white/5 rounded text-zinc-500 disabled:opacity-30"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" /></svg>
                    </button>
                    <button 
                      onClick={() => moveGoal(index, 'DOWN')}
                      disabled={index === goals.length - 1}
                      className="p-1 hover:bg-white/5 rounded text-zinc-500 disabled:opacity-30"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
                    </button>
                  </div>

                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-bold text-white">{safeRender(goal.title)}</span>
                      {goal.active && (
                        <span className="text-[9px] font-black bg-emerald-500 text-white px-1.5 py-0.5 rounded uppercase tracking-tighter">ACTIVE</span>
                      )}
                    </div>
                    <div className="h-3 bg-zinc-800 rounded-full overflow-hidden">
                      <div 
                        className="h-3 bg-gradient-to-r from-purple-500 via-pink-500 to-purple-400 rounded-full transition-all duration-700 ease-out shadow-purple-500/30"
                        style={{ width: `${Math.min(100, (goal.currentAmount / goal.targetAmount) * 100)}%` }}
                      />
                    </div>
                    <div className="text-xs text-gray-400 mt-2 flex justify-between">
                      <span>{safeRender(goal.currentAmount)} / {safeRender(goal.targetAmount)} Tokens</span>
                      <span>{safeRender(Math.round(Math.min(100, (goal.currentAmount / goal.targetAmount) * 100)))}%</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {!goal.active && (
                      <button 
                        onClick={() => handleToggleGoalActive(goal, true)}
                        className="text-[10px] font-bold text-zinc-500 hover:text-emerald-400 transition-colors"
                      >
                        Activate
                      </button>
                    )}
                    <button 
                      onClick={() => startEditGoal(goal)}
                      className="p-2 hover:bg-white/5 rounded-lg text-zinc-400 hover:text-white transition-all"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                    </button>
                    <button 
                      onClick={() => handleDeleteGoal(goal.id)}
                      className="p-2 hover:bg-red-500/10 rounded-lg text-zinc-400 hover:text-red-500 transition-all"
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                    </button>
                  </div>
                </div>
              ))}

              {/* Add/Edit Form */}
              {(editingGoalId || goalTitle) && (
                <div className="mt-8 pt-8 border-t border-white/5">
                  <h3 className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-6">{editingGoalId ? 'Edit Goal' : 'New Goal'}</h3>
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    <div className="space-y-4">
                      <div>
                        <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Goal Title</label>
                        <input 
                          type="text" 
                          value={goalTitle}
                          onChange={(e) => setGoalTitle(e.target.value)}
                          placeholder="e.g. New Camera Setup"
                          className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 focus:outline-none focus:border-purple-500/50 transition-colors text-white placeholder:text-zinc-700"
                        />
                      </div>
                      <div>
                        <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Target Tokens</label>
                        <div className="relative">
                          <input 
                            type="number" 
                            value={goalTarget}
                            onChange={(e) => setGoalTarget(Number(e.target.value))}
                            className="w-full bg-white/5 border border-white/10 rounded-lg pl-4 pr-16 py-2.5 focus:outline-none focus:border-purple-500/50 transition-colors text-white"
                            min="1"
                          />
                          <div className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-black text-zinc-600 uppercase tracking-widest pointer-events-none">
                            Tokens
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-6">
                      <label className="flex items-center gap-3 p-3 bg-white/5 rounded-xl border border-white/5 cursor-pointer hover:bg-white/10 transition-colors group">
                        <input 
                          type="checkbox"
                          checked={goalAutoReset}
                          onChange={(e) => setGoalAutoReset(e.target.checked)}
                          className="w-4 h-4 rounded border-zinc-700 bg-zinc-900 text-indigo-500 focus:ring-indigo-500/20"
                        />
                        <div className="flex flex-col">
                          <span className="text-xs font-bold text-zinc-300">Auto Reset</span>
                          <span className="text-[10px] text-zinc-500">Starts over when reached</span>
                        </div>
                      </label>
                    </div>

                    <div className="flex flex-col justify-end gap-3 lg:pl-8 lg:border-l lg:border-white/5">
                      <button
                        onClick={editingGoalId ? handleUpdateGoal : handleCreateGoal}
                        disabled={isUpdatingGoal || !goalTitle || goalTarget <= 0}
                        className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 disabled:bg-zinc-800 disabled:text-zinc-600 text-white rounded-xl font-bold transition-all shadow-lg shadow-indigo-600/20 flex items-center justify-center gap-2"
                      >
                        {isUpdatingGoal ? 'Saving...' : (editingGoalId ? '💾 Save Changes' : '✨ Create Goal')}
                      </button>
                      <button
                        onClick={cancelEdit}
                        className="w-full py-3 bg-white/5 hover:bg-white/10 text-white rounded-xl font-bold transition-all"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>}

          {/* Private Show Settings */}
          {privateSettings && (
            <div className="p-4 rounded-xl bg-white/5 border border-white/10 mb-4">
              <h3 className="text-sm font-semibold mb-3">Private Show</h3>

              <button
                onClick={() =>
                  handlePrivateSettingsUpdate({ ...privateSettings, enabled: !privateSettings.enabled })
                }
                className={`px-3 py-1 rounded-md text-xs transition ${
                  privateSettings.enabled
                    ? 'bg-green-500/20 text-green-400 border border-green-400/30 hover:bg-green-500/30'
                    : 'bg-red-500/20 text-red-400 border border-red-400/30 hover:bg-red-500/30'
                }`}
              >
                {privateSettings.enabled ? 'Enabled' : 'Disabled'}
              </button>

              <div className="mt-3">
                <label className="text-xs text-zinc-400 block mb-1">Price per minute (tokens)</label>
                <input
                  type="number"
                  value={privateSettings.pricePerMinute}
                  onChange={(e) =>
                    setPrivateSettings({ ...privateSettings, pricePerMinute: Number(e.target.value) })
                  }
                  min={1}
                  className="w-full bg-black/40 border border-white/10 rounded p-2 text-sm text-white"
                />
              </div>

              {/* Spy on Private Settings */}
              <div className="mt-4 pt-3 border-t border-white/5">
                <h4 className="text-xs font-bold text-amber-400/80 uppercase tracking-wider mb-2">👁 Spy on Private</h4>

                <label className="flex items-center gap-2 cursor-pointer mb-2">
                  <div
                    onClick={() => setPrivateSettings({ ...privateSettings, allowSpyOnPrivate: !privateSettings.allowSpyOnPrivate })}
                    className={`relative w-8 h-4 rounded-full transition-colors cursor-pointer ${privateSettings.allowSpyOnPrivate ? 'bg-amber-500' : 'bg-zinc-700'}`}
                  >
                    <div className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-transform ${privateSettings.allowSpyOnPrivate ? 'translate-x-4' : 'translate-x-0.5'}`} />
                  </div>
                  <span className="text-xs text-zinc-300">{privateSettings.allowSpyOnPrivate ? 'Spy Enabled' : 'Spy Disabled'}</span>
                </label>

                {privateSettings.allowSpyOnPrivate && (
                  <>
                    <div className="mt-2">
                      <label className="text-xs text-zinc-400 block mb-1">Spy price per minute (tokens)</label>
                      <input
                        type="number"
                        value={privateSettings.spyPricePerMinute ?? 25}
                        onChange={(e) => setPrivateSettings({ ...privateSettings, spyPricePerMinute: Number(e.target.value) })}
                        min={1}
                        className="w-full bg-black/40 border border-white/10 rounded p-2 text-sm text-white"
                      />
                    </div>
                    <div className="mt-2">
                      <label className="text-xs text-zinc-400 block mb-1">Max spy viewers (blank = unlimited)</label>
                      <input
                        type="number"
                        value={privateSettings.maxSpyViewers ?? ''}
                        onChange={(e) => setPrivateSettings({ ...privateSettings, maxSpyViewers: e.target.value ? Number(e.target.value) : null })}
                        min={1}
                        placeholder="∞"
                        className="w-full bg-black/40 border border-white/10 rounded p-2 text-sm text-white placeholder:text-zinc-600"
                      />
                    </div>
                  </>
                )}
              </div>

              <button
                onClick={() => handlePrivateSettingsUpdate(privateSettings)}
                className="mt-3 w-full bg-purple-500/20 text-purple-300 p-2 rounded text-sm hover:bg-purple-500/30 transition"
              >
                Save
              </button>
            </div>
          )}

          {/* Goal Groups Section */}
          <GoalGroupBuilder />

          {/* Tip Menu Actions Section */}
          <TipMenuManager />
        </div>
      </div>

      {user && (
        <>
          <CreatorModerationSettingsPanel 
            creatorId={Number(user.id)} 
            isOpen={isModSettingsOpen} 
            onClose={() => setIsModSettingsOpen(false)} 
          />
        </>
      )}
    </div>
  );
};

export default CreatorLiveDashboard;
