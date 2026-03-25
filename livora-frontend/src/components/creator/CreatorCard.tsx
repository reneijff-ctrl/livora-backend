import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ICreator } from '../../domain/creator/ICreator';
import { safeRender } from '@/utils/safeRender';

function formatLiveDuration(startedAt?: string | null): string | null {
  if (!startedAt) return null;
  const start = new Date(startedAt).getTime();
  if (isNaN(start)) return null;
  const diff = Math.max(0, Math.floor((Date.now() - start) / 1000));
  if (diff < 60) return '<1m';
  const mins = Math.floor(diff / 60);
  if (mins < 60) return `${mins}m`;
  const hrs = Math.floor(mins / 60);
  const rem = mins % 60;
  return rem > 0 ? `${hrs}h ${rem}m` : `${hrs}h`;
}

interface CreatorCardProps {
  creator: ICreator;
  onClick?: (creator: ICreator) => void;
  variant?: 'featured' | 'explore';
}

const CreatorCardSkeleton: React.FC<{ variant?: 'featured' | 'explore' }> = React.memo(({ variant = 'featured' }) => (
  <div className={`w-full flex-none rounded-2xl overflow-hidden bg-[#0f0f14] shadow-md animate-pulse relative ${variant === 'explore' ? 'aspect-[3/3.8]' : 'aspect-[3/4]'}`}>
    <div className="absolute top-3 left-3 flex gap-2">
      <div className="w-12 h-5 bg-white/5 rounded-full" />
      <div className="w-8 h-5 bg-white/5 rounded-full" />
    </div>
    <div className="absolute top-3 right-3">
      <div className="w-10 h-4 bg-white/5 rounded-full" />
    </div>
    <div className="absolute bottom-0 left-0 right-0 p-4">
      <div className="h-3 bg-white/5 rounded w-2/3 mb-3" />
      <div className="h-5 bg-white/10 rounded w-3/4 mb-1.5" />
      <div className="h-3 bg-white/5 rounded w-1/2 mb-3" />
      <div className="w-full h-1 bg-white/5 rounded-full" />
    </div>
  </div>
));

const CreatorCard: React.FC<CreatorCardProps> = React.memo(({ creator, onClick, variant = 'featured' }) => {
  const navigate = useNavigate();
  const [thumbnailError, setThumbnailError] = React.useState(false);

  const handleClick = () => {
    if (onClick) {
      onClick(creator);
    } else {
      navigate(`/creators/${creator.userId}`);
    }
  };

  React.useEffect(() => {
    setThumbnailError(false);
  }, [creator.userId, creator.isLive, creator.activeStream?.thumbnailUrl]);

  const liveDuration = useMemo(() => formatLiveDuration(creator.streamStartedAt), [creator.streamStartedAt]);

  if (!creator) return null;

  const isLive = !!creator.isLive;
  const isOnline = creator.isOnline || creator.online;

  const hasStreamThumb = !!(creator.activeStream?.thumbnailUrl && isLive && !thumbnailError);
  const imageUrl = hasStreamThumb ? creator.activeStream!.thumbnailUrl : creator.avatarUrl;

  const goalProgress = useMemo(() => {
    const goal = creator.activeStream?.goal;
    if (!goal || typeof goal === 'string') return null;
    if (!goal.targetAmount || goal.targetAmount <= 0) return null;
    return {
      title: goal.title,
      current: goal.currentAmount ?? 0,
      target: goal.targetAmount,
      percent: Math.min(100, ((goal.currentAmount ?? 0) / goal.targetAmount) * 100),
    };
  }, [creator.activeStream?.goal]);

  return (
    <div
      onClick={handleClick}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleClick(); }}
      role="button"
      tabIndex={0}
      className={`group relative w-full flex-none rounded-2xl overflow-hidden transition-all duration-300 transform-gpu cursor-pointer ${variant === 'explore' ? 'aspect-[3/3.8]' : 'aspect-[3/4]'} ${
        isLive
          ? 'bg-[#0f0f14] ring-1 ring-red-500/20 hover:ring-red-500/40 shadow-[0_2px_16px_rgba(239,68,68,0.08)] hover:shadow-[0_6px_24px_rgba(239,68,68,0.14)] hover:scale-[1.025]'
          : 'bg-[#0f0f14] shadow-md hover:shadow-[0_6px_20px_rgba(0,0,0,0.45)] hover:scale-[1.02]'
      }`}
    >
      {/* IMAGE */}
      {imageUrl ? (
        <img
          src={imageUrl}
          alt={creator.displayName}
          className={`absolute inset-0 w-full h-full object-cover rounded-2xl transition-transform duration-500 group-hover:scale-105 ${
            !isLive ? 'brightness-[0.8] saturate-[0.85]' : ''
          }`}
          onError={() => setThumbnailError(true)}
        />
      ) : (
        <div className="absolute inset-0 w-full h-full flex items-center justify-center bg-[#0f0f14] overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-b from-indigo-500/10 via-transparent to-black/40" />
          <img 
            src="/icoon_joinlivora.png" 
            alt="Livora Logo"
            className="w-32 h-32 object-contain opacity-20 group-hover:opacity-30 transition-all duration-700 group-hover:scale-110 pointer-events-none select-none drop-shadow-2xl"
          />
        </div>
      )}

      {/* HOVER GLOW */}
      <div className="absolute inset-0 bg-gradient-to-tr from-indigo-500/8 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none" />

      {/* CTA OVERLAY — contained pill in upper-center, does not obscure bottom info */}
      <div className="absolute inset-x-0 top-[38%] flex justify-center opacity-0 group-hover:opacity-100 transition-all duration-250 z-30 pointer-events-none">
        {isLive ? (
          <button
            type="button"
            className="pointer-events-auto bg-white/95 text-black px-5 py-2 rounded-full font-bold text-[12px] uppercase tracking-wide shadow-xl transform translate-y-2 group-hover:translate-y-0 transition-transform duration-300 backdrop-blur-sm hover:bg-white cursor-pointer"
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              navigate(`/creators/${creator.userId}/live`);
            }}
          >
            Watch Stream
          </button>
        ) : (
          <div className="bg-white/95 text-black px-5 py-2 rounded-full font-bold text-[12px] uppercase tracking-wide shadow-xl transform translate-y-2 group-hover:translate-y-0 transition-transform duration-300 backdrop-blur-sm">
            View Profile
          </div>
        )}
      </div>

      {/* BOTTOM GRADIENT — anchored premium content zone */}
      <div className={`absolute inset-x-0 bottom-0 pointer-events-none ${
        isLive
          ? 'h-[92%] bg-gradient-to-t from-black from-12% via-black/85 via-40% to-transparent'
          : 'h-[65%] bg-gradient-to-t from-black from-5% via-black/60 via-45% to-transparent'
      }`} />

      {/* TOP-LEFT: LIVE + Viewers */}
      {isLive && (
        <div className="absolute top-3 left-3 z-20 flex items-center gap-1.5">
          <div className="bg-red-600 text-white text-[10px] font-black px-2.5 py-[3px] rounded-full uppercase tracking-wider shadow-lg flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 bg-white rounded-full animate-pulse" />
            LIVE
          </div>
          {creator.viewerCount !== undefined && creator.viewerCount > 0 && (
            <div className="bg-black/55 backdrop-blur-md px-2 py-[3px] rounded-full shadow-lg text-white text-[10px] font-bold border border-white/10 flex items-center gap-1">
              <svg className="w-3 h-3 text-white/60" fill="currentColor" viewBox="0 0 20 20"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z"/><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd"/></svg>
              {safeRender(creator.viewerCount?.toLocaleString())}
            </div>
          )}
        </div>
      )}

      {/* TOP-RIGHT: Duration + Paid/Free */}
      {isLive && (liveDuration || creator.isPaid !== undefined) && (
        <div className="absolute top-3 right-3 z-20 flex items-center gap-1.5">
          {liveDuration && (
            <div className="bg-black/55 backdrop-blur-md px-2 py-[2px] rounded-full text-white/80 text-[9px] font-semibold border border-white/5 flex items-center gap-1">
              <svg className="w-2.5 h-2.5 text-white/50" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              {liveDuration}
            </div>
          )}
          {creator.isPaid ? (
            <div className="bg-amber-500/75 backdrop-blur-md px-2 py-[2px] rounded-full text-white text-[9px] font-bold border border-amber-400/25 flex items-center gap-0.5">
              💎 {creator.admissionPrice ? `${creator.admissionPrice}` : 'Paid'}
            </div>
          ) : isLive ? (
            <div className="bg-emerald-600/65 backdrop-blur-md px-2 py-[2px] rounded-full text-white text-[9px] font-bold border border-emerald-400/15">
              Free
            </div>
          ) : null}
        </div>
      )}

      {/* OFFLINE STATUS — top left */}
      {!isLive && (
        <div className="absolute top-3 left-3 z-20">
          <div
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-black/55 backdrop-blur-md border border-white/5 shadow-lg transition-opacity duration-300 ${
              isOnline ? 'opacity-100' : 'opacity-60'
            }`}
          >
            <div className={`w-1.5 h-1.5 rounded-full ${
              isOnline ? 'bg-green-500 shadow-[0_0_6px_rgba(34,197,94,0.8)]' : 'bg-zinc-500'
            }`} />
            <span className="text-[10px] font-bold text-white/90 tracking-tight uppercase">
              {isOnline ? 'Online' : 'Offline'}
            </span>
          </div>
        </div>
      )}

      {/* BOTTOM INFO */}
      <div className="absolute bottom-0 left-0 right-0 px-4 pb-5 pt-2 z-10">
        {/* IDENTITY ROW */}
        <div className="flex items-center gap-2.5">
          {hasStreamThumb && creator.avatarUrl && (
            <div className="flex-shrink-0 w-8 h-8 rounded-full ring-2 ring-white/15 overflow-hidden shadow-lg">
              <img
                src={creator.avatarUrl}
                alt=""
                className="w-full h-full object-cover"
                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
              />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <div className="text-[15px] font-extrabold text-white truncate drop-shadow-[0_1px_4px_rgba(0,0,0,0.6)] leading-tight">
              {safeRender(creator.displayName)}
            </div>
            <div className="flex items-center gap-1 mt-0.5">
              {creator.username && creator.username !== creator.displayName && (
                <span className="text-[10px] text-white/45 font-medium truncate">
                  @{safeRender(creator.username)}
                </span>
              )}
              {creator.followersCount > 0 && (
                <span className="text-[10px] text-white/35 font-medium whitespace-nowrap">
                  {creator.username && creator.username !== creator.displayName ? '·' : ''} {creator.followersCount.toLocaleString()} {creator.followersCount === 1 ? 'follower' : 'followers'}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* BOTTOM CONTEXT — priority: 1) tip goal  2) stream title  3) fallback meta */}
        {isLive && goalProgress ? (
          <div className="mt-2.5 pt-2 border-t border-white/[0.06]">
            <div className="flex items-center justify-between gap-2 mb-1">
              <span className="text-[10px] text-white/60 font-bold truncate flex-1">
                🎯 {safeRender(goalProgress.title)}
              </span>
              <span className="text-[10px] text-white/40 font-semibold whitespace-nowrap tabular-nums">
                {goalProgress.current.toLocaleString()} / {goalProgress.target.toLocaleString()} tokens
              </span>
            </div>
            <div className="w-full h-[5px] bg-white/[0.08] rounded-full overflow-hidden">
              <div 
                className="h-full bg-gradient-to-r from-red-500 via-orange-400 to-amber-400 rounded-full transition-all duration-700 ease-out shadow-[0_0_4px_rgba(251,146,60,0.2)]" 
                style={{ width: `${goalProgress.percent}%` }}
              />
            </div>
          </div>
        ) : isLive ? (
          <div className="mt-2.5 pt-2 border-t border-white/[0.06]">
            {creator.activeStream?.description ? (
              <p className="text-[11px] text-white/65 font-semibold leading-snug line-clamp-2">
                {safeRender(creator.activeStream.description)}
              </p>
            ) : (
              <p className="text-[11px] text-white/55 font-medium leading-relaxed">
                {(() => {
                  const parts: string[] = [];
                  if (creator.isPaid) {
                    parts.push(creator.admissionPrice ? `${creator.admissionPrice} tokens` : 'Paid stream');
                  } else {
                    parts.push('Free stream');
                  }
                  if (creator.streamCategory) parts.push(creator.streamCategory);
                  if (liveDuration) parts.push(`Live ${liveDuration}`);
                  if (creator.viewerCount && creator.viewerCount > 0) parts.push(`${creator.viewerCount} ${creator.viewerCount === 1 ? 'viewer' : 'viewers'}`);
                  return parts.join(' • ');
                })()}
              </p>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
});

(CreatorCard as any).Skeleton = CreatorCardSkeleton;

export default CreatorCard as React.FC<CreatorCardProps> & { Skeleton: React.FC<{ variant?: 'featured' | 'explore' }> };
