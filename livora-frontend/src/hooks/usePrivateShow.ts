import { useState, useEffect, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import { getPrivateSettingsByCreator, PrivateSettings } from '@/api/privateSettingsService';
import privateShowService, { PrivateSession, PrivateSessionStatus, PrivateSpySession, PrivateSessionAvailability } from '@/api/privateShowService';
import { showToast } from '@/components/Toast';

interface UsePrivateShowResult {
  privateSettings: PrivateSettings | null;
  privateSession: PrivateSession | null;
  setPrivateSession: React.Dispatch<React.SetStateAction<PrivateSession | null>>;
  isEndingSession: boolean;
  spySession: PrivateSpySession | null;
  isJoiningSpy: boolean;
  isLeavingSpy: boolean;
  creatorActivePrivateSession: PrivateSession | null;
  privateAvailability: PrivateSessionAvailability | null;
  effectiveStreamId: string;
  isBlockedByPrivate: boolean;
  handleEndSession: () => Promise<void>;
  handleJoinSpy: () => Promise<void>;
  handleLeaveSpy: () => Promise<void>;
}

/**
 * usePrivateShow — Manages private show sessions, spy sessions, and availability.
 * Handles WS subscriptions, polling, and session recovery on refresh.
 */
export const usePrivateShow = (
  userId: string | undefined,
  creatorUserId: number | undefined,
  isOwnPage: boolean,
  sessionId: string,
  connected: boolean,
  subscribe: ((dest: string, cb: (msg: any) => void) => { unsubscribe: () => void } | null) | null | undefined,
): UsePrivateShowResult => {
  const [privateSettings, setPrivateSettings] = useState<PrivateSettings | null>(null);
  const [privateSession, setPrivateSession] = useState<PrivateSession | null>(null);
  const [isEndingSession, setIsEndingSession] = useState(false);
  const [spySession, setSpySession] = useState<PrivateSpySession | null>(null);
  const [isJoiningSpy, setIsJoiningSpy] = useState(false);
  const [isLeavingSpy, setIsLeavingSpy] = useState(false);
  const [creatorActivePrivateSession, setCreatorActivePrivateSession] = useState<PrivateSession | null>(null);
  const [privateAvailability, setPrivateAvailability] = useState<PrivateSessionAvailability | null>(null);

  // Fetch creator private show settings (re-fetched periodically)
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
    if (!creatorUserId || !userId) return;
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
  }, [creatorUserId, userId]);

  // Subscribe to private show status events
  useEffect(() => {
    if (!connected || !userId || !subscribe) return;
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
            viewerId: Number(userId),
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
            viewerId: Number(userId),
            pricePerMinute: prev?.pricePerMinute || data.payload.pricePerMinute || 50,
          }) as PrivateSession);
        } else if (data.type === 'PRIVATE_SHOW_ENDED') {
          showToast('Private session ended', 'info');
          setPrivateSession(null);
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
  }, [connected, subscribe, userId, creatorUserId, spySession]);

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
          if (prev.status === PrivateSessionStatus.ACTIVE || prev.status === PrivateSessionStatus.ENDED) return prev;
          if (prev.status !== normalizedStatus) {
            if (normalizedStatus === PrivateSessionStatus.ACCEPTED) showToast('Creator accepted your private show request!', 'success');
            else if (normalizedStatus === PrivateSessionStatus.ACTIVE) showToast('Private show is now LIVE!', 'success');
            else if (normalizedStatus === PrivateSessionStatus.REJECTED) { showToast('Creator rejected your private show request.', 'info'); return null; }
            else if (normalizedStatus === PrivateSessionStatus.ENDED) return null;
            return { ...prev, ...updated, status: normalizedStatus };
          }
          return prev;
        });
      } catch (e) { console.warn('Failed to poll session status:', e); }
    }, 2000);
    return () => clearInterval(interval);
  }, [privateSessionId]);

  // Recover active private session on refresh
  useEffect(() => {
    if (!userId || !creatorUserId) return;
    apiClient.get('/private-show/active').then(res => {
      if (res.data && res.data.id) {
        const s = res.data;
        const normalizedStatus = PrivateSessionStatus[s.status as keyof typeof PrivateSessionStatus] || s.status;
        if (normalizedStatus === PrivateSessionStatus.REQUESTED || normalizedStatus === PrivateSessionStatus.ACCEPTED || normalizedStatus === PrivateSessionStatus.ACTIVE) {
          setPrivateSession({ id: s.id, viewerId: s.viewerId, creatorId: s.creatorId, pricePerMinute: s.pricePerMinute, status: normalizedStatus });
        }
      }
    }).catch(() => {});
  }, [userId, creatorUserId]);

  // Discover if creator has an active private session we can spy on
  useEffect(() => {
    if (!userId || !creatorUserId || privateSession) return;
    if (!privateSettings?.enabled || !privateSettings?.allowSpyOnPrivate) return;
    if (Number(userId) === creatorUserId) return;
    const checkCreatorActiveSession = async () => {
      try {
        const session = await privateShowService.getActiveSessionForCreator(creatorUserId);
        if (session && session.status === PrivateSessionStatus.ACTIVE && session.viewerId !== Number(userId)) {
          setCreatorActivePrivateSession(session);
          try {
            const existingSpy = await privateShowService.getActiveSpySession(session.id);
            if (existingSpy) setSpySession(existingSpy);
          } catch {}
        } else {
          setCreatorActivePrivateSession(null);
        }
      } catch { setCreatorActivePrivateSession(null); }
    };
    checkCreatorActiveSession();
    const interval = setInterval(checkCreatorActiveSession, 10000);
    return () => clearInterval(interval);
  }, [userId, creatorUserId, privateSession, privateSettings]);

  const handleEndSession = useCallback(async () => {
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
  }, [privateSession, isEndingSession]);

  const handleJoinSpy = useCallback(async () => {
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
  }, [creatorActivePrivateSession, isJoiningSpy]);

  const handleLeaveSpy = useCallback(async () => {
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
  }, [spySession, isLeavingSpy]);

  // Compute effective streamId
  const effectiveStreamId = privateSession?.status === PrivateSessionStatus.ACTIVE
    ? `private-session-${privateSession.id}`
    : sessionId;

  // Determine if current viewer is blocked from video
  const isBlockedByPrivate = (() => {
    if (!privateAvailability?.hasActivePrivate) return false;
    if (isOwnPage) return false;
    if (privateAvailability.isCurrentUserPrivateViewer || privateAvailability.currentUserPrivateViewer) return false;
    if (privateAvailability.isCurrentUserActiveSpy || privateAvailability.currentUserActiveSpy) return false;
    return true;
  })();

  return {
    privateSettings,
    privateSession,
    setPrivateSession,
    isEndingSession,
    spySession,
    isJoiningSpy,
    isLeavingSpy,
    creatorActivePrivateSession,
    privateAvailability,
    effectiveStreamId,
    isBlockedByPrivate,
    handleEndSession,
    handleJoinSpy,
    handleLeaveSpy,
  };
};
