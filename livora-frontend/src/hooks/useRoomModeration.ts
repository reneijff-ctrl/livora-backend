import { useState, useEffect, useRef, useMemo } from 'react';
import apiClient from '@/api/apiClient';
import creatorService from '@/api/creatorService';
import { useWs } from '@/ws/WsContext';

interface Viewer {
  id: number;
  username: string;
  displayName: string | null;
  isFollower?: boolean;
  isModerator?: boolean;
}

interface UseRoomModerationResult {
  isCurrentUserMod: boolean;
  isRoomBanned: boolean;
  roomBanInfo: { banType: string; expiresAt: string } | null;
  viewers: Viewer[];
  viewersLoading: boolean;
  followerUserIds: Set<number>;
  moderatorUserIds: Set<number>;
}

/**
 * useRoomModeration — Manages moderator status, room bans, and viewer list.
 * Subscribes to WS events for real-time mod/ban status changes.
 */
export const useRoomModeration = (
  userId: string | number | undefined,
  creatorUserId: number | undefined,
  isOwnPage: boolean,
  activeTab: 'CHAT' | 'PM' | 'USERS',
): UseRoomModerationResult => {
  const { subscribe, connected } = useWs();
  const [isCurrentUserMod, setIsCurrentUserMod] = useState(false);
  const isCurrentUserModRef = useRef(false);
  const [isRoomBanned, setIsRoomBanned] = useState(false);
  const [roomBanInfo, setRoomBanInfo] = useState<{ banType: string; expiresAt: string } | null>(null);
  const [viewers, setViewers] = useState<Viewer[]>([]);
  const [viewersLoading, setViewersLoading] = useState(false);

  // Check room ban status on load
  useEffect(() => {
    if (!userId || !creatorUserId || isOwnPage) return;
    apiClient.get(`/room-bans/check/${creatorUserId}/${userId}`)
      .then(res => { if (res.data?.banned) setIsRoomBanned(true); })
      .catch(() => {});
  }, [userId, creatorUserId, isOwnPage]);

  // Check if current user is a moderator for this creator
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
        if (!isCurrentUserModRef.current) setIsCurrentUserMod(false);
      });
  }, [userId, creatorUserId, isOwnPage]);

  // Listen for moderator status changes via WS
  useEffect(() => {
    let unsub = () => {};

    if (connected && userId && creatorUserId) {
      const result = subscribe('/user/queue/moderation', (msg) => {
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
      if (typeof result === 'function') {
        unsub = result;
      }
    }

    return () => {
      unsub();
    };
  }, [connected, subscribe, userId, creatorUserId]);

  // Listen for ban/unban WS events
  useEffect(() => {
    let unsub = () => {};

    if (connected && userId) {
      const result = subscribe('/user/queue/notifications', (msg) => {
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
      if (typeof result === 'function') {
        unsub = result;
      }
    }

    return () => {
      unsub();
    };
  }, [connected, subscribe, userId, creatorUserId]);

  // Fetch viewers when USERS tab is active
  useEffect(() => {
    if (activeTab !== 'USERS' || !creatorUserId || !userId) return;
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
  }, [activeTab, creatorUserId, userId]);

  // Derived sets for chat badges
  const followerUserIds = useMemo(() => {
    const ids = new Set<number>();
    for (const v of viewers) { if (v.isFollower) ids.add(v.id); }
    return ids;
  }, [viewers]);

  const moderatorUserIds = useMemo(() => {
    const ids = new Set<number>();
    for (const v of viewers) { if (v.isModerator) ids.add(v.id); }
    return ids;
  }, [viewers]);

  return {
    isCurrentUserMod,
    isRoomBanned,
    roomBanInfo,
    viewers,
    viewersLoading,
    followerUserIds,
    moderatorUserIds,
  };
};
