import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/apiClient';
import { showToast } from './Toast';
import EmptyState from './EmptyState';

const VodGallery: React.FC = () => {
  const [vods, setVods] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchVods = async () => {
      try {
        const response = await apiClient.get('/stream/vod');
        setVods(response.data);
      } catch (err) {
        console.error('Failed to fetch VODs', err);
        showToast('Could not load replays', 'error');
      } finally {
        setLoading(false);
      }
    };
    fetchVods();
  }, []);

  if (loading) return <div>Loading replays...</div>;

  return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 shadow-2xl shadow-black/40">
      <h2 className="text-2xl font-bold text-zinc-100 mb-8 mt-0">Replays (VOD)</h2>
      {vods.length === 0 ? (
        <EmptyState 
          message="No replays available yet. Check back later after some streams have ended!"
          icon="📼"
        />
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-6">
          {vods.map(vod => (
            <div key={vod.id} className="bg-white/5 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300 hover:scale-[1.02] hover:border-purple-500/30 group">
              <div className="h-40 bg-zinc-900/50 flex items-center justify-center text-5xl group-hover:bg-zinc-800/50 transition-colors">
                📼
              </div>
              <div className="p-5">
                <h3 className="text-lg font-bold text-zinc-100 mb-1 truncate">{vod.title}</h3>
                <p className="text-sm text-zinc-500 mb-4">
                  {vod.endedAt ? new Date(vod.endedAt).toLocaleDateString() : 'Finished recently'}
                </p>
                <button 
                  onClick={() => navigate(`/vod/${vod.id}`)}
                  className="w-full py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-bold text-sm shadow-lg shadow-purple-600/20 transition-all duration-200"
                >
                  Watch Replay
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default VodGallery;
