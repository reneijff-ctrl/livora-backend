// Centralized design tokens and helpers for the Livora dark theme
export const colors = {
  background: '#08080A',
  cardBg: '#0F0F14',
  accentPurple: '#A855F7',
  accentPink: '#EC4899',
  textPrimary: '#F4F4F5',
  textSecondary: '#71717A',
  border: 'rgba(255, 255, 255, 0.06)',
};

export const gradients = {
  primary: 'linear-gradient(135deg, #A855F7 0%, #EC4899 100%)',
  subtle: 'linear-gradient(180deg, rgba(168,85,247,0.08) 0%, rgba(236,72,153,0.06) 100%)',
};

export const shadow = {
  soft: '0 20px 60px rgba(0,0,0,0.6)',
  ring: `0 0 0 1px ${colors.border}`,
};

export const radii = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  full: 9999,
};

export const placeholders = {
  avatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=200&h=200&auto=format&fit=crop',
  banner: 'https://images.unsplash.com/photo-1614850523296-d8c1af93d400?q=80&w=1200&h=400&auto=format&fit=crop',
  post: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800&h=600&auto=format&fit=crop',
};

export const cardStyle: React.CSSProperties = {
  backgroundColor: colors.cardBg,
  border: `1px solid ${colors.border}`,
  borderRadius: radii.lg,
  boxShadow: shadow.soft,
};

export const button = {
  primary: (disabled = false): React.CSSProperties => ({
    background: gradients.primary,
    color: colors.textPrimary,
    padding: '10px 16px',
    borderRadius: radii.md,
    opacity: disabled ? 0.6 : 1,
  }),
  outline: (): React.CSSProperties => ({
    background: 'transparent',
    color: colors.textPrimary,
    padding: '10px 16px',
    borderRadius: radii.md,
    border: `1px solid ${colors.accentPurple}`,
  }),
};

export const linkStyle: React.CSSProperties = {
  color: colors.textPrimary,
  opacity: 0.9,
};
