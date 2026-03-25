import React, { useState, useCallback, forwardRef, useImperativeHandle } from 'react';
import { GIFT_CATALOG, Gift } from '@/constants/GiftCatalog';

export interface GiftSelectorModalHandle {
  open: () => void;
  close: () => void;
}

interface GiftSelectorModalProps {
  onSelectGift: (gift: Gift) => void;
}

/**
 * GiftSelectorModal - A premium dark-themed modal for selecting virtual gifts.
 * Uses forwardRef to allow imperative control (open/close) without re-rendering the parent.
 */
const GiftSelectorModal = forwardRef<GiftSelectorModalHandle, GiftSelectorModalProps>(({ onSelectGift }, ref) => {
  const [isOpen, setIsOpen] = useState(false);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);

  useImperativeHandle(ref, () => ({
    open,
    close
  }));

  if (!isOpen) return null;

  const getRarityColor = (rarity: Gift['rarity']) => {
    switch (rarity) {
      case 'common': return 'text-zinc-400 bg-zinc-400/10 border-zinc-400/20';
      case 'rare': return 'text-blue-400 bg-blue-400/10 border-blue-400/20';
      case 'epic': return 'text-purple-400 bg-purple-400/10 border-purple-400/20';
      case 'legendary': return 'text-yellow-400 bg-yellow-400/10 border-yellow-400/20';
      default: return 'text-zinc-400 bg-zinc-400/10 border-zinc-400/20';
    }
  };

  const getEmoji = (id: string) => {
    switch (id) {
      case 'rose': return '🌹';
      case 'champagne': return '🍾';
      case 'fireworks': return '🎆';
      case 'lambo': return '🏎️';
      case 'meteor': return '☄️';
      case 'galaxyStorm': return '🌌';
      default: return '🎁';
    }
  };

  const handleSelect = (gift: Gift) => {
    onSelectGift(gift);
    close();
  };

  return (
    <div 
      className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm"
      onClick={close}
    >
      <div 
        className="w-full max-w-2xl bg-[#0f0f14] border border-white/10 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-6 border-b border-white/5 flex justify-between items-center bg-[#16161D]/50">
          <div>
            <h2 className="text-2xl font-black text-white tracking-tight">Gift Catalog</h2>
            <p className="text-sm text-white/40 font-medium">Select a gift to send to the creator</p>
          </div>
          <button 
            onClick={close}
            className="p-2 bg-white/5 hover:bg-white/10 rounded-full text-white/40 hover:text-white transition-all duration-200"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Gift Grid */}
        <div className="p-6 overflow-y-auto">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            {GIFT_CATALOG.map((gift) => (
              <button
                key={gift.id}
                onClick={() => handleSelect(gift)}
                className="group relative flex flex-col items-center p-6 bg-[#16161D] border border-white/5 rounded-2xl transition-all duration-300 hover:border-indigo-500/50 hover:bg-indigo-500/5 hover:scale-[1.02] active:scale-95 text-center"
              >
                {/* Rarity Badge */}
                <div className={`absolute top-3 right-3 px-2 py-0.5 rounded-full text-[9px] font-black uppercase tracking-widest border ${getRarityColor(gift.rarity)}`}>
                  {gift.rarity}
                </div>

                {/* Gift Visual */}
                <div className="text-4xl mb-4 group-hover:scale-110 transition-transform duration-300 filter drop-shadow-lg">
                  {getEmoji(gift.id)}
                </div>

                {/* Gift Info */}
                <h3 className="text-sm font-bold text-white mb-1 group-hover:text-indigo-400 transition-colors">{gift.name}</h3>
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-black text-yellow-500">{gift.price} tokens</span>
                </div>
                
                {/* Hover Effect Glow */}
                <div className="absolute inset-0 bg-indigo-500/0 group-hover:bg-indigo-500/5 rounded-2xl transition-colors pointer-events-none" />
              </button>
            ))}
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 bg-[#16161D]/30 border-t border-white/5 text-center">
          <p className="text-[10px] text-white/20 font-bold uppercase tracking-[0.2em]">Premium Virtual Gifts</p>
        </div>
      </div>
    </div>
  );
});

export default React.memo(GiftSelectorModal);
