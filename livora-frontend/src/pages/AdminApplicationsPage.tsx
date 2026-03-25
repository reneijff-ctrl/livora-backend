import React, { useEffect, useState } from 'react';
import adminService, { CreatorApplication } from '../api/adminService';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';

const AdminApplicationsPage: React.FC = () => {
  const [applications, setApplications] = useState<CreatorApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [selectedApp, setSelectedApp] = useState<CreatorApplication | null>(null);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [showViewModal, setShowViewModal] = useState(false);
  const [reviewNotes, setReviewNotes] = useState('');
  
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    fetchApplications();
  }, [user, navigate]);

  const fetchApplications = async () => {
    try {
      setLoading(true);
      const data = await adminService.getCreatorVerifications(undefined, 0, 50);
      setApplications(data.content.map((v: any) => ({
        id: v.id,
        userId: v.creator?.user?.id,
        username: v.creator?.user?.username,
        email: v.creator?.user?.email,
        status: v.status,
        submittedAt: v.createdAt,
        termsAccepted: true,
        ageVerified: true
      })));
      setError(null);
    } catch (err) {
      setError('Failed to load applications');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id: number) => {
    if (!window.confirm('Are you sure you want to approve this application? The user will be upgraded to CREATOR role.')) return;

    try {
      setProcessingId(id);
      await adminService.approveVerification(id);
      showToast('Application approved successfully', 'success');
      await fetchApplications();
    } catch (err) {
      console.error(err);
    } finally {
      setProcessingId(null);
    }
  };

  const handleOpenReject = (app: CreatorApplication) => {
    setSelectedApp(app);
    setReviewNotes('');
    setShowRejectModal(true);
  };

  const handleReject = async () => {
    if (!selectedApp) return;
    if (!reviewNotes.trim()) {
      showToast('Please provide review notes for rejection', 'error');
      return;
    }

    try {
      setProcessingId(selectedApp.id);
      await adminService.rejectVerification(selectedApp.id, reviewNotes);
      showToast('Application rejected', 'success');
      setShowRejectModal(false);
      await fetchApplications();
    } catch (err) {
      console.error(err);
    } finally {
      setProcessingId(null);
    }
  };

  const handleView = (app: CreatorApplication) => {
    setSelectedApp(app);
    setShowViewModal(true);
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
      <SEO title="Admin - Creator Applications" />
      <h1 style={{ marginBottom: '2rem', fontSize: '2rem', fontWeight: 'bold' }}>Creator Applications</h1>

      {error && (
        <div style={{ backgroundColor: '#fee2e2', color: '#b91c1c', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      <div style={{ backgroundColor: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', overflow: 'hidden', border: '1px solid #e5e7eb' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb' }}>
            <tr>
              <th style={{ padding: '1rem' }}>Username</th>
              <th style={{ padding: '1rem' }}>Email</th>
              <th style={{ padding: '1rem' }}>Submitted At</th>
              <th style={{ padding: '1rem' }}>Status</th>
              <th style={{ padding: '1rem' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {applications.map((app) => {
              const isProcessing = processingId === app.id;
              return (
                <tr key={app.id} style={{ borderBottom: '1px solid #f3f4f6' }}>
                  <td style={{ padding: '1rem', fontWeight: '500' }}>{app.username}</td>
                  <td style={{ padding: '1rem', color: '#4b5563' }}>{app.email}</td>
                  <td style={{ padding: '1rem', color: '#6b7280' }}>
                    {new Date(app.submittedAt).toLocaleString()}
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <span style={{
                      padding: '0.25rem 0.75rem',
                      borderRadius: '9999px',
                      fontSize: '0.75rem',
                      fontWeight: '600',
                      backgroundColor: 
                        app.status === 'APPROVED' ? '#dcfce7' : 
                        app.status === 'REJECTED' ? '#fee2e2' : 
                        app.status === 'UNDER_REVIEW' ? '#dbeafe' : '#fef9c3',
                      color: 
                        app.status === 'APPROVED' ? '#166534' : 
                        app.status === 'REJECTED' ? '#991b1b' : 
                        app.status === 'UNDER_REVIEW' ? '#1e40af' : '#854d0e'
                    }}>
                      {app.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td style={{ padding: '1rem' }}>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button 
                        onClick={() => handleView(app)}
                        style={{ padding: '0.4rem 0.8rem', borderRadius: '6px', backgroundColor: '#f3f4f6', color: '#374151', border: '1px solid #d1d5db', cursor: 'pointer', fontSize: '0.875rem' }}
                      >
                        View
                      </button>
                      
                      {app.status !== 'APPROVED' && app.status !== 'REJECTED' && (
                        <>
                          <button 
                            onClick={() => handleApprove(app.id)}
                            disabled={isProcessing}
                            style={{ 
                              padding: '0.4rem 0.8rem', 
                              borderRadius: '6px', 
                              backgroundColor: isProcessing ? '#9ca3af' : '#10b981', 
                              color: '#fff', 
                              border: 'none', 
                              cursor: isProcessing ? 'not-allowed' : 'pointer', 
                              fontSize: '0.875rem',
                              minWidth: '80px'
                            }}
                          >
                            {isProcessing ? '...' : 'Approve'}
                          </button>
                          <button 
                            onClick={() => handleOpenReject(app)}
                            disabled={isProcessing}
                            style={{ padding: '0.4rem 0.8rem', borderRadius: '6px', backgroundColor: isProcessing ? '#9ca3af' : '#ef4444', color: '#fff', border: 'none', cursor: isProcessing ? 'not-allowed' : 'pointer', fontSize: '0.875rem' }}
                          >
                            Reject
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {applications.length === 0 && (
              <tr>
                <td colSpan={5} style={{ padding: '3rem', textAlign: 'center', color: '#6b7280' }}>
                  No creator applications found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Reject Modal */}
      {showRejectModal && (
        <div style={modalStyles.overlay}>
          <div style={modalStyles.modal}>
            <div style={modalStyles.header}>
              <h2 style={modalStyles.title}>Reject Application</h2>
              <button onClick={() => setShowRejectModal(false)} style={modalStyles.closeButton}>&times;</button>
            </div>
            <div style={{ padding: '1.5rem' }}>
              <p style={{ marginBottom: '1rem', color: '#4b5563' }}>
                Rejecting application for <strong>{selectedApp?.username}</strong>.
              </p>
              <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: '600' }}>Review Notes (visible to user)</label>
              <textarea 
                value={reviewNotes}
                onChange={(e) => setReviewNotes(e.target.value)}
                placeholder="Reason for rejection..."
                style={{ width: '100%', padding: '0.75rem', borderRadius: '6px', border: '1px solid #ddd', minHeight: '120px', marginBottom: '1.5rem' }}
              />
              <div style={{ display: 'flex', gap: '1rem' }}>
                <button 
                  onClick={() => setShowRejectModal(false)} 
                  style={{ flex: 1, padding: '0.75rem', borderRadius: '6px', border: '1px solid #ddd', backgroundColor: '#fff', cursor: 'pointer' }}
                >
                  Cancel
                </button>
                <button 
                  onClick={handleReject}
                  disabled={processingId !== null}
                  style={{ flex: 1, padding: '0.75rem', borderRadius: '6px', border: 'none', backgroundColor: '#ef4444', color: '#fff', fontWeight: '600', cursor: processingId !== null ? 'not-allowed' : 'pointer' }}
                >
                  {processingId !== null ? 'Rejecting...' : 'Confirm Reject'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* View Modal */}
      {showViewModal && (
        <div style={modalStyles.overlay}>
          <div style={modalStyles.modal}>
            <div style={modalStyles.header}>
              <h2 style={modalStyles.title}>Application Details</h2>
              <button onClick={() => setShowViewModal(false)} style={modalStyles.closeButton}>&times;</button>
            </div>
            <div style={{ padding: '1.5rem' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
                <div>
                  <label style={modalStyles.detailLabel}>Username</label>
                  <div style={modalStyles.detailValue}>{selectedApp?.username}</div>
                </div>
                <div>
                  <label style={modalStyles.detailLabel}>Email</label>
                  <div style={modalStyles.detailValue}>{selectedApp?.email}</div>
                </div>
                <div>
                  <label style={modalStyles.detailLabel}>Status</label>
                  <div style={modalStyles.detailValue}>{selectedApp?.status}</div>
                </div>
                <div>
                  <label style={modalStyles.detailLabel}>Submitted At</label>
                  <div style={modalStyles.detailValue}>{selectedApp && new Date(selectedApp.submittedAt).toLocaleString()}</div>
                </div>
                <div>
                  <label style={modalStyles.detailLabel}>Terms Accepted</label>
                  <div style={modalStyles.detailValue}>{selectedApp?.termsAccepted ? '✅ Yes' : '❌ No'}</div>
                </div>
                <div>
                  <label style={modalStyles.detailLabel}>Age Verified</label>
                  <div style={modalStyles.detailValue}>{selectedApp?.ageVerified ? '✅ Yes' : '❌ No'}</div>
                </div>
              </div>
              
              {selectedApp?.approvedAt && (
                <div style={{ marginBottom: '1rem' }}>
                   <label style={modalStyles.detailLabel}>Approved At</label>
                   <div style={modalStyles.detailValue}>{new Date(selectedApp.approvedAt).toLocaleString()}</div>
                </div>
              )}

              {selectedApp?.reviewNotes && (
                <div style={{ marginBottom: '1rem' }}>
                  <label style={modalStyles.detailLabel}>Review Notes</label>
                  <div style={{ ...modalStyles.detailValue, backgroundColor: '#f9fafb', padding: '0.75rem', borderRadius: '6px', border: '1px solid #e5e7eb' }}>
                    {selectedApp.reviewNotes}
                  </div>
                </div>
              )}

              <button 
                onClick={() => setShowViewModal(false)} 
                style={{ width: '100%', padding: '0.75rem', borderRadius: '6px', border: '1px solid #ddd', backgroundColor: '#fff', cursor: 'pointer', fontWeight: '600' }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const modalStyles: { [key: string]: React.CSSProperties } = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modal: {
    backgroundColor: '#fff',
    borderRadius: '12px',
    width: '90%',
    maxWidth: '500px',
    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    overflow: 'hidden',
  },
  header: {
    padding: '1.25rem 1.5rem',
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
  },
  title: {
    margin: 0,
    fontSize: '1.25rem',
    fontWeight: '700',
    color: '#111827',
  },
  closeButton: {
    background: 'none',
    border: 'none',
    fontSize: '1.5rem',
    cursor: 'pointer',
    color: '#9ca3af',
  },
  detailLabel: {
    display: 'block',
    fontSize: '0.75rem',
    fontWeight: '600',
    color: '#6b7280',
    textTransform: 'uppercase',
    marginBottom: '0.25rem',
  },
  detailValue: {
    fontSize: '1rem',
    color: '#111827',
    fontWeight: '500',
  }
};

export default AdminApplicationsPage;
