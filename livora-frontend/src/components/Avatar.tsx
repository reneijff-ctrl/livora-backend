import React from 'react';
import ImageWithFallback from './ImageWithFallback';

interface AvatarProps {
  src?: string | null;
  alt: string;
  size: number;
  initials?: string;
  className?: string;
}

const Avatar: React.FC<AvatarProps> = ({ src, alt, size, initials, className = '' }) => {
  const fallback = (
    <div className="flex h-full w-full items-center justify-center bg-zinc-900 text-zinc-300 font-black" style={{ fontSize: `${size * 0.3}px` }}>
      {initials || (alt ? alt.charAt(0).toUpperCase() : '?')}
    </div>
  );

  return (
    <div 
      className={`shrink-0 overflow-hidden rounded-2xl bg-gradient-to-tr from-purple-600 to-pink-600 shadow-[0_6px_18px_rgba(0,0,0,0.4)] ${className}`}
      style={{ width: size, height: size }}
    >
      <ImageWithFallback
        src={src || undefined}
        alt={alt}
        fallback={fallback}
        className="h-full w-full object-cover"
        draggable={false}
      />
    </div>
  );
};

export default Avatar;
