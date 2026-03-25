import React, { useState, useEffect, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import { showToast } from '@/components/Toast';

interface UserInfo {
  id: number;
  email: string;
  role: string;
}

interface CreatorModerationPanelProps {
  creatorId: number;
  isOpen: boolean;
  onClose: () => void;
}

const CreatorModerationPanel: React.FC<CreatorModerationPanelProps> = ({ creatorId, isOpen, onClose }) => {
  const [viewers, setViewers] = useState<UserInfo[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchViewers = useCallback(async () => {
    try {
      setLoading(true);
      const response = await apiClient.get<UserInfo[]>(`/stream/moderation/viewers/${creatorId}`);
      setViewers(response.data);
    } catch (error) {
      console.error('Failed to fetch viewers', error);
    } finally {
      setLoading(false);
    }
  }, [creatorId]);

  useEffect(() => {
    if (isOpen) {
      fetchViewers();
      const interval = setInterval(fetchViewers, 10000); // Refresh every 10s
      return () => clearInterval(interval);
    }
  }, [isOpen, fetchViewers]);

  const handleMute = async (userId: number, minutes: number) => {
    try {
      await apiClient.post('/stream/moderation/mute', {
        creatorId,
        userId,
        durationMinutes: minutes
      });
      showToast(`User muted for ${minutes} minutes`, 'success');
    } catch (error) {
      showToast('Failed to mute user', 'error');
    }
  };

  const handleShadowMute = async (userId: number) => {
    try {
      await apiClient.post('/stream/moderation/shadow-mute', {
        creatorId,
        userId
      });
      showToast('User shadow muted', 'success');
    } catch (error) {
      showToast('Failed to shadow mute user', 'error');
    }
  };

  const handleKick = async (userId: number) => {
    try {
      await apiClient.post('/stream/moderation/kick', {
        creatorId,
        userId
      });
      showToast('User kicked', 'success');
      setViewers(prev => prev.filter(v => v.id !== userId));
    } catch (error) {
      showToast('Failed to kick user', 'error');
    }
  };

  return (
    <div
      className={`fixed top-0 right-0 h-full w-80 z-[100] transition-transform duration-500 ease-in-out ${
        isOpen ? 'translate-x-0' : 'translate-x-full'
      }`}
    >
      <div className="h-full glass-panel border-l border-white/10 shadow-2xl flex flex-col overflow-hidden">
        <div className="p-4 border-b border-white/10 flex justify-between items-center bg-white/5">
          <h2 className="text-lg font-bold text-white uppercase tracking-wider">Moderation</h2>
          <button
            onClick={onClose}
            className="text-white/50 hover:text-white transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-4">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs font-bold text-zinc-500 uppercase tracking-widest">
              Viewers ({viewers.length})
            </span>
            <button 
              onClick={fetchViewers}
              className="text-[10px] text-indigo-400 hover:text-indigo-300 font-bold uppercase"
              disabled={loading}
            >
              {loading ? 'Refreshing...' : 'Refresh'}
            </button>
          </div>

          {viewers.length === 0 && !loading && (
            <div className="text-center py-10">
              <p className="text-zinc-500 text-sm italic">No viewers yet</p>
            </div>
          )}

          {viewers.map((viewer) => (
            <div 
              key={viewer.id} 
              className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-3 hover:bg-white/10 transition-colors"
            >
              <div className="flex justify-between items-center">
                <span className="text-sm font-bold text-white truncate max-w-[150px]">
                  {viewer.email.split('@')[0]}
                </span>
                <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  ID: {String(viewer.id).substring(0, 4)}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="col-span-2 flex gap-1">
                  <button
                    onClick={() => handleMute(viewer.id, 5)}
                    className="flex-1 text-[10px] py-1.5 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 transition-colors border border-white/5"
                  >
                    Mute 5m
                  </button>
                  <button
                    onClick={() => handleMute(viewer.id, 30)}
                    className="flex-1 text-[10px] py-1.5 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 transition-colors border border-white/5"
                  >
                    30m
                  </button>
                  <button
                    onClick={() => handleMute(viewer.id, 1440)}
                    className="flex-1 text-[10px] py-1.5 rounded-lg bg-zinc-800 text-zinc-300 hover:bg-zinc-700 transition-colors border border-white/5"
                  >
                    24h
                  </button>
                </div>
                
                <button
                  onClick={() => handleShadowMute(viewer.id)}
                  className="text-[10px] py-1.5 rounded-lg bg-indigo-500/20 text-indigo-300 hover:bg-indigo-500/30 transition-colors border border-indigo-500/20"
                >
                  Shadow Mute
                </button>
                
                <button
                  onClick={() => handleKick(viewer.id)}
                  className="text-[10px] py-1.5 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 transition-colors border border-red-500/20"
                >
                  Kick
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default CreatorModerationPanel;
