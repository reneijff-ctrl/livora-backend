import React, { useState, useEffect } from 'react';
import fraudService from '../api/fraudService';
import { safeRender } from '@/utils/safeRender';
import { 
  UserAdminResponse, 
  FailedLogin, 
  PaymentAnomaly, 
  ChargebackHistory, 
  FraudSignal, 
  FraudRiskLevel 
} from '../types/fraud';
import { showToast } from './Toast';
import FraudUserDetails from './FraudUserDetails';
import { useAuth } from '../auth/useAuth';

const FraudDashboard: React.FC = () => {
  const { user, authLoading } = useAuth();
  const [activeTab, setActiveTab] = useState<'users' | 'signals' | 'logins' | 'anomalies' | 'chargebacks'>('users');
  const [highRiskUsers, setHighRiskUsers] = useState<UserAdminResponse[]>([]);
  const [signals, setSignals] = useState<FraudSignal[]>([]);
  const [failedLogins, setFailedLogins] = useState<FailedLogin[]>([]);
  const [anomalies, setAnomalies] = useState<PaymentAnomaly[]>([]);
  const [chargebacks, setChargebacks] = useState<ChargebackHistory[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  const fetchData = async () => {
    // Guard against fetching if not authorized or still loading auth
    if (!user || authLoading || user.role !== 'ADMIN') {
        return;
    }

    setLoading(true);
    try {
      if (activeTab === 'users') {
        const data = await fraudService.getUsersByRiskLevel(FraudRiskLevel.HIGH);
        setHighRiskUsers(data.content);
      } else if (activeTab === 'signals') {
        const data = await fraudService.getSignals();
        setSignals(data.content);
      } else if (activeTab === 'logins') {
        const data = await fraudService.getFailedLogins();
        setFailedLogins(data.content);
      } else if (activeTab === 'anomalies') {
        const data = await fraudService.getPaymentAnomalies();
        setAnomalies(data.content);
      } else if (activeTab === 'chargebacks') {
        const data = await fraudService.getChargebacks();
        setChargebacks(data.content);
      }
    } catch (error) {
      showToast('Failed to fetch fraud data', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [activeTab, user, authLoading]);

  const getRiskColor = (level: string) => {
    switch (level) {
      case 'CRITICAL': return '#ff4d4f';
      case 'HIGH': return '#f5222d';
      case 'MEDIUM': return '#faad14';
      case 'LOW': return '#52c41a';
      default: return '#8c8c8c';
    }
  };

  const renderTabContent = () => {
    if (loading) return <p style={styles.loading}>Loading data...</p>;

    switch (activeTab) {
      case 'users':
        return (
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr style={styles.tableHeader}>
                  <th>Email</th>
                  <th>Status</th>
                  <th>Risk Level</th>
                  <th>Created At</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {highRiskUsers.length === 0 ? (
                  <tr><td colSpan={5} style={styles.emptyCell}>No high risk users found.</td></tr>
                ) : (
                  highRiskUsers.map(u => (
                    <tr key={u.id} style={styles.tableRow}>
                      <td>{safeRender(u.email)}</td>
                      <td>{safeRender(u.status)}</td>
                      <td>
                        <span style={{ ...styles.badge, backgroundColor: getRiskColor(u.fraudRiskLevel) }}>
                          {safeRender(u.fraudRiskLevel)}
                        </span>
                      </td>
                      <td>
                        {safeRender(u.createdAt ? new Date(u.createdAt).toLocaleString() : 'N/A')}
                      </td>
                      <td>
                        <button onClick={() => setSelectedUserId(u.id.toString())} style={styles.viewButton}>View Details</button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        );
      case 'signals':
        return (
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr style={styles.tableHeader}>
                  <th>User ID</th>
                  <th>Type</th>
                  <th>Risk</th>
                  <th>Source</th>
                  <th>Status</th>
                  <th>Created At</th>
                </tr>
              </thead>
              <tbody>
                {(signals || []).map(s => (
                  <tr key={s.id} style={styles.tableRow}>
                    <td>{safeRender(s.userId)}</td>
                    <td>{safeRender(s.type)}</td>
                    <td>
                      <span style={{ ...styles.badge, backgroundColor: getRiskColor(s.riskLevel) }}>
                        {safeRender(s.riskLevel)}
                      </span>
                    </td>
                    <td>{safeRender(s.source)}</td>
                    <td>{safeRender(s.resolved ? 'Resolved' : 'Open')}</td>
                    <td>
                      {safeRender(s.createdAt ? new Date(s.createdAt).toLocaleString() : 'N/A')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      case 'logins':
        return (
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr style={styles.tableHeader}>
                  <th>Email</th>
                  <th>IP Address</th>
                  <th>User Agent</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {(failedLogins || []).map((l, i) => (
                  <tr key={i} style={styles.tableRow}>
                    <td>{safeRender(l.email || 'N/A')}</td>
                    <td>{safeRender(l.ipAddress || 'N/A')}</td>
                    <td style={styles.userAgentCell} title={l.userAgent || ''}>{safeRender(l.userAgent || 'N/A')}</td>
                    <td>
                      {safeRender(l.timestamp ? new Date(l.timestamp).toLocaleString() : 'N/A')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      case 'anomalies':
        return (
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr style={styles.tableHeader}>
                  <th>User</th>
                  <th>Amount</th>
                  <th>Risk Level</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {(anomalies || []).map(a => (
                  <tr key={a.paymentId} style={styles.tableRow}>
                    <td>{safeRender(a.userEmail)}</td>
                    <td>{safeRender(a.amount)} {safeRender(a.currency)}</td>
                    <td>
                      <span style={{ ...styles.badge, backgroundColor: getRiskColor(a.riskLevel) }}>
                        {safeRender(a.riskLevel)}
                      </span>
                    </td>
                    <td>
                      {safeRender(a.createdAt ? new Date(a.createdAt).toLocaleString() : 'N/A')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      case 'chargebacks':
        return (
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr style={styles.tableHeader}>
                  <th>User</th>
                  <th>Amount</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th>User Risk</th>
                </tr>
              </thead>
              <tbody>
                {(chargebacks || []).map((c, i) => (
                  <tr key={i} style={styles.tableRow}>
                    <td>{c.userEmail}</td>
                    <td>{c.amount} {c.currency}</td>
                    <td>{c.reason}</td>
                    <td>{c.status}</td>
                    <td>
                      <span style={{ ...styles.badge, backgroundColor: getRiskColor(c.fraudRisk) }}>
                        {c.fraudRisk}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
    }
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <h2 style={styles.title}>🕵️ Fraud Monitoring Dashboard</h2>
        <div style={styles.tabs}>
          <button onClick={() => setActiveTab('users')} style={activeTab === 'users' ? styles.activeTab : styles.tab}>High Risk Users</button>
          <button onClick={() => setActiveTab('signals')} style={activeTab === 'signals' ? styles.activeTab : styles.tab}>All Signals</button>
          <button onClick={() => setActiveTab('logins')} style={activeTab === 'logins' ? styles.activeTab : styles.tab}>Failed Logins</button>
          <button onClick={() => setActiveTab('anomalies')} style={activeTab === 'anomalies' ? styles.activeTab : styles.tab}>Anomalies</button>
          <button onClick={() => setActiveTab('chargebacks')} style={activeTab === 'chargebacks' ? styles.activeTab : styles.tab}>Chargebacks</button>
        </div>
      </header>

      <main style={styles.main}>
        {renderTabContent()}
      </main>

      {selectedUserId && (
        <FraudUserDetails 
          userId={selectedUserId} 
          onClose={() => setSelectedUserId(null)} 
        />
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    backgroundColor: '#fff',
    borderRadius: '12px',
    border: '1px solid #eee',
    padding: '1.5rem',
    marginTop: '2rem',
  },
  header: {
    marginBottom: '1.5rem',
    borderBottom: '1px solid #eee',
    paddingBottom: '1rem',
  },
  title: {
    margin: '0 0 1rem 0',
    fontSize: '1.5rem',
  },
  tabs: {
    display: 'flex',
    gap: '0.5rem',
    flexWrap: 'wrap',
  },
  tab: {
    padding: '8px 16px',
    border: '1px solid #ddd',
    backgroundColor: '#f8f9fa',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.9rem',
    color: '#666',
  },
  activeTab: {
    padding: '8px 16px',
    border: '1px solid #6772e5',
    backgroundColor: '#6772e5',
    color: '#fff',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.9rem',
    fontWeight: 'bold',
  },
  main: {
    minHeight: '200px',
  },
  tableWrapper: {
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
  },
  tableHeader: {
    backgroundColor: '#f8f9fa',
    textAlign: 'left',
  },
  tableRow: {
    borderBottom: '1px solid #eee',
  },
  badge: {
    padding: '2px 8px',
    borderRadius: '12px',
    color: '#fff',
    fontSize: '0.75rem',
    fontWeight: 'bold',
    textTransform: 'uppercase',
  },
  emptyCell: {
    padding: '2rem',
    textAlign: 'center',
    color: '#999',
  },
  loading: {
    textAlign: 'center',
    padding: '2rem',
    color: '#666',
  },
  viewButton: {
    padding: '4px 8px',
    backgroundColor: '#1890ff',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.8rem',
  },
  userAgentCell: {
    maxWidth: '200px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    fontSize: '0.8rem',
    color: '#666',
  }
};

export default FraudDashboard;
