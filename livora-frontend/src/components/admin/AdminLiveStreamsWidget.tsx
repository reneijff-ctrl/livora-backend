import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import adminService from '../../api/adminService';
import { safeRender } from '@/utils/safeRender';
import { webSocketService } from '../../websocket/webSocketService';
import { AdminRealtimeEventDTO, LiveStreamInfo } from '../../types';
import { showToast } from '../Toast';

interface AdminLiveStreamsWidgetProps {
  streams: LiveStreamInfo[];
  setStreams: React.Dispatch<React.SetStateAction<LiveStreamInfo[]>>;
  loading: boolean;
  onRefresh?: () => Promise<void>;
}

const LiveDuration: React.FC<{ startedAt: string }> = ({ startedAt }) => {
  const [, setTick] = useState(0);
  
  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  if (!startedAt) return <>00:00</>;
  
  const start = new Date(startedAt).getTime();
  const now = new Date().getTime();
  const diff = Math.max(0, now - start);
  
  const seconds = Math.floor((diff / 1000) % 60);
  const minutes = Math.floor((diff / (1000 * 60)) % 60);
  const hours = Math.floor(diff / (1000 * 60 * 60));
  
  const pad = (n: number) => n.toString().padStart(2, '0');
  
  if (hours > 0) {
    return <>{pad(hours)}:{pad(minutes)}:{pad(seconds)}</>;
  }
  return <>{pad(minutes)}:{pad(seconds)}</>;
};

const AdminLiveStreamsWidget: React.FC<AdminLiveStreamsWidgetProps> = React.memo(({ streams, setStreams, loading, onRefresh }) => {
  const navigate = useNavigate();
  const [viewerCounts, setViewerCounts] = useState<Record<number, number>>({});
  const [spikes, setSpikes] = useState<Record<number, boolean>>({});
  const subscriptions = useRef<Map<number, () => void>>(new Map());

  const handleEnableSlowMode = useCallback(async (roomId: string) => {
    try {
      await adminService.enableSlowMode(roomId);
      showToast('Slow mode enabled', 'success');
      setStreams(prev => prev.map(s => s.id === roomId ? { ...s, slowMode: true } : s));
    } catch (err) {
      showToast('Failed to enable slow mode', 'error');
    }
  }, [setStreams]);

  const handleDisableSlowMode = useCallback(async (roomId: string) => {
    try {
      await adminService.disableSlowMode(roomId);
      showToast('Slow mode disabled', 'success');
      setStreams(prev => prev.map(s => s.id === roomId ? { ...s, slowMode: false } : s));
    } catch (err) {
      showToast('Failed to disable slow mode', 'error');
    }
  }, [setStreams]);

  const handleMuteUser = useCallback(async (streamId: string, userId: number, username: string) => {
    if (!window.confirm(`Are you sure you want to MUTE ${username}?`)) return;
    try {
      await adminService.muteUser(streamId, userId);
      showToast(`User ${username} muted`, 'success');
    } catch (err) {
      showToast('Failed to mute user', 'error');
    }
  }, []);

  const handleKickUser = useCallback(async (streamId: string, userId: number, username: string) => {
    if (!window.confirm(`Are you sure you want to KICK ${username}?`)) return;
    try {
      await adminService.kickUser(streamId, userId);
      showToast(`User ${username} kicked`, 'success');
    } catch (err) {
      showToast('Failed to kick user', 'error');
    }
  }, []);

  const handleStopStream = useCallback(async (streamId: string, username: string) => {
    if (!window.confirm(`Are you sure you want to FORCE STOP the stream for ${username}?`)) return;
    try {
      await adminService.stopStream(streamId);
      showToast('Stream stopped successfully', 'success');
      // Optimistically remove from list and refresh via parent if possible
      setStreams(prev => prev.filter(s => s.id !== streamId));
      if (onRefresh) await onRefresh();
    } catch (err) {
      showToast('Failed to stop stream', 'error');
      console.error(err);
    }
  }, [onRefresh, setStreams]);

  const subscribeToStream = useCallback((uId: number) => {
    if (!webSocketService.isConnected()) return;
    if (subscriptions.current.has(uId)) return;

    const unsub = webSocketService.subscribe(`/exchange/amq.topic/viewers.${uId}`, (msg) => {
      try {
        const data = JSON.parse(msg.body);
        const payload = data.payload || data;
        if (payload.viewerCount !== undefined) {
          const targetUserId = payload.creatorUserId || uId;
          const newCount = payload.viewerCount;

          setViewerCounts(prev => {
            const prevCount = prev[targetUserId];
            if (prevCount !== undefined) {
              if (newCount - prevCount > 50) {
                setSpikes(s => ({ ...s, [targetUserId]: true }));
              } else {
                setSpikes(s => ({ ...s, [targetUserId]: false }));
              }
            }
            return { ...prev, [targetUserId]: newCount };
          });
        }
      } catch (e) {
        console.error(`Failed to parse viewer count for user ${uId}`, e);
      }
    });
    if (typeof unsub === 'function') {
      subscriptions.current.set(uId, unsub);
    }
  }, []);

  useEffect(() => {
    return () => {
      // Cleanup all viewer subscriptions on unmount
      subscriptions.current.forEach(unsubFn => unsubFn());
      subscriptions.current.clear();
    };
  }, []);

  useEffect(() => {
    const currentStreamUserIds = new Set((streams || []).map(s => s.userId).filter(Boolean));

    // Subscribe to new streams (connection-aware)
    (streams || []).forEach(stream => {
      if (stream.userId) {
        subscribeToStream(stream.userId);
      }
    });

    // Unsubscribe from removed streams
    subscriptions.current.forEach((unsub, uId) => {
      if (!currentStreamUserIds.has(uId)) {
        unsub();
        subscriptions.current.delete(uId);
        setViewerCounts(prev => {
          const next = { ...prev };
          delete next[uId];
          return next;
        });
        setSpikes(prev => {
          const next = { ...prev };
          delete next[uId];
          return next;
        });
      }
    });
  }, [streams, subscribeToStream]);

  // Re-subscribe to all active streams on WS reconnect
  useEffect(() => {
    const off = webSocketService.subscribeStateChange((connected) => {
      if (connected) {
        (streams || []).forEach(stream => {
          if (stream.userId) {
            subscribeToStream(stream.userId);
          }
        });
      }
    });

    return () => off();
  }, [streams, subscribeToStream]);

  return (
    <div className="bg-gradient-to-br from-zinc-900 to-zinc-950 border border-zinc-800/60 rounded-2xl overflow-hidden shadow-2xl mt-8">
      <div className="px-6 py-5 border-b border-zinc-800/60 flex justify-between items-center bg-zinc-900/50">
        <h3 className="text-white font-semibold text-lg flex items-center gap-2">
          Active Live Streams
        </h3>
        <div className="flex items-center gap-2 bg-red-500/10 border border-red-500/20 rounded-full px-3 py-1">
          <div className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(239,68,68,0.6)]"></div>
          <span className="text-red-500 text-[10px] font-bold uppercase tracking-wider">
            {safeRender(streams?.length || 0)} LIVE
          </span>
        </div>
      </div>
      
      <div className="overflow-x-auto">
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center text-zinc-500 italic">
            <svg className="animate-spin h-6 w-6 text-zinc-700 mb-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span className="text-sm font-medium">Loading active streams...</span>
          </div>
        ) : (!streams || streams.length === 0) ? (
          <div className="p-16 flex flex-col items-center justify-center text-zinc-500 bg-zinc-900/10">
            <div className="w-16 h-16 rounded-full bg-zinc-800/20 flex items-center justify-center mb-4 border border-zinc-800/40">
              <svg className="w-8 h-8 text-zinc-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M3 3l18 18" opacity="0.4" />
              </svg>
            </div>
            <p className="text-zinc-400 font-medium">No active streams</p>
            <p className="text-xs text-zinc-600 mt-1 uppercase tracking-widest font-semibold">Monitoring Active</p>
          </div>
        ) : (
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-zinc-950/30 text-zinc-500 text-[10px] uppercase tracking-widest font-bold">
                <th className="px-6 py-4 font-bold">Creator</th>
                <th className="px-6 py-4 font-bold">Viewers</th>
                <th className="px-6 py-4 font-bold">Duration</th>
                <th className="px-6 py-4 font-bold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800/40">
              {(streams || []).map((stream) => (
                <tr key={stream.id} className="hover:bg-zinc-800/20 transition-colors group">
                  <td className="px-6 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-zinc-800 border border-zinc-700 flex items-center justify-center text-zinc-300 text-xs font-bold shadow-inner">
                        {safeRender(stream.username.charAt(0).toUpperCase())}
                      </div>
                      <div className="flex flex-col">
                        <div className="flex items-center gap-2">
                          <span className="text-zinc-200 font-medium group-hover:text-white transition-colors">
                            {safeRender(stream.username)}
                          </span>
                          {stream.fraudRiskScore !== undefined && (
                            <div 
                              className={`w-2 h-2 rounded-full ${
                                stream.fraudRiskScore < 40 ? 'bg-green-500' : 
                                stream.fraudRiskScore <= 70 ? 'bg-orange-500' : 'bg-red-500'
                              }`} 
                              title={`Risk Score: ${stream.fraudRiskScore}`}
                            />
                          )}
                          {spikes[stream.userId] && (
                            <span className="bg-orange-500/10 text-orange-500 text-[10px] px-1.5 py-0.5 rounded-md border border-orange-500/20 font-bold flex items-center gap-1 shadow-[0_0_8px_rgba(249,115,22,0.1)]">
                              <span className="animate-pulse">⚡</span> Viewer Spike
                            </span>
                          )}
                          {stream.messageRate !== undefined && stream.messageRate > 5 && (
                            <span className="bg-red-500/10 text-red-500 text-[10px] px-1.5 py-0.5 rounded-md border border-red-500/20 font-bold flex items-center gap-1 shadow-[0_0_8px_rgba(239,68,68,0.1)]">
                              <span className="animate-pulse">💬</span> Chat Spam
                            </span>
                          )}
                          {stream.privateActive && (
                            <span className="bg-purple-500/10 text-purple-400 text-[10px] px-1.5 py-0.5 rounded-md border border-purple-500/20 font-bold flex items-center gap-1">
                              🔒 Private{stream.privatePricePerMinute != null ? ` ${stream.privatePricePerMinute}/min` : ''}
                            </span>
                          )}
                          {stream.privateActive && (stream.activeSpyCount ?? 0) > 0 && (
                            <span className="bg-cyan-500/10 text-cyan-400 text-[10px] px-1.5 py-0.5 rounded-md border border-cyan-500/20 font-bold flex items-center gap-1">
                              👁 {stream.activeSpyCount} {stream.activeSpyCount === 1 ? 'spy' : 'spies'}
                            </span>
                          )}
                          {stream.privateActive && stream.spyEnabled && (stream.activeSpyCount ?? 0) === 0 && (
                            <span className="bg-zinc-700/30 text-zinc-500 text-[10px] px-1.5 py-0.5 rounded-md border border-zinc-700/30 font-bold">
                              Spy On
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-1.5">
                          <span className="w-1 h-1 bg-red-500 rounded-full animate-pulse"></span>
                          <span className="text-[9px] text-red-500 font-bold uppercase tracking-tighter">Live Now</span>
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-3">
                    <div className="flex items-center gap-2">
                      <div className="w-1.5 h-1.5 rounded-full bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.4)]"></div>
                      <span className="text-zinc-300 font-medium">{safeRender(viewerCounts[stream.userId] ?? stream.viewerCount)}</span>
                    </div>
                  </td>
                  <td className="px-6 py-3 text-zinc-400 font-mono text-sm">
                    <LiveDuration startedAt={stream.startedAt} />
                  </td>
                  <td className="px-6 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <button 
                        onClick={() => navigate(`/creators/${stream.creatorId}/live`)}
                        className="p-1.5 text-zinc-500 hover:text-indigo-400 hover:bg-indigo-400/10 rounded-lg transition-all"
                        title="View Stream"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      </button>
                      <button 
                        onClick={() => stream.slowMode ? handleDisableSlowMode(stream.id) : handleEnableSlowMode(stream.id)}
                        className={`p-1.5 transition-all rounded-lg ${stream.slowMode ? 'text-blue-500 bg-blue-500/10 hover:bg-blue-500/20' : 'text-zinc-500 hover:text-blue-500 hover:bg-blue-500/10'}`}
                        title={stream.slowMode ? "Disable Slow Mode" : "Enable Slow Mode"}
                      >
                        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="m12 10 2 5" />
                          <path d="m17 11 1 5" />
                          <path d="m9 5.5 3.5 2" />
                          <path d="M16 10c.5-1.1.2-2.4-.8-3c-1.1-.6-2.4-.4-3 .8" />
                          <path d="M7 10c.5-1.1.2-2.4-.8-3C5.1 6.4 3.9 6.6 3.2 7.8" />
                          <path d="M21 10c.5-1.1.2-2.4-.8-3c-1.1-.6-2.4-.4-3 .8" />
                          <path d="M12 15.5c-3.3 0-6-2.7-6-6s2.7-6 6-6 6 2.7 6 6-2.7 6-6 6Z" />
                          <path d="M12 15.5c0 3.3-2.7 6-6 6s-6-2.7-6-6" />
                          <path d="M18 15.5c0 3.3 2.7 6 6 6s6-2.7-6-6" />
                          <path d="M12 15.5V22" />
                        </svg>
                      </button>
                      <button 
                        onClick={() => handleMuteUser(stream.id, stream.userId, stream.username)}
                        className="p-1.5 text-zinc-500 hover:text-yellow-500 hover:bg-yellow-500/10 rounded-lg transition-all"
                        title="Mute User"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2" />
                        </svg>
                      </button>
                      <button 
                        onClick={() => handleKickUser(stream.id, stream.userId, stream.username)}
                        className="p-1.5 text-zinc-500 hover:text-orange-500 hover:bg-orange-500/10 rounded-lg transition-all"
                        title="Kick User"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 7a4 4 0 11-8 0 4 4 0 018 0zM9 14a6 6 0 00-6 6v1h12v-1a6 6 0 00-6-6zM21 12h-6" />
                        </svg>
                      </button>
                      <button 
                        onClick={() => handleStopStream(stream.id, stream.username)}
                        className="p-1.5 text-zinc-500 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-all"
                        title="Force Stop Stream"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
});

export default AdminLiveStreamsWidget;
