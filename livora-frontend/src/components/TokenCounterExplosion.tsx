import React, { useState, useCallback, useEffect } from 'react';

interface TokenCounterExplosionProps {
  tokenAmount: number;
  animationKey: string | number;
}

interface ExplosionItem {
  id: string;
  amount: number;
}

/**
 * TokenCounterExplosion - A high-performance animation for token counts.
 * Triggers a floating "+{amount} tokens" animation with scale and fade effects.
 * Uses only transform and opacity for GPU acceleration.
 */
const TokenCounterExplosion: React.FC<TokenCounterExplosionProps> = ({ tokenAmount, animationKey }) => {
  const [items, setItems] = useState<ExplosionItem[]>([]);

  const trigger = useCallback((amount: number) => {
    const id = Math.random().toString(36).substring(2, 9);
    setItems(prev => [...prev, { id, amount }]);
    
    // Cleanup after animation finishes (1.5s)
    setTimeout(() => {
      setItems(prev => prev.filter(item => item.id !== id));
    }, 1500);
  }, []);

  useEffect(() => {
    if (animationKey && tokenAmount > 0) {
      trigger(tokenAmount);
    }
  }, [animationKey, tokenAmount, trigger]);

  return (
    <div className="absolute inset-0 pointer-events-none z-50 flex items-center justify-center overflow-hidden">
      {items.map(item => (
        <div 
          key={item.id}
          className="token-explosion-item"
        >
          <div className="flex flex-col items-center">
            <span className="font-black text-5xl text-yellow-400 drop-shadow-[0_0_20px_rgba(234,179,8,0.8)] italic">
              +{item.amount.toLocaleString()}
            </span>
            <span className="text-sm font-black text-white/80 uppercase tracking-widest -mt-2">
              Tokens
            </span>
          </div>
        </div>
      ))}
    </div>
  );
};

TokenCounterExplosion.displayName = 'TokenCounterExplosion';

export default React.memo(TokenCounterExplosion);
