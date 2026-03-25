import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import apiClient from '@/api/apiClient';
import { getAccessToken } from '@/auth/jwt';

interface LiveVideoPlayerProps {
  src: string;
  streamId?: string;
  autoPlay?: boolean;
  muted?: boolean;
}

const LiveVideoPlayer: React.FC<LiveVideoPlayerProps> = ({ 
  src, 
  streamId,
  autoPlay = true, 
  muted = false 
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!src || !videoRef.current) return;

    const video = videoRef.current;

    const initHls = () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }

      if (Hls.isSupported()) {
        const hls = new Hls({
          xhrSetup: (xhr) => {
            const t = getAccessToken();
            if (t) {
              xhr.setRequestHeader('Authorization', `Bearer ${t}`);
            }
          },
          // Retry logic
          manifestLoadingMaxRetry: 10,
          manifestLoadingRetryDelay: 1000,
          levelLoadingMaxRetry: 5,
          levelLoadingRetryDelay: 1000,
          fragLoadingMaxRetry: 5,
          fragLoadingRetryDelay: 1000,
        });

        hls.loadSource(src);
        hls.attachMedia(video);

        hls.on(Hls.Events.ERROR, (_event, data) => {
          console.error('HLS Error:', data);
          
          // Report error to backend monitoring
          if (streamId) {
            apiClient.post('/auth/monitoring/playback-error', {
              streamId,
              errorType: data.details || data.type,
              detail: data.fatal ? 'FATAL' : 'NON-FATAL'
            }, {
              headers: { 'Content-Type': 'application/json' },
              // @ts-ignore
              _skipToast: true
            }).catch(e => console.error('Failed to report playback error', e));
          }

          if (data.fatal) {
            switch (data.type) {
              case Hls.ErrorTypes.NETWORK_ERROR:
                console.log('Fatal network error encountered, trying to recover');
                hls.startLoad();
                break;
              case Hls.ErrorTypes.MEDIA_ERROR:
                console.log('Fatal media error encountered, trying to recover');
                hls.recoverMediaError();
                break;
              default:
                console.log('Fatal error, destroying and re-initializing');
                setError('Stream error occurred. Retrying...');
                setTimeout(initHls, 3000);
                break;
            }
          }
        });

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          setError(null);
          if (autoPlay) {
            video.play().catch(e => console.error("Autoplay failed:", e));
          }
        });

        hlsRef.current = hls;
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        // Fallback for Safari
        video.src = src;
        video.addEventListener('loadedmetadata', () => {
          if (autoPlay) {
            video.play().catch(e => console.error("Autoplay failed:", e));
          }
        });
        
        video.onerror = () => {
          console.error('Video element error');
          setError('Stream error. Retrying...');
          setTimeout(() => {
            video.src = src;
            video.load();
          }, 3000);
        };
      } else {
        setError('HLS is not supported in this browser.');
      }
    };

    initHls();

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [src, autoPlay]);

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', backgroundColor: '#000' }}>
      <video
        ref={videoRef}
        muted={muted}
        playsInline
        style={{ width: '100%', height: '100%', objectFit: 'contain' }}
      />
      {error && (
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: 'rgba(0,0,0,0.5)',
          color: '#fff',
          zIndex: 5
        }}>
          {error}
        </div>
      )}
    </div>
  );
};

export default LiveVideoPlayer;
