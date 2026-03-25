import React from 'react';
import { Link } from 'react-router-dom';
import ImageWithFallback from '@/components/ImageWithFallback';

interface LogoProps {
  size?: number; // height in px
  maxWidth?: string | number;
  clickable?: boolean;
  glow?: boolean;
  opacity?: number;
  ariaLabel?: string;
  style?: React.CSSProperties;
  className?: string;
}

/**
 * Reusable JoinLivora Logo component.
 * - Props:
 *   - size: number (default 40) — controls rendered height
 *   - clickable: boolean — when true, wraps logo in a Link to "/"
 * - No background container; optimized for sharp look on dark backgrounds.
 */
const Logo: React.FC<LogoProps> = ({ 
  size = 40, 
  maxWidth,
  clickable = false, 
  glow = true, 
  opacity = 1, 
  ariaLabel = 'JoinLivora',
  style = {},
  className
}) => {
  const image = (
    <ImageWithFallback
      src="/logo.png"
      alt="JoinLivora"
      className={className}
      style={{
        height: className ? undefined : size,
        maxWidth: maxWidth,
        width: 'auto',
        display: 'block',
        opacity: opacity,
        objectFit: 'contain',
        borderRadius: (size && size > 40) ? 12 : 4, // Subtle rounding if it's not transparent
        // Soft neon glow to keep it sharp and readable on dark backgrounds
        filter: glow ? 'drop-shadow(0 0 12px rgba(168,85,247,0.35))' : 'none',
        ...style,
      }}
      fallback={
        <span
          style={{
            display: 'inline-block',
            fontWeight: 800,
            letterSpacing: '-0.025em',
            fontSize: Math.max(12, Math.round(size * 0.8)),
            backgroundImage: 'var(--gradient-primary)',
            WebkitBackgroundClip: 'text',
            backgroundClip: 'text',
            color: 'transparent',
            lineHeight: 1,
          }}
        >
          JoinLivora
        </span>
      }
    />
  );

  if (clickable) {
    return (
      <Link
        to="/"
        aria-label={ariaLabel}
        className={className}
        style={{ 
          display: 'inline-flex', 
          alignItems: 'center', 
          height: className ? 'auto' : size 
        }}
      >
        {image}
      </Link>
    );
  }

  return image;
};

export default Logo;
