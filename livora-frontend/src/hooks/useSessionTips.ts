import { useRef, useCallback, useMemo, useEffect } from 'react';
import { TipEvent } from '@/types';

interface LeaderboardEntry {
  username: string;
  total: number;
}

interface UseSessionTipsResult {
  registerTip: (tip: TipEvent) => { topTipper: string | null; leaderboard: LeaderboardEntry[] };
  getStats: () => { topTipper: string | null; leaderboard: LeaderboardEntry[] };
  reset: () => void;
}

/**
 * useSessionTips - Hook to track tip statistics within a viewing session.
 * Tracks leaderboard, biggest single tip, and top tipper.
 * Uses refs to prevent unnecessary re-renders of the parent component.
 */
export const useSessionTips = (sessionId: string): UseSessionTipsResult => {
  const totalsRef = useRef<Record<string, number>>({});
  const biggestTipRef = useRef<number>(0);
  const topTipperRef = useRef<string | null>(null);
  
  const reset = useCallback(() => {
    totalsRef.current = {};
    biggestTipRef.current = 0;
    topTipperRef.current = null;
  }, []);

  const getStats = useCallback(() => {
    const leaderboard = Object.entries(totalsRef.current)
      .map(([username, total]) => ({ username, total }))
      .sort((a, b) => b.total - a.total);
    
    return {
      topTipper: topTipperRef.current,
      leaderboard
    };
  }, []);

  useEffect(() => {
    reset();
  }, [sessionId, reset]);

  const registerTip = useCallback((tip: TipEvent) => {
    const { username, amount } = tip;
    if (!username || amount <= 0) return getStats();

    const currentTotal = (totalsRef.current[username] || 0) + amount;
    totalsRef.current[username] = currentTotal;

    if (amount > biggestTipRef.current) {
      biggestTipRef.current = amount;
    }

    const currentTopTipper = topTipperRef.current;
    if (!currentTopTipper || currentTotal > (totalsRef.current[currentTopTipper] || 0)) {
      topTipperRef.current = username;
    }

    return getStats();
  }, [getStats]);

  return useMemo(() => ({
    registerTip,
    getStats,
    reset
  }), [registerTip, getStats, reset]);
};
