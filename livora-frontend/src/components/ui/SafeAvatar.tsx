import React, { useState } from 'react';

interface SafeAvatarProps {
  size: number;
  src?: string | null;
  name: string;
  className?: string;
}

/**
 * SafeAvatar component that gracefully handles missing or broken images.
 * Renders initials as a fallback.
 */
const SafeAvatar: React.FC<SafeAvatarProps> = ({ size, src, name, className = "" }) => {
  const [hasError, setHasError] = useState(false);

  // Get first letter of name or '?' if name is empty
  const initials = (name?.trim() || '?').charAt(0).toUpperCase();

  // Dynamic styles for the container and text
  const containerStyle = {
    width: `${size}px`,
    height: `${size}px`,
    minWidth: `${size}px`,
    minHeight: `${size}px`,
  };

  const textStyle = {
    fontSize: `${Math.max(12, Math.floor(size * 0.35))}px`,
  };

  return (
    <div 
      className={`rounded-2xl bg-gradient-to-br from-zinc-100 to-zinc-200 flex items-center justify-center overflow-hidden flex-shrink-0 border border-zinc-100/50 ${className}`}
      style={containerStyle}
    >
      {src && !hasError ? (
        <img
          src={src}
          alt={name}
          className="w-full h-full object-cover"
          onError={() => setHasError(true)}
        />
      ) : (
        <div className="flex flex-col items-center gap-1">
          <span 
            className="font-bold text-zinc-400 select-none"
            style={textStyle}
          >
            {initials}
          </span>
          <div 
            className="bg-zinc-300/30 rounded-full" 
            style={{ width: `${size * 0.2}px`, height: '2px' }}
          />
        </div>
      )}
    </div>
  );
};

export default SafeAvatar;
