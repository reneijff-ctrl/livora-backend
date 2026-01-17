import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import SEO from '../components/SEO';
import apiClient from '../api/apiClient';
import AdminRealtimeDashboard from '../components/AdminRealtimeDashboard';
import adminContentService from '../api/adminContentService';
import { ContentItem } from '../api/contentService';
import { showToast } from '../components/Toast';

interface AdminSubscription {
  id: string;
  userEmail: string;
  status: string;
  stripeSubscriptionId: string;
  createdAt: string;
}

interface AdminPayout {
  id: string;
  userEmail: string;
  tokenAmount: number;
  eurAmount: number;
  status: string;
  createdAt: string;
}

const AdminPanel: React.FC = () => {
  const { user } = useAuth();
  const [subscriptions, setSubscriptions] = useState<AdminSubscription[]>([]);
  const [payouts, setPayouts] = useState<AdminPayout[]>([]);
  const [content, setContent] = useState<ContentItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [subRes, payoutRes, contentRes] = await Promise.all([
        apiClient.get<AdminSubscription[]>('/api/admin/subscriptions'),
        apiClient.get<AdminPayout[]>('/api/admin/payouts'),
        adminContentService.getAllContent()
      ]);
      setSubscriptions(subRes.data);
      setPayouts(payoutRes.data);
      setContent(contentRes);
    } catch (error) {
      console.error('Failed to fetch admin data', error);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleDisableContent = async (id: string) => {
    try {
      await adminContentService.disableContent(id);
      showToast('Content disabled.', 'success');
      fetchData();
    } catch (error) {
      showToast('Failed to disable content.', 'error');
    }
  };

  const handleDeleteContent = async (id: string) => {
    if (!window.confirm('Permanent delete?')) return;
    try {
      await adminContentService.deleteContent(id);
      showToast('Content deleted.', 'success');
      fetchData();
    } catch (error) {
      showToast('Failed to delete content.', 'error');
    }
  };

  return (
    <div style={{ padding: '2rem', fontFamily: 'sans-serif' }}>
      <SEO title="Admin Panel" canonical="/admin" />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>🛡️ Admin Dashboard</h1>
        <Link to="/dashboard">Back to Dashboard</Link>
      </div>

      <p>Welcome, <strong>{user?.email}</strong>!</p>

      <section style={{ marginTop: '2rem' }}>
        <h2>Real-time Activity Monitor</h2>
        <AdminRealtimeDashboard />
      </section>

      <section style={{ marginTop: '3rem' }}>
        <h2>Content Moderation</h2>
        {isLoading ? (
          <p>Loading content...</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
              <thead>
                <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Title</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Creator</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Access</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {content.length === 0 ? (
                  <tr>
                    <td colSpan={4} style={{ padding: '20px', textAlign: 'center' }}>No content found.</td>
                  </tr>
                ) : (
                  content.map((item) => (
                    <tr key={item.id}>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.title}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.creatorEmail}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.accessLevel}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button 
                            onClick={() => handleDisableContent(item.id)}
                            style={{ padding: '4px 8px', cursor: 'pointer', backgroundColor: '#faad14', border: 'none', borderRadius: '4px', color: 'white' }}
                          >
                            Disable
                          </button>
                          <button 
                            onClick={() => handleDeleteContent(item.id)}
                            style={{ padding: '4px 8px', cursor: 'pointer', backgroundColor: '#f5222d', border: 'none', borderRadius: '4px', color: 'white' }}
                          >
                            Delete
                          </button>
                          <Link to={`/content/${item.id}`} style={{ padding: '4px 8px', backgroundColor: '#1890ff', color: 'white', textDecoration: 'none', borderRadius: '4px' }}>View</Link>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section style={{ marginTop: '3rem' }}>
        <h2>User Subscriptions</h2>
        {isLoading ? (
          <p>Loading subscriptions...</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
              <thead>
                <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>User Email</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Status</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Stripe ID</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Created At</th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.length === 0 ? (
                  <tr>
                    <td colSpan={4} style={{ padding: '20px', textAlign: 'center' }}>No subscriptions found.</td>
                  </tr>
                ) : (
                  subscriptions.map((sub) => (
                    <tr key={sub.id}>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{sub.userEmail}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>
                        <span style={{ 
                          padding: '4px 8px', 
                          borderRadius: '4px', 
                          backgroundColor: sub.status === 'ACTIVE' ? '#e6ffed' : '#fff1f0',
                          color: sub.status === 'ACTIVE' ? '#28a745' : '#cf1322',
                          fontWeight: 'bold',
                          fontSize: '0.85rem'
                        }}>
                          {sub.status}
                        </span>
                      </td>
                      <td style={{ padding: '12px', border: '1px solid #ddd', fontSize: '0.9rem', color: '#666' }}>{sub.stripeSubscriptionId}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd', fontSize: '0.9rem' }}>{new Date(sub.createdAt).toLocaleString()}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section style={{ marginTop: '3rem' }}>
        <h2>Creator Payouts</h2>
        {isLoading ? (
          <p>Loading payouts...</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
              <thead>
                <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>User Email</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Tokens</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Amount</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Status</th>
                  <th style={{ padding: '12px', border: '1px solid #ddd' }}>Date</th>
                </tr>
              </thead>
              <tbody>
                {payouts.length === 0 ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '20px', textAlign: 'center' }}>No payouts found.</td>
                  </tr>
                ) : (
                  payouts.map((p) => (
                    <tr key={p.id}>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{p.userEmail}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>{p.tokenAmount}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>€{p.eurAmount.toFixed(2)}</td>
                      <td style={{ padding: '12px', border: '1px solid #ddd' }}>
                        <span style={{ 
                          padding: '4px 8px', 
                          borderRadius: '4px', 
                          backgroundColor: p.status === 'PAID' ? '#e6ffed' : p.status === 'PENDING' ? '#fff7e6' : '#fff1f0',
                          color: p.status === 'PAID' ? '#28a745' : p.status === 'PENDING' ? '#faad14' : '#cf1322',
                          fontWeight: 'bold',
                          fontSize: '0.85rem'
                        }}>
                          {p.status}
                        </span>
                      </td>
                      <td style={{ padding: '12px', border: '1px solid #ddd', fontSize: '0.9rem' }}>{new Date(p.createdAt).toLocaleString()}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};

export default AdminPanel;
