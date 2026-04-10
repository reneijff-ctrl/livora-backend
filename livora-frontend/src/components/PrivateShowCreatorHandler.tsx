import React, { useState, useEffect } from 'react';
import { useWs } from '../ws/WsContext';
import privateShowService, { PrivateSession, PrivateSessionStatus } from '../api/privateShowService';
import { showToast } from './Toast';

interface PrivateShowRequestPayload {
  sessionId: string;
  viewerEmail: string;
  pricePerMinute: number;
}

interface PrivateShowCreatorHandlerProps {
  onSessionAccepted?: (session: PrivateSession) => void;
}

const PrivateShowCreatorHandler: React.FC<PrivateShowCreatorHandlerProps> = ({ onSessionAccepted }) => {
  const { subscribe, connected } = useWs();
  const [requests, setRequests] = useState<PrivateShowRequestPayload[]>([]);

  useEffect(() => {
    let unsub = () => {};

    if (connected) {
      const result = subscribe('/user/queue/private-show-requests', (message) => {
        const data = JSON.parse(message.body);
        if (data.type === 'PRIVATE_SHOW_REQUEST') {
          setRequests((prev) => [...prev, data.payload]);
          showToast(`New Private Show Request from ${data.payload.viewerEmail}`, 'info');
        }
      });
      if (typeof result === 'function') {
        unsub = result;
      }
    }

    return () => {
      unsub();
    };
  }, [connected, subscribe]);

  const handleAccept = async (sessionId: string, request: PrivateShowRequestPayload) => {
    try {
      const updatedSession = await privateShowService.acceptRequest(sessionId);
      showToast('Request accepted!', 'success');
      setRequests((prev) => prev.filter((r) => r.sessionId !== sessionId));
      const normalizedSession: PrivateSession = {
        id: updatedSession.id,
        viewerId: updatedSession.viewerId,
        creatorId: updatedSession.creatorId,
        pricePerMinute: updatedSession.pricePerMinute,
        status: PrivateSessionStatus[updatedSession.status as keyof typeof PrivateSessionStatus] || PrivateSessionStatus.ACCEPTED,
      };
      if (onSessionAccepted) {
        onSessionAccepted(normalizedSession);
      }
    } catch (error) {
      showToast('Failed to accept request', 'error');
    }
  };

  const handleReject = async (sessionId: string) => {
    try {
      await privateShowService.rejectRequest(sessionId);
      setRequests((prev) => prev.filter((r) => r.sessionId !== sessionId));
      showToast('Request rejected', 'info');
    } catch (error) {
      showToast('Failed to reject request', 'error');
    }
  };

  if (requests.length === 0) return null;

  return (
    <div style={styles.modalOverlay}>
      <div style={styles.modal}>
        <h3 style={styles.title}>Private Show Requests</h3>
        {requests.map((req) => (
          <div key={req.sessionId} style={styles.requestItem}>
            <div>
              <strong>{req.viewerEmail}</strong>
              <div style={styles.price}>{req.pricePerMinute} 🪙 / min</div>
            </div>
            <div style={styles.actions}>
              <button onClick={() => handleAccept(req.sessionId, req)} style={styles.acceptButton}>Accept</button>
              <button onClick={() => handleReject(req.sessionId)} style={styles.rejectButton}>Reject</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.7)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 2000,
  },
  modal: {
    backgroundColor: '#1c1c21',
    padding: '1.5rem',
    borderRadius: '12px',
    border: '1px solid #2d2d35',
    width: '100%',
    maxWidth: '400px',
  },
  title: {
    margin: '0 0 1rem 0',
    color: '#fff',
  },
  requestItem: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '1rem',
    backgroundColor: '#09090b',
    borderRadius: '8px',
    marginBottom: '0.5rem',
  },
  price: {
    fontSize: '0.8rem',
    color: '#ffd700',
  },
  actions: {
    display: 'flex',
    gap: '0.5rem',
  },
  acceptButton: {
    padding: '0.4rem 0.8rem',
    backgroundColor: '#22c55e',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.8rem',
  },
  rejectButton: {
    padding: '0.4rem 0.8rem',
    backgroundColor: '#ef4444',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.8rem',
  },
};

export default PrivateShowCreatorHandler;
