import React, { useEffect, useState, useRef } from 'react';
import { Transport, Producer } from 'mediasoup-client';

interface StreamHealthWidgetProps {
  isLive: boolean;
  viewerCount: number;
  sendTransport: React.RefObject<Transport | null>;
  producers: React.RefObject<Map<string, Producer>>;
}

type TransportStatus = 'connected' | 'connecting' | 'disconnected' | 'failed' | 'new' | 'closed';
type HealthScore = 'GOOD' | 'WARNING' | 'POOR' | 'OFFLINE';

const StreamHealthWidget: React.FC<StreamHealthWidgetProps> = ({ isLive, viewerCount, sendTransport, producers }) => {
  const [bitrate, setBitrate] = useState(0);
  const [transportStatus, setTransportStatus] = useState<TransportStatus>('disconnected');
  const [healthScore, setHealthScore] = useState<HealthScore>('OFFLINE');
  const prevBytesSent = useRef(0);
  const prevTimestamp = useRef(0);

  useEffect(() => {
    if (!isLive) {
      setBitrate(0);
      setTransportStatus('disconnected');
      setHealthScore('OFFLINE');
      prevBytesSent.current = 0;
      prevTimestamp.current = 0;
      return;
    }

    const updateStats = async () => {
      // Transport status
      const transport = sendTransport.current;
      if (transport) {
        const state = (transport.connectionState || 'disconnected') as TransportStatus;
        setTransportStatus(state);
      } else {
        setTransportStatus('disconnected');
      }

      // Bitrate from video producer stats
      const videoProducer = producers.current?.get('video');
      if (videoProducer && !videoProducer.closed) {
        try {
          const stats = await videoProducer.getStats();
          let totalBytesSent = 0;
          let timestamp = 0;
          stats.forEach((report: any) => {
            if (report.type === 'outbound-rtp' && report.kind === 'video') {
              totalBytesSent += report.bytesSent || 0;
              timestamp = report.timestamp;
            }
          });

          if (prevBytesSent.current > 0 && prevTimestamp.current > 0 && timestamp > prevTimestamp.current) {
            const deltaBytes = totalBytesSent - prevBytesSent.current;
            const deltaTime = (timestamp - prevTimestamp.current) / 1000; // seconds
            const kbps = Math.round((deltaBytes * 8) / deltaTime / 1000);
            setBitrate(kbps > 0 ? kbps : 0);
          }
          prevBytesSent.current = totalBytesSent;
          prevTimestamp.current = timestamp;
        } catch {
          // Producer may have been closed
        }
      }
    };

    updateStats();
    const interval = setInterval(updateStats, 3000);
    return () => clearInterval(interval);
  }, [isLive, sendTransport, producers]);

  // Calculate health score
  useEffect(() => {
    if (!isLive) {
      setHealthScore('OFFLINE');
      return;
    }
    if (transportStatus === 'failed') {
      setHealthScore('POOR');
    } else if (bitrate > 0 && bitrate < 500) {
      setHealthScore('WARNING');
    } else if (bitrate >= 500) {
      setHealthScore('GOOD');
    } else {
      setHealthScore('WARNING');
    }
  }, [isLive, transportStatus, bitrate]);

  const statusColor = (status: TransportStatus) => {
    switch (status) {
      case 'connected': return 'text-green-400';
      case 'connecting': case 'new': return 'text-yellow-400';
      case 'failed': return 'text-red-400';
      default: return 'text-zinc-500';
    }
  };

  const statusDot = (status: TransportStatus) => {
    switch (status) {
      case 'connected': return 'bg-green-400 shadow-green-400/50';
      case 'connecting': case 'new': return 'bg-yellow-400 shadow-yellow-400/50';
      case 'failed': return 'bg-red-400 shadow-red-400/50';
      default: return 'bg-zinc-500';
    }
  };

  const healthColor = (score: HealthScore) => {
    switch (score) {
      case 'GOOD': return 'text-green-400';
      case 'WARNING': return 'text-yellow-400';
      case 'POOR': return 'text-red-400';
      default: return 'text-zinc-500';
    }
  };

  const healthBg = (score: HealthScore) => {
    switch (score) {
      case 'GOOD': return 'from-green-500/10 to-transparent border-green-500/20';
      case 'WARNING': return 'from-yellow-500/10 to-transparent border-yellow-500/20';
      case 'POOR': return 'from-red-500/10 to-transparent border-red-500/20';
      default: return 'from-zinc-500/10 to-transparent border-zinc-500/20';
    }
  };

  return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl p-6">
      <h2 className="font-bold text-zinc-100 mb-4 text-sm uppercase tracking-widest">Stream Health</h2>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
        {/* Viewer Count */}
        <div className="bg-white/5 border border-white/5 rounded-xl p-4">
          <p className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-2">Viewers</p>
          <p className="text-2xl font-bold text-white">{isLive ? viewerCount : 0}</p>
          <p className="text-xs text-zinc-500 mt-1">{isLive ? 'watching now' : 'offline'}</p>
        </div>

        {/* Bitrate */}
        <div className="bg-white/5 border border-white/5 rounded-xl p-4">
          <p className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-2">Bitrate</p>
          <p className="text-2xl font-bold text-white">{isLive ? bitrate : 0} <span className="text-sm font-normal text-zinc-400">kbps</span></p>
          <p className="text-xs text-zinc-500 mt-1">{isLive && bitrate >= 500 ? 'stable' : isLive && bitrate > 0 ? 'low' : isLive ? 'measuring...' : 'offline'}</p>
        </div>

        {/* Transport Status */}
        <div className="bg-white/5 border border-white/5 rounded-xl p-4">
          <p className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-2">Transport</p>
          <div className="flex items-center gap-2">
            <div className={`w-2.5 h-2.5 rounded-full shadow-lg ${statusDot(isLive ? transportStatus : 'disconnected')}`} />
            <p className={`text-lg font-semibold capitalize ${statusColor(isLive ? transportStatus : 'disconnected')}`}>
              {isLive ? transportStatus : 'disconnected'}
            </p>
          </div>
          <p className="text-xs text-zinc-500 mt-1">WebRTC connection</p>
        </div>

        {/* Health Score */}
        <div className={`bg-gradient-to-br ${healthBg(healthScore)} border rounded-xl p-4`}>
          <p className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em] mb-2">Health</p>
          <p className={`text-2xl font-bold ${healthColor(healthScore)}`}>{healthScore}</p>
          <p className="text-xs text-zinc-500 mt-1">overall quality</p>
        </div>
      </div>
    </div>
  );
};

export default StreamHealthWidget;
