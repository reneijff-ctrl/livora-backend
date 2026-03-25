import React, { useEffect, useState } from 'react';
import adminService, { AdminCreator } from '../api/adminService';
import { safeRender } from '@/utils/safeRender';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';

const AdminCreatorsPage: React.FC = () => {
  const [creators, setCreators] = useState<AdminCreator[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updatingUserId, setUpdatingUserId] = useState<number | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    fetchCreators();
  }, [user, navigate]);

  const fetchCreators = async () => {
    try {
      setLoading(true);
      const data = await adminService.getCreators();
      setCreators(data.content);
      setError(null);
    } catch (err) {
      setError('Failed to load creators');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (userId: number, newStatus: string) => {
    const confirmMessage = `Are you sure you want to change status to ${newStatus}?`;
    if (!window.confirm(confirmMessage)) return;

    try {
      setUpdatingUserId(userId);
      await adminService.updateCreatorStatus(userId, newStatus);
      // Refresh list
      await fetchCreators();
      showToast('Status updated successfully', 'success');
    } catch (err) {
      // The centralized handler will show a toast for 4xx/5xx errors
      console.error(err);
    } finally {
      setUpdatingUserId(null);
    }
  };

  if (loading) return <Loader type="grid" />;

  const spinnerStyle = `
    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
  `;

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <style>{spinnerStyle}</style>
      <SEO title="Admin - Creator Moderation" />
      <h1 style={{ marginBottom: '2rem', fontSize: '2rem', fontWeight: 'bold' }}>Creator Moderation</h1>

      {error && (
        <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      <div style={{ backgroundColor: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden', border: '1px solid #e5e7eb' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb' }}>
            <tr>
              <th style={{ padding: '1rem' }}>Creator</th>
              <th style={{ padding: '1rem' }}>Email</th>
              <th style={{ padding: '1rem' }}>Status</th>
              <th style={{ padding: '1rem' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {creators.map((creator) => {
              const isUpdating = updatingUserId === creator.id;
              return (
                <tr key={creator.id} style={{ borderBottom: '1px solid #f3f4f6' }}>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ fontWeight: '500', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={creator.displayName || 'Unnamed'}>{safeRender(creator.displayName || 'Unnamed')}</div>
                    <div style={{ fontSize: '0.875rem', color: '#6b7280', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={`@${creator.username || creator.id}`}>@{safeRender(creator.username || creator.id)}</div>
                  </td>
                  <td style={{ padding: '1rem', color: '#4b5563' }}>{safeRender(creator.email)}</td>
                  <td style={{ padding: '1rem' }}>
                    <span style={{
                      padding: '0.25rem 0.75rem',
                      borderRadius: '9999px',
                      fontSize: '0.75rem',
                      fontWeight: '600',
                      backgroundColor: 
                        creator.status === 'ACTIVE' ? '#dcfce7' : 
                        creator.status === 'SUSPENDED' ? '#fee2e2' : '#fef9c3',
                      color: 
                        creator.status === 'ACTIVE' ? '#166534' : 
                        creator.status === 'SUSPENDED' ? '#991b1b' : '#854d0e'
                    }}>
                      {safeRender(creator.status)}
                    </span>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      {creator.status === 'PENDING' && (
                        <button 
                          onClick={() => handleStatusChange(creator.id, 'ACTIVE')}
                          disabled={isUpdating}
                          style={{ 
                            padding: '0.4rem 0.8rem', 
                            borderRadius: '6px', 
                            backgroundColor: isUpdating ? '#9ca3af' : '#4f46e5', 
                            color: '#fff', 
                            border: 'none', 
                            cursor: isUpdating ? 'not-allowed' : 'pointer', 
                            fontSize: '0.875rem',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.5rem',
                            minWidth: '80px',
                            justifyContent: 'center'
                          }}
                        >
                          {isUpdating ? (
                            <span style={{
                              width: '12px',
                              height: '12px',
                              border: '2px solid #fff',
                              borderTop: '2px solid transparent',
                              borderRadius: '50%',
                              animation: 'spin 0.8s linear infinite'
                            }} />
                          ) : 'Approve'}
                        </button>
                      )}
                      {creator.status !== 'SUSPENDED' && (
                        <button 
                          onClick={() => handleStatusChange(creator.id, 'SUSPENDED')}
                          disabled={isUpdating}
                          style={{ padding: '0.4rem 0.8rem', borderRadius: '6px', backgroundColor: isUpdating ? '#9ca3af' : '#ef4444', color: '#fff', border: 'none', cursor: isUpdating ? 'not-allowed' : 'pointer', fontSize: '0.875rem' }}
                        >
                          Suspend
                        </button>
                      )}
                      {creator.status !== 'PENDING' && creator.status !== 'DRAFT' && (
                        <button 
                          onClick={() => handleStatusChange(creator.id, 'PENDING')}
                          disabled={isUpdating}
                          style={{ padding: '0.4rem 0.8rem', borderRadius: '6px', backgroundColor: isUpdating ? '#9ca3af' : '#f59e0b', color: '#fff', border: 'none', cursor: isUpdating ? 'not-allowed' : 'pointer', fontSize: '0.875rem' }}
                        >
                          Mark Pending
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {creators.length === 0 && (
              <tr>
                <td colSpan={4} style={{ padding: '3rem', textAlign: 'center', color: '#6b7280' }}>
                  No creators found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AdminCreatorsPage;
