import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import apiClient from '../api/apiClient';
import LiveVideoPlayer from '../components/LiveVideoPlayer';
import { showToast } from '../components/Toast';
import SEO from '../components/SEO';

const VodPage: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, hasPremiumAccess } = useAuth();
  
  const [vod, setVod] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [hlsUrl, setHlsUrl] = useState<string | null>(null);

  useEffect(() => {
    const loadVod = async () => {
      if (!streamId || !isAuthenticated) return;
      try {
        setLoading(true);
        // We reuse the stream status or metadata endpoint. 
        // For now, let's assume we can fetch it via /stream/room/{id} or similar
        // Actually, we need the stream metadata.
        const response = await apiClient.get(`/stream/room/${streamId}`);
        const data = response.data;
        setVod(data);
        
        // Use the same secured HLS endpoint. 
        // The backend HlsProxyController will now also check the recordings directory.
        const securedHlsUrl = `${import.meta.env.VITE_API_URL}/api/stream/${streamId}/hls`;
        setHlsUrl(securedHlsUrl);

        if (data.isPremium && !hasPremiumAccess()) {
          showToast('This is a premium replay. Please upgrade your subscription.', 'error');
        }
      } catch (e) {
        console.error('Failed to load VOD', e);
        showToast('Replay not found', 'error');
        navigate('/live');
      } finally {
        setLoading(false);
      }
    };

    loadVod();
  }, [streamId, isAuthenticated, hasPremiumAccess, navigate]);

  if (loading || !isAuthenticated) {
    return <div style={styles.centered}>Loading replay...</div>;
  }

  if (!vod) {
    return <div style={styles.centered}>Replay not found</div>;
  }

  return (
    <div style={styles.container}>
      <SEO title={vod.streamTitle || 'Replay'} />
      
      <div style={styles.layout}>
        <div style={styles.main}>
          <div style={styles.videoContainer}>
            {hlsUrl && (vod.isPremium ? hasPremiumAccess() : true) ? (
              <LiveVideoPlayer 
              src={hlsUrl} 
            />
            ) : (
              <div style={styles.videoPlaceholder}>
                <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>🔒</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>Premium Replay</div>
                <p>Upgrade your subscription to watch this replay.</p>
              </div>
            )}
          </div>

          <div style={styles.infoSection}>
            <h1 style={styles.title}>{vod.streamTitle}</h1>
            <div style={styles.metadata}>
              <span>Creator #{vod.userId}</span>
              <span style={styles.separator}>•</span>
              <span>Finished on {vod.endedAt ? new Date(vod.endedAt).toLocaleDateString() : 'N/A'}</span>
            </div>
            <p style={styles.description}>{vod.description || 'No description provided.'}</p>
          </div>
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '2rem',
    maxWidth: '1200px',
    margin: '0 auto',
    color: '#F4F4F5',
  },
  layout: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2rem',
  },
  main: {
    flex: '1',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  videoContainer: {
    position: 'relative',
    width: '100%',
    aspectRatio: '16/9',
    backgroundColor: '#000',
    borderRadius: '24px',
    overflow: 'hidden',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  videoPlaceholder: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#fff',
    backgroundColor: '#1a1a1a',
    textAlign: 'center',
    padding: '2rem',
  },
  infoSection: {
    backgroundColor: '#0F0F14',
    padding: '2.5rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  title: {
    margin: '0 0 0.75rem 0',
    fontSize: '2.25rem',
    fontWeight: '800',
    letterSpacing: '-0.025em',
    color: '#F4F4F5',
  },
  metadata: {
    display: 'flex',
    alignItems: 'center',
    color: '#71717A',
    fontSize: '0.95rem',
    marginBottom: '2rem',
    fontWeight: '500',
  },
  separator: {
    margin: '0 0.75rem',
  },
  description: {
    lineHeight: '1.7',
    color: '#A1A1AA',
    fontSize: '1.05rem',
  },
  centered: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '80vh',
    fontSize: '1.2rem',
    color: '#6b7280',
  },
};

export default VodPage;
