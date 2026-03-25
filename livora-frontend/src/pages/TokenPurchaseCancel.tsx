import React from 'react';
import { useNavigate } from 'react-router-dom';
import SEO from '../components/SEO';

const TokenPurchaseCancel: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '4rem 2rem', textAlign: 'center', fontFamily: 'sans-serif', maxWidth: '600px', margin: '0 auto' }}>
      <SEO title="Purchase Canceled" />
      <div style={{ fontSize: '5rem', marginBottom: '1.5rem' }}>🛒</div>
      <h1>Purchase Canceled</h1>
      <p style={{ fontSize: '1.2rem', color: '#666', marginBottom: '3rem' }}>
        Your account was not charged. If you had trouble with the payment, you can try again or contact support.
      </p>
      
      <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
        <button 
          onClick={() => navigate('/tokens/purchase')}
          style={{ 
            padding: '12px 24px', 
            backgroundColor: '#6772e5', 
            color: 'white', 
            border: 'none', 
            borderRadius: '8px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: 'bold'
          }}
        >
          Try Again
        </button>
        <button 
          onClick={() => navigate('/dashboard')}
          style={{ 
            padding: '12px 24px', 
            backgroundColor: '#f3f3f3', 
            color: '#333', 
            border: 'none', 
            borderRadius: '8px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: 'bold'
          }}
        >
          Back to Viewer Hub
        </button>
      </div>
    </div>
  );
};

export default TokenPurchaseCancel;
