import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { getCreatorEarnings, CreatorEarnings } from '../api/creatorApi';

const CreatorDashboard: React.FC = () => {
  const { user } = useAuth();
  const [earnings, setEarnings] = useState<CreatorEarnings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchEarnings = async () => {
      try {
        setLoading(true);
        const data = await getCreatorEarnings();
        setEarnings(data);
        setError(null);
      } catch (err: any) {
        console.error('Failed to fetch creator earnings', err);
        setError('Failed to load earnings data.');
      } finally {
        setLoading(false);
      }
    };

    fetchEarnings();
  }, []);

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h1>Creator Dashboard</h1>
      
      <div style={{ 
        backgroundColor: '#f8f9fa', 
        padding: '15px', 
        borderRadius: '8px', 
        marginBottom: '20px',
        border: '1px solid #dee2e6'
      }}>
        <h2>Welcome, {user?.email}</h2>
        <p>Manage your content, streams, and earnings here.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '20px' }}>
        <div style={{ 
          padding: '15px', 
          border: '1px solid #dee2e6', 
          borderRadius: '8px' 
        }}>
          <h3>Earnings</h3>
          {loading ? (
            <p>Loading...</p>
          ) : error ? (
            <p style={{ color: '#dc3545' }}>{error}</p>
          ) : (
            <>
              <p style={{ fontSize: '24px', fontWeight: 'bold' }}>
                {new Intl.NumberFormat('de-DE', { style: 'currency', currency: earnings?.currency || 'EUR' }).format(earnings?.monthlyEarnings || 0)}
              </p>
              <p style={{ color: '#6c757d' }}>Total earnings this month</p>
            </>
          )}
        </div>

        <div style={{ 
          padding: '15px', 
          border: '1px solid #dee2e6', 
          borderRadius: '8px' 
        }}>
          <h3>Active Stream Status</h3>
          <p style={{ color: '#dc3545', fontWeight: 'bold' }}>Offline</p>
          <p style={{ color: '#6c757d' }}>You are not currently streaming</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '10px' }}>
        <button 
          style={{
            padding: '10px 20px',
            backgroundColor: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
          onClick={() => alert('Starting stream...')}
        >
          Start Stream
        </button>
        <button 
          style={{
            padding: '10px 20px',
            backgroundColor: '#28a745',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
          onClick={() => alert('Redirecting to earnings...')}
        >
          View Earnings
        </button>
      </div>
    </div>
  );
};

export default CreatorDashboard;
