import React from 'react';
import { safeRender } from '@/utils/safeRender';

export interface MediaItem {
  id: string;
  thumbnail: string;
  isPremium: boolean;
  isLocked?: boolean;
  unlockPrice?: number;
  type?: string;
  mediaUrl?: string;
  title?: string;
}

interface CreatorMediaTabProps {
  media: MediaItem[];
  onItemClick?: (item: MediaItem) => void;
  onUnlock?: (id: string) => void;
}

const CreatorMediaTab: React.FC<CreatorMediaTabProps> = ({ media, onItemClick, onUnlock }) => {
  return (
    <div className="media-grid">
      {media.map((item) => (
        <div 
          key={item.id} 
          className="media-card" 
          onClick={() => {
            if (item.isLocked) {
              onUnlock?.(item.id);
            } else {
              onItemClick?.(item);
            }
          }}
        >
          <img 
            src={item.thumbnail} 
            alt={item.title || "Creator Media"} 
            className={item.isLocked ? 'blur-xl grayscale' : ''} 
          />
          
          {item.isPremium && (
            <span className="premium-badge">Premium</span>
          )}

          {item.isLocked && (
            <div className="absolute inset-0 flex flex-col items-center justify-center p-4 text-center z-10">
               <span className="text-[10px] font-black uppercase tracking-[0.2em] text-white mb-2 drop-shadow-md">
                  EXCLUSIVE CONTENT
               </span>
               <span className="text-[10px] font-bold uppercase tracking-widest text-zinc-400 mb-6">
                 Unlock for {safeRender(item.unlockPrice)} tokens
               </span>
               <button
                 onClick={(e) => {
                   e.stopPropagation();
                   onUnlock?.(item.id);
                 }}
                 className="px-6 py-2 rounded-full bg-amber-500 text-black text-[10px] font-black uppercase tracking-widest hover:bg-amber-400 transition-all active:scale-95 shadow-lg shadow-amber-500/20"
               >
                 Unlock
               </button>
            </div>
          )}
        </div>
      ))}

      <style>{`
        .media-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
          gap: 16px;
        }

        .media-card {
          position: relative;
          cursor: pointer;
          overflow: hidden;
          border-radius: 10px;
          aspect-ratio: 1/1;
          background: #18181b;
        }

        .media-card img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          border-radius: 10px;
          transition: transform .2s;
        }

        .media-card:hover img {
          transform: scale(1.05);
        }

        .premium-badge {
          position: absolute;
          top: 10px;
          right: 10px;
          background: #f59e0b;
          color: #000;
          padding: 4px 8px;
          border-radius: 4px;
          font-size: 10px;
          font-weight: bold;
          z-index: 20;
        }
      `}</style>
    </div>
  );
};

export default CreatorMediaTab;
