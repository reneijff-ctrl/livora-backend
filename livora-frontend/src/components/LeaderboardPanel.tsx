import React, { useState, useCallback, forwardRef, useImperativeHandle } from 'react';
import { safeRender } from '@/utils/safeRender';

export interface LeaderboardPanelHandle {
  toggle: () => void;
  open: () => void;
  close: () => void;
}

export interface LeaderboardEntry {
  username: string;
  total: number;
}

interface LeaderboardPanelProps {
  creatorId?: string;
  streamId?: string;
  leaderboard: LeaderboardEntry[];
}

/**
 * LeaderboardPanel - A slide-in panel showing top session tippers.
 * Uses CSS transforms for smooth animations and avoids layout shifts.
 * Now optimized with React.memo and primitive props.
 */
const LeaderboardPanel = forwardRef<LeaderboardPanelHandle, LeaderboardPanelProps>(({ leaderboard, creatorId, streamId }, ref) => {
  const [isOpen, setIsOpen] = useState(false);

  const toggle = useCallback(() => setIsOpen(prev => !prev), []);
  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);

  useImperativeHandle(ref, () => ({
    toggle,
    open,
    close
  }));

  const top5 = leaderboard.slice(0, 5);

  return (
    <div 
      className={`absolute top-24 right-0 bottom-24 w-72 bg-black/40 backdrop-blur-2xl border-l border-white/5 z-40 transition-transform duration-500 ease-in-out shadow-2xl ${isOpen ? 'translate-x-0' : 'translate-x-full'}`}
    >
      {/* Toggle Tab (Visible when closed) */}
      <button 
        onClick={toggle}
        className="absolute left-0 top-1/2 -translate-x-full -translate-y-1/2 p-2.5 bg-black/40 backdrop-blur-2xl border border-r-0 border-white/5 rounded-l-2xl text-white/40 hover:text-white hover:bg-black/60 transition-all group"
        title={isOpen ? "Close Leaderboard" : "Open Leaderboard"}
      >
        <div className={`transition-transform duration-500 ${isOpen ? 'rotate-180' : 'rotate-0'}`}>
          {isOpen ? (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          ) : (
            <div className="flex flex-col items-center gap-2 py-1">
               <svg className="w-5 h-5 text-indigo-500 group-hover:animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
               </svg>
               <span className="text-[9px] font-black uppercase tracking-[0.2em] [writing-mode:vertical-lr] rotate-180">Leaderboard</span>
            </div>
          )}
        </div>
      </button>

      {/* Panel Content */}
      <div className="p-6 h-full flex flex-col">
        <div className="flex items-center justify-between mb-8 pb-4 border-b border-white/5">
          <div>
            <h3 className="text-white font-black uppercase tracking-widest text-[11px] mb-0.5">Top Supporters</h3>
            <p className="text-white/30 text-[9px] font-medium tracking-wide">Weekly Leaderboard</p>
          </div>
          <button 
            onClick={close}
            className="p-1.5 hover:bg-white/5 rounded-full text-white/20 hover:text-white transition-all"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto pr-2 custom-scrollbar">
          {top5.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-40 opacity-20">
              <span className="text-4xl mb-4">🏆</span>
              <p className="text-[10px] font-bold uppercase tracking-widest">No tips yet</p>
            </div>
          ) : (
            top5.map((entry, index) => (
              <div 
                key={entry.username} 
                className="flex items-center justify-between group animate-in fade-in slide-in-from-right-4 duration-500"
                style={{ animationDelay: `${index * 100}ms` }}
              >
                <div className="flex items-center gap-4">
                  <div className="relative">
                    <span className={`w-9 h-9 flex items-center justify-center rounded-xl text-xs font-black shadow-lg transition-transform group-hover:scale-110 ${
                      index === 0 ? 'bg-gradient-to-br from-yellow-400 to-amber-600 text-white' : 
                      index === 1 ? 'bg-gradient-to-br from-zinc-300 to-zinc-500 text-white' : 
                      index === 2 ? 'bg-gradient-to-br from-amber-700 to-amber-900 text-white' : 
                      'bg-white/5 text-white/40'
                    }`}>
                      {index + 1}
                    </span>
                    {index === 0 && <span className="absolute -top-1.5 -right-1.5 text-xs animate-bounce">👑</span>}
                  </div>
                  <div className="flex flex-col">
                    <span className="text-sm font-bold text-white/90 group-hover:text-indigo-400 transition-colors truncate max-w-[110px]">
                      {safeRender(entry.username)}
                    </span>
                    <span className="text-[9px] font-black text-white/20 uppercase tracking-widest">Top Tipper</span>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm font-black text-white group-hover:text-indigo-400 transition-colors">
                    {safeRender(entry.total.toLocaleString())}
                  </div>
                  <div className="text-[9px] font-bold text-indigo-500 uppercase tracking-tighter">Tokens</div>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="mt-auto pt-6 border-t border-white/5">
          <p className="text-center text-[8px] text-white/10 font-black uppercase tracking-[0.4em]">Livora Premium Stream</p>
        </div>
      </div>
    </div>
  );
});

LeaderboardPanel.displayName = 'LeaderboardPanel';

export default React.memo(LeaderboardPanel);
