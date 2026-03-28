import { useState, useEffect, useRef, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import tipService from '@/api/tipService';
import { webSocketService } from '@/websocket/webSocketService';
import { normalizeLiveEvent } from '@/components/live/LiveEventsController';
import { GoalStatusEvent } from '@/types/events';
import { resolveAnimationByAmount, resolveRarityByAmount } from '@/utils/animationUtils';
import { LeaderboardEntry } from '@/components/LeaderboardPanel';

interface UseStreamTipsResult {
  leaderboard: LeaderboardEntry[];
  topTipper: { name: string | null; amount: number };
  tokenExplosion: { amount: number; key: number };
  legendaryEffect: { username: string; amount: number; isVisible: boolean; effectType: string };
  goal: GoalStatusEvent | null;
  goalOverlayVisible: boolean;
  setGoalOverlayVisible: (visible: boolean) => void;
  showGoalBanner: boolean;
  handleTip: (payloadOrAmount: any) => void;
  handleSelectGift: (gift: any) => void;
  handleGoalUpdate: (data: any) => void;
  handleSuperTipEnd: () => void;
  handleMilestoneReached: (milestone: { title: string; targetAmount: number }, remainingTokens: number) => void;
  overlayRef: React.RefObject<any>;
  processedTipsRef: React.RefObject<Set<string>>;
}

/**
 * useStreamTips — Manages tip overlays, leaderboard, goal state, and WS subscriptions.
 * Handles monetization stream, goals stream, and leaderboard stream.
 */
export const useStreamTips = (
  creatorUserId: number | undefined,
  availability: "ONLINE" | "LIVE" | "OFFLINE" | null,
  userDisplayName: string | undefined,
  roomId?: string,
): UseStreamTipsResult => {
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);
  const [topTipper, setTopTipper] = useState<{ name: string | null; amount: number }>({ name: null, amount: 0 });
  const [tokenExplosion, setTokenExplosion] = useState({ amount: 0, key: 0 });
  const [legendaryEffect, setLegendaryEffect] = useState({ username: '', amount: 0, isVisible: false, effectType: '' });
  const [goal, setGoal] = useState<GoalStatusEvent | null>(null);
  const [goalOverlayVisible, setGoalOverlayVisible] = useState(() => localStorage.getItem("goalOverlayHidden") !== "true");
  const [showGoalBanner, setShowGoalBanner] = useState(false);

  const overlayRef = useRef<any>(null);
  const processedTipsRef = useRef<Set<string>>(new Set());
  const timeoutsRef = useRef<Set<NodeJS.Timeout>>(new Set());

  // Cleanup timeouts on unmount
  useEffect(() => {
    return () => timeoutsRef.current.forEach(clearTimeout);
  }, []);

  // Fetch initial leaderboard from backend
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

  const handleMilestoneReached = useCallback((milestone: { title: string; targetAmount: number }, remainingTokens: number) => {
    window.dispatchEvent(new CustomEvent('chat:system-message', {
      detail: { id: `milestone-${Date.now()}`, content: `🔥 Milestone reached: ${milestone.title}!` }
    }));
    if (remainingTokens > 0) {
      setTimeout(() => {
        window.dispatchEvent(new CustomEvent('chat:system-message', {
          detail: { id: `milestone-next-${Date.now()}`, content: `🔥 ${remainingTokens} tokens to next goal!` }
        }));
      }, 1500);
    }
  }, []);

  const handleTip = useCallback((payloadOrAmount: any) => {
    console.debug("TIP RECEIVED IN WATCHPAGE", payloadOrAmount);
    if (typeof payloadOrAmount === 'object' && payloadOrAmount?.type && !['TIP', 'SUPER_TIP'].includes(payloadOrAmount.type)) {
      console.warn("Non-tip event filtered out in handleTip", payloadOrAmount.type);
      return;
    }

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
    const username = (isAmount ? userDisplayName : (payloadOrAmount?.username || payloadOrAmount?.viewer)) || 'Someone';
    const payload = isAmount ? { amount, username, animationType: resolveAnimationByAmount(amount) } : payloadOrAmount;

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
      if (top5.length > 0) {
        setTopTipper({ name: top5[0].username, amount: top5[0].total });
      }
      return top5;
    });

    // rarity resolved inline below via resolveRarityByAmount

    if (amount >= 50) {
      setTokenExplosion({ amount, key: Date.now() });
    }
    if (amount >= 500) {
      setLegendaryEffect({ username, amount, isVisible: true, effectType: `legendary-${Date.now()}` });
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
  }, [userDisplayName]);

  const handleSelectGift = useCallback(async (gift: any) => {
    if (!roomId) {
      console.error('GIFT: Cannot send gift — no roomId available');
      return;
    }
    try {
      await tipService.sendTokenTip(roomId, gift.price, '', undefined, gift.name);
      // UI update is driven by WebSocket monetization event — no optimistic update here.
    } catch (err: any) {
      console.error('GIFT: Failed to send gift tip', err);
    }
  }, [roomId]);

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
          case 'ACTION_TRIGGERED':
          case 'TIP_MENU':
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

  return {
    leaderboard,
    topTipper,
    tokenExplosion,
    legendaryEffect,
    goal,
    goalOverlayVisible,
    setGoalOverlayVisible,
    showGoalBanner,
    handleTip,
    handleSelectGift,
    handleGoalUpdate,
    handleSuperTipEnd,
    handleMilestoneReached,
    overlayRef,
    processedTipsRef,
  };
};
