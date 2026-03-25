import React from 'react';
import { safeRender } from '@/utils/safeRender';

export interface TopTipperBannerProps {
  creatorId?: string;
  topTipperName: string | null;
  topTipAmount?: number;
  onProfileOpen?: (username: string) => void;
}

/**
 * TopTipperBanner - A premium banner that displays the current top tipper of the session.
 * Features an animated gold border, a soft floating effect, and a smooth entry animation.
 * Now positioned at the top-left corner.
 */
const TopTipperBanner: React.FC<TopTipperBannerProps> = ({ 
  topTipperName, 
  topTipAmount, 
  onProfileOpen 
}) => {
  if (!topTipperName) return null;

  return (
    <div className="absolute top-[20px] left-[20px] z-30 pointer-events-none animate-banner-entrance-left">
      <div 
        className="flex items-center gap-3 px-6 py-2 bg-black/40 backdrop-blur-xl border border-yellow-500/50 rounded-full shadow-2xl animate-gold-glow animate-float pointer-events-auto cursor-pointer"
        onClick={() => onProfileOpen && onProfileOpen(topTipperName)}
      >
        <div className="flex items-center gap-2">
          <span className="text-yellow-500 text-[10px] font-black uppercase tracking-[0.2em] drop-shadow-sm">
            Top Tipper
          </span>
          <div className="w-1.5 h-1.5 bg-yellow-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(234,179,8,0.8)]" />
        </div>
        
        <div className="h-4 w-[1px] bg-white/10" />
        
        <div className="flex items-center gap-2">
          <span className="text-white text-sm font-bold tracking-tight drop-shadow-md truncate max-w-[120px]">
            {safeRender(topTipperName)}
          </span>
          {topTipAmount !== undefined && topTipAmount > 0 && (
            <span className="text-yellow-400 text-[10px] font-bold">
              ({safeRender(topTipAmount.toLocaleString())})
            </span>
          )}
          <span className="text-base filter drop-shadow-lg">👑</span>
        </div>
      </div>
    </div>
  );
};

TopTipperBanner.displayName = 'TopTipperBanner';

export default React.memo(TopTipperBanner);
