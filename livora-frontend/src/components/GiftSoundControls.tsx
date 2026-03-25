import React, { useState, useCallback } from 'react';
import { spatialSoundEngine } from '@/utils/SpatialSoundEngine';

interface GiftSoundControlsProps {
  isStatic?: boolean;
}

/**
 * GiftSoundControls - A small floating control panel for gift audio settings.
 * Features a glassmorphism aesthetic, volume slider, and mute toggle.
 * Optimized with React.memo to prevent unnecessary re-renders.
 */
const GiftSoundControls: React.FC<GiftSoundControlsProps> = ({ isStatic = false }) => {
  const [volume, setVolume] = useState(() => Math.round(spatialSoundEngine.getVolume() * 100));
  const [isMuted, setIsMuted] = useState(() => spatialSoundEngine.getIsMuted());

  const handleVolumeChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const newVolume = parseInt(e.target.value, 10);
    setVolume(newVolume);
    spatialSoundEngine.setVolume(newVolume / 100);
    
    // Auto-unmute when sliding if it was muted and we are increasing volume
    if (isMuted && newVolume > 0) {
      setIsMuted(false);
      spatialSoundEngine.mute(false);
    }
  }, [isMuted]);

  const toggleMute = useCallback(() => {
    const newMuted = !isMuted;
    setIsMuted(newMuted);
    spatialSoundEngine.mute(newMuted);
  }, [isMuted]);

  const containerClasses = isStatic 
    ? "relative z-30 pointer-events-auto select-none w-full" 
    : "absolute bottom-[20px] left-[20px] z-30 pointer-events-auto select-none";

  const panelClasses = isStatic
    ? "flex items-center gap-3 px-4 py-3 bg-white/5 border border-white/10 rounded-xl transition-all duration-300"
    : "flex items-center gap-2 px-3 py-1.5 bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl shadow-2xl transition-all duration-300 hover:bg-black/60 group/panel";

  const sliderContainerClasses = isStatic
    ? "flex items-center gap-2 flex-1"
    : "flex items-center gap-2 w-0 group-hover/panel:w-20 overflow-hidden transition-all duration-300 opacity-0 group-hover/panel:opacity-100";

  return (
    <div className={containerClasses}>
      <div className={panelClasses}>
        {/* Mute Toggle */}
        <button 
          onClick={toggleMute}
          className={`p-1.5 rounded-full transition-all active:scale-90 ${
            isMuted ? 'text-red-500 bg-red-500/10' : 'text-white/60 hover:text-white hover:bg-white/10'
          }`}
          title={isMuted ? "Unmute" : "Mute"}
        >
          {isMuted ? (
             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2" />
             </svg>
          ) : (
             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
             </svg>
          )}
        </button>

        {/* Volume Slider Container */}
        <div className={sliderContainerClasses}>
           <input
            type="range"
            min="0"
            max="100"
            value={isMuted ? 0 : volume}
            onChange={handleVolumeChange}
            className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:bg-purple-500 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:shadow-lg [&::-webkit-slider-thumb]:shadow-purple-500/40"
          />
        </div>
        
        {/* Label */}
        <div className={isStatic ? "flex items-center" : "hidden group-hover/panel:flex items-center"}>
          <span className="text-xs text-purple-400 tabular-nums">
            {isMuted ? '0%' : `${volume}%`}
          </span>
        </div>
      </div>
    </div>
  );
};

GiftSoundControls.displayName = 'GiftSoundControls';

export default React.memo(GiftSoundControls);
