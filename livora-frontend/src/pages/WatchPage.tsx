import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCreatorPublicProfile } from '@/hooks/useCreatorPublicProfile';
import { webSocketService } from '@/websocket/webSocketService';
import apiClient from '@/api/apiClient';
import creatorService from '@/api/creatorService';
import { safeRender } from '@/utils/safeRender';
import { normalizeLiveEvent } from '@/components/live/LiveEventsController';
import { useAuth } from '@/auth/useAuth';
import { useWallet } from '@/wallet/WalletContext';
import { useWs } from '@/ws/WsContext';
import Navbar from '@/components/Navbar';
import AbuseReportModal from '@/components/AbuseReportModal';
import TipModal from '@/components/TipModal';
import GiftSelectorModal, { GiftSelectorModalHandle } from '@/components/GiftSelectorModal';
import { GoalStatusEvent } from '@/types/events';
import { resolveAnimationByAmount, resolveRarityByAmount } from '@/utils/animationUtils';
import { ReportTargetType } from '@/types/report';
import { showToast } from '@/components/Toast';
import LiveLayout from '@/layouts/LiveLayout';

// New Modular Components
import LiveStreamPlayer from '@/components/live/LiveStreamPlayer';
import GoalOverlay from '@/components/live/GoalOverlay';
import GoalLadderOverlay from '@/components/live/GoalLadderOverlay';
import LiveChatPanel from '@/components/live/LiveChatPanel';
import LiveTipOverlays from '@/components/live/LiveTipOverlays';
import TopTipperBanner from '@/components/TopTipperBanner';
import LeaderboardPanel, { LeaderboardPanelHandle, LeaderboardEntry } from '@/components/LeaderboardPanel';
import PrivateShowRequestButton from '@/components/PrivateShowRequestButton';
import PrivateShowSessionOverlay from '@/components/PrivateShowSessionOverlay';
import { getPrivateSettingsByCreator, PrivateSettings } from '@/api/privateSettingsService';
import privateShowService, { PrivateSession, PrivateSessionStatus, PrivateSpySession, PrivateSessionAvailability } from '@/api/privateShowService';
import { getActivePm, getPmMessages, sendPmMessage, markPmAsRead, PmSession, PmMessage } from '@/api/pmService';

const WatchPage: React.FC = () => {
  const { identifier } = useParams<{ identifier: string }>();
  const { creator, loading: profileLoading } = useCreatorPublicProfile();
  const { user, refreshTokenBalance } = useAuth();
  const navigate = useNavigate();
  const { presenceMap, subscribe, connected } = useWs();
  const { subscribeToExplosions } = useWallet();

  const [availability, setAvailability] = useState<"ONLINE" | "LIVE" | "OFFLINE" | null>(null);
  const [room, setRoom] = useState<any>(null);
  const [hasAccess, setHasAccess] = useState<boolean | null>(null);
  const [viewerCount, setViewerCount] = useState<number>(0);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [topTipper, setTopTipper] = useState<{ name: string | null; amount: number }>({ name: null, amount: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [needsInteraction, setNeedsInteraction] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);
  const [showTipModal, setShowTipModal] = useState(false);
  const [videoWidth, setVideoWidth] = useState(1400);
  const [isResizing, setIsResizing] = useState(false);
  const [goal, setGoal] = useState<GoalStatusEvent | null>(null);
  const [goalOverlayVisible, setGoalOverlayVisible] = useState(() => localStorage.getItem("goalOverlayHidden") !== "true");
  const [showGoalBanner, setShowGoalBanner] = useState(false);
  const [tokenExplosion, setTokenExplosion] = useState({ amount: 0, key: 0 });
  const [legendaryEffect, setLegendaryEffect] = useState({ username: '', amount: 0, isVisible: false, effectType: '' });
  const [privateSettings, setPrivateSettings] = useState<PrivateSettings | null>(null);
  const [privateSession, setPrivateSession] = useState<PrivateSession | null>(null);
  const [isEndingSession, setIsEndingSession] = useState(false);

  // Spy on Private state
  const [spySession, setSpySession] = useState<PrivateSpySession | null>(null);
  const [isJoiningSpy, setIsJoiningSpy] = useState(false);
  const [isLeavingSpy, setIsLeavingSpy] = useState(false);
  const [creatorActivePrivateSession, setCreatorActivePrivateSession] = useState<PrivateSession | null>(null);

  // Private session availability — determines whether current viewer can access video
  const [privateAvailability, setPrivateAvailability] = useState<PrivateSessionAvailability | null>(null);

  // Follow state
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [followerCount, setFollowerCount] = useState(0);

  // PM States (simple single-session for viewer)
  const [pmSession, setPmSession] = useState<PmSession | null>(null);
  const [pmMessages, setPmMessages] = useState<PmMessage[]>([]);
  const [pmEnded, setPmEnded] = useState(false);
  const [activeTab, setActiveTab] = useState<'CHAT' | 'PM' | 'USERS'>('CHAT');
  const activeTabRef = useRef<'CHAT' | 'PM' | 'USERS'>('CHAT');

  // Viewer list state
  const [viewers, setViewers] = useState<{ id: number; username: string; displayName: string | null; isFollower?: boolean; isModerator?: boolean }[]>([]);
  const [viewersLoading, setViewersLoading] = useState(false);

  // Current user moderator status
  const [isCurrentUserMod, setIsCurrentUserMod] = useState(false);
  const isCurrentUserModRef = useRef(false);

  // Room ban state
  const [isRoomBanned, setIsRoomBanned] = useState(false);
  const [roomBanInfo, setRoomBanInfo] = useState<{ banType: string; expiresAt: string } | null>(null);

  // Local hidden-user state (viewer-side only, no backend effect) — persisted to localStorage
  const [hiddenUserIds, setHiddenUserIds] = useState<Set<number>>(() => {
    try {
      const saved = localStorage.getItem('livora-hidden-users');
      return saved ? new Set(JSON.parse(saved) as number[]) : new Set();
    } catch { return new Set(); }
  });
  const toggleHideUser = useCallback((userId: number) => {
    setHiddenUserIds(prev => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }, []);
  const unhideAll = useCallback(() => setHiddenUserIds(new Set()), []);

  // Persist hidden users to localStorage
  useEffect(() => {
    try { localStorage.setItem('livora-hidden-users', JSON.stringify([...hiddenUserIds])); } catch {}
  }, [hiddenUserIds]);

  // Derived follower user IDs for chat badge
  const followerUserIds = useMemo(() => {
    const ids = new Set<number>();
    for (const v of viewers) { if (v.isFollower) ids.add(v.id); }
    return ids;
  }, [viewers]);

  // Derived moderator user IDs for chat badge
  const moderatorUserIds = useMemo(() => {
    const ids = new Set<number>();
    for (const v of viewers) { if (v.isModerator) ids.add(v.id); }
    return ids;
  }, [viewers]);

  // Viewer settings — persisted to localStorage
  const [chatFontSize, setChatFontSize] = useState(() => {
    try { const v = localStorage.getItem('livora-chat-font-size'); return v ? Number(v) : 14; } catch { return 14; }
  });
  const [showTipOverlays, setShowTipOverlays] = useState(() => {
    try { return localStorage.getItem('livora-hide-tip-overlays') !== 'true'; } catch { return true; }
  });
  const [showTopTipperBanner, setShowTopTipperBanner] = useState(() => {
    try { return localStorage.getItem('livora-hide-top-banner') !== 'true'; } catch { return true; }
  });

  useEffect(() => {
    try { localStorage.setItem('livora-chat-font-size', String(chatFontSize)); } catch {}
  }, [chatFontSize]);
  useEffect(() => {
    try { localStorage.setItem('livora-hide-tip-overlays', showTipOverlays ? 'false' : 'true'); } catch {}
  }, [showTipOverlays]);
  useEffect(() => {
    try { localStorage.setItem('livora-hide-top-banner', showTopTipperBanner ? 'false' : 'true'); } catch {}
  }, [showTopTipperBanner]);

  const creatorUserId = creator?.profile?.userId;
  const isOwnPage = user && creatorUserId ? Number(user.id) === creatorUserId : false;

  // Check room ban status on load and listen for ban/unban WS events
  useEffect(() => {
    if (!user || !creatorUserId || isOwnPage) return;
    apiClient.get(`/room-bans/check/${creatorUserId}/${user.id}`)
      .then(res => { if (res.data?.banned) setIsRoomBanned(true); })
      .catch(() => {});
  }, [user, creatorUserId, isOwnPage]);

  // Check if current user is a moderator for this creator
  // Use user?.id as dep instead of user object to prevent re-fires on auth context reference changes
  const userId = user?.id;
  useEffect(() => {
    if (!userId || !creatorUserId || isOwnPage) return;
    console.debug('[MOD_DEBUG] Fetching moderator status for creatorUserId=', creatorUserId, 'userId=', userId);
    apiClient.get(`/stream/moderators/check/${creatorUserId}`)
      .then(res => {
        const isMod = !!res.data?.isModerator;
        console.debug('[MOD_DEBUG] REST /check response: isModerator=', isMod);
        isCurrentUserModRef.current = isMod;
        setIsCurrentUserMod(isMod);
      })
      .catch((err) => {
        console.debug('[MOD_DEBUG] REST /check failed:', err?.response?.status, '- keeping current mod status:', isCurrentUserModRef.current);
        // Only reset to false if we never confirmed mod status; preserve confirmed true
        if (!isCurrentUserModRef.current) setIsCurrentUserMod(false);
      });
  }, [userId, creatorUserId, isOwnPage]);

  // Listen for moderator status changes via WS (backend sends to /queue/moderation)
  useEffect(() => {
    if (!userId || !creatorUserId) return;
    const unsub = webSocketService.subscribe('/user/queue/moderation', (msg) => {
      try {
        const data = typeof msg.body === 'string' ? JSON.parse(msg.body) : msg.body;
        console.debug('[MOD_DEBUG] WS /queue/moderation event:', data);
        if (data.type === 'MODERATOR_STATUS' && data.payload?.creatorId === creatorUserId) {
          const isMod = !!data.payload?.isModerator;
          console.debug('[MOD_DEBUG] WS MODERATOR_STATUS update: isModerator=', isMod);
          isCurrentUserModRef.current = isMod;
          setIsCurrentUserMod(isMod);
        }
      } catch {}
    });
    return () => { if (unsub) unsub(); };
  }, [userId, creatorUserId]);

  useEffect(() => {
    if (!user) return;
    const unsub = webSocketService.subscribe('/user/queue/notifications', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.type === 'ROOM_BANNED' && data.payload?.creatorId === creatorUserId) {
          setIsRoomBanned(true);
          setRoomBanInfo({ banType: data.payload.banType, expiresAt: data.payload.expiresAt });
        } else if (data.type === 'ROOM_UNBANNED' && data.payload?.creatorId === creatorUserId) {
          setIsRoomBanned(false);
          setRoomBanInfo(null);
        }
      } catch {}
    });
    return () => { if (unsub) unsub(); };
  }, [user, creatorUserId]);

  // Sync follow state from creator profile data
  useEffect(() => {
    if (!creator?.profile) return;
    setIsFollowing(!!creator.profile.followedByCurrentUser);
    setFollowerCount(creator.profile.followersCount || 0);
  }, [creator?.profile]);

  // Fetch viewers when USERS tab is active
  useEffect(() => {
    if (activeTab !== 'USERS' || !creatorUserId || !user) return;
    let cancelled = false;
    const fetchViewers = async () => {
      setViewersLoading(true);
      try {
        const list = await creatorService.getViewers(creatorUserId);
        console.debug('[VIEWER_DEBUG] raw API response:', list.length, 'viewers', list.map((v: any) => ({ id: v.id, username: v.username })));
        if (!cancelled) setViewers(list);
      } catch {
        if (!cancelled) setViewers([]);
      } finally {
        if (!cancelled) setViewersLoading(false);
      }
    };
    fetchViewers();
    const interval = setInterval(fetchViewers, 10000);
    return () => { cancelled = true; clearInterval(interval); };
  }, [activeTab, creatorUserId, user]);

  const handleMilestoneReached = useCallback((milestone: { title: string; targetAmount: number }, remainingTokens: number) => {
    // Broadcast milestone reached message to chat
    window.dispatchEvent(new CustomEvent('chat:system-message', {
      detail: { id: `milestone-${Date.now()}`, content: `🔥 Milestone reached: ${milestone.title}!` }
    }));

    // If there are more milestones, hype the next goal
    if (remainingTokens > 0) {
      setTimeout(() => {
        window.dispatchEvent(new CustomEvent('chat:system-message', {
          detail: { id: `milestone-next-${Date.now()}`, content: `🔥 ${remainingTokens} tokens to next goal!` }
        }));
      }, 1500);
    }
  }, []);

  const sessionIdRef = useRef(Math.random().toString(36).substring(2, 9));
  const sessionId = sessionIdRef.current;
  const overlayRef = useRef<any>(null);
  const processedTipsRef = useRef<Set<string>>(new Set());
  const leaderboardRef = useRef<LeaderboardPanelHandle>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const giftModalRef = useRef<GiftSelectorModalHandle>(null);
  const timeoutsRef = useRef<Set<NodeJS.Timeout>>(new Set());

  // Fetch creator private show settings (re-fetched periodically to stay in sync with creator changes)
  useEffect(() => {
    if (!creatorUserId) return;
    let cancelled = false;
    const fetchSettings = () => {
      getPrivateSettingsByCreator(creatorUserId)
        .then(res => { if (!cancelled) setPrivateSettings(res.data); })
        .catch(() => { if (!cancelled) setPrivateSettings(null); });
    };
    fetchSettings();
    const interval = setInterval(fetchSettings, 10000);
    return () => { cancelled = true; clearInterval(interval); };
  }, [creatorUserId]);

  // Poll private session availability for access control
  useEffect(() => {
    if (!creatorUserId || !user) return;
    let cancelled = false;
    const fetchAvailability = () => {
      privateShowService.getAvailability(creatorUserId).then(data => {
        if (!cancelled) setPrivateAvailability(data);
      }).catch(() => {
        if (!cancelled) setPrivateAvailability(null);
      });
    };
    fetchAvailability();
    const interval = setInterval(fetchAvailability, 5000);
    return () => { cancelled = true; clearInterval(interval); };
  }, [creatorUserId, user]);

  // PM: keep refs in sync
  useEffect(() => {
    activeTabRef.current = activeTab;
  }, [activeTab]);

  // PM: WebSocket subscriptions (simple single-session)
  useEffect(() => {
    if (!connected || !user || !subscribe) return;

    const loadSession = () => {
      getActivePm().then(sessions => {
        if (sessions.length > 0) {
          setPmSession(sessions[0]);
          setActiveTab('PM');
          getPmMessages(sessions[0].roomId).then(msgs => setPmMessages(msgs)).catch(() => {});
        }
      }).catch(() => {});
    };

    loadSession();

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
          setPmSession(session);
          setPmEnded(false);
          setActiveTab('PM');
          showToast('Creator started a private chat', 'info');
        }
        if (data.type === 'PM_SESSION_ENDED') {
          setPmSession(null);
          setPmMessages([]);
          setPmEnded(true);
        }
      } catch (e) {}
    });

    const pmMsgUnsub = subscribe('/user/queue/pm-messages', (msg) => {
      try {
        const data: PmMessage = JSON.parse(msg.body);
        if (!data.roomId) return;
        setPmMessages(prev => [...prev, data]);
        // Unread system: increment locally if not viewing PM tab
        if (activeTabRef.current !== 'PM') {
          setPmSession(prev => prev && prev.roomId === data.roomId ? { ...prev, unreadCount: (prev.unreadCount || 0) + 1 } : prev);
        }
      } catch (e) {}
    });

    return () => {
      pmEventUnsub.unsubscribe();
      pmMsgUnsub.unsubscribe();
    };
  }, [connected, user, subscribe]);

  const handleSendPm = (content: string) => {
    if (!pmSession || !content.trim()) return;
    sendPmMessage(pmSession.roomId, content.trim());
  };

  const handlePmTabOpen = () => {
    if (pmSession) {
      setPmSession(prev => prev ? { ...prev, unreadCount: 0 } : prev);
      markPmAsRead(pmSession.roomId).catch(() => {});
    }
  };

  const handleTabChange = (tab: 'CHAT' | 'PM' | 'USERS') => {
    setActiveTab(tab);
  };

  // Subscribe to private show status events
  useEffect(() => {
    if (!connected || !user || !subscribe) return;
    const unsub = subscribe('/user/queue/private-show-status', (message) => {
      try {
        const data = JSON.parse(message.body);
        if (data.type === 'PRIVATE_SHOW_ACCEPTED') {
          showToast('Creator accepted your private show request!', 'success');
          setPrivateSession(prev => ({
            ...(prev || {}),
            id: data.payload.sessionId,
            status: PrivateSessionStatus.ACCEPTED,
            creatorId: creatorUserId || 0,
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
            creatorId: creatorUserId || 0,
            viewerId: Number(user.id),
            pricePerMinute: prev?.pricePerMinute || data.payload.pricePerMinute || 50,
          }) as PrivateSession);
        } else if (data.type === 'PRIVATE_SHOW_ENDED') {
          showToast('Private session ended', 'info');
          setPrivateSession(null);
          // If we were spying, the spy session also ends (server cascade)
          if (spySession) {
            setSpySession(null);
            setCreatorActivePrivateSession(null);
          }
        } else if (data.type === 'SPY_SESSION_ENDED') {
          showToast('Spy session ended', 'info');
          setSpySession(null);
          setCreatorActivePrivateSession(null);
        }
      } catch (e) {
        console.error('Failed to parse private show event', e);
      }
    });
    return () => { if (unsub) unsub.unsubscribe(); };
  }, [connected, subscribe, user, creatorUserId, spySession]);

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

          // Skip update if already in terminal state
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

  // Recover active private session on refresh
  useEffect(() => {
    if (!user || !creatorUserId) return;
    apiClient.get('/private-show/active').then(res => {
      if (res.data && res.data.id) {
        const s = res.data;
        const normalizedStatus = PrivateSessionStatus[s.status as keyof typeof PrivateSessionStatus] || s.status;
        if (normalizedStatus === PrivateSessionStatus.REQUESTED || normalizedStatus === PrivateSessionStatus.ACCEPTED || normalizedStatus === PrivateSessionStatus.ACTIVE) {
          setPrivateSession({
            id: s.id,
            viewerId: s.viewerId,
            creatorId: s.creatorId,
            pricePerMinute: s.pricePerMinute,
            status: normalizedStatus,
          });
        }
      }
    }).catch(() => {});
  }, [user, creatorUserId]);

  // Discover if creator has an active private session we can spy on (only when we're not the main viewer)
  useEffect(() => {
    if (!user || !creatorUserId || privateSession) return;
    if (!privateSettings?.enabled || !privateSettings?.allowSpyOnPrivate) return;
    if (Number(user.id) === creatorUserId) return;

    const checkCreatorActiveSession = async () => {
      try {
        const session = await privateShowService.getActiveSessionForCreator(creatorUserId);
        if (session && session.status === PrivateSessionStatus.ACTIVE && session.viewerId !== Number(user.id)) {
          setCreatorActivePrivateSession(session);
          // Check if we already have an active spy session (recovery on refresh)
          try {
            const existingSpy = await privateShowService.getActiveSpySession(session.id);
            if (existingSpy) setSpySession(existingSpy);
          } catch {}
        } else {
          setCreatorActivePrivateSession(null);
        }
      } catch {
        setCreatorActivePrivateSession(null);
      }
    };

    checkCreatorActiveSession();
    const interval = setInterval(checkCreatorActiveSession, 10000);
    return () => clearInterval(interval);
  }, [user, creatorUserId, privateSession, privateSettings]);

  const handleJoinSpy = async () => {
    if (!creatorActivePrivateSession || isJoiningSpy) return;
    setIsJoiningSpy(true);
    try {
      const spy = await privateShowService.joinAsSpy(creatorActivePrivateSession.id);
      setSpySession(spy);
      showToast('You are now spying on the private session!', 'success');
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Failed to join as spy', 'error');
    } finally {
      setIsJoiningSpy(false);
    }
  };

  const handleLeaveSpy = async () => {
    if (!spySession || isLeavingSpy) return;
    setIsLeavingSpy(true);
    try {
      await privateShowService.leaveSpySession(spySession.id);
      setSpySession(null);
      setCreatorActivePrivateSession(null);
      showToast('Left spy session', 'info');
    } catch (e: any) {
      showToast('Failed to leave spy session', 'error');
    } finally {
      setIsLeavingSpy(false);
    }
  };

  // Compute effective streamId: switch to private-session-{id} when session is ACTIVE
  const effectiveStreamId = privateSession?.status === PrivateSessionStatus.ACTIVE
    ? `private-session-${privateSession.id}`
    : sessionId;

  // Determine if current viewer is blocked from video by an active private session
  const isBlockedByPrivate = (() => {
    if (!privateAvailability?.hasActivePrivate) return false;
    if (isOwnPage) return false; // creator always allowed
    if (privateAvailability.isCurrentUserPrivateViewer || privateAvailability.currentUserPrivateViewer) return false;
    if (privateAvailability.isCurrentUserActiveSpy || privateAvailability.currentUserActiveSpy) return false;
    return true;
  })();

  // Fetch initial leaderboard from backend on stream load
  useEffect(() => {
    if (!creatorUserId) return;
    apiClient.get(`/tips/leaderboard/${creatorUserId}`)
      .then(res => {
        const entries: LeaderboardEntry[] = (res.data || []).map((e: any) => ({
          username: e.username,
          total: e.totalAmount ?? e.total ?? 0
        }));
        setLeaderboard(entries);
        if (entries.length > 0) {
          setTopTipper({ name: entries[0].username, amount: entries[0].total });
        }
      })
      .catch(err => console.warn("Failed to fetch leaderboard", err));
  }, [creatorUserId]);

  const handleTip = useCallback((payloadOrAmount: any) => {
    console.debug("TIP RECEIVED IN WATCHPAGE", payloadOrAmount);
    // Ensure only valid tips reach the state and overlay
    if (typeof payloadOrAmount === 'object' && payloadOrAmount?.type && !['TIP', 'SUPER_TIP'].includes(payloadOrAmount.type)) {
      console.warn("Non-tip event filtered out in handleTip", payloadOrAmount.type);
      return;
    }

    // Deduplication: prevent the same tip from triggering overlays twice
    const dedupId = payloadOrAmount?.messageId || payloadOrAmount?.id || payloadOrAmount?.payload?.id;
    if (dedupId && processedTipsRef.current.has(dedupId)) {
      console.debug('TIP DEDUP: skipping already-processed tip', dedupId);
      return;
    }
    if (dedupId) {
      processedTipsRef.current.add(dedupId);
    }

    const isAmount = typeof payloadOrAmount === 'number';
    const amount = isAmount ? payloadOrAmount : (payloadOrAmount?.amount || 0);
    const username = (isAmount ? user?.displayName : (payloadOrAmount?.username || payloadOrAmount?.viewer)) || 'Someone';
    const payload = isAmount ? { amount, username, animationType: resolveAnimationByAmount(amount) } : payloadOrAmount;

    // Optimistic leaderboard update: merge incoming tip into current leaderboard
    setLeaderboard(prev => {
      const updated = prev.map(e => ({ ...e }));
      const existing = updated.find(e => e.username === username);
      if (existing) {
        existing.total += amount;
      } else {
        updated.push({ username, total: amount });
      }
      updated.sort((a, b) => b.total - a.total);
      const top5 = updated.slice(0, 5);
      // Update top tipper from the freshly computed leaderboard
      if (top5.length > 0) {
        setTopTipper({ name: top5[0].username, amount: top5[0].total });
      }
      return top5;
    });

    const rarity = payload.rarity || resolveRarityByAmount(amount);

    // Restore token explosion effect (>= 50 tokens)
    if (amount >= 50) {
      setTokenExplosion({ amount, key: Date.now() });
    }

    // Restore legendary full-screen effect (>= 500 tokens)
    if (amount >= 500) {
      setLegendaryEffect({
        username,
        amount,
        isVisible: true,
        effectType: `legendary-${Date.now()}`
      });
    }

    overlayRef.current?.queueTip({
      id: dedupId || crypto.randomUUID(),
      type: payload.gift ? "gift" : "token",
      amount: payload.amount ?? 0,
      username: payload.senderUsername ?? payload.username ?? "Anonymous",
      rarity: payload.gift?.rarity ?? payload.rarity ?? resolveRarityByAmount(amount),
      animationType: payload.gift?.animationType ?? payload.animationType ?? resolveAnimationByAmount(amount),
      timestamp: Date.now()
    });
  }, [user?.displayName]);

  const handleSelectGift = useCallback((gift: any) => {
    handleTip({ amount: gift.price, username: user?.displayName || 'Someone', animationType: gift.animationType, rarity: gift.rarity, gift: { rarity: gift.rarity, animationType: gift.animationType, name: gift.name }, giftId: gift.id, giftName: gift.name });
  }, [handleTip, user?.displayName]);

  const handleUnlock = async () => {
    if (!creatorUserId || loading) return;
    setLoading(true);
    try {
      const res = await apiClient.post(`/livestream/${creatorUserId}/unlock`);
      if (res.data.success) { setHasAccess(true); setRoom(res.data.room); refreshTokenBalance(); }
    } catch (err: any) { console.error("Unlock failed", err); setError(err.response?.data?.message || "Failed to unlock stream"); }
    finally { setLoading(false); }
  };


  useEffect(() => {
    const fetchData = async () => {
      if (!creator?.profile?.id) return;
      try {
        const res = await apiClient.get(`/v2/public/creators/${creatorUserId}/availability`);
        console.debug("CREATOR AVAILABILITY RESPONSE", res.data);
        setAvailability(res.data.availability || res.data.status || "OFFLINE");
        if (res.data.viewerCount !== undefined) setViewerCount(res.data.viewerCount);
      } catch (err) { 
        console.warn("Stream availability endpoint failed", err);
        setAvailability("OFFLINE");
        setViewerCount(0);
      }
      finally { setLoading(false); }
    };
    fetchData();
    return () => timeoutsRef.current.forEach(clearTimeout);
  }, [creatorUserId, creator?.profile?.id]);

  useEffect(() => {
    if (!creatorUserId || !user || availability !== "LIVE") { setRoom(null); return; }
    apiClient.get(`/livestream/${creatorUserId}/access`).then(res => { setRoom(res.data); setHasAccess(res.data.hasAccess); });
  }, [creatorUserId, user, availability]);

  // Presence-driven availability updates (from global WsContext subscription)
  // NOTE: creators.presence is subscribed globally via WsContext — do not subscribe here.
  useEffect(() => {
    if (!creatorUserId) return;
    const presence = presenceMap[Number(creatorUserId)];
    if (presence?.availability) {
      setAvailability(presence.availability as "ONLINE" | "LIVE" | "OFFLINE");
    }
  }, [creatorUserId, presenceMap]);

  useEffect(() => {
    if (!creatorUserId) return;
    const unsubViewers = webSocketService.subscribe(`/exchange/amq.topic/viewers.${creatorUserId}`, (msg) => {
      const payload = JSON.parse(msg.body).payload || JSON.parse(msg.body);
      if (payload.viewerCount !== undefined) setViewerCount(payload.viewerCount);
    });
    return () => { if (typeof unsubViewers === "function") unsubViewers(); };
  }, [creatorUserId]);

  const handleSuperTipEnd = useCallback(() => {
    console.debug("SUPER_TIP_END: Clearing highlight overlay");
    setLegendaryEffect(prev => ({ ...prev, isVisible: false }));
  }, []);

  const handleGoalUpdate = useCallback((data: any) => {
    if (['GOAL_PROGRESS', 'GOAL_STATUS', 'GOAL_COMPLETED', 'GOAL_GROUP_PROGRESS', 'GOAL_GROUP_COMPLETED', 'MILESTONE_REACHED', 'GOAL_SWITCH'].includes(data.type)) {
      setGoal(data);
      if (data.type === 'GOAL_COMPLETED') {
        setShowGoalBanner(true);
        const t = setTimeout(() => setShowGoalBanner(false), 8000);
        timeoutsRef.current.add(t);
      }
    }
  }, []);

  // Dedicated monetization stream subscription
  useEffect(() => {
    if (!creatorUserId || availability !== 'LIVE') return;
    const unsubMonetization = webSocketService.subscribe(`/exchange/amq.topic/monetization.${creatorUserId}`, (msg) => {
      try {
        const incoming = JSON.parse(msg.body);
        const message = normalizeLiveEvent(incoming);
        console.debug('MONETIZATION STREAM:', message.type, message);
        switch (message.type) {
          case 'TIP':
          case 'SUPER_TIP':
            handleTip(message);
            break;
          case 'SUPER_TIP_END':
            handleSuperTipEnd();
            break;
          case 'PIN_MESSAGE':
            // Pinned messages handled via chat stream in ChatComponent
            break;
          case 'ACTION_TRIGGERED':
          case 'TIP_MENU':
            // These are rendered in chat — handled by ChatComponent
            break;
        }
      } catch (e) { console.error('Error processing monetization event', e); }
    });
    return () => { if (typeof unsubMonetization === "function") unsubMonetization(); };
  }, [creatorUserId, availability, handleTip, handleSuperTipEnd]);

  // Dedicated goals stream subscription
  useEffect(() => {
    if (!creatorUserId || availability !== 'LIVE') return;
    const unsubGoals = webSocketService.subscribe(`/exchange/amq.topic/goals.${creatorUserId}`, (msg) => {
      try {
        const incoming = JSON.parse(msg.body);
        const message = normalizeLiveEvent(incoming);
        const eventType = message.type || incoming.type;
        const goalData = { ...(message.payload || message), type: eventType };
        console.debug('GOALS STREAM:', eventType, goalData);
        handleGoalUpdate(goalData);
      } catch (e) { console.error('Error processing goal event', e); }
    });
    return () => { if (typeof unsubGoals === "function") unsubGoals(); };
  }, [creatorUserId, availability, handleGoalUpdate]);

  // Dedicated leaderboard stream subscription
  useEffect(() => {
    if (!creatorUserId || availability !== 'LIVE') return;
    const unsubLeaderboard = webSocketService.subscribe(`/exchange/amq.topic/leaderboard.${creatorUserId}`, (msg) => {
      try {
        const incoming = JSON.parse(msg.body);
        const data = incoming.data || incoming;
        console.debug('LEADERBOARD STREAM:', data);
        if (Array.isArray(data)) {
          const entries: LeaderboardEntry[] = data.map((e: any) => ({
            username: e.username,
            total: e.totalAmount ?? e.total ?? 0
          }));
          setLeaderboard(entries);
          if (entries.length > 0) {
            setTopTipper({ name: entries[0].username, amount: entries[0].total });
          }
        }
      } catch (e) { console.error('Error processing leaderboard event', e); }
    });
    return () => { if (typeof unsubLeaderboard === "function") unsubLeaderboard(); };
  }, [creatorUserId, availability]);

  console.debug("Creator loaded", creator);
  console.debug("Availability state", availability);

  // Step 4 — Update the guard condition and loading state
  if (profileLoading || (loading && !creator?.profile)) {
    return (
      <div className="h-screen bg-black flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-white/20 border-t-white rounded-full animate-spin" />
          <p className="text-white font-medium animate-pulse">Loading experience...</p>
        </div>
      </div>
    );
  }

  if (creator === null || creator === undefined || !creator?.profile?.id) {
    console.debug("Creator not found guard triggered", { 
      hasCreator: !!creator, 
      hasProfile: !!creator?.profile,
      profileLoading, 
      loading 
    });
    return (
      <div className="h-screen bg-black flex flex-col items-center justify-center p-8 text-center">
        <h2 className="text-white text-2xl font-bold mb-4">Creator not found</h2>
        <p className="text-zinc-400 mb-8 max-w-md">
          We couldn't find the creator you're looking for. They may have changed their username or deleted their account.
        </p>
        <button 
          onClick={() => navigate('/explore')} 
          className="px-8 py-3 bg-white text-black rounded-full font-bold hover:bg-zinc-200 transition"
        >
          Back to Explore
        </button>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-[#050505] overflow-hidden">
      <Navbar />
      <div className="flex flex-1 overflow-hidden">
        <LiveLayout
          video={
            <div className={`premium-video-container flex flex-col ${availability === 'LIVE' ? 'video-live' : ''} flex-1 overflow-hidden relative`}>
              <div className="absolute top-0 left-0 right-0 flex items-center justify-between p-6 bg-gradient-to-b from-black/80 z-20">
                <div className="flex items-center gap-3">
                  <button onClick={() => navigate(`/creators/${identifier}`)} className="p-2 hover:bg-white/10 rounded-full transition text-white">←</button>
                  <div>
                    <h2 className="font-semibold text-lg text-white leading-tight">{safeRender(creator.profile.displayName)}</h2>
                    <div className="flex items-center gap-2">
                      <span className={`w-2 h-2 rounded-full ${availability === 'LIVE' ? 'bg-red-500 animate-pulse' : (availability === 'ONLINE' ? 'bg-green-500' : 'bg-zinc-500')}`} />
                      <p className="text-[11px] text-zinc-100/70 font-medium">
                        {availability === 'LIVE' ? 'Live' : (availability === 'ONLINE' ? 'Online' : 'Offline')}
                      </p>
                      <span className="text-[11px] text-zinc-100/50">·</span>
                      <p className="text-[11px] text-zinc-100/70 font-medium">{followerCount.toLocaleString()} {followerCount === 1 ? 'follower' : 'followers'}</p>
                    </div>
                  </div>
                  {user && !isOwnPage && (
                    <button
                      onClick={async () => {
                        if (followLoading) return;
                        setFollowLoading(true);
                        try {
                          if (isFollowing) {
                            const status = await creatorService.unfollowCreator(creatorUserId!);
                            setIsFollowing(status.following);
                            setFollowerCount(status.followers ?? followerCount);
                          } else {
                            const status = await creatorService.followCreator(creatorUserId!);
                            setIsFollowing(status.following);
                            setFollowerCount(status.followers ?? followerCount);
                          }
                        } catch (err: any) {
                          console.error('Follow action failed:', err);
                          showToast(err.response?.data?.message || 'Follow action failed', 'error');
                        } finally {
                          setFollowLoading(false);
                        }
                      }}
                      disabled={followLoading}
                      className={`ml-2 px-3 py-1 rounded-full text-xs font-bold transition-all active:scale-95 border ${
                        isFollowing
                          ? 'bg-white/10 text-white border-white/20 hover:bg-white/20'
                          : 'bg-white text-black border-white hover:bg-zinc-200'
                      } ${followLoading ? 'opacity-50 cursor-not-allowed' : ''}`}
                    >
                      {followLoading ? '...' : isFollowing ? 'Following' : 'Follow'}
                    </button>
                  )}
                </div>
                {availability === "LIVE" && (
                  <div className="status-cluster flex items-center gap-2">
                    <button onClick={() => giftModalRef.current?.open()} className="p-1.5 px-3 bg-indigo-500 rounded-full text-xs font-bold text-white shadow-lg">🎁 Gifts</button>
                    <button onClick={() => setShowTipModal(true)} className="send-tokens-btn px-3 py-1.5 bg-white text-black rounded-full text-xs font-bold">Send Tokens</button>
                    <button onClick={() => leaderboardRef.current?.toggle()} className="p-1.5 px-3 bg-white/5 rounded-full text-xs font-bold text-white border border-white/5">🏆 Leaderboard</button>
                    {user && Number(user.id) !== creatorUserId && privateSettings?.enabled && !privateSession && !spySession && (
                      <PrivateShowRequestButton creatorId={creatorUserId!} pricePerMinute={privateSettings.pricePerMinute} onSessionCreated={(session) => setPrivateSession(session)} />
                    )}
                    {/* Spy on Private button — shown when creator has an active private session and we're not the main viewer */}
                    {user && !privateSession && !spySession && creatorActivePrivateSession && privateSettings?.allowSpyOnPrivate && privateAvailability?.allowSpyOnPrivate && (
                      <button
                        onClick={handleJoinSpy}
                        disabled={isJoiningSpy}
                        className="px-3 py-1.5 bg-gradient-to-r from-amber-600 to-orange-500 text-white rounded-full text-xs font-bold shadow-lg hover:from-amber-500 hover:to-orange-400 transition disabled:opacity-50"
                        title={`Spy on private — ${privateSettings.spyPricePerMinute} tokens/min`}
                      >
                        {isJoiningSpy ? 'Joining...' : `👁 Spy · ${privateSettings.spyPricePerMinute}/min`}
                      </button>
                    )}
                    {/* Active spy session indicator + leave button */}
                    {spySession && (
                      <>
                        <span className="px-2.5 py-1 bg-amber-500/15 border border-amber-500/40 rounded-full text-amber-300 text-xs font-bold">👁 Spying · {spySession.spyPricePerMinute}/min</span>
                        <button
                          onClick={handleLeaveSpy}
                          disabled={isLeavingSpy}
                          className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-full text-xs font-bold transition"
                        >
                          {isLeavingSpy ? 'Leaving...' : 'Leave Spy'}
                        </button>
                      </>
                    )}
                    {privateSession?.status === PrivateSessionStatus.REQUESTED && (
                      <span className="px-2.5 py-1 bg-purple-500/15 border border-purple-500/40 rounded-full text-purple-300 text-xs font-bold animate-pulse">⏳ Waiting for creator...</span>
                    )}
                    {privateSession?.status === PrivateSessionStatus.ACCEPTED && (
                      <span className="px-2.5 py-1 bg-purple-500/15 border border-purple-500/40 rounded-full text-purple-300 text-xs font-bold">✅ Creator accepted! Starting soon...</span>
                    )}
                    {privateSession?.status === PrivateSessionStatus.ACTIVE && (
                      <>
                        <span className="px-2.5 py-1 bg-red-500/15 border border-red-500/40 rounded-full text-red-400 text-xs font-bold">🔴 Private Active</span>
                        <button
                          onClick={handleEndSession}
                          disabled={isEndingSession}
                          className="px-3 py-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed text-white rounded-full text-xs font-bold transition"
                        >
                          {isEndingSession ? 'Ending...' : 'End Private Session'}
                        </button>
                      </>
                    )}
                    {user && Number(user.id) !== creatorUserId && (
                      <button onClick={() => {
                        const lastReport = localStorage.getItem('report_cooldown');
                        if (lastReport && Date.now() - parseInt(lastReport, 10) < 60000) {
                          showToast('You can submit another report in a few seconds.', 'info');
                          return;
                        }
                        setShowReportModal(true);
                      }} className="p-1.5 px-3 bg-white/5 rounded-full text-xs font-bold text-white border border-white/5 hover:bg-red-500/20 hover:border-red-500/30 transition" title="Report Stream">🚩 Report</button>
                    )}
                  </div>
                )}
              </div>

              {isRoomBanned ? (
                <div className="flex-1 flex flex-col items-center justify-center bg-black/80 rounded-2xl border border-red-500/20 p-12 text-center gap-6">
                  <div className="w-20 h-20 rounded-full bg-red-500/10 border border-red-500/30 flex items-center justify-center text-4xl">🚫</div>
                  <div>
                    <h3 className="text-white text-xl font-bold mb-2">You are banned from this room</h3>
                    <p className="text-zinc-400 text-sm max-w-sm">
                      {roomBanInfo?.banType === 'permanent'
                        ? 'You have been permanently banned from this room.'
                        : roomBanInfo?.expiresAt && roomBanInfo.expiresAt !== 'never'
                          ? `Your ban expires at ${new Date(roomBanInfo.expiresAt).toLocaleString()}.`
                          : 'You have been banned from participating in this room.'}
                    </p>
                  </div>
                </div>
              ) : isBlockedByPrivate ? (
                <div className="flex-1 flex flex-col items-center justify-center bg-black/80 rounded-2xl border border-white/5 p-12 text-center gap-6">
                  <div className="w-20 h-20 rounded-full bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-4xl">🔒</div>
                  <div>
                    <h3 className="text-white text-xl font-bold mb-2">Private Session in Progress</h3>
                    <p className="text-zinc-400 text-sm max-w-sm">This creator is currently in a private session. The stream will be available again when the session ends.</p>
                  </div>
                  {privateAvailability?.allowSpyOnPrivate && privateAvailability.canCurrentUserSpy && privateAvailability.activeSessionId && (
                    <button
                      onClick={handleJoinSpy}
                      disabled={isJoiningSpy}
                      className="mt-2 px-6 py-2.5 bg-gradient-to-r from-amber-600 to-orange-500 text-white rounded-full text-sm font-bold shadow-lg hover:from-amber-500 hover:to-orange-400 transition disabled:opacity-50"
                    >
                      {isJoiningSpy ? 'Joining...' : `👁 Spy on Private · ${privateAvailability.spyPricePerMinute} tokens/min`}
                    </button>
                  )}
                </div>
              ) : (
                <LiveStreamPlayer
                  creatorId={creatorUserId} roomId={room?.streamRoomId} streamId={sessionId} user={user} availability={availability}
                  hasAccess={hasAccess} room={room} error={error} loading={loading} needsInteraction={needsInteraction}
                  setHasAccess={setHasAccess} setAvailability={setAvailability} setError={setError} setLoading={setLoading}
                  setNeedsInteraction={setNeedsInteraction} handleUnlock={handleUnlock} identifier={identifier || ""} navigate={navigate}
                  onProfileOpen={(u) => navigate(`/creators/${u}`)}
                  videoWidth={videoWidth} isResizing={isResizing} containerRef={containerRef}
                >
                  {showTopTipperBanner && <TopTipperBanner creatorId={creatorUserId} topTipperName={topTipper.name} topTipAmount={topTipper.amount} onProfileOpen={(u) => navigate(`/creators/${u}`)} />}
                  <LeaderboardPanel ref={leaderboardRef} creatorId={creatorUserId} streamId={effectiveStreamId} leaderboard={leaderboard} />
                  {showTipOverlays && <LiveTipOverlays creatorId={creatorUserId} roomId={room?.streamRoomId} overlayRef={overlayRef} tokenExplosion={tokenExplosion} legendaryEffect={legendaryEffect} />}
                  {privateSession?.status === PrivateSessionStatus.ACTIVE && (
                    <PrivateShowSessionOverlay
                      sessionId={privateSession.id}
                      pricePerMinute={privateSession.pricePerMinute}
                      isCreator={false}
                      onSessionEnded={() => setPrivateSession(null)}
                    />
                  )}
                </LiveStreamPlayer>
              )}

              {goal && goal.active && goalOverlayVisible && availability === 'LIVE' && (
                goal.milestones && goal.milestones.length > 0 ? (
                  <GoalLadderOverlay title={goal.title} currentAmount={goal.currentAmount} targetAmount={goal.targetAmount} milestones={goal.milestones} onClose={() => setGoalOverlayVisible(false)} onMilestoneReached={handleMilestoneReached} />
                ) : (
                  <GoalOverlay goal={goal.targetAmount} progress={goal.currentAmount} title={goal.title} onClose={() => setGoalOverlayVisible(false)} />
                )
              )}

            </div>
          }
          chat={
            <LiveChatPanel
              creatorId={creatorUserId} roomId={room?.streamRoomId} onTipClick={() => setShowTipModal(true)} onGiftClick={() => giftModalRef.current?.open()} onProfileOpen={(u) => navigate(`/creators/${u}`)}
              availability={availability} minTip={creator?.profile?.minTip || 1} onTip={handleTip} 
              onGoalUpdate={handleGoalUpdate}
              onSuperTipEnd={handleSuperTipEnd}
              pmSession={pmSession}
              pmMessages={pmMessages}
              unreadPm={pmSession?.unreadCount || 0}
              onSendPm={handleSendPm}
              onPmTabOpen={handlePmTabOpen}
              activeTab={activeTab}
              onTabChange={handleTabChange}
              userId={user ? Number(user.id) : undefined}
              pmEnded={pmEnded}
              viewers={viewers}
              viewersLoading={viewersLoading}
              hiddenUserIds={hiddenUserIds}
              onToggleHideUser={toggleHideUser}
              onUnhideAll={unhideAll}
              followerUserIds={followerUserIds}
              moderatorUserIds={moderatorUserIds}
              isCurrentUserMod={isCurrentUserMod}
              chatFontSize={chatFontSize}
              onChatFontSizeChange={setChatFontSize}
              showTipOverlays={showTipOverlays}
              onShowTipOverlaysChange={setShowTipOverlays}
              showTopTipperBanner={showTopTipperBanner}
              onShowTopTipperBannerChange={setShowTopTipperBanner}
            />
          }
        />
      </div>
      <GiftSelectorModal ref={giftModalRef} onSelectGift={handleSelectGift} />
      {showReportModal && <AbuseReportModal isOpen={showReportModal} onClose={() => setShowReportModal(false)} targetType={ReportTargetType.STREAM} targetId={room?.streamRoomId || creator.profile.userId.toString()} targetLabel={creator.profile.displayName || creator.profile.username || 'this creator'} reportedUserId={creator.profile.userId} />}
      {showTipModal && <TipModal isOpen={showTipModal} onClose={() => setShowTipModal(false)} creatorId={creator.profile.userId} onSuccess={(amount) => handleTip(amount)} />}
      {showGoalBanner && goal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center pointer-events-none overflow-hidden">
          <div className="absolute inset-0 bg-indigo-900/20 backdrop-blur-sm" />
          <div className="relative flex flex-col items-center gap-6">
            <h2 className="text-6xl font-black text-white uppercase tracking-tighter">Goal Reached!</h2>
            <p className="text-2xl font-bold text-indigo-300 italic">"{safeRender(goal.title)}"</p>
            <div className="bg-white/10 px-8 py-3 rounded-full border border-white/20 text-white font-black">🪙{safeRender(goal.targetAmount.toLocaleString())}</div>
          </div>
        </div>
      )}

    </div>
  );
};

export default WatchPage;
