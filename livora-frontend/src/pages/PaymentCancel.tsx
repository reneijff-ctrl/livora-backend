import React from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

const PaymentCancel: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const creatorId = searchParams.get('creatorId');

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '70vh',
    textAlign: 'center',
    padding: '2rem',
    fontFamily: 'system-ui, -apple-system, sans-serif'
  };

  const handleBackToProfile = () => {
    if (creatorId) {
      navigate(`/creators/${creatorId}`);
    } else {
      navigate('/explore');
    }
  };

  return (
    <div style={containerStyle}>
      <div style={{ fontSize: '5rem', marginBottom: '1rem' }}>❌</div>
      <h1 style={{ color: '#f87171' }}>Payment cancelled</h1>
      <p style={{ color: '#71717A', marginBottom: '2rem', maxWidth: '400px', lineHeight: '1.6' }}>
        Your transaction was cancelled. No charges were made to your account.
      </p>
      <div style={{ display: 'flex', gap: '1rem' }}>
        <button
          onClick={handleBackToProfile}
          style={{
            padding: '0.875rem 2rem',
            backgroundColor: '#6366f1',
            color: 'white',
            border: 'none',
            borderRadius: '12px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: '800',
            boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)'
          }}
        >
          {creatorId ? 'Back to Creator Profile' : 'Back to Explore'}
        </button>
        <button
          onClick={() => navigate('/dashboard')}
          style={{
            padding: '0.875rem 2rem',
            backgroundColor: 'rgba(255, 255, 255, 0.05)',
            color: '#F4F4F5',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '12px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: '700'
          }}
        >
          Go to Viewer Hub
        </button>
      </div>
    </div>
  );
};

export default PaymentCancel;
