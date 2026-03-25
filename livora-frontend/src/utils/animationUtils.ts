/**
 * Unified tip tier resolver. All components must use this single source of truth.
 *
 * Tiers:
 *   common    → 1–49 tokens
 *   rare      → 50–199 tokens
 *   epic      → 200–499 tokens
 *   legendary → 500+ tokens
 */
export type TipTier = 'common' | 'rare' | 'epic' | 'legendary';

export function resolveTipTier(amount: number): TipTier {
  if (amount >= 500) return 'legendary';
  if (amount >= 200) return 'epic';
  if (amount >= 50) return 'rare';
  return 'common';
}

/**
 * Resolves the animation type string based on the tip amount.
 *
 * Rules:
 *   common    → small floating hearts
 *   rare      → golden coin burst
 *   epic      → fireworks effect
 *   legendary → mega explosion + username banner
 */
export const resolveAnimationByAmount = (amount: number): string => {
  const tier = resolveTipTier(amount);
  switch (tier) {
    case 'legendary': return 'mega-explosion';
    case 'epic': return 'fireworks';
    case 'rare': return 'golden-coin-burst';
    default: return 'small-hearts';
  }
};

/** @deprecated Use resolveTipTier() instead */
export const resolveRarityByAmount = resolveTipTier;

/**
 * Maps backend animation names (from TipNotificationService.getAnimationForAmount)
 * to frontend CSS animation class keys.
 */
export const BACKEND_ANIMATION_MAP: Record<string, string> = {
  coin: 'golden-coin-burst',
  heart: 'small-hearts',
  diamond: 'mega-explosion',
  fireworks: 'fireworks'
};

/**
 * Resolves a backend or gift animationType to a canonical frontend animation key.
 * Falls back to the input if no mapping exists.
 */
export function resolveAnimationType(backendType?: string): string {
  if (!backendType) return 'small-hearts';
  return BACKEND_ANIMATION_MAP[backendType] || backendType;
}
