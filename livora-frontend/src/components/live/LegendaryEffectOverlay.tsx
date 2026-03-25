import React, { useState, useEffect, useRef } from 'react';
import { safeRender } from '../../utils/safeRender';

/**
 * LegendaryEffectOverlayProps - Defines the primitive props for the component.
 * effectType - A unique string to trigger the animation (e.g. combined with timestamp).
 * isVisible - Whether the effect is active from the parent's perspective.
 */
interface LegendaryEffectOverlayProps {
  username?: string;
  amount?: number;
  isVisible?: boolean;
  effectType?: string;
  durationSeconds?: number;
}

/**
 * LegendaryEffectOverlay - A high-impact fullscreen overlay for Legendary gifts.
 * Features a radial golden burst, background flash, and large center text.
 * Wrapped in React.memo to prevent unnecessary re-renders.
 */
const LegendaryEffectOverlay: React.FC<LegendaryEffectOverlayProps> = ({ 
  username, 
  amount, 
  isVisible, 
  effectType,
  durationSeconds 
}) => {
  const [active, setActive] = useState(false);
  const [localData, setLocalData] = useState({ username: '', amount: 0 });
  const timeoutsRef = useRef<number[]>([]);

  // Trigger animation whenever effectType changes and isVisible is true
  useEffect(() => {
    if (isVisible && effectType) {
      setLocalData({ 
        username: typeof username === 'string' ? username : (username as any)?.username || 'Someone', 
        amount: Number(amount) || 0 
      });
      setActive(true);
      
      const duration = (durationSeconds && durationSeconds > 0) ? durationSeconds * 1000 : 5000;
      const timeout = window.setTimeout(() => {
        setActive(false);
      }, duration);
      
      timeoutsRef.current.push(timeout as unknown as number);
    }
  }, [isVisible, effectType, username, amount, durationSeconds]);

  // Cleanup timeouts on unmount
  useEffect(() => {
    return () => {
      // eslint-disable-next-line react-hooks/exhaustive-deps
      timeoutsRef.current.forEach(clearTimeout);
    };
  }, []);

  if (!active || !localData.username) return null;

  return (
    <div className="fixed inset-0 z-[200] pointer-events-none flex items-center justify-center overflow-hidden">
      {/* Background Flash */}
      <div className="absolute inset-0 bg-yellow-500 opacity-0 animate-legendary-flash" />

      {/* Radial Burst */}
      <div className="absolute w-[800px] h-[800px] rounded-full bg-gradient-radial from-yellow-400/40 via-yellow-600/10 to-transparent opacity-0 scale-0 animate-legendary-burst" />

      {/* Center Content */}
      <div className="relative flex flex-col items-center gap-4 animate-legendary-text">
        <div className="flex flex-col items-center">
          <span className="text-yellow-500 text-sm font-black uppercase tracking-[0.5em] mb-2 drop-shadow-[0_0_10px_rgba(234,179,8,0.8)]">
            Legendary Gift
          </span>
          <h2 className="text-6xl md:text-8xl font-black text-white drop-shadow-[0_10px_20px_rgba(0,0,0,0.8)] text-center px-4">
            {safeRender(localData.username)}
          </h2>
        </div>

        <div className="px-10 py-4 bg-white/10 backdrop-blur-3xl border border-white/20 rounded-2xl shadow-2xl">
          <span className="text-4xl md:text-6xl font-black text-transparent bg-clip-text bg-gradient-to-r from-yellow-200 via-yellow-500 to-yellow-200 animate-pulse">
            +{safeRender(localData.amount)} TOKENS
          </span>
        </div>

        <div className="mt-4 flex gap-6">
          <span className="text-4xl animate-bounce" style={{ animationDelay: '0s' }}>👑</span>
          <span className="text-4xl animate-bounce" style={{ animationDelay: '0.2s' }}>✨</span>
          <span className="text-4xl animate-bounce" style={{ animationDelay: '0.4s' }}>💎</span>
        </div>
      </div>
    </div>
  );
};

export default React.memo(LegendaryEffectOverlay);
