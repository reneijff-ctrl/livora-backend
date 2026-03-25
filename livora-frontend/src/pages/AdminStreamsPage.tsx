import React, { useEffect, useState } from 'react';
import adminService from '../api/adminService';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';

const AdminStreamsPage: React.FC = () => {
  const [streams, setStreams] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [stoppingId, setStoppingId] = useState<string | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    fetchActiveStreams(page);
  }, [user, navigate, page]);

  const fetchActiveStreams = async (pageNum: number) => {
    try {
      setLoading(true);
      const data = await adminService.getStreams(pageNum, 20);
      setStreams(data.content || []);
      setTotalPages(data.totalPages || 1);
      setError(null);
    } catch (err) {
      setError('Failed to load active streams');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleStopStream = async (streamId: string) => {
    if (!window.confirm('Are you sure you want to FORCE STOP this stream?')) return;

    try {
      setStoppingId(streamId);
      await adminService.stopStream(streamId);
      await fetchActiveStreams(page);
      showToast('Stream stopped successfully', 'success');
    } catch (err) {
      console.error(err);
    } finally {
      setStoppingId(null);
    }
  };

  if (loading) return <Loader type="grid" />;

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <SEO title="Admin - Active Streams" />
      <h1 style={{ marginBottom: '2rem', fontSize: '2rem', fontWeight: 'bold' }}>Active Streams</h1>

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
              <th style={{ padding: '1rem' }}>Stream ID</th>
              <th style={{ padding: '1rem' }}>Title</th>
              <th style={{ padding: '1rem' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {streams.map((stream) => (
              <tr key={stream.streamId} style={{ borderBottom: '1px solid #f3f4f6' }}>
                <td style={{ padding: '1rem' }}>
                  <div style={{ fontWeight: '500' }}>{stream.creatorUsername || 'Unknown'}</div>
                </td>
                <td style={{ padding: '1rem', color: '#4b5563', fontSize: '0.875rem' }}>{stream.streamId}</td>
                <td style={{ padding: '1rem', color: '#4b5563' }}>{stream.creatorUsername ? `${stream.creatorUsername}'s Stream` : 'Untitled Stream'}</td>
                <td style={{ padding: '1rem' }}>
                  <button 
                    onClick={() => handleStopStream(stream.streamId)}
                    disabled={stoppingId === stream.streamId}
                    style={{ 
                      padding: '0.4rem 0.8rem', 
                      borderRadius: '6px', 
                      backgroundColor: stoppingId === stream.streamId ? '#9ca3af' : '#ef4444', 
                      color: '#fff', 
                      border: 'none', 
                      cursor: stoppingId === stream.streamId ? 'not-allowed' : 'pointer', 
                      fontSize: '0.875rem' 
                    }}
                  >
                    {stoppingId === stream.streamId ? 'Stopping...' : 'Force Stop'}
                  </button>
                </td>
              </tr>
            ))}
            {streams.length === 0 && (
              <tr>
                <td colSpan={4} style={{ padding: '3rem', textAlign: 'center', color: '#6b7280' }}>
                  No active streams found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '1rem' }}>
        <button 
          disabled={page <= 0} 
          onClick={() => setPage(page - 1)}
          style={{ padding: '0.5rem 1rem', borderRadius: '6px', border: '1px solid #d1d5db', cursor: page <= 0 ? 'not-allowed' : 'pointer' }}
        >
          Previous
        </button>
        <span style={{ color: '#4b5563' }}>Page {page + 1} of {totalPages}</span>
        <button 
          disabled={page + 1 >= totalPages} 
          onClick={() => setPage(page + 1)}
          style={{ padding: '0.5rem 1rem', borderRadius: '6px', border: '1px solid #d1d5db', cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer' }}
        >
          Next
        </button>
      </div>
    </div>
  );
};

export default AdminStreamsPage;
