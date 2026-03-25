/**
 * Feature Flags configuration for the Livora frontend.
 */
export const FEATURE_FLAGS = {
  // Enables/Disables token support navigation
  SUPPORT_TOKENS: import.meta.env.VITE_SUPPORT_TOKENS === 'true',
};
