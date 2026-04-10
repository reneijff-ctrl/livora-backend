import React, { useState, useEffect } from 'react';
import { useWs } from '../ws/WsContext';
import privateShowService from '../api/privateShowService';
import { showToast } from './Toast';
import { useAuth } from '../auth/useAuth';

interface PrivateShowSessionOverlayProps {
  sessionId: string;
  pricePerMinute: number;
  isCreator: boolean;
  onSessionEnded: () => void;
}

const PrivateShowSessionOverlay: React.FC<PrivateShowSessionOverlayProps> = ({ 
  sessionId, 
  pricePerMinute, 
  isCreator,
  onSessionEnded 
}) => {
  const { subscribe, connected } = useWs();
  const { refreshTokenBalance } = useAuth();
  const [seconds, setSeconds] = useState(0);
  const [active, setActive] = useState(true);

  useEffect(() => {
    let unsub = () => {};

    if (connected) {
      const result = subscribe('/user/queue/private-show-status', (message) => {
        const data = JSON.parse(message.body);
        if (data.type === 'PRIVATE_SHOW_ENDED') {
          setActive(false);
          showToast(`Private session ended: ${data.payload.reason || 'Unknown reason'}`, 'info');
          onSessionEnded();
        }
      });
      if (typeof result === 'function') {
        unsub = result;
      }
    }

    return () => {
      unsub();
    };
  }, [connected, subscribe, onSessionEnded]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (active) {
      interval = setInterval(() => {
        setSeconds((prev) => {
          const next = prev + 1;
          // Refresh balance every minute for the viewer to reflect deductions
          if (!isCreator && next % 60 === 0) {
            refreshTokenBalance();
          }
          return next;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [active, isCreator, refreshTokenBalance]);

  const handleEnd = async () => {
    try {
      await privateShowService.endSession(sessionId);
      setActive(false);
      onSessionEnded();
    } catch (error) {
      showToast('Failed to end session', 'error');
    }
  };

  const formatTime = (totalSeconds: number) => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (!active) return null;

  return (
    <div style={styles.overlay}>
      <div style={styles.content}>
        <div style={styles.badge}>PRIVATE SESSION</div>
        <div style={styles.timer}>{formatTime(seconds)}</div>
        <div style={styles.cost}>{pricePerMinute} 🪙 / min</div>
        <button onClick={handleEnd} style={styles.endButton}>End Session</button>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  overlay: {
    position: 'absolute',
    top: '1rem',
    right: '1rem',
    zIndex: 100,
  },
  content: {
    backgroundColor: 'rgba(0,0,0,0.8)',
    padding: '1rem',
    borderRadius: '8px',
    border: '1px solid #ffd700',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '0.5rem',
    color: '#fff',
  },
  badge: {
    fontSize: '0.7rem',
    fontWeight: 'bold',
    color: '#ffd700',
    letterSpacing: '0.1em',
  },
  timer: {
    fontSize: '1.5rem',
    fontWeight: 'bold',
    fontFamily: 'monospace',
  },
  cost: {
    fontSize: '0.8rem',
    opacity: 0.8,
  },
  endButton: {
    marginTop: '0.5rem',
    padding: '0.3rem 0.6rem',
    backgroundColor: '#ef4444',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.75rem',
  },
};

export default PrivateShowSessionOverlay;
