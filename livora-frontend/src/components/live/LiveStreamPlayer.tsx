import React, { useRef } from 'react';
import { useWebRTCStream } from '@/hooks/useWebRTCStream';
import Watermark from '@/components/Watermark';

interface LiveStreamPlayerProps {
  creatorId: number | undefined;
  roomId: string | undefined;
  streamId: string;
  user: any;
  availability: "ONLINE" | "LIVE" | "OFFLINE" | null;
  hasAccess: boolean | null;
  room: any;
  error: string | null;
  loading: boolean;
  needsInteraction: boolean;
  setHasAccess: (access: boolean) => void;
  setAvailability: (availability: "ONLINE" | "LIVE" | "OFFLINE" | null) => void;
  setError: (error: string | null) => void;
  setLoading: (loading: boolean) => void;
  setNeedsInteraction: (needs: boolean) => void;
  handleUnlock: () => void;
  onProfileOpen: (username: string) => void;
  identifier: string;
  navigate: (path: string) => void;
  videoWidth: number;
  isResizing: boolean;
  containerRef: React.RefObject<HTMLDivElement>;
  children?: React.ReactNode;
}

const LiveStreamPlayer: React.FC<LiveStreamPlayerProps> = ({
  creatorId,
  roomId,
  streamId,
  user,
  availability,
  hasAccess,
  room,
  error,
  loading,
  needsInteraction,
  setHasAccess,
  setAvailability,
  setError,
  setLoading,
  setNeedsInteraction,
  handleUnlock,
  identifier,
  navigate,
  videoWidth,
  isResizing,
  containerRef,
  children
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);

  useWebRTCStream({
    creatorId,
    streamId,
    user,
    availability,
    hasAccess,
    room,
    error,
    videoRef,
    setHasAccess,
    setAvailability,
    setError,
    setLoading,
    setNeedsInteraction
  });

  return (
    <div className="flex-1 flex items-center justify-center min-w-0 bg-[#050505] overflow-hidden shadow-[inset_0_0_100px_rgba(0,0,0,0.8)]">
      <div 
        ref={containerRef}
        style={{ 
          width: `${videoWidth}px`, 
          height: `${(videoWidth * 9) / 16}px`,
          maxWidth: '100%',
          maxHeight: '100%'
        }}
        className={`relative bg-black rounded-2xl overflow-hidden shadow-2xl border border-white/5 ${isResizing ? '' : 'transition-[width,height] duration-75 ease-out'} group/video`}
      >
        {/* Always mount video element once connected/available */}
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          className={`w-full h-full object-cover ${availability === 'LIVE' && hasAccess === true ? 'block' : 'hidden'}`}
        />

        {/* Error Overlay (General) */}
        {error && (
          <div className="absolute top-20 left-1/2 -translate-x-1/2 bg-red-600/90 text-white px-4 py-2 rounded-full text-xs font-bold z-[100] backdrop-blur-sm shadow-lg flex items-center gap-2">
            <span>⚠️</span> {error}
            <button onClick={() => setError(null)} className="ml-2 hover:bg-black/20 rounded-full p-1 leading-none">×</button>
          </div>
        )}

        {/* Watermark */}
        <Watermark />

        {/* This allows other overlays (leaderboard, tips, etc.) to be rendered inside the same container if passed as children */}
        {children}

        {/* Interaction Overlay */}
        {availability === 'LIVE' && hasAccess === true && needsInteraction && (
          <button
            className="absolute inset-0 flex items-center justify-center bg-black/50 text-white z-30 backdrop-blur-sm"
            onClick={() => {
              if (videoRef.current) {
                videoRef.current.muted = false;
                videoRef.current.play();
                setNeedsInteraction(false);
              }
            }}
          >
            <div className="flex flex-col items-center gap-4">
              <div className="w-16 h-16 bg-white/20 rounded-full flex items-center justify-center animate-pulse">
                <span className="text-2xl">▶</span>
              </div>
              <span className="font-bold tracking-widest uppercase text-sm">Click to Start Stream</span>
            </div>
          </button>
        )}

        {/* Locked Screen Overlay */}
        {availability === 'LIVE' && hasAccess === false && (
          <div className="absolute inset-0 flex flex-col items-center justify-center p-8 text-center bg-zinc-900 z-10">
            <div className="w-20 h-20 bg-zinc-800 rounded-full flex items-center justify-center mb-6 border border-[#16161D] shadow-xl">
              <span className="text-3xl">🔒</span>
            </div>
            <h3 className="text-2xl font-bold mb-3 text-white">Private Broadcast</h3>
            <p className="text-zinc-400 mb-8 max-w-sm leading-relaxed text-sm">
              This stream is exclusive. Unlock access to join the conversation and watch the live broadcast.
            </p>
            <button 
              onClick={handleUnlock}
              disabled={loading}
              className="px-10 py-4 bg-white text-black rounded-full font-bold hover:bg-zinc-200 transition-all transform hover:scale-105 active:scale-95 shadow-lg disabled:bg-zinc-400"
            >
              {loading ? 'Unlocking...' : `Unlock Stream (${room?.admissionPrice} Tokens)`}
            </button>
          </div>
        )}

        {/* Offline/Away Screen Overlay */}
        {availability !== 'LIVE' && (
          <div className="absolute inset-0 flex items-center justify-center text-center p-8 bg-zinc-900/50 z-10">
            <div className="max-w-md">
              <p className="text-xl font-bold mb-2 text-white">
                {availability === 'OFFLINE' ? 'Creator is currently offline.' : 'Creator stepped away'}
              </p>
              <p className="text-zinc-500 text-sm italic mb-6">
                {availability === 'OFFLINE' ? 'Visit the profile to follow and get notified.' : 'Please stay tuned.'}
              </p>
              {availability === 'OFFLINE' && (
                <button 
                  onClick={() => navigate(`/creators/${identifier}`)} 
                  className="px-8 py-3 bg-white text-black rounded-full font-bold hover:bg-zinc-200 transition transform hover:scale-105"
                >
                  View Profile
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default React.memo(LiveStreamPlayer);
