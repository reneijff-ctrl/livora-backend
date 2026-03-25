import React, { useState, useEffect } from 'react';
import streamingService, { StreamRoom } from '../api/streamingService';
import { useWs } from '../ws/WsContext';
import { showToast } from './Toast';

const CreatorLivePanel: React.FC = () => {
  const { subscribe, connected, presenceMap } = useWs();
  const [room, setRoom] = useState<StreamRoom | null>(null);
  const [loading, setLoading] = useState(true);
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [ingestInfo, setIngestInfo] = useState<{ server: string, streamKey: string } | null>(null);
  const [streamData, setStreamData] = useState({ title: '', description: '', minChatTokens: 0, isPaid: false, pricePerMessage: 0, recordingEnabled: false });

  useEffect(() => {
    const fetchMyRoom = async () => {
      try {
        const myRoom = await streamingService.getMyRoom();
        setRoom(myRoom);
        setIsBroadcasting(myRoom.isLive);

        const info = await streamingService.getIngestInfo();
        setIngestInfo(info);
      } catch (err) {
        console.error('Failed to fetch my room or ingest info', err);
      } finally {
        setLoading(false);
      }
    };
    fetchMyRoom();

    // Start polling live status every 5 seconds
    const pollStatus = async () => {
      try {
        const status = await streamingService.getLiveStatus();
        setIsBroadcasting(status.isLive);
        setRoom(prev => prev ? { 
          ...prev, 
          viewerCount: status.viewerCount, 
          isLive: status.isLive,
          streamTitle: status.streamTitle || prev.streamTitle
        } : null);
      } catch (err) {
        // Silent error for polling to avoid UI flicker/noise
        console.debug('Live status poll failed', err);
      }
    };

    const intervalId = setInterval(pollStatus, 5000);

    return () => clearInterval(intervalId);
  }, []);

  // Presence-driven viewer count updates (from global WsContext subscription)
  // NOTE: creators.presence is subscribed globally via WsContext — do not subscribe here.
  useEffect(() => {
    if (!room?.userId) return;
    const creatorUserId = Number(room.userId);
    const presence = presenceMap[creatorUserId];
    if (presence?.viewerCount !== undefined && presence?.viewerCount !== null) {
      setRoom(prev => prev ? { ...prev, viewerCount: Number(presence.viewerCount) } : null);
    }
  }, [room?.userId, presenceMap]);

  const handleStart = async () => {
    try {
      const startedRoom = await streamingService.startStream({
        title: streamData.title || "My Live Stream",
        description: streamData.description,
        minChatTokens: streamData.minChatTokens,
        isPaid: streamData.isPaid,
        pricePerMessage: streamData.pricePerMessage,
        recordingEnabled: streamData.recordingEnabled
      });
      setRoom(startedRoom);
      
      setIsBroadcasting(true);
      showToast('Stream metadata updated. You can now start streaming from OBS!', 'success');
    } catch (err) {
      console.error('Failed to start stream', err);
      showToast('Failed to start stream', 'error');
    }
  };

  const handleStop = async () => {
    try {
      const stoppedRoom = await streamingService.stopStream();
      setRoom(stoppedRoom);
      setIsBroadcasting(false);
      showToast('Stream stopped', 'info');
    } catch (err) {
      console.error('Failed to stop stream', err);
      showToast('Failed to stop stream', 'error');
    }
  };

  if (loading) return <div>Loading Creator Panel...</div>;

  return (
    <div style={styles.panel}>
      <div style={styles.header}>
        <h3 style={styles.title}>Creator Live Panel</h3>
        <div style={styles.statusContainer}>
          {isBroadcasting ? (
            <div style={styles.liveIndicator}>
              <span style={styles.redDot}></span> LIVE
            </div>
          ) : (
            <div style={styles.offlineIndicator}>OFFLINE</div>
          )}
          <div style={styles.viewerCount}>
            👁️ {room?.viewerCount || 0} Viewers
          </div>
        </div>
      </div>

      <div style={styles.ingestInfo}>
        <h4>OBS Settings</h4>
        <div style={styles.infoRow}>
          <strong>Server:</strong> 
          <code style={styles.code}>{ingestInfo?.server}</code>
        </div>
        <div style={styles.infoRow}>
          <strong>Stream Key:</strong> 
          <code style={styles.code}>{ingestInfo?.streamKey}</code>
        </div>
        <p style={styles.helpText}>
          Copy these into OBS Studio (Settings {'>'} Stream) to start your broadcast.
        </p>
      </div>

      {!isBroadcasting ? (
        <div style={styles.form}>
          <input
            type="text"
            placeholder="Stream Title"
            value={streamData.title}
            onChange={e => setStreamData({ ...streamData, title: e.target.value })}
            style={styles.input}
          />
          <input
            type="text"
            placeholder="Description"
            value={streamData.description}
            onChange={e => setStreamData({ ...streamData, description: e.target.value })}
            style={styles.input}
          />
          <div style={styles.inlineForm}>
            <label style={styles.label}>Min Chat Tokens:</label>
            <input
              type="number"
              value={streamData.minChatTokens}
              onChange={e => setStreamData({ ...streamData, minChatTokens: Number(e.target.value) })}
              style={styles.smallInput}
            />
          </div>
          <div style={styles.inlineForm}>
            <label style={{ ...styles.label, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={streamData.isPaid}
                onChange={e => setStreamData({ ...streamData, isPaid: e.target.checked })}
                style={{ width: '18px', height: '18px' }}
              />
              Paid Chat (PPV)
            </label>
            {streamData.isPaid && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <label style={styles.label}>Price per Message:</label>
                <input
                  type="number"
                  value={streamData.pricePerMessage}
                  onChange={e => setStreamData({ ...streamData, pricePerMessage: Number(e.target.value) })}
                  style={styles.smallInput}
                  min={1}
                />
              </div>
            )}
          </div>
          <div style={styles.inlineForm}>
            <label style={{ ...styles.label, display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={streamData.recordingEnabled}
                onChange={e => setStreamData({ ...streamData, recordingEnabled: e.target.checked })}
                style={{ width: '18px', height: '18px' }}
              />
              Enable Recording
            </label>
          </div>
          <button onClick={handleStart} style={styles.startButton}>
            Go Live
          </button>
        </div>
      ) : (
        <div style={styles.activeActions}>
          <div style={styles.streamInfo}>
            <span style={styles.activeTitle}>{room?.streamTitle}</span>
            <p style={styles.description}>{room?.description}</p>
          </div>
          <button onClick={handleStop} style={styles.stopButton}>
            Stop Live
          </button>
        </div>
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  panel: {
    padding: '1.75rem',
    borderRadius: '16px',
    backgroundColor: '#16161a',
    border: '1px solid #2d2d35',
    boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '1.75rem',
    paddingBottom: '0.75rem',
    borderBottom: '1px solid #2d2d35',
  },
  title: {
    margin: 0,
    fontSize: '1.25rem',
    fontWeight: '700',
    color: '#ffffff',
  },
  statusContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
  },
  liveIndicator: {
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    color: '#ef4444',
    padding: '0.25rem 0.75rem',
    borderRadius: '1rem',
    fontSize: '0.75rem',
    fontWeight: '800',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    border: '1px solid rgba(239, 68, 68, 0.2)',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  redDot: {
    width: '8px',
    height: '8px',
    backgroundColor: '#ef4444',
    borderRadius: '50%',
    display: 'inline-block',
    boxShadow: '0 0 8px #ef4444',
  },
  offlineIndicator: {
    backgroundColor: '#27272a',
    color: '#a1a1aa',
    padding: '0.25rem 0.75rem',
    borderRadius: '1rem',
    fontSize: '0.75rem',
    fontWeight: '700',
    border: '1px solid #3f3f46',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  viewerCount: {
    fontSize: '0.85rem',
    fontWeight: '600',
    color: '#a1a1aa',
    display: 'flex',
    alignItems: 'center',
    gap: '0.25rem',
  },
  ingestInfo: {
    backgroundColor: '#1c1c21',
    padding: '1.25rem',
    borderRadius: '12px',
    marginBottom: '1.75rem',
    border: '1px solid #2d2d35',
  },
  ingestTitle: {
    fontSize: '0.9rem',
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: '1rem',
    display: 'block',
    textTransform: 'uppercase',
    letterSpacing: '0.025em',
  },
  infoRow: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    marginBottom: '1rem',
  },
  infoLabel: {
    fontSize: '0.75rem',
    fontWeight: '600',
    color: '#71717a',
    textTransform: 'uppercase',
  },
  code: {
    backgroundColor: '#09090b',
    padding: '0.6rem 0.8rem',
    borderRadius: '6px',
    border: '1px solid #27272a',
    fontFamily: 'JetBrains Mono, SFMono-Regular, Consolas, monospace',
    color: '#6772e5',
    fontSize: '0.85rem',
    wordBreak: 'break-all',
  },
  helpText: {
    fontSize: '0.75rem',
    color: '#71717a',
    margin: '0.5rem 0 0 0',
    lineHeight: '1.4',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  input: {
    padding: '0.75rem 1rem',
    borderRadius: '8px',
    backgroundColor: '#09090b',
    border: '1px solid #27272a',
    color: '#ffffff',
    fontSize: '0.95rem',
    outline: 'none',
    transition: 'border-color 0.2s',
  },
  inlineForm: {
    display: 'flex',
    alignItems: 'center',
    gap: '1.5rem',
    flexWrap: 'wrap',
  },
  label: {
    fontSize: '0.85rem',
    fontWeight: '600',
    color: '#a1a1aa',
  },
  smallInput: {
    padding: '0.5rem',
    borderRadius: '6px',
    backgroundColor: '#09090b',
    border: '1px solid #27272a',
    color: '#ffffff',
    width: '100px',
    outline: 'none',
  },
  startButton: {
    padding: '0.85rem',
    backgroundColor: '#6772e5',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    fontWeight: '700',
    fontSize: '1rem',
    marginTop: '0.5rem',
    transition: 'transform 0.1s, background-color 0.2s',
  },
  activeActions: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '1.5rem',
    backgroundColor: '#1c1c21',
    padding: '1.25rem',
    borderRadius: '12px',
    border: '1px solid #2d2d35',
  },
  streamInfo: {
    flex: 1,
  },
  activeTitle: {
    display: 'block',
    fontSize: '1.1rem',
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: '0.25rem',
  },
  description: {
    fontSize: '0.9rem',
    color: '#a1a1aa',
    margin: 0,
  },
  stopButton: {
    padding: '0.75rem 1.5rem',
    backgroundColor: '#ef4444',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    fontWeight: '700',
    transition: 'background-color 0.2s',
  },
};

export default CreatorLivePanel;
