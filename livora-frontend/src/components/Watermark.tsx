import React from 'react';

/**
 * Watermark - A subtle, floating watermark overlay for the video stream.
 * Optimized for performance using GPU-accelerated transforms and will-change.
 */
const Watermark: React.FC = () => {
  return (
    <div 
      className="absolute top-[20px] right-[20px] z-10 pointer-events-none opacity-[0.12] select-none will-change-transform watermark-float"
      aria-hidden="true"
    >
      <img 
        src="/icoon_joinlivora.png" 
        alt="" 
        className="w-20 md:w-32 h-auto object-contain watermark-neon"
      />
    </div>
  );
};

export default React.memo(Watermark);
