import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import SEO from '../components/SEO';
import apiClient from '../api/apiClient';
import AdminRealtimeDashboard from '../components/AdminRealtimeDashboard';
import adminContentService from '../api/adminContentService';
import { ContentItem } from '../api/contentService';
import { showToast } from '../components/Toast';
import AdminReports from '../components/AdminReports';
import FraudDashboard from '../components/FraudDashboard';
import Loader from '../components/Loader';

interface AdminSubscription {
  id: string;
  userEmail: string;
  status: string;
  createdAt: string;
}

interface AdminPayout {
  id: string;
  userEmail: string;
  amount: number;
  status: string;
  createdAt: string;
}

const AdminPanel: React.FC = () => {
  const { user, authLoading } = useAuth();
  const [subscriptions, setSubscriptions] = useState<AdminSubscription[]>([]);
  const [page, setPage] = useState(0);
  const [subTotalPages, setSubTotalPages] = useState(1);

  const [payouts, setPayouts] = useState<AdminPayout[]>([]);
  const [payoutPage, setPayoutPage] = useState(0);
  const [payoutTotalPages, setPayoutTotalPages] = useState(1);

  const [content, setContent] = useState<ContentItem[]>([]);
  const [contentPage, setContentPage] = useState(0);
  const [contentTotalPages, setContentTotalPages] = useState(1);

  const [isLoading, setIsLoading] = useState(true);

  const fetchSubscriptions = async (page: number) => {
    try {
      const res = await apiClient.get<any>(`/admin/subscriptions?page=${page}&size=20`);
      setSubscriptions(res.data.content || []);
      setSubTotalPages(res.data.totalPages || 1);
    } catch (e) {
      console.error('Failed to fetch subscriptions', e);
    }
  };

  const fetchPayouts = async (page: number) => {
    try {
      const res = await apiClient.get<any>(`/admin/payouts?page=${page}&size=20`);
      setPayouts(res.data.content || []);
      setPayoutTotalPages(res.data.totalPages || 1);
    } catch (e) {
      console.error('Failed to fetch payouts', e);
    }
  };

  const fetchContent = async (page: number) => {
    try {
      const res = await adminContentService.getAllContent(page, 20);
      setContent(res.content || []);
      setContentTotalPages(res.totalPages || 1);
    } catch (e) {
      console.error('Failed to fetch content', e);
    }
  };

  useEffect(() => {
    if (!user || authLoading) return;
    setIsLoading(true);
    fetchSubscriptions(page).finally(() => setIsLoading(false));
  }, [user, authLoading, page]);

  useEffect(() => {
    if (!user || authLoading) return;
    setIsLoading(true);
    fetchPayouts(payoutPage).finally(() => setIsLoading(false));
  }, [user, authLoading, payoutPage]);

  useEffect(() => {
    if (!user || authLoading) return;
    setIsLoading(true);
    fetchContent(contentPage).finally(() => setIsLoading(false));
  }, [user, authLoading, contentPage]);

  const handleDisableContent = async (id: string) => {
    try {
      await adminContentService.disableContent(id);
      showToast('Content disabled.', 'success');
      setContent(prev =>
        prev.map(item =>
          item.id === id ? { ...item, status: 'DISABLED' } : item
        )
      );
    } catch (error) {
      showToast('Failed to disable content.', 'error');
    }
  };

  const handleDeleteContent = async (id: string) => {
    if (!window.confirm('Permanent delete?')) return;
    try {
      await adminContentService.deleteContent(id);
      showToast('Content deleted.', 'success');
      setContent(prev => prev.filter(item => item.id !== id));
    } catch (error) {
      showToast('Failed to delete content.', 'error');
    }
  };

  return (
    <div style={{ padding: '2rem', fontFamily: 'sans-serif' }}>
      <SEO title="Admin Panel" canonical="/admin" />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>🛡️ Admin Dashboard</h1>
        <Link to="/dashboard">Back to Viewer Hub</Link>
      </div>

      <p>Welcome, <strong>{user?.email}</strong>!</p>

      <section style={{ marginTop: '2rem' }}>
        <h2>Real-time Activity Monitor</h2>
        <AdminRealtimeDashboard />
      </section>

      <section style={{ marginTop: '3rem' }}>
        <AdminReports />
      </section>

      <section style={{ marginTop: '3rem' }}>
        <FraudDashboard />
      </section>

      {isLoading ? (
        <div style={{ padding: '2rem' }}>
          <Loader type="skeleton" />
        </div>
      ) : (
        <>
          <section style={{ marginTop: '3rem' }}>
            <h2>Content Moderation</h2>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Title</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Creator</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Access</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Status</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {content.length === 0 ? (
                    <tr>
                      <td colSpan={5} style={{ padding: '20px', textAlign: 'center' }}>No content found.</td>
                    </tr>
                  ) : (
                    content.map((item) => (
                      <tr key={item.id}>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.title}</td>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.creatorEmail}</td>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>{item.accessLevel}</td>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>
                          <span style={{ 
                            padding: '4px 8px', 
                            borderRadius: '4px', 
                            backgroundColor: item.status === 'ACTIVE' ? '#e6ffed' : '#fff1f0',
                            color: item.status === 'ACTIVE' ? '#28a745' : '#cf1322',
                            fontWeight: 'bold',
                            fontSize: '0.85rem'
                          }}>
                            {item.status || 'ACTIVE'}
                          </span>
                        </td>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>
                          <div style={{ display: 'flex', gap: '0.5rem' }}>
                            <button 
                              onClick={() => handleDisableContent(item.id)}
                              disabled={item.status === 'DISABLED'}
                              style={{ 
                                padding: '4px 8px', 
                                cursor: item.status === 'DISABLED' ? 'not-allowed' : 'pointer', 
                                backgroundColor: item.status === 'DISABLED' ? '#ddd' : '#faad14', 
                                border: 'none', 
                                borderRadius: '4px', 
                                color: 'white' 
                              }}
                            >
                              {item.status === 'DISABLED' ? 'Disabled' : 'Disable'}
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
              <div style={{ marginTop: '1rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <button disabled={contentPage <= 0} onClick={() => setContentPage(contentPage - 1)}>Prev</button>
                <span>Page {contentPage + 1} of {contentTotalPages}</span>
                <button disabled={contentPage + 1 >= contentTotalPages} onClick={() => setContentPage(contentPage + 1)}>Next</button>
              </div>
            </div>
          </section>

          <section style={{ marginTop: '3rem' }}>
            <h2>User Subscriptions</h2>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>User Email</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Status</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Created At</th>
                  </tr>
                </thead>
                <tbody>
                  {subscriptions.length === 0 ? (
                    <tr>
                      <td colSpan={3} style={{ padding: '20px', textAlign: 'center' }}>No subscriptions found.</td>
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
                        <td style={{ padding: '12px', border: '1px solid #ddd', fontSize: '0.9rem' }}>{sub.createdAt ? new Date(sub.createdAt).toLocaleString() : 'N/A'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
              <div style={{ marginTop: '1rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <button disabled={page <= 0} onClick={() => setPage(page - 1)}>Prev</button>
                <span>Page {page + 1} of {subTotalPages}</span>
                <button disabled={page + 1 >= subTotalPages} onClick={() => setPage(page + 1)}>Next</button>
              </div>
            </div>
          </section>

          <section style={{ marginTop: '3rem' }}>
            <h2>Creator Payouts</h2>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '1rem' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f3f3f3', textAlign: 'left' }}>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>User Email</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Amount</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Status</th>
                    <th style={{ padding: '12px', border: '1px solid #ddd' }}>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {payouts.length === 0 ? (
                    <tr>
                      <td colSpan={4} style={{ padding: '20px', textAlign: 'center' }}>No payouts found.</td>
                    </tr>
                  ) : (
                    payouts.map((p) => (
                      <tr key={p.id}>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>{p.userEmail}</td>
                        <td style={{ padding: '12px', border: '1px solid #ddd' }}>€{(p.amount || 0).toFixed(2)}</td>
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
                        <td style={{ padding: '12px', border: '1px solid #ddd', fontSize: '0.9rem' }}>{p.createdAt ? new Date(p.createdAt).toLocaleString() : 'N/A'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
              <div style={{ marginTop: '1rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <button disabled={payoutPage <= 0} onClick={() => setPayoutPage(payoutPage - 1)}>Prev</button>
                <span>Page {payoutPage + 1} of {payoutTotalPages}</span>
                <button disabled={payoutPage + 1 >= payoutTotalPages} onClick={() => setPayoutPage(payoutPage + 1)}>Next</button>
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default AdminPanel;
