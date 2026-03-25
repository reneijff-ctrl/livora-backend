import React from 'react';
import TipOverlayManager from "@/components/live/TipOverlayManager";
import TokenCounterExplosion from '@/components/TokenCounterExplosion';
import LegendaryEffectOverlay from '@/components/live/LegendaryEffectOverlay';

interface LiveTipOverlaysProps {
  creatorId: any;
  roomId: any;
  overlayRef: React.RefObject<any>;
  tokenExplosion: { amount: number; key: number };
  legendaryEffect: {
    username: string;
    amount: number;
    isVisible: boolean;
    effectType: string;
  };
}

const LiveTipOverlays: React.FC<LiveTipOverlaysProps> = ({
  creatorId,
  roomId,
  overlayRef,
  tokenExplosion,
  legendaryEffect
}) => {
  return (
    <>
      {/* Token Counter Explosion */}
      <TokenCounterExplosion 
        tokenAmount={tokenExplosion.amount} 
        animationKey={tokenExplosion.key} 
      />

      {/* Tip Overlay - Stays mounted to preserve animation queue */}
      <TipOverlayManager ref={overlayRef} creatorId={creatorId} streamId={roomId} />

      {/* Legendary Effect Overlay */}
      <LegendaryEffectOverlay 
        username={legendaryEffect.username}
        amount={legendaryEffect.amount}
        isVisible={legendaryEffect.isVisible}
        effectType={legendaryEffect.effectType}
      />
    </>
  );
};

export default React.memo(LiveTipOverlays);
