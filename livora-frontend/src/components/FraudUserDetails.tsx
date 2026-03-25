import React, { useState, useEffect } from 'react';
import fraudService from '../api/fraudService';
import { FraudEvent, RiskScore, FraudSignal } from '../types/fraud';
import { showToast } from './Toast';

interface FraudUserDetailsProps {
  userId: string;
  onClose: () => void;
}

const FraudUserDetails: React.FC<FraudUserDetailsProps> = ({ userId, onClose }) => {
  const [history, setHistory] = useState<FraudEvent[]>([]);
  const [signals, setSignals] = useState<FraudSignal[]>([]);
  const [riskScore, setRiskScore] = useState<RiskScore | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchDetails = async () => {
    setLoading(true);
    try {
      const [historyData, signalsData, scoreData] = await Promise.all([
        fraudService.getUserFraudHistory(userId),
        fraudService.getUserSignals(userId),
        fraudService.getUserRiskScore(userId)
      ]);
      setHistory(historyData);
      setSignals(signalsData.content);
      setRiskScore(scoreData);
    } catch (error) {
      showToast('Failed to fetch user fraud details', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDetails();
  }, [userId]);

  if (loading) return (
    <div style={styles.overlay}>
      <div style={styles.modal}>
        <p>Loading details...</p>
        <button onClick={onClose} style={styles.closeButton}>Close</button>
      </div>
    </div>
  );

  return (
    <div style={styles.overlay}>
      <div style={styles.modal}>
        <div style={styles.header}>
          <h3>User Fraud Details (ID: {userId})</h3>
          <button onClick={onClose} style={styles.closeButton}>×</button>
        </div>

        <div style={styles.content}>
          <section style={styles.section}>
            <h4>Current Risk Score</h4>
            {riskScore ? (
              <div style={styles.scoreCard}>
                <div style={{ ...styles.scoreValue, color: riskScore.score > 70 ? '#ff4d4f' : riskScore.score > 40 ? '#faad14' : '#52c41a' }}>
                  {riskScore.score}/100
                </div>
                <p style={styles.breakdown}>{riskScore.breakdown}</p>
                <small>Last evaluated: {riskScore.lastEvaluatedAt ? new Date(riskScore.lastEvaluatedAt).toLocaleString() : 'N/A'}</small>
              </div>
            ) : <p>No risk score available.</p>}
          </section>

          <section style={styles.section}>
            <h4>Active Signals</h4>
            <div style={styles.scrollList}>
              {(signals || []).length === 0 ? <p>No active signals.</p> : (signals || []).map(s => (
                <div key={s.id} style={styles.listItem}>
                  <div>
                    <strong>{s.type}</strong> ({s.riskLevel}) - {s.source}
                    <p style={styles.reason}>{s.reason}</p>
                    <small>{s.createdAt ? new Date(s.createdAt).toLocaleString() : 'N/A'}</small>
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section style={styles.section}>
            <h4>Fraud Event History</h4>
            <div style={styles.scrollList}>
              {(history || []).length === 0 ? <p>No history available.</p> : (history || []).map(h => (
                <div key={h.id} style={styles.listItem}>
                  <div>
                    <strong>{h.eventType}</strong>
                    <p style={styles.reason}>{h.reason}</p>
                    <small>{h.createdAt ? new Date(h.createdAt).toLocaleString() : 'N/A'}</small>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  modal: {
    backgroundColor: '#fff',
    borderRadius: '12px',
    width: '90%',
    maxWidth: '800px',
    maxHeight: '90vh',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
  },
  header: {
    padding: '1rem 1.5rem',
    borderBottom: '1px solid #eee',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  closeButton: {
    background: 'none',
    border: 'none',
    fontSize: '1.5rem',
    cursor: 'pointer',
    color: '#999',
  },
  content: {
    padding: '1.5rem',
    overflowY: 'auto',
    flex: 1,
  },
  section: {
    marginBottom: '2rem',
  },
  scoreCard: {
    padding: '1rem',
    backgroundColor: '#f8f9fa',
    borderRadius: '8px',
    border: '1px solid #eee',
  },
  scoreValue: {
    fontSize: '2rem',
    fontWeight: 'bold',
  },
  breakdown: {
    margin: '0.5rem 0',
    color: '#444',
  },
  scrollList: {
    maxHeight: '300px',
    overflowY: 'auto',
    border: '1px solid #eee',
    borderRadius: '8px',
  },
  listItem: {
    padding: '1rem',
    borderBottom: '1px solid #eee',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  reason: {
    margin: '4px 0',
    fontSize: '0.9rem',
    color: '#666',
  }
};

export default FraudUserDetails;
