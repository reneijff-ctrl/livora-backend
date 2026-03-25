import React, { useState, useEffect, useRef, useCallback, forwardRef, useImperativeHandle } from 'react';
import { spatialSoundEngine } from '@/utils/spatialSoundEngine';
import { resolveTipTier, resolveAnimationType, TipTier } from '@/utils/animationUtils';
import { safeRender } from '@/utils/safeRender';

type OverlayTip = {
  id: string
  type: "token" | "gift"
  amount: number
  username: string
  rarity: string
  animationType: string
  timestamp: number
}

export interface TipOverlayManagerHandle {
  queueTip: (tip: OverlayTip) => void;
}

interface ActiveTip extends OverlayTip {}

interface TipOverlayManagerProps {
  creatorId?: string | number;
  streamId?: string | number;
  onTipAnimationEnd?: (tipId: string) => void;
  onOverlayClose?: () => void;
}

/**
 * TipOverlayManager - Handles a queue of gift animations.
 * Supports simultaneous small gifts and prioritized big gifts (epic/legendary).
 * Big gifts block new animations until they complete.
 */
const TipOverlayManager = forwardRef<TipOverlayManagerHandle, TipOverlayManagerProps>((props, ref) => {
  const { onTipAnimationEnd } = props;
  const [activeTips, setActiveTips] = useState<ActiveTip[]>([]);
  const queueRef = useRef<ActiveTip[]>([]);
  const timeoutsRef = useRef<number[]>([]);

  // Cleanup timeouts on unmount
  useEffect(() => {
    return () => {
      // eslint-disable-next-line react-hooks/exhaustive-deps
      timeoutsRef.current.forEach(id => window.clearTimeout(id));
    };
  }, []);

  const processQueue = useCallback(() => {
    if (queueRef.current.length === 0) return;

    setActiveTips(currentActive => {
      const next = queueRef.current[0];
      const rarity = next?.rarity ?? (next as any)?.gift?.rarity ?? "common";
      const isNextLegendary = rarity === 'legendary';
      const isNextEpic = rarity === 'epic';

      const legendaryActive = currentActive.some(g => (g?.rarity ?? (g as any)?.gift?.rarity) === 'legendary');
      const epicActive = currentActive.some(g => (g?.rarity ?? (g as any)?.gift?.rarity) === 'epic');

      // If a legendary is playing, nothing else starts
      if (legendaryActive) return currentActive;

      if (isNextLegendary) {
        // Legendary starts immediately! Other active ones will be hidden by CSS.
        const gift = queueRef.current.shift()!;
        const tier = resolveTipTier(gift.amount);
        const { duration } = getTierStyles(tier);
        const timeout = window.setTimeout(() => {
          setActiveTips(prev =>
            prev
              .filter(Boolean)
              .filter(g => g.id !== gift.id)
          );
          onTipAnimationEnd?.(gift.id);
        }, duration);
        timeoutsRef.current.push(timeout as unknown as number);
        return [...currentActive.filter(Boolean), gift];
      }

      // If an epic is playing, nothing else starts
      if (epicActive) return currentActive;

      if (isNextEpic) {
        // Epic gift - Exclusive screen (wait for others to finish)
        if (currentActive.length > 0) return currentActive;

        const gift = queueRef.current.shift()!;
        const epicTier = resolveTipTier(gift.amount);
        const { duration: epicDuration } = getTierStyles(epicTier);
        const timeout = window.setTimeout(() => {
          setActiveTips(prev =>
            prev
              .filter(Boolean)
              .filter(g => g.id !== gift.id)
          );
          onTipAnimationEnd?.(gift.id);
        }, epicDuration);
        timeoutsRef.current.push(timeout as unknown as number);
        return [gift];
      } else {
        // Simultaneous small gifts (max 5)
        if (currentActive.length < 5) {
          const gift = queueRef.current.shift()!;
          const smallTier = resolveTipTier(gift.amount);
          const { duration: smallDuration } = getTierStyles(smallTier);
          const timeout = window.setTimeout(() => {
            setActiveTips(prev =>
              prev
                .filter(Boolean)
                .filter(g => g.id !== gift.id)
            );
            onTipAnimationEnd?.(gift.id);
          }, smallDuration);
          timeoutsRef.current.push(timeout as unknown as number);
          return [...currentActive.filter(Boolean), gift];
        }
        return currentActive;
      }
    });
  }, [onTipAnimationEnd]);

  // Check queue whenever active gifts change
  useEffect(() => {
    if (queueRef.current.length > 0) {
      const timer = window.setTimeout(processQueue, 50);
      return () => window.clearTimeout(timer);
    }
  }, [activeTips, processQueue]);

  // Sound effect trigger - watches the active gifts and plays sound for new entries
  const playedRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    activeTips.forEach(gift => {
      if (!gift || !gift.id) return;
      if (!playedRef.current.has(gift.id)) {
        const rarity = gift?.rarity ?? (gift as any)?.gift?.rarity ?? "common";
        const profile = rarity;
        // Legendary gifts play in center (pan 0), others random pan between -0.7 and 0.7
        const pan = rarity === 'legendary' ? 0 : (Math.random() * 1.4 - 0.7);
        
        spatialSoundEngine.play(profile, pan);
        playedRef.current.add(gift.id);
      }
    });

    if (activeTips.length === 0 && playedRef.current.size > 0) {
      playedRef.current.clear();
    }
  }, [activeTips]);

  useImperativeHandle(ref, () => ({
    queueTip: (tip: any) => {
      console.debug("TIP ADDED TO OVERLAY QUEUE", tip);
      
      // Step 2 — Guard before processing
      if (!tip || typeof tip !== "object") {
        console.warn("TIP OVERLAY INVALID TIP", tip);
        return;
      }

      // Step 3 — Ensure ID exists
      if (!tip.id) {
        tip.id = crypto.randomUUID();
      }

      // Step 4 — Optional chaining and normalization
      const tipId = tip?.id ?? crypto.randomUUID();
      const amount = Number(tip?.amount) || 0;
      const username = safeRender(tip?.username || tip?.senderUsername) || "Anonymous";
      
      // Step 4 — Ensure new tips added to queue are normalized
      const normalizedTip: ActiveTip = {
        id: tipId,
        amount,
        username,
        rarity: tip?.rarity || "normal",
        animationType: tip?.animationType || "default",
        type: tip?.type || "token",
        timestamp: tip?.timestamp || Date.now()
      };

      // Step 5 — Push normalizedTip into overlay queue
      queueRef.current.push(normalizedTip);
      processQueue();
    }
  }));

  // Tier styles use the unified resolveTipTier() from animationUtils

  const getTierStyles = (tier: TipTier) => {
    switch (tier) {
      case 'common':
        return { container: 'px-4 py-2 rounded-full border border-white/10 bg-black/30 backdrop-blur-md', text: 'text-sm text-white/80', label: '', duration: 2500 };
      case 'rare':
        return { container: 'px-5 py-3 rounded-full border border-indigo-500/40 bg-black/40 backdrop-blur-xl shadow-[0_0_20px_rgba(99,102,241,0.3)]', text: 'text-lg font-bold text-indigo-300', label: '', duration: 3500 };
      case 'epic':
        return { container: 'px-6 py-3 rounded-full border border-yellow-500/50 bg-black/50 backdrop-blur-xl shadow-[0_0_30px_rgba(234,179,8,0.4)]', text: 'text-xl font-black text-yellow-400', label: 'HUGE TIP!', duration: 4000 };
      case 'legendary':
        return { container: 'px-8 py-4 rounded-full border border-purple-500/60 bg-black/60 backdrop-blur-xl shadow-[0_0_50px_rgba(168,85,247,0.5)]', text: 'text-2xl font-black text-purple-300', label: 'LEGENDARY!', duration: 5000 };
    }
  };

  const getEmoji = (type?: string) => {
    switch (type) {
      case 'mega-explosion': return '💥';
      case 'fireworks': return '🎆';
      case 'golden-coin-burst': return '🪙';
      case 'floatHearts':
      case 'small-hearts': return '💖';
      case 'goldenRain': return '💰';
      case 'cosmicStorm': return '🌀';
      case 'meteorFall': return '☄️';
      case 'goldPulseBorder': return '✨';
      default: return '🎁';
    }
  };

  const getAnimationClass = (type?: string) => {
    switch (type) {
      case 'mega-explosion': return 'anim-megaExplosion';
      case 'fireworks': return 'anim-fireworks';
      case 'golden-coin-burst': return 'anim-coinBurst';
      case 'floatHearts':
      case 'small-hearts': return 'anim-floatUp';
      case 'goldenRain': return 'anim-goldenRain';
      case 'cosmicStorm': return 'anim-cosmicStorm';
      case 'meteorFall': return 'anim-meteorFall';
      case 'goldPulseBorder': return 'anim-goldPulseBorder';
      default: return 'anim-floatUp';
    }
  };

  const legendaryActive = activeTips.some(g => (g?.rarity ?? (g as any)?.gift?.rarity) === 'legendary');

  return (
    <div className="absolute inset-0 pointer-events-none z-40 overflow-hidden flex flex-col items-center justify-center">
      {activeTips.map((tip, index) => {
        if (!tip || !tip?.id) return null;

        const rarity = tip?.rarity ?? (tip as any)?.gift?.rarity ?? "common";
        const isBig = rarity === 'epic' || rarity === 'legendary';
        const isLegendary = rarity === 'legendary';
        const animationType = resolveAnimationType(tip?.animationType) || "default";
        const tier = resolveTipTier(tip.amount);
        const tierStyle = getTierStyles(tier);
        
        const shouldHide = legendaryActive && !isLegendary;
        const offset = !isBig ? (index * 60) - ((activeTips.length - 1) * 30) : 0;

        return (
          <div 
            key={tip?.id} 
            className={`absolute flex flex-col items-center pointer-events-none transition-all duration-500 ${shouldHide ? 'opacity-0 scale-0' : 'opacity-100 scale-100'}`}
            style={{ 
              top: '50%',
              transform: isBig ? 'translateY(-50%)' : `translateY(calc(-50% + ${offset}px))`,
              zIndex: isBig ? 100 : 10 + index
            }}
          >
            <div className={`tip-overlay-item flex flex-col items-center gap-1.5 ${getAnimationClass(animationType)}`}>
              {tierStyle.label && (
                <div className={`mb-1 bg-gradient-to-r ${isLegendary ? 'from-indigo-500 via-white to-purple-500' : 'from-yellow-500 via-white to-yellow-500'} text-black px-5 py-0.5 rounded-full font-black text-[10px] uppercase tracking-widest shadow-lg animate-pulse`}>
                  {safeRender(tierStyle.label)}
                </div>
              )}
              <div className={tierStyle.container}>
                <span className={`${tierStyle.text} drop-shadow-[0_2px_4px_rgba(0,0,0,0.5)]`}>
                  +{safeRender(tip?.amount)} tokens <span className="animate-bounce inline-block">{getEmoji(animationType)}</span>
                </span>
              </div>
              <span className={`font-bold ${isBig ? 'text-[13px] text-white bg-black/60 px-5 py-1.5' : 'text-[11px] text-white/70 bg-black/40 px-3 py-1'} rounded-full transition-all`}>
                {safeRender(tip?.username)} sent {tip?.type === 'gift' ? 'a Gift' : 'Tokens'}!
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
});

export default React.memo(TipOverlayManager);
