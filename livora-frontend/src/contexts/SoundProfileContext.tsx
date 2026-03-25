import React, { createContext, useContext, useState, useCallback, useMemo, ReactNode, useEffect } from 'react';
import { spatialSoundEngine } from '@/utils/SpatialSoundEngine';
import { soundEngine } from '@/utils/SoundEngine';

/**
 * SoundProfile - Available auditory themes for the platform.
 * cinematic: High-impact, immersive effects.
 * arcade: Retro, bleepy, energetic effects.
 * minimal: Subtle, clean, unobtrusive clicks and pops.
 * luxury: Sophisticated, smooth, premium soundscape.
 */
export type SoundProfile = "cinematic" | "arcade" | "minimal" | "luxury";

interface SoundProfileContextType {
  profile: SoundProfile;
  changeProfile: (newProfile: SoundProfile) => void;
}

const SoundProfileContext = createContext<SoundProfileContextType | undefined>(undefined);

const LOCAL_STORAGE_KEY = 'livora-sound-profile';

/**
 * SoundProfileProvider - Manages the global sound theme for interaction effects.
 * Persists the selected profile to localStorage for session continuity.
 */
export const SoundProfileProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [profile, setProfile] = useState<SoundProfile>(() => {
    if (typeof window === 'undefined') return "cinematic";
    const saved = localStorage.getItem(LOCAL_STORAGE_KEY);
    if (saved && ["cinematic", "arcade", "minimal", "luxury"].includes(saved)) {
      return saved as SoundProfile;
    }
    return "cinematic"; // Default to cinematic
  });

  const changeProfile = useCallback((newProfile: SoundProfile) => {
    setProfile(newProfile);
    localStorage.setItem(LOCAL_STORAGE_KEY, newProfile);
    // Update audio engines
    spatialSoundEngine.setProfile(newProfile);
    soundEngine.setProfile(newProfile);
  }, []);

  // Sync initial profile with audio engines on mount
  useEffect(() => {
    spatialSoundEngine.setProfile(profile);
    soundEngine.setProfile(profile);
  }, []);

  // Memoize value to prevent unnecessary re-renders of consumers
  const value = useMemo(() => ({
    profile,
    changeProfile
  }), [profile, changeProfile]);

  return (
    <SoundProfileContext.Provider value={value}>
      {children}
    </SoundProfileContext.Provider>
  );
};

/**
 * useSoundProfile - Custom hook to access the sound profile context.
 */
export const useSoundProfile = () => {
  const context = useContext(SoundProfileContext);
  if (context === undefined) {
    throw new Error('useSoundProfile must be used within a SoundProfileProvider');
  }
  return context;
};
