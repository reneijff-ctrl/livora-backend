import React, { useState } from 'react';

// ─── helpers ────────────────────────────────────────────────────────────────

const GRADIENTS = [
  'from-violet-600 to-indigo-600',
  'from-emerald-600 to-teal-600',
  'from-rose-600 to-pink-600',
  'from-amber-600 to-orange-600',
  'from-sky-600 to-blue-600',
];

function pickGradient(seed: string | number): string {
  const n =
    typeof seed === 'number'
      ? seed
      : seed.split('').reduce((a, c) => a + c.charCodeAt(0), 0);
  return GRADIENTS[n % GRADIENTS.length];
}

function getInitials(name: string): string {
  return (name || '?')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('') || '?';
}

// ─── size presets ────────────────────────────────────────────────────────────

const SIZE: Record<string, { outer: string; text: string }> = {
  sm:  { outer: 'w-8 h-8',   text: 'text-xs' },
  md:  { outer: 'w-10 h-10', text: 'text-sm' },
  lg:  { outer: 'w-14 h-14', text: 'text-base' },
  xl:  { outer: 'w-20 h-20', text: 'text-xl' },
  '2xl': { outer: 'w-28 h-28', text: 'text-3xl' },
};

// ─── component ───────────────────────────────────────────────────────────────

interface AdminAvatarProps {
  /** Display name — used for initials and alt text */
  name: string;
  /** Optional real profile photo URL */
  src?: string | null;
  /** Seed for deterministic gradient colour (defaults to name) */
  seed?: string | number;
  /** Size preset: sm | md | lg | xl | 2xl  (default: md) */
  size?: keyof typeof SIZE;
  /** Extra Tailwind classes on the wrapper */
  className?: string;
  /** Rounded style — default is rounded-xl */
  rounded?: string;
}

const AdminAvatar: React.FC<AdminAvatarProps> = ({
  name,
  src,
  seed,
  size = 'md',
  className = '',
  rounded = 'rounded-xl',
}) => {
  const [imgError, setImgError] = useState(false);
  const { outer, text } = SIZE[size] ?? SIZE.md;
  const gradient = pickGradient(seed ?? name);
  const initials = getInitials(name);
  const showImage = !!src && !imgError;

  return (
    <div
      className={[
        'relative shrink-0 overflow-hidden',
        'bg-gradient-to-br',
        gradient,
        outer,
        rounded,
        // subtle glow matching gradient colour
        'ring-1 ring-white/10',
        'shadow-[0_0_12px_rgba(0,0,0,0.5)]',
        // hover: scale + brightness
        'transition-transform duration-200 ease-out',
        'hover:scale-105 hover:brightness-110',
        className,
      ].join(' ')}
    >
      {showImage ? (
        <img
          src={src!}
          alt={name}
          className="w-full h-full object-cover"
          onError={() => setImgError(true)}
        />
      ) : (
        <span
          className={[
            'absolute inset-0 flex items-center justify-center',
            'font-bold text-white/90 select-none',
            text,
          ].join(' ')}
        >
          {initials}
        </span>
      )}
    </div>
  );
};

export default AdminAvatar;
export { pickGradient, getInitials };
