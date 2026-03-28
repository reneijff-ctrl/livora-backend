import React from 'react';
import { ContentItem } from '../api/contentService';
import { useAuth } from '../auth/useAuth';
import { safeRender } from '@/utils/safeRender';

import ImageWithFallback from '@/components/ImageWithFallback';

interface ContentCardProps {
  content: ContentItem;
  onUnlock?: (id: string) => void;
  onClick?: (content: ContentItem) => void;
}

const ContentCard: React.FC<ContentCardProps> = ({ content, onUnlock, onClick }) => {
  const { user, hasPremiumAccess } = useAuth();

  const isLocked = () => {
    if (content.unlocked) return false;
    if (content.accessLevel === 'FREE') return false;
    if (content.accessLevel === 'PREMIUM') return !hasPremiumAccess();
    if (content.accessLevel === 'CREATOR') return user?.role !== 'CREATOR' && user?.role !== 'ADMIN';
    return true;
  };

  const locked = isLocked();
  
  const getDisplayUrl = () => {
    if (content.type === 'VIDEO' || content.type === 'CLIP') {
      return content.thumbnailUrl;
    }
    return content.mediaUrl || content.thumbnailUrl;
  };

  const accessBadgeColors = {
    FREE: { bg: 'bg-slate-200', text: 'text-slate-600' },
    PREMIUM: { bg: 'bg-amber-100', text: 'text-amber-800' },
    CREATOR: { bg: 'bg-indigo-100', text: 'text-indigo-800' },
  };

  const badge = accessBadgeColors[content.accessLevel] || accessBadgeColors.FREE;

  const handleCardClick = () => {
    if (locked && onUnlock) {
      onUnlock(content.id);
    } else if (!locked && onClick) {
      onClick(content);
    }
  };

  return (
    <div
      className="flex flex-col rounded-xl overflow-hidden bg-[#1a1a2e] shadow-lg cursor-pointer"
      onClick={handleCardClick}
    >
      {/* Image container with hover overlay */}
      <div className="relative group" style={{ paddingTop: '100%' }}>
        <ImageWithFallback
          src={getDisplayUrl() || undefined}
          alt={content.title}
          className={`absolute inset-0 w-full h-full object-cover ${locked ? 'blur-xl grayscale' : ''}`}
          fallback={
            <div className="absolute inset-0 flex items-center justify-center bg-[#2d2d39] text-gray-500 text-sm">
              No Thumbnail
            </div>
          }
        />

        {/* Premium / access badge (top-right) */}
        {content.accessLevel !== 'FREE' && (
          <span className={`absolute top-2 right-2 z-10 px-2 py-0.5 rounded-full text-xs font-bold ${badge.bg} ${badge.text}`}>
            {safeRender(content.accessLevel)}
          </span>
        )}

        {/* Lock overlay with unlock button */}
        {locked && (
          <div className="absolute inset-0 flex flex-col items-center justify-center p-4 text-center z-10">
            <span className="text-[10px] font-black uppercase tracking-[0.2em] text-white mb-2 drop-shadow-md">
              EXCLUSIVE CONTENT
            </span>
            {content.unlockPriceTokens != null && (
              <span className="text-[10px] font-bold uppercase tracking-widest text-zinc-400 mb-6">
                Unlock for {safeRender(content.unlockPriceTokens)} tokens
              </span>
            )}
            {onUnlock && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onUnlock(content.id);
                }}
                className="px-6 py-2 rounded-full bg-amber-500 text-black text-[10px] font-black uppercase tracking-widest hover:bg-amber-400 transition-all active:scale-95 shadow-lg shadow-amber-500/20"
              >
                Unlock
              </button>
            )}
          </div>
        )}

        {/* Hover overlay with description */}
        {!locked && content.description && (
          <div className="absolute inset-0 bg-black/70 backdrop-blur-sm opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex flex-col justify-end p-3">
            <h3 className="text-white text-sm font-semibold line-clamp-1">
              {safeRender(content.title)}
            </h3>
            <p className="text-xs text-gray-300 line-clamp-2 mt-1">
              {safeRender(content.description)}
            </p>
          </div>
        )}
      </div>

      {/* Title always visible below image */}
      <div className="px-3 pt-2 pb-3">
        <h3 className="text-sm font-medium text-white truncate">
          {safeRender(content.title)}
        </h3>
        {content.accessLevel === 'FREE' && (
          <div className="flex items-center justify-between mt-1">
            <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${badge.bg} ${badge.text}`}>
              {safeRender(content.accessLevel)}
            </span>
            {content.createdAt && (
              <span className="text-xs text-gray-400">
                {safeRender(new Date(content.createdAt).toLocaleDateString())}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ContentCard;
